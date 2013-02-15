package com.scurab.java.ftpleecher;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 11.2.13
 * Time: 20:35
 * To change this template use File | Settings | File Templates.
 */
public class FTPLeechMaster implements FTPDownloadListener {

    private int mWorkingThreads = 4;

    private static List<FTPDownloadThread> mQueue = new ArrayList<FTPDownloadThread>();

    private Thread mWorkingThread;

    private boolean mIsRunning = true;

    private NotificationAdapter mAdapter;

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
                                if (state == FTPDownloadThread.State.Downloaded || state == FTPDownloadThread.State.Finished || state == FTPDownloadThread.State.Paused) {
                                    continue;//nothing to do
                                } else if (state == FTPDownloadThread.State.Created) {
                                    System.out.println("Starting " + thread.getContext().remoteFullPath + " part: " + thread.getPart());
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

    public void enqueue(DownloadTask task) {
        int size = mQueue.size();
        for (FTPDownloadThread t : task.getData()) {
            t.registerListener(this);
            t.setIndex(size);
            size++;
        }

        synchronized (mQueue) {
            mQueue.addAll(task.getData());
            mQueue.notifyAll();
        }
        if(mAdapter != null){
            mAdapter.performNotifyDataChanged();
        }
    }

    @Override
    public void onError(FTPDownloadThread source, Exception e) {
//        synchronized (mQueue){
//            mQueue.notifyAll();
//        }
        if(mAdapter != null){
            mAdapter.performNotifyDataChanged(source);
        }
    }

    @Override
    public void onFatalError(FTPDownloadThread source, FatalFTPException e) {
        synchronized (mQueue) {
            mQueue.notifyAll();
        }
        if(mAdapter != null){
            mAdapter.performNotifyDataChanged(source);
        }
    }

    @Override
    public void onDownloadProgress(FTPDownloadThread source, double down, double downPerSec) {
        if(mAdapter != null){
            mAdapter.performNotifyDataChanged(source);
        }
    }

    @Override
    public void onStatusChange(FTPDownloadThread thread, FTPDownloadThread.State state) {
        if (state == FTPDownloadThread.State.Downloaded) {
            System.out.println("Downloaded " + thread.getContext().remoteFullPath + " part: " + thread.getPart());
            synchronized (mQueue) {
                mQueue.notifyAll();
            }
        }
        if(mAdapter != null){
            mAdapter.performNotifyDataChanged(thread);
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

    public int size(){
        return mQueue.size();
    }

    public FTPDownloadThread getItem(int index){
        return mQueue.get(index);
    }

    public void setNotificationAdapter(NotificationAdapter adapter) {
        mAdapter = adapter;
    }
}
