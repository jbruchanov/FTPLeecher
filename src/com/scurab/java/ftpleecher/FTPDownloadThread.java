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
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 11.2.13
 * Time: 20:34
 * To change this template use File | Settings | File Templates.
 */
public class FTPDownloadThread extends Thread implements Runnable, Cloneable {

    private int mIndex = -1;

    private Object mLock = new Object();

    private final FTPContext mConfig;

    private State mState;

    private List<FTPDownloadListener> mListeners = new ArrayList<FTPDownloadListener>();

    private long mDownloaded;

    private int mSpeed;

    public enum State{
        Created, Connecting, Connected, Downloading, Error, WaitingForRetry, Paused, Downloaded, Merging, Finished
    }
    private Throwable mException;

    private static final int NOTIFY = 1000; //notify download progress after 3s

    protected FTPDownloadThread(FTPContext config){
        mConfig = config;
        setFtpState(State.Created);
        if(config.parts > 1){
            setName(String.format("%s %03d", mConfig.server, config.part));
        }else{
            setName(mConfig.server);
        }
    }

    @Override
    public void run() {
         downloadImpl();
    }

    private void downloadImpl(){
        FTPClient ftpClient = null;
        FileOutputStream fileOutputStream = null;
        InputStream input = null;
        while(!(mState == State.Downloaded || mState == State.Finished)){
            try{
                File f = getLocalFile();
                mConfig.localFile = f;
                long alreadyDownloaded = onPreInit(f);

                if(mState == State.Downloaded){
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
                if(input == null || ftpClient.getReplyCode() >= 300){
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

                while((len = input.read(buffer)) != -1){
                    long now = System.currentTimeMillis();
                    if((now - lastNotify) > NOTIFY){
                        int v = (int)(downloadedInSec / (float)NOTIFY) * 1000;
                        onDownloadProgress(mDownloaded, v);
                        mSpeed = v;
                        downloadedInSec = 0;
                        lastNotify = now;
                    }
                    final int subLen = (int)(mConfig.currentPieceLength - mDownloaded);
                    final int realLenToWrite = Math.min(len, subLen);
                    fileOutputStream.write(buffer, 0, realLenToWrite);

                    //save values for notification
                    mDownloaded += realLenToWrite;
                    downloadedInSec += realLenToWrite;

                    if(mDownloaded == mConfig.currentPieceLength){
                        //stop, we are complete
                        break;
                    }
                }

                if(mState == State.Paused){
                    synchronized (mLock){
                        mLock.wait();
                    }
                }

                //close and finish
                ftpClient.disconnect();

                if(mConfig.currentPieceLength == mDownloaded){
                    setFtpState(mConfig.parts == 1 ? State.Finished : State.Downloaded);
                }//otherwise just restart process and again
            }catch(FatalFTPException ffe){
                mException = ffe;
                onFatalError(ffe);
                setFtpState(State.Paused);
                synchronized (mLock){
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mException = null;
            }catch(IOException e){
                onError(e);
                try {
                    setFtpState(State.WaitingForRetry);
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                    //ingore wake
                }
            }
            catch(Throwable t){
                mException = t;
                onFatalError(new FatalFTPException(t));
                setFtpState(State.Error);
                synchronized (mLock){
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            finally {
                //close and release everything
                if(fileOutputStream != null){
                    try{fileOutputStream.close();}catch (Exception e){/**/}
                }
                if(input != null){
                    try{input.close();}catch (Exception e){/**/}
                }
                if(ftpClient != null){
                    try{ftpClient.disconnect();}catch (Exception e){/**/}
                }
            }
        }
    }

    private String getFtpCodeName(int value){
        Field[] fields = FTPReply.class.getDeclaredFields();
        for(Field f : fields){
            if(f.getType() == int.class){
                try {
                    if(value == f.getInt(null)){
                        return f.getName();
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }
        return "";
    }


    /**
     * Pre init downloading
     * @return length of already mDownloaded pice
     * @throws FatalFTPException
     */
    private long onPreInit(File f) throws FatalFTPException {
        long alreadyDownloaded = 0;
        //for restart
        if(!mConfig.resume){
            //try delete already existing file
            if(f.exists() && !f.delete()){
                //if we can't delete it, just stop and wait
                throw new FatalFTPException("Unable to delete file:" + f.getAbsolutePath());
            }
        }else{
            //we have something, so download it
            alreadyDownloaded = f.length();
            if(alreadyDownloaded > mConfig.currentPieceLength){
                throw new FatalFTPException("Already downloaded part is bigger then defined piece length!\nFile:"+ f.getAbsolutePath());
            }else{
                setFtpState(State.Downloaded);
            }
        }
        return alreadyDownloaded;
    }

    /**
     *
     * @return
     * @throws FatalFTPException if outputDirector cant be created
     */
    private File getLocalFile() throws FatalFTPException {
        String[] urlParts = mConfig.remoteFullPath.split("/");
        String fileName = urlParts[urlParts.length-1];
        String localFile = null;
        File folder = new File(mConfig.outputDirectory);
        if(!folder.exists() && !folder.mkdir()){
            throw new FatalFTPException("Unable to create folder:" + mConfig.outputDirectory);
        }
        if(mConfig.parts == 1){
            localFile = String.format(mConfig.localSingleFileTemplate, mConfig.outputDirectory, fileName);
        }else{
            localFile = String.format(mConfig.localMultipleFilesTemplate, mConfig.outputDirectory, fileName, mConfig.part);
        }
        return new File(localFile);
    }

    public State getFtpState(){
        return mState;
    }

    protected void setFtpState(State state){
        mState = state;
        mSpeed = 0;
        synchronized (mListeners){
            for(FTPDownloadListener l : mListeners){
                l.onStatusChange(this, state);
            }
        }
    }

    public void registerListener(FTPDownloadListener listener) {
        synchronized (mListeners){
            mListeners.add(listener);
        }
    }

    public void unregisterListener(FTPDownloadListener listener){
        synchronized (mListeners){
            mListeners.remove(listener);
        }
    }

    //region Notification
    private void onFatalError(FatalFTPException ffe) {
        synchronized (mListeners){
            for(FTPDownloadListener l : mListeners){
                l.onFatalError(this, ffe);
            }
        }
    }

    private void onError(Exception e) {
        synchronized (mListeners){
            for(FTPDownloadListener l : mListeners){
                l.onError(this, e);
            }
        }
    }

    /**
     * Notification
     * @param downloaded for whole pcs
     * @param downloadedInSec fot last second
     */
    private void onDownloadProgress(long downloaded, long downloadedInSec) {
        synchronized (mListeners){
            for(FTPDownloadListener l : mListeners){
                l.onDownloadProgress(this, downloaded, downloadedInSec);
            }
        }
    }
    //endregion

    public FTPContext getContext() {
        return mConfig;
    }

    @Override
    protected FTPDownloadThread clone() throws CloneNotSupportedException {
        return new FTPDownloadThread(mConfig);
    }

    public int getPart() {
        return mConfig.part;
    }

    public void setPause(boolean pause){
        if(pause){
            if(mState == State.Downloading){
                synchronized (mLock){
                    mState = State.Paused;
                }
            }
        }else{
            if(mState == State.Paused){
                synchronized (mLock){
                    mState = State.Downloading;
                    mLock.notifyAll();
                }
            }
        }
    }

    public Throwable getException(){
        return mException;
    }

    public int getIndex() {
        return mIndex;
    }

    public void setIndex(int index) {
        mIndex = index;
    }

    public long getDownloaded() {
        return mDownloaded;
    }

    public int getSpeed() {
        return mSpeed;
    }
}
