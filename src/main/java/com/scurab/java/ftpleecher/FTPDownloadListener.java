package com.scurab.java.ftpleecher;

public interface FTPDownloadListener {

    void onError(FTPDownloadThread source, Exception e);

    void onFatalError(FTPDownloadThread source, FatalFTPException e);

    void onDownloadProgress(FTPDownloadThread source, double down, double downPerSec);

    void onStatusChange(FTPDownloadThread source, FTPDownloadThread.State state);
}
