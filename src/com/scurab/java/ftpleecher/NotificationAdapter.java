package com.scurab.java.ftpleecher;

public interface NotificationAdapter extends FTPDownloadListener {

    /**
     * Called if there is any bigger change of queue
     */
    void performNotifyDataChanged();

    /**
     * Called where is change only for one particular thread, ig. change of state
     *
     * @param thread
     */
    void performNotifyDataChanged(FTPDownloadThread thread);
}
