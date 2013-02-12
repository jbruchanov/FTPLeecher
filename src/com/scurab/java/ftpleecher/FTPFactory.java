package com.scurab.java.ftpleecher;

import com.scurab.java.ftpleecher.tools.TextUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    /**
     * Creates collection of download threads
     * @return
     * @throws IOException
     * @throws FatalFTPException
     */
    public Collection<FTPDownloadThread> createThreads() throws IOException, FatalFTPException {
        FTPClient fc = openFtpClient(mConfig);

        FTPFile[] files = fc.listFiles(mConfig.filename);

        List<FTPDownloadThread> result = new ArrayList<FTPDownloadThread>();
        for(FTPFile file : files){
            if(file.isFile()){
                result.addAll(createThreadsForFile(mConfig, file));
            }else{
                FactoryConfig newCfg = mConfig.clone();
                newCfg.filename = mConfig.filename + (mConfig.filename.endsWith("/") ? "" : "/") + file.getName();
                result.addAll(createThreadsForDirectory(newCfg, fc, file));
            }
        }
        return result;
    }

    private List<FTPDownloadThread> createThreadsForFile(final FactoryConfig config, final FTPFile file){
        long size = file.getSize();

        final int parts = (int)Math.ceil((size / (double)config.pieceLength));

        List<FTPDownloadThread> result = new ArrayList<FTPDownloadThread>();

        FTPFactory.FactoryConfig newCfg = config.clone();
        if(!config.filename.endsWith(file.getName())){
            newCfg.filename = config.filename + (config.filename.endsWith("/") ? "" : "/") + file.getName();
        }

        if(parts > 1){
            for(int i = 0;i<parts;i++){
                result.add(new FTPDownloadThread(newCfg, i));
            }
        }else{
            result.add(new FTPDownloadThread(newCfg));
        }

        return result;
    }

    private List<FTPDownloadThread> createThreadsForDirectory(final FactoryConfig config, final FTPClient fclient, final FTPFile file) throws IOException {
        FTPFile[] files = fclient.listFiles(config.filename);
        List<FTPDownloadThread> result = new ArrayList<FTPDownloadThread>();
        for(FTPFile f : files){
            if(f.isDirectory()){
                result.addAll(createThreadsForDirectory(config, fclient, f));
            }else{
                FTPFactory.FactoryConfig newCfg = config.clone();
                newCfg.filename = config.filename + (config.filename.endsWith("/") ? "" : "/") + f.getName();
                result.addAll(createThreadsForFile(config, f));
            }
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

    public static class FactoryConfig implements Cloneable
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
         * Set download mode for file<br/>
         * {@link FTP#BINARY_FILE_TYPE}, {@link FTP#ASCII_FILE_TYPE}
         *
         */
        public int fileType = FTP.BINARY_FILE_TYPE;

        @Override
        protected FactoryConfig clone() {
            try{
                return (FactoryConfig)super.clone();
            }
            catch(Exception e){
                throw new IllegalStateException(e);
            }
        }
    }
}
