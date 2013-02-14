package com.scurab.java.ftpleecher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 13.2.13
 * Time: 22:34
 */
public class DownloadTask implements FTPDownloadListener {

    private List<FTPDownloadThread> mData;

    private List<FTPDownloadThread> mWorkingThreads;

    private String sourcePath;

    public DownloadTask(String sourceFullPath, Collection<FTPDownloadThread> data) {
        sourcePath = sourceFullPath;
        mData = new ArrayList<FTPDownloadThread>(data);
        mWorkingThreads = new ArrayList<FTPDownloadThread>(data);
    }

    private void bind(){
        for(FTPDownloadThread t : mData){
            t.registerListener(this);
        }
    }

    @Override public void onError(FTPDownloadThread source, Exception e) { }

    @Override public void onFatalError(FTPDownloadThread source, FatalFTPException e) { }

    @Override public void onDownloadProgress(FTPDownloadThread source, double down, double downPerSec) { }

    @Override
    public void onStatusChange(FTPDownloadThread source, FTPDownloadThread.State state) {
        boolean merge = false;
        if(state == FTPDownloadThread.State.Finished){
            synchronized (mWorkingThreads){
                if(!mWorkingThreads.remove(source)){
                    System.err.println("This thread is not from this task!" + source.getConfig().toString());
                }
                merge = mWorkingThreads.size() == 0 && mData.size() > 1;
            }
        }
        if(merge){
            onMergeFiles();
        }
    }

    public void onMergeFiles() {
        //TODO:
    }

    public List<FTPDownloadThread> getData() {
        return Collections.unmodifiableList(mData);
    }
}
