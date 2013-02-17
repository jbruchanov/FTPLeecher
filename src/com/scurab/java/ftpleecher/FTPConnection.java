package com.scurab.java.ftpleecher;

import org.apache.commons.net.ftp.FTP;

/**
 * Simple container for ftp connection info
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
     * If set, username must be ser as well;
     */
    public String password;

    /**
     * Set connection mode
     */
    public boolean passive = true;

    /**
     * Set download mode for file<br/>
     * {@link org.apache.commons.net.ftp.FTP#BINARY_FILE_TYPE}, {@link org.apache.commons.net.ftp.FTP#ASCII_FILE_TYPE}
     */
    public int fileType = FTP.BINARY_FILE_TYPE;

    /**
     * Use secure ftp connection
     * @return
     */
    public boolean ftps = false;


    @Override
    public String toString() {
        return String.format("%s://%s@%s:%s/", ftps ? "ftps" : "ftp",  username, server, port);
    }
}
