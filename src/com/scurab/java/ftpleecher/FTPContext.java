package com.scurab.java.ftpleecher;

import java.io.File;

/**
 * Base general context for each downloading thread<br/>
 * Every object should be unique per thread
 */
public class FTPContext extends FTPConnection implements Cloneable {
    public FTPContext() {
    }

    public FTPContext(FTPConnection fc) {
        server = fc.server;
        port = fc.port;
        username = fc.username;
        passive = fc.passive;
        password = fc.password;
        fileType = fc.fileType;
    }

    /**
     * Remote file to download<br/>
     * Must be defined full path
     * <code>/folder1/folder2/kernel.img</code>
     */
    public String remoteFullPath;

    /**
     * Define length of one particular piece of downloading in bytes.<br/>
     * Default value is 15MB
     */

    public int globalPieceLength = 15000000;

    /**
     * Counted size of this particular piece
     */
    public int currentPieceLength = 0;

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
    public int bufferSize = 64 * 1024;

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