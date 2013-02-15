package com.scurab.java.ftpleecher;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Core implementation of downloading
 */
public class FTPDownloadThread extends Thread implements Runnable, Cloneable {

    /**
     * Default time for calling {@link FTPDownloadListener#onDownloadProgress(FTPDownloadThread, double, double)} *
     */
    private static final int NOTIFY = 1000;
    /**
     * Index is only for indexing in table to fire change only for row *
     */
    private int mIndex = -1;
    /**
     * just sync lock *
     */
    private Object mLock = new Object();
    /**
     * Base configuration for downloading process
     */
    private final FTPContext mConfig;
    /**
     * Current FTP state *
     */
    private State mState;
    /**
     * already downloaded len
     */
    private long mDownloaded;
    /**
     * current avg speed in time {@link #NOTIFY}ms
     */
    private int mSpeed;
    /**
     * Collection for listeners *
     */
    private List<FTPDownloadListener> mListeners = new ArrayList<FTPDownloadListener>();
    /**
     * Keep somewhere last exception for UI layer
     */
    private Throwable mException;

    /**
     * Base thread states *
     */
    public enum State {
        Created, Connecting, Connected, Downloading, Error, WaitingForRetry, Paused, Downloaded, Merging, Finished;
    }

    protected FTPDownloadThread(FTPContext config) {
        if (config == null) {
            throw new IllegalArgumentException("FTPContext is null!");
        }

        mConfig = config;
        setFtpState(State.Created);

        if (config.parts > 1) {
            setName(String.format("%s %03d", mConfig.server, config.part));
        } else {
            setName(mConfig.server);
        }
    }

    @Override
    public void run() {
        downloadImpl();
    }

