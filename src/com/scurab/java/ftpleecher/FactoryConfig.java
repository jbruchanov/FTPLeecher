package com.scurab.java.ftpleecher;

import org.apache.commons.net.ftp.FTP;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 13.2.13
 * Time: 22:56
 * To change this template use File | Settings | File Templates.
 */
public class FactoryConfig implements Cloneable
{
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
     * Remote filename to download<br/>
     * Must be defined full path
     * <code>/folder1/folder2/kernel.img</code>
     */
    public String filename;

    /**
     * Define length of one particular piece of downloading in bytes.<br/>
     * Default value is 15MB
     */
    public int pieceLength = 15000000;

    /**
     * Define output directory for temp files
     */
    public String outputDirectory;

    /**
     * Allow resuming of downloading parts.<br/>
     * If false, part will be deleted and completely re-downloaded if there will be any download problem.
     */
    public boolean resume = false;


    /**
     * Define buffer size for ftp downloading<br/>
     * Default value is 64KiB
     */
    public int bufferSize = 64*1024;

    /**
     * local file template for {@link String#format(String, Object...)}<br/>
     * Must contains 3 variables for outputFolder, file name and counter
     */
    public String localMultipleFilesTemplate = "%s/%s.part%03d";

    /**
     * local file template for {@link String#format(String, Object...)}<br/>
     * Must contains 3 variables for outputFolder, file name and counter
     */
    public String localSingleFileTemplate = "%s/%s";

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

    @Override
    public FactoryConfig clone() {
        try{
            return (FactoryConfig)super.clone();
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
    }
}