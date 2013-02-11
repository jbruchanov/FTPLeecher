package com.scurab.java.ftpleecher;

import com.scurab.java.ftpleecher.tools.TextUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 11.2.13
 * Time: 20:34
 * To change this template use File | Settings | File Templates.
 */
public class FTPFactory {

    private FactoryConfig mConfig;

    public FTPFactory(FactoryConfig config){
        checkConfig(config);
        mConfig = config;
    }

    private void checkConfig(FactoryConfig config){
        if (config.username == null && config.password != null
                || config.username != null && config.password == null) {
            throw new IllegalArgumentException("Username or password is null!");
        }

        if(TextUtils.isNullOrEmpty(config.filename)){
            throw new IllegalArgumentException("Filename must be set!");
        }

        if(TextUtils.isNullOrEmpty(config.outputDirectory)){
            throw new IllegalArgumentException("Output directory must be set!");
        }
    }

    public FTPDownloadThread[] createThreads() throws IOException, FatalFTPException {
        FTPClient fc = openFtpClient(mConfig);

        FTPFile[] files = fc.listFiles(mConfig.filename);
        assert(files.length == 1);
        FTPFile file = files[0];
        long size = file.getSize();

        final int parts = (int)Math.ceil((size / (double)mConfig.pieceLength));

        FTPDownloadThread[] result = new FTPDownloadThread[parts];

        for(int i = 0;i<parts;i++){
            FTPDownloadThread ft = new FTPDownloadThread(mConfig, i);
            result[i] = ft;
        }

        return result;
    }

    /**
     * Open ftp connection based on {@link FactoryConfig}
     * @param config
     * @return
     * @throws IOException
     */
    protected static FTPClient openFtpClient(FactoryConfig config) throws IOException, FatalFTPException {
        FTPClient fc = new FTPClient();

        fc.connect(config.server, config.port);
        if(config.username != null){
            fc.login(config.username, config.password);
        }

        if(!fc.setFileType(config.fileType)){
            throw new FatalFTPException("Unable to set file type:"+ config.fileType);
        }
        return fc;
    }

    public static class FactoryConfig
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
        public String localFileTemplate = "%s/%s.part%03d";

        /**
         * Set download mode for file<br/>
         * {@link FTP#BINARY_FILE_TYPE}, {@link FTP#ASCII_FILE_TYPE}
         *
         */
        public int fileType = FTP.BINARY_FILE_TYPE;
    }
}
