package com.scurab.java.ftpleecher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 11.2.13
 * Time: 20:35
 * To change this template use File | Settings | File Templates.
 */
public class FTPLeechMaster implements FTPDownloadThread.FTPDownloadListener {

    private int mWorkingThreads = 4;

    private static List<FTPDownloadThread> mQueue = new ArrayList<FTPDownloadThread>();

    private Thread mWorkingThread;

    private boolean mIsRunning = true;

    public FTPLeechMaster() {
        mWorkingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                doImpl();
            }
        });
        mWorkingThread.setName("FTPLeechMaster");
        mWorkingThread.start();
    }

    public void doImpl() {
        try {
            while (mIsRunning) {
                while (!mQueue.isEmpty()) {
                    synchronized (mQueue) {
                        for (int i = 0, s = 0, n = mQueue.size(); i < n && s < mWorkingThreads; i++) {
                            FTPDownloadThread thread = mQueue.get(i);
                            if (thread != null) {
                                final FTPDownloadThread.State state = thread.getFtpState();
                                if (state == FTPDownloadThread.State.Finished) {
                                    continue;//nothing to dos
                                } else if (state == FTPDownloadThread.State.Created) {
                                    System.out.println("Starting " + thread.getConfig().filename + " part: " + thread.getPart());
                                    thread.start();
                                }
                                s++;
                            }
                        }
                        mQueue.wait();
                    }
                }
                if (mIsRunning) {
                    synchronized (mQueue) {
                        mQueue.wait();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void enqueue(Collection<FTPDownloadThread> data) {
        for (FTPDownloadThread t : data) {
            t.setListener(this);
        }

        synchronized (mQueue) {
            mQueue.addAll(data);
            mQueue.notifyAll();
        }
    }

    @Override
    public void onError(FTPDownloadThread source, Exception e) {
//        synchronized (mQueue){
//            mQueue.notifyAll();
//        }
    }

    @Override
    public void onFatalError(FTPDownloadThread source, FatalFTPException e) {
        synchronized (mQueue) {
            mQueue.notifyAll();
        }
    }

    @Override
    public void onDownloadProgress(FTPDownloadThread source, double down, double downPerSec) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onStatusChange(FTPDownloadThread thread, FTPDownloadThread.State state) {
        if (state == FTPDownloadThread.State.Finished) {
            System.out.println("Finished " + thread.getConfig().filename + " part: " + thread.getPart());
            synchronized (mQueue) {
                mQueue.notifyAll();
            }
        }
    }

    public void setWorkingThreads(int value) {
        synchronized (mQueue) {
            mWorkingThreads = value;
            mQueue.notifyAll();
        }
    }

    public void stop() {
        mIsRunning = false;
    }
}