    /**
     * Complete download implementation
     */
    private void downloadImpl() {
        FTPClient ftpClient = null;
        FileOutputStream fileOutputStream = null;
        InputStream input = null;
        while (!(mState == State.Downloaded || mState == State.Finished)) {
            try {
                File f = getLocalFile();
                mConfig.localFile = f;
                long alreadyDownloaded = onPreInit(f);

                //state can be set in getLocalFile when pieceLen and fileSize are same
                if (mState == State.Downloaded) {
                    setFtpState(mConfig.parts == 1 ? State.Finished : State.Downloaded);
                    break;
                }

                //connect
                setFtpState(State.Connecting);
                ftpClient = FTPFactory.openFtpClient(mConfig);
                setFtpState(State.Connected);

                //init start values
                final long startOffset = (mConfig.part * mConfig.globalPieceLength) + alreadyDownloaded;
                ftpClient.setRestartOffset(startOffset);

                //create streams
                input = ftpClient.retrieveFileStream(mConfig.remoteFullPath);
                if (input == null || ftpClient.getReplyCode() >= 300) {
                    throw new FatalFTPException(getFtpCodeName(ftpClient.getReplyCode()) + "\n" + ftpClient.getReplyString());
                }
                fileOutputStream = new FileOutputStream(f);

                setFtpState(State.Downloading);

                //download
                byte[] buffer = new byte[mConfig.bufferSize];
                int len = 0;

                mDownloaded = alreadyDownloaded;
                long downloadedInSec = 0;

                long lastNotify = System.currentTimeMillis();
                //region datacopy
                while ((len = input.read(buffer)) != -1) {
                    long now = System.currentTimeMillis();
                    //call notification methods
                    if ((now - lastNotify) > NOTIFY) {
                        //count avg speed in defined time for 1s
                        int v = (int) (downloadedInSec / (float) NOTIFY) * 1000;
                        onDownloadProgress(mDownloaded, v);
                        mSpeed = v;
                        downloadedInSec = 0;
                        lastNotify = now;
                    }
                    final int subLen = (int) (mConfig.currentPieceLength - mDownloaded);
                    final int realLenToWrite = Math.min(len, subLen);
                    fileOutputStream.write(buffer, 0, realLenToWrite);

                    //save values for notification
                    mDownloaded += realLenToWrite;
                    downloadedInSec += realLenToWrite;

                    //stop, we are complete
                    if (mDownloaded == mConfig.currentPieceLength) {
                        break;
                    }

                    //wait if user paused downloading
                    //FIXME:lock for state
                    if (mState == State.Paused) {
                        synchronized (mLock) {
                            mLock.wait();
                        }
                    }
                }
                //endregion datacopy

                //close and finish
                ftpClient.disconnect();

                if (mConfig.currentPieceLength == mDownloaded) {
                    setFtpState(mConfig.parts == 1 ? State.Finished : State.Downloaded);
                }//otherwise just restart process and again

            } catch (FatalFTPException ffe) {
                mException = ffe;
                onFatalError(ffe);
                setFtpState(State.Paused);
                synchronized (mLock) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mException = null;
            } catch (IOException e) {
                onError(e);
                setFtpState(State.WaitingForRetry);
                //wait 10s and try again
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e1) { /* ignore wake */ }
            } catch (Throwable t) {
                mException = t;
                onFatalError(new FatalFTPException(t));
                setFtpState(State.Error);
                synchronized (mLock) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) { /* ignore wake */ }
                }
            } finally {
                //region close and release everything
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (Exception e) {/**/}
                }
                if (input != null) {
                    try {
                        input.close();
                    } catch (Exception e) {/**/}
                }
                if (ftpClient != null) {
                    try {
                        ftpClient.disconnect();
                    } catch (Exception e) {/**/}
                }
                //endregion
            }
        }
    }

    /**
     * Help method for translating number ftp codes to readable string<br/>
     * Uses reflection
     *
     * @param value
     * @return
     */
    private String getFtpCodeName(int value) {
        Field[] fields = FTPReply.class.getDeclaredFields();
        for (Field f : fields) {
            if (f.getType() == int.class) {
                try {
                    if (value == f.getInt(null)) {
                        return f.getName();
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return "";
    }

    /**
     * Pre init downloading
     *
     * @return length of already mDownloaded pice
     * @throws FatalFTPException
     */
    private long onPreInit(File f) throws FatalFTPException {
        long alreadyDownloaded = 0;
        //for restart
        if (!mConfig.resume) {
            //try delete already existing file
            if (f.exists() && !f.delete()) {
                //if we can't delete it, just stop and wait
                throw new FatalFTPException("Unable to delete file:" + f.getAbsolutePath());
            }
        } else {
            //we have something, so download it
            alreadyDownloaded = f.length();
            if (alreadyDownloaded > mConfig.currentPieceLength) {
                throw new FatalFTPException("Already downloaded part is bigger then defined piece length!\nFile:" + f.getAbsolutePath());
            } else {
                setFtpState(State.Downloaded);
            }
        }
        return alreadyDownloaded;
    }

    /**
     * Get local file according to current configuration<br/>
     * Checks if {@link FTPContext#outputDirectory} exists (if not -> creating)
     *
     * @return
     * @throws FatalFTPException if outputDirector cant be created
     */
    private File getLocalFile() throws FatalFTPException {
        String[] urlParts = mConfig.remoteFullPath.split("/");
        String fileName = urlParts[urlParts.length - 1];

        //check folder
        File folder = new File(mConfig.outputDirectory);
        if (!folder.exists() && !folder.mkdir()) {
            throw new FatalFTPException("Unable to create folder:" + mConfig.outputDirectory);
        }

        //gen file
        String localFile;
        if (mConfig.parts == 1) {
            localFile = String.format(mConfig.localSingleFileTemplate, mConfig.outputDirectory, fileName);
        } else {
            localFile = String.format(mConfig.localMultipleFilesTemplate, mConfig.outputDirectory, fileName, mConfig.part);
        }
        return new File(localFile);
    }

    /**
     * Returns current ftp state {@link State}
     *
     * @return
     */
    public State getFtpState() {
        return mState;
    }

    /**
     * Set current state for thread and notify listeners
     *
     * @param state
     */
    protected void setFtpState(State state) {
        mState = state;
        mSpeed = 0;
        synchronized (mListeners) {
            for (FTPDownloadListener l : mListeners) {
                l.onStatusChange(this, state);
            }
        }
    }

    /**
     * Register event listener for this thread
     *
     * @param listener
     */
    public void registerListener(FTPDownloadListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    /**
     * Unregister event listener for this thread
     *
     * @param listener
     */
    public void unregisterListener(FTPDownloadListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    //region Notification
    private void onFatalError(FatalFTPException ffe) {
        synchronized (mListeners) {
            for (FTPDownloadListener l : mListeners) {
                l.onFatalError(this, ffe);
            }
        }
    }

    private void onError(Exception e) {
        synchronized (mListeners) {
            for (FTPDownloadListener l : mListeners) {
                l.onError(this, e);
            }
        }
    }

    /**
     * Notification
     *
     * @param downloaded      for whole pcs
     * @param downloadedInSec fot last second
     */
    private void onDownloadProgress(long downloaded, long downloadedInSec) {
        synchronized (mListeners) {
            for (FTPDownloadListener l : mListeners) {
                l.onDownloadProgress(this, downloaded, downloadedInSec);
            }
        }
    }
    //endregion

    /**
     * Get current clone of context => any changes are useless
     *
     * @return
     */
    public FTPContext getContext() {
        return mConfig.clone();
    }

    @Override
    protected FTPDownloadThread clone() throws CloneNotSupportedException {
        return new FTPDownloadThread(mConfig);
    }

    /**
     * Put this thread into paused state
     *
     * @param pause
     */
    public void setPause(boolean pause) {
        if (pause) {
            if (mState == State.Downloading) {
                synchronized (mLock) {
                    mState = State.Paused;
                }
            }
        } else {
            if (mState == State.Paused) {
                synchronized (mLock) {
                    mState = State.Downloading;
                    mLock.notifyAll();
                }
            }
        }
    }

    /**
     * Get last exception or null if there wasn't any exception
     *
     * @return
     */
    public Throwable getException() {
        return mException;
    }

    /**
     * Get current thread index
     *
     * @return
     */
    public int getIndex() {
        return mIndex;
    }

    public void setIndex(int index) {
        mIndex = index;
    }

    /**
     * Get actual size of downloaded data
     *
     * @return
     */
    public long getDownloaded() {
        return mDownloaded;
    }

    /**
     * Get current actual speed<br/>
     * Speed is avg speed per second
     *
     * @return
     */
    public int getSpeed() {
        return mSpeed;
    }
}
