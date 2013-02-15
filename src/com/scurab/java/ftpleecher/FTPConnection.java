package com.scurab.java.ftpleecher;

import org.apache.commons.net.ftp.FTP;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 14.2.13
 * Time: 21:25
 * To change this template use File | Settings | File Templates.
 */
public class FTPConnection {

    /**
     * server address
     * <code>ftp.linux.com</code>
     */
    public String server;

    /**
     * port for ftp server, default is 21
     */
    public int port = 21;

    /**
     * Optional value for username<br/>
     * If set, password must be set aswell
     */
    public String username;

    /**
     * Optional value for password<br/>
     * If set, username must be ser asweel;
     */
    public String password;

    /**
     * Set connection mode
     */
    public boolean passive = true;

    /**
     * Set download mode for file<br/>
     * {@link org.apache.commons.net.ftp.FTP#BINARY_FILE_TYPE}, {@link org.apache.commons.net.ftp.FTP#ASCII_FILE_TYPE}
     *
     */
    public int fileType = FTP.BINARY_FILE_TYPE;

}
