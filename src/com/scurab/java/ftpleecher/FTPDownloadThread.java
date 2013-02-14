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

    private final FactoryConfig mConfig;

    private final int mPart;

    private State mState;

    private List<FTPDownloadListener> mListeners = new ArrayList<FTPDownloadListener>();

    private long mDownloaded;

    private int mSpeed;

    public enum State{
        Created, Connecting, Connected, Downloading, Error, WaitingForRetry, Paused, Finished
    }

    private volatile int mNotifiactionTime = 1000; //notify download progress after 1s

    /**
     * Use this constructor if download is not divided to parts
     * @param config
     */
    protected FTPDownloadThread(FactoryConfig config){
        this(config, -1);
    }

    protected FTPDownloadThread(FactoryConfig config, int part){
        mConfig = config;
        mPart = part;
        setFtpState(State.Created);
        if(mPart == -1){
            setName(String.format("%s %03d", mConfig.server, part));
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
        while(mState != State.Finished){
            try{
                File f = getLocalFile();
                long alreadyDownloaded = onPreInit(f);

                if(mState == State.Finished){
                    break;
                }

                //connect
                setFtpState(State.Connecting);
                ftpClient = FTPFactory.openFtpClient(mConfig);
                setFtpState(State.Connected);

                //init start values
                final long startOffset = (mPart * mConfig.pieceLength) + alreadyDownloaded;
                final long toDownload = mConfig.pieceLength - alreadyDownloaded;
                ftpClient.setRestartOffset(startOffset);

                //create streams
                input = ftpClient.retrieveFileStream(mConfig.filename);
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
                    if(now - lastNotify > mNotifiactionTime){
                        int v = (int)(downloadedInSec / (float)mNotifiactionTime) * 1000;
                        onDownloadProgress(mDownloaded, v);
                        mSpeed = v;
                        downloadedInSec = 0;
                    }
                    final int subLen = (int)(mConfig.pieceLength - mDownloaded);
                    final int realLenToWrite = Math.min(len, subLen);
                    fileOutputStream.write(buffer, 0, realLenToWrite);

                    //save values for notification
                    mDownloaded += realLenToWrite;
                    downloadedInSec += realLenToWrite;

                    if(mDownloaded == mConfig.pieceLength){
                        //stop, we are complete
                        break;
                    }
                    if(mState == State.Paused){
                        synchronized (mLock){
                            mLock.wait();
                        }
                    }
                }
                //close and finish
                ftpClient.disconnect();
                setFtpState(State.Finished);

            }catch(FatalFTPException ffe){
                onFatalError(ffe);
                try {
                    setFtpState(State.WaitingForRetry);
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                    //ingore wake
                }
                break;
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
                onFatalError(new FatalFTPException(t));
                setFtpState(State.Error);
//                synchronized (this){
//                    try {
//                        wait();
//                    } catch (InterruptedException e) {
//
//                    }
//                }
//                break;
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
            if(alreadyDownloaded > mConfig.pieceLength){
                throw new FatalFTPException("Already downloaded part is bigger then defined piece length!\nFile:"+ f.getAbsolutePath());
            }else{
                setFtpState(State.Finished);
            }
        }
        return alreadyDownloaded;
    }

    private File getLocalFile(){
        String[] urlParts = mConfig.filename.split("/");
        String fileName = urlParts[urlParts.length-1];
        String localFile = null;
        if(mPart == -1){
            localFile = String.format(mConfig.localSingleFileTemplate, mConfig.outputDirectory, fileName);
        }else{
            localFile = String.format(mConfig.localMultipleFilesTemplate, mConfig.outputDirectory, fileName, mPart);
        }
        return new File(localFile);
    }

    public State getFtpState(){
        return mState;
    }

    protected void setFtpState(State state){
        mState = state;
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

    public FactoryConfig getConfig() {
        return mConfig;
    }

    @Override
    protected FTPDownloadThread clone() throws CloneNotSupportedException {
        return new FTPDownloadThread(mConfig, mPart);
    }

    public int getPart() {
        return mPart;
    }

    public int getNotifiactionTime() {
        return mNotifiactionTime;
    }

    /**
     * Set notification time to call {@link FTPDownloadListener#onDownloadProgress(int, FTPDownloadThread, double, double)} }
     * @param notifiactionTime min value is 1000 for 1s
     */
    public void setNotifiactionTime(int notifiactionTime) {
        mNotifiactionTime = Math.max(1000, notifiactionTime);
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
