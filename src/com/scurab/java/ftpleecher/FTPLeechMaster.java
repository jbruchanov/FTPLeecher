package com.scurab.java.ftpleecher;

import java.util.*;

/**
 * Cor class for implenting working queue
 */
public class FTPLeechMaster implements FTPDownloadListener {

    /**
     * variable for count of running download threads
     */
    private volatile int mWorkingThreads = 4;
    /**
     * Queue of threds *
     */
    private final static List<FTPDownloadThread> mQueue = new ArrayList<FTPDownloadThread>();
    /**
     * Current working threads where is handled main logic
     */
    private Thread mWorkingThread;

    private final boolean mIsRunning = true;

    private NotificationAdapter mAdapter;

    /**
     * thread index counter *
     */
    private static int mThreadIndex = 0;

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

        while (mIsRunning) {
            try {
                while (!mQueue.isEmpty()) {
                    synchronized (mQueue) {
                        /*
                         * Main cycle for starting new waiting threads
                         * This cycle is called every time if any thread change state
                         */

                        //use sorted value to move all created thread to the end to avoid more thread for restarted
                        int downloading = getRunningThreads();

                        System.out.println(String.format("FTPLeecher d:%s w:%s", downloading, mWorkingThreads));
                        if (downloading < mWorkingThreads) {
                            //region cycle
                            for (int i = 0, n = mQueue.size();i < n && downloading < mWorkingThreads; i++) {
                                FTPDownloadThread thread = mQueue.get(i);
                                if (thread != null) {
                                    FTPDownloadThread.State state = thread.getFtpState();
                                    if (state == FTPDownloadThread.State.Created) {
                                        if(thread.start()){
                                            System.out.println("Started (" + i + ")");
                                            downloading++;//increase working threads number
                                            mQueue.wait();//wait for state update about this thread
                                            System.out.println(String.format("FTPLeecher after start d:%s w:%s", getRunningThreads(), mWorkingThreads));
                                        }else{
                                            //already running
                                        }
                                    }
                                }
                            }
                        }
                        //endregion cycle
                        synchronized (mQueue) {
                            //wait for any change
                            mQueue.wait();
                        }
                    }
                }
                //if queue is empty, still wait for new events
                if (mIsRunning) {
                    synchronized (mQueue) {
                        mQueue.wait();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private int getRunningThreads() {
        int downloading = 0;
        for (int i = 0, n = mQueue.size(); i < n; i++) {
            FTPDownloadThread thread = mQueue.get(i);
            final FTPDownloadThread.State state = thread.getFtpState();
//            System.out.printf("%s %s", i , state.toString());
            if (thread != null) {
                if (isRunning(state)) {
                    downloading++;
                }
            }
        }
        return downloading;
    }

    private boolean isRunning(FTPDownloadThread.State state) {
        //Started, Connecting, Connected, Downloading, Error, WaitingForRetry, Paused, ;
        return  state == FTPDownloadThread.State.Started ||
                state == FTPDownloadThread.State.Connecting ||
                state == FTPDownloadThread.State.Connected ||
                state == FTPDownloadThread.State.Downloading ||
                state == FTPDownloadThread.State.Error ||
                state == FTPDownloadThread.State.WaitingForRetry ||
                state == FTPDownloadThread.State.Paused;
    }

    private boolean isStopped(FTPDownloadThread.State state) {
        //Created, Started, Connecting, Connected, Downloading, Error, FatalError, WaitingForRetry, Paused, Downloaded, Merging, Finished;
        return  state == FTPDownloadThread.State.Created ||
                state == FTPDownloadThread.State.FatalError ||
                state == FTPDownloadThread.State.Downloaded ||
                state == FTPDownloadThread.State.Merging ||
                state == FTPDownloadThread.State.Finished;
    }

    /**
     * Enqueue new download task to queue for download
     * @param tasks
     */
    public void enqueue(Collection<DownloadTask> tasks) {
        for(DownloadTask task : tasks){
            enqueue(task);
        }
    }

    /**
     * Enqueue new download task to queue for download
     *
     * @param task
     */
    public void enqueue(DownloadTask task) {
        //register listener and inc thread index
        for (FTPDownloadThread t : task.getData()) {
            t.registerListener(this);
            t.setIndex(mThreadIndex++);
        }

        //add data and notify working thread about change
        synchronized (mQueue) {
            mQueue.addAll(task.getData());
            mQueue.notifyAll();
        }

        //notify adapter about big change
        if (mAdapter != null) {
            mAdapter.performNotifyDataChanged();
        }
    }

    //region notification
    @Override
    public void onError(FTPDownloadThread source, Exception e) {
//        synchronized (mQueue){
//            mQueue.notifyAll();
//        }
        if (mAdapter != null) {
            mAdapter.performNotifyDataChanged(source);
        }
    }

    @Override
    public void onFatalError(FTPDownloadThread source, FatalFTPException e) {
        synchronized (mQueue) {
            mQueue.notifyAll();
        }
        if (mAdapter != null) {
            mAdapter.performNotifyDataChanged(source);
        }
    }

    @Override
    public void onDownloadProgress(FTPDownloadThread source, double down, double downPerSec) {
        if (mAdapter != null) {
            mAdapter.performNotifyDataChanged(source);
        }
    }

    @Override
    public void onStatusChange(FTPDownloadThread thread, FTPDownloadThread.State state) {
        if (isStopped(state) || state == FTPDownloadThread.State.Started) {
            synchronized (mQueue) {
                mQueue.notifyAll();
            }
        }
        if (state == FTPDownloadThread.State.Downloaded || state == FTPDownloadThread.State.Finished) {
            System.out.println("Downloaded " + thread.getContext().remoteFullPath + " part: " + thread.getContext().part);
        }
        if (mAdapter != null) {
            mAdapter.performNotifyDataChanged(thread);
        }
    }

    //endregion notification

    /**
     * Set current number of working threads
     *
     * @param value
     */
    public void setWorkingThreads(int value) {
        synchronized (mQueue) {
            mWorkingThreads = value;
            mQueue.notifyAll();
        }
    }

    /**
     * Return current size of queue incl. already finished threads
     *
     * @return
     */
    public int size() {
        return mQueue.size();
    }


    public FTPDownloadThread getItem(int index) {
        return mQueue.get(index);
    }

    public void setNotificationAdapter(NotificationAdapter adapter) {
        mAdapter = adapter;
    }

    public Statistics getStatistics() {
        Statistics stats = new Statistics();

        int speed = 0;
        long down = 0;
        long complete = 0;

        for (FTPDownloadThread t : mQueue) {
            speed += t.getSpeed();
            down += t.getDownloaded();
            complete += t.getContext().currentPieceLength;
        }
        long toDown = complete - down;
        int eta = (speed == 0 ? 0 : (int) (toDown / speed));

        Statistics s = new Statistics();
        s.currentSpeed = speed;
        s.eta = eta;
        return s;
    }

    /**
     * Help data container for some informations about downloading process
     */
    public static class Statistics {
        /**
         * Current speed (avg/s) in bytes
         */
        public int currentSpeed;

        /**
         * estimated time of arrival in seconds
         */
        public int eta;
    }
}

