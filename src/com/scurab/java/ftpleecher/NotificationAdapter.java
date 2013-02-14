package com.scurab.java.ftpleecher;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 13.2.13
 * Time: 23:20
 * To change this template use File | Settings | File Templates.
 */
public interface NotificationAdapter extends FTPDownloadListener{

    void performNotifyDataChanged();

    void performNotifyDataChanged(FTPDownloadThread thread);
}
