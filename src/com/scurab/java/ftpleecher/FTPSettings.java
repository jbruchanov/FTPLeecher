package com.scurab.java.ftpleecher;

import org.apache.commons.net.ftp.FTP;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 15.2.13
 * Time: 23:55
 * To change this template use File | Settings | File Templates.
 */
public class FTPSettings {

    /**
     * Define length of one particular piece of downloading in bytes.<br/>
     * Default value is 15MB
     */

    public int globalPieceLength = 15000000;

    /**
     * Allow resuming of downloading parts.<br/>
     * If false, part will be deleted and completely re-downloaded if there will be any download problem.
     */
    public boolean resume = false;

    /**
     * Define buffer size for ftp downloading<br/>
     * Default value is 64KiB
     */
    public int bufferSize = 64 * 1024;

    /**
     * Set download mode for file<br/>
     * {@link org.apache.commons.net.ftp.FTP#BINARY_FILE_TYPE}, {@link org.apache.commons.net.ftp.FTP#ASCII_FILE_TYPE}
     */
    public int fileType = FTP.BINARY_FILE_TYPE;
}
