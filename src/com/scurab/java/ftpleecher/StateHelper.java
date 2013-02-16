package com.scurab.java.ftpleecher;
import com.scurab.java.ftpleecher.FTPDownloadThread.State;

/**
 * Help class for simply claryfing {@link com.scurab.java.ftpleecher.FTPDownloadThread.State}
 */
public class StateHelper {

    /**
     * Returns true if thread is some downloading activity
     * @param state
     */
    public static boolean isActive(FTPDownloadThread.State state){
        return !(state == State.Downloaded || state == State.Finished || state == State.Merging || state == State.Started || state == State.Created);
    }
}
