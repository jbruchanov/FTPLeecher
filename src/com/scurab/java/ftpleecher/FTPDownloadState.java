package com.scurab.java.ftpleecher;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 13.2.13
 * Time: 22:36
 * To change this template use File | Settings | File Templates.
 */
public enum FTPDownloadState {
    Created, Connecting, Connected, Downloading, Error, WaitingForRetry, Paused, Finished, Merging
}