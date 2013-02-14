package com.scurab.java.ftpleecher;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 13.2.13
 * Time: 22:36
 * To change this template use File | Settings | File Templates.
 */
public interface FTPDownloadListener {

    void onError(FTPDownloadThread source, Exception e);

    void onFatalError(FTPDownloadThread source, FatalFTPException e);

    void onDownloadProgress(FTPDownloadThread source, double down, double downPerSec);

    void onStatusChange(FTPDownloadThread source, FTPDownloadThread.State state);
}
