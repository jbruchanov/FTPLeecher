package com.scurab.java.ftpleecher;

import com.scurab.java.ftpleecher.tools.TextUtils;
import org.apache.commons.net.ftp.FTPClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Core implementation of downloading
 */
public class FTPDownloadThread implements Runnable {

    /**
     * Default wait time for any error to retry
     */
    public static final int DEFAULT_WAIT = 10000;
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
    private final Object mLock = new Object();
    /**
     * Base configuration for downloading process
     */
    private final FTPContext mConfig;
    /**
     * Current FTP state *
     */
    private State mState = State.Created;

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
    private final List<FTPDownloadListener> mListeners = new ArrayList<FTPDownloadListener>();
    /**
     * Keep somewhere last exception for UI layer
     */
    private Throwable mException;

    private DownloadTask mParentTask;

    private String mThreadName;

    private Thread mWorkingThread;

    private static final int FATAL_ERROR_TO_STOP = 5;

    private int mFatalErrorCounter = 0;

    /**
     * Base thread states *
     */
    public enum State {
        //created must last to go trough all states and
        Created, Started, Connecting, Connected, Downloading, Error, FatalError, WaitingForRetry, Paused, Downloaded, Merging, Finished
    }

    protected FTPDownloadThread(FTPContext config) {
        if (config == null) {
            throw new IllegalArgumentException("FTPContext is null!");
        }
        mConfig = config;

        if (config.parts > 1) {
            mThreadName = (String.format("%s %03d", mConfig.fileName, config.part));
        } else {
            mThreadName = (mConfig.fileName);
        }
    }

    public synchronized boolean start(){
        if(mWorkingThread != null){
            return false;
        }else{
            mWorkingThread = new Thread(this);
            mWorkingThread.setName(mThreadName);
            mWorkingThread.start();
            return true;
        }
    }

    public synchronized void restart(){
        if(mWorkingThread != null){
            throw new IllegalStateException("Thread already started");
        }else{
            if(mState != State.Created){
                setFtpState(State.Created);
            }
        }
    }

    @Override
    public void run() {
        mFatalErrorCounter = 0;
        do {
            mException = null;
            //don't inform about this state, it's just flag that thread is already running
            setFtpState(State.Started);
            downloadImpl();
            if (!(mState == State.Downloaded || mState == State.Finished || mState == State.FatalError)) {
                mException = new Exception("WTF_ERROR Unexpected Leaving downloading process! State:" + mState + ", try restart this thread");
                mState = State.Error;
            }
        }
        while (!(mState == State.Downloaded || mState == State.Finished || mState == State.FatalError)) ;
        synchronized (this) {
            mWorkingThread = null;
        }
    }

    /**
     * Complete download implementation
     */
    protected void downloadImpl() {
        FTPClient ftpClient = null;
        FileOutputStream fileOutputStream = null;
        InputStream input = null;

        boolean forceResume = false;
        while (mState == State.Started || mState == State.Downloading) {
            try {
                File f = getLocalFile();
                mConfig.localFile = f;
                long alreadyDownloaded = onPreInit(f, forceResume);

                forceResume = false;
                //state can be set in getLocalFile when pieceLen and fileSize are same
                if (mState == State.Downloaded || mState == State.Finished) {
                    mDownloaded = mConfig.currentPieceLength;
                    break;
                }

                //connect
                setFtpState(State.Connecting);
                ftpClient = FTPFactory.openFtpClient(mConfig);
                setFtpState(State.Connected);

                //init start values
                final long startOffset = ((long)mConfig.part * mConfig.globalPieceLength) + alreadyDownloaded;
                ftpClient.setRestartOffset(startOffset);

                //create streams
                input = ftpClient.retrieveFileStream(mConfig.remoteFullPath);
                if (input == null || ftpClient.getReplyCode() >= 300) {
                    throw new FatalFTPException(TextUtils.getFtpCodeName(ftpClient.getReplyCode()) + "\n" + ftpClient.getReplyString());
                }
                fileOutputStream = new FileOutputStream(f, startOffset > 0);

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

                    final long subLen = (mConfig.currentPieceLength - mDownloaded);
                    final int realLenToWrite = (int) Math.min(len, subLen);
                    fileOutputStream.write(buffer, 0, realLenToWrite);

                    //save values for notification
                    mDownloaded += realLenToWrite;
                    downloadedInSec += realLenToWrite;

                    //stop, we are complete
                    if (mDownloaded == mConfig.currentPieceLength) {
                        break;
                    }

                    //wait if user paused downloading
                    if (mState == State.Paused) {
                        synchronized (mLock) {
                            setFtpState(State.Paused);//set again and notify about state change
                            mLock.wait();
                            //here you can be only in Downloading state
                            setFtpState(State.Downloading);
                            //set force resume if there is any trouble
                            forceResume = true;
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
                mFatalErrorCounter++;
                mException = ffe;
                ffe.printStackTrace();
                onFatalError(ffe);
                if(mFatalErrorCounter >= FATAL_ERROR_TO_STOP){
                    setFtpState(State.FatalError);
                    return;
                }else{
                    setFtpState(State.WaitingForRetry);
                    //wait 10s and try again
                    try {
                        Thread.sleep(DEFAULT_WAIT);
                    } catch (InterruptedException e1) { /* ignore wake */ }
                }
            } catch (IOException e) {
                onError(e);
                setFtpState(State.WaitingForRetry);
                //wait 10s and try again
                try {
                    Thread.sleep(DEFAULT_WAIT);
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
     * Pre init downloading
     *
     * @return length of already mDownloaded pice
     * @throws FatalFTPException
     */
    public long onPreInit(File f, boolean forceResume) throws FatalFTPException {
        long alreadyDownloaded = 0;
        //for restart
        if (!mConfig.resume && !forceResume) {
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
            } else if(mConfig.currentPieceLength == alreadyDownloaded) {
                setFtpState(mConfig.parts == 1 ? State.Finished : State.Downloaded);
            }//else just continue to download
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
    protected synchronized void setFtpState(State state) {
        if(state == State.Created && !(mState == State.Downloaded || mState == State.Finished || mState == State.FatalError)){
            throw new IllegalStateException("Restarted thread can be only from downloaded or finished state");
        }
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
    public void onFatalError(FatalFTPException ffe) {
        synchronized (mListeners) {
            for (FTPDownloadListener l : mListeners) {
                l.onFatalError(this, ffe);
            }
        }
    }

    public void onError(Exception e) {
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
    public void onDownloadProgress(long downloaded, long downloadedInSec) {
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

    /**
     * Put this thread into paused state
     *
     * @param pause
     */
    public void setPause(boolean pause) {
        //don't call setFtpState to avoid notifying listeners
        //notification is sent when working thread is really going to sleep
        if (pause) {
            if (StateHelper.isActive(mState)) {
                synchronized (mLock) {
                    mState = State.Paused;
                }
            }else{
                throw new IllegalStateException("Thread is in non-pausable state!");
            }
        } else {
            if (mState == State.Paused) {
                synchronized (mLock) {
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

    protected DownloadTask getParentTask() {
        return mParentTask;
    }

    protected void setParentTask(DownloadTask parentTask) {
        if(mParentTask != null && mParentTask != parentTask){
            throw new IllegalStateException("This thread already has different parent DownloadTask");
        }
        mParentTask = parentTask;
    }
}
