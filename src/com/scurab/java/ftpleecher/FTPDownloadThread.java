package com.scurab.java.ftpleecher;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 11.2.13
 * Time: 20:34
 * To change this template use File | Settings | File Templates.
 */
public class FTPDownloadThread extends Thread implements Runnable, Cloneable {

    /**
     * Notification interface
     */
    public interface FTPDownloadListener{
        void onError(FTPDownloadThread source, Exception e);

        void onFatalError(FTPDownloadThread source, FatalFTPException e);

        void onDownloadProgress(FTPDownloadThread source, double down, double downPerSec);

        void onStatusChange(FTPDownloadThread source, State state);
    }

    private final FTPFactory.FactoryConfig mConfig;

    private final int mPart;

    private State mState;

    private FTPDownloadListener mListener;

    public enum State{
        Created, Connecting, Connected, Downloading, Error, WaitingForRetry, Finished
    }

    /**
     * Use this constructor if download is not divided to parts
     * @param config
     */
    protected FTPDownloadThread(FTPFactory.FactoryConfig config){
        this(config, -1);
    }

    protected FTPDownloadThread(FTPFactory.FactoryConfig config, int part){
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
                InputStream input = ftpClient.retrieveFileStream(mConfig.filename);
                if(input == null || ftpClient.getReplyCode() >= 300){
                    throw new FatalFTPException(getFtpCodeName(ftpClient.getReplyCode()) + "\n" + ftpClient.getReplyString());
                }
                FileOutputStream fos = new FileOutputStream(f);

                setFtpState(State.Downloading);

                //download
                byte[] buffer = new byte[mConfig.bufferSize];
                int len = 0;

                long downloaded = alreadyDownloaded;
                long downloadedInSec = 0;

                long lastNotify = System.currentTimeMillis();

                while((len = input.read(buffer)) != -1){
                    long now = System.currentTimeMillis();
                    if(now - lastNotify > 1000){
                        onDownloadProgress(downloaded, downloadedInSec);
                        downloadedInSec = 0;
                    }
                    final int subLen = (int)(mConfig.pieceLength - downloaded);
                    final int realLenToWrite = Math.min(len, subLen);
                    fos.write(buffer, 0, realLenToWrite);

                    //save values for notification
                    downloaded += realLenToWrite;
                    downloadedInSec += realLenToWrite;

                    if(downloaded == mConfig.pieceLength){
                        //stop, we are complete
                        break;
                    }
                }

                //close and finish
                fos.close();
                input.close();
                ftpClient.disconnect();
                setFtpState(State.Finished);

            }catch(FatalFTPException ffe){
                onFatalError(ffe);
                setFtpState(State.Error);
                synchronized (this){
//                    try {
//                        wait();
//                    } catch (InterruptedException e) {
//
//                    }
                }
                break;
            }catch(IOException e){
                setFtpState(State.Error);
                onError(e);
//                try {
//                    setFtpState(State.WaitingForRetry);
//                    Thread.sleep(30000);
//                } catch (InterruptedException e1) {
//                    //ingore wake
//                }
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
     * @return length of already downloaded pice
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
        if(mListener != null){
            mListener.onStatusChange(this, state);
        }
    }

    public void setListener(FTPDownloadListener listener) {
        mListener = listener;
    }

    //region Notification
    private void onFatalError(FatalFTPException ffe) {
        if(mListener != null){
            mListener.onFatalError(this, ffe);
        }
    }
    private void onError(Exception e) {
        if(mListener != null){
            mListener.onError(this, e);
        }
    }

    /**
     * Notification
     * @param downloaded for whole pcs
     * @param downloadedInSec fot last second
     */
    private void onDownloadProgress(long downloaded, long downloadedInSec) {
        if(mListener != null){
            mListener.onDownloadProgress(this, downloaded, downloadedInSec);
        }
    }
    //endregion

    public FTPFactory.FactoryConfig getConfig() {
        return mConfig;
    }

    @Override
    protected FTPDownloadThread clone() throws CloneNotSupportedException {
        return new FTPDownloadThread(mConfig, mPart);
    }

    public int getPart() {
        return mPart;
    }
}
