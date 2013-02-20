package com.scurab.java.ftpleecher.test;


import com.scurab.java.ftpleecher.FTPContext;
import com.scurab.java.ftpleecher.FTPDownloadThread;

import java.util.Random;

public class FtpDownloadThreadTest extends FTPDownloadThread{

    private static Random sRandom = new Random();

    private Exception mException;

    public long getDownloaded() {
        return mDownloaded;
    }

    public int getSpeed() {
        return mSpeed;
    }

    private long mDownloaded;

    private int mSpeed;

    public FtpDownloadThreadTest(FTPContext config) {
        super(config);
    }

    @Override
    protected void downloadImpl() {
        FTPContext context = getContext();
        try {
            setFtpState(State.Connecting);
            Thread.sleep(1000);
            setFtpState(State.Connected);
            Thread.sleep(1000);
            setFtpState(State.Downloading);
            for (int i = 0; i < 120; i++) {
                if(getFtpState() == State.Paused){
                    synchronized (this){
                        this.wait();
                        setFtpState(State.Downloading);
                    }
                }
                Thread.sleep(1000);
                mDownloaded += 100000 + sRandom.nextInt(50000);
                if(mDownloaded > context.currentPieceLength){
                    mDownloaded = context.currentPieceLength;
                }
                mSpeed = 100000 + sRandom.nextInt(50000);
                onDownloadProgress(mDownloaded, mSpeed);
                if(mDownloaded == context.currentPieceLength){
                    break;
                }
                if(i > 0 && i % 70 == 0 && sRandom.nextBoolean()){
                    mException = new Exception("Fatal fake error");
                    setFtpState(State.FatalError);
                    return;
                }
                if(i > 0 && i % 15 == 0 && sRandom.nextBoolean()){
                    setFtpState(State.Error);
                    mException = new Exception("Fake error");
                    Thread.sleep(1000);
                    setFtpState(State.WaitingForRetry);
                    Thread.sleep(10000);
                    setFtpState(State.Connecting);
                    setFtpState(State.Connected);
                    setFtpState(State.Downloading);
                }
            }
            if(mDownloaded == context.currentPieceLength){
                setFtpState(State.Downloaded);
            }
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    @Override
    public Throwable getException() {
        return mException;
    }

    @Override
    public void setPause(boolean pause) {
        if(pause){
            setFtpState(State.Paused);
        }else{
            synchronized (this){
                this.notifyAll();
            }
        }
    }

    @Override
    public synchronized void restart() {
        mDownloaded = 0;
        super.restart();
    }
}
