package com.scurab.java.ftpleecher;

import java.io.File;

/**
 * Base general context for each downloading thread<br/>
 * Every object should be unique per thread
 */
public class FTPContext extends FTPSettings implements Cloneable {
    public FTPContext() {
    }

    public FTPContext(FTPConnection fc) {
        setConnection(fc);
    }

    public FTPContext setConnection(FTPConnection fc){
        server = fc.server;
        port = fc.port;
        username = fc.username;
        passive = fc.passive;
        password = fc.password;
        fileType = fc.fileType;
        return this;
    }

    public FTPContext setSettings(FTPSettings fs){
        globalPieceLength = fs.globalPieceLength;
        resume = fs.resume;
        bufferSize = fs.bufferSize;
        fileType = fs.fileType;
        return this;
    }

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
     * Remote file to download<br/>
     * Must be defined full path
     * <code>/folder1/folder2/kernel.img</code>
     */
    public String remoteFullPath;

    /**
     * Counted size of this particular piece
     */
    public int currentPieceLength = 0;

    /**
     * Define output directory for temp files
     */
    public String outputDirectory;

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
     * Local file name
     */
    public File localFile;

    /**
     * Original filename on FTP server
     */
    public String fileName;

    /**
     * Flag for making common groups of threads
     */
    public long groupId;

    /**
     * Part number
     */
    public int part = 0;

    /**
     * Complete number of parts
     */
    public int parts = 1;

    @Override
    public FTPContext clone() {
        try {
            return (FTPContext) super.clone();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}