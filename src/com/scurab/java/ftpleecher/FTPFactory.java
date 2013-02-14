package com.scurab.java.ftpleecher;

import com.scurab.java.ftpleecher.tools.TextUtils;
import com.sun.javaws.exceptions.InvalidArgumentException;
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

//        if(TextUtils.isNullOrEmpty(config.filename)){
//            throw new IllegalArgumentException("Filename must be set!");
//        }
//
//        if(TextUtils.isNullOrEmpty(config.outputDirectory)){
//            throw new IllegalArgumentException("Output directory must be set!");
//        }
    }

    /**
     * Creates collection of download threads
     * @return
     * @throws IOException
     * @throws FatalFTPException
     */
    public DownloadTask createTask(String fullpath, String downloadTo) throws IOException, FatalFTPException {
        mConfig.outputDirectory = downloadTo;
        FTPClient fc = openFtpClient(mConfig);

        FTPFile[] files = fc.listFiles(fullpath);

        List<FTPDownloadThread> result = new ArrayList<FTPDownloadThread>();
        if(result == null){
            throw new IllegalArgumentException("FTP item not found, path:" + fullpath);
        }
        for(FTPFile file : files){
            FactoryConfig newCfg = mConfig.clone();
            if(file.isFile()){
                newCfg.filename = fullpath;
                result.addAll(createThreadsForFile(newCfg, file));
            }else{
                newCfg.filename = mConfig.filename + (mConfig.filename.endsWith("/") ? "" : "/") + file.getName();
                result.addAll(createThreadsForDirectory(newCfg, fc, file));
            }
        }

        DownloadTask task = new DownloadTask(fullpath, result);
        return task;
    }

    private List<FTPDownloadThread> createThreadsForFile(final FactoryConfig config, final FTPFile file){
        long size = file.getSize();

        final int parts = (int)Math.ceil((size / (double)config.pieceLength));

        List<FTPDownloadThread> result = new ArrayList<FTPDownloadThread>();

        FactoryConfig newCfg = config.clone();
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
                FactoryConfig newCfg = config.clone();
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
    public static FTPClient openFtpClient(FactoryConfig config) throws IOException, FatalFTPException {
        FTPClient fc = new FTPClient();



        fc.connect(config.server, config.port);
        if(config.username != null){
            fc.login(config.username, config.password);
        }

        if(config.passive){
            fc.enterLocalPassiveMode();
        }else{
            fc.enterLocalActiveMode();
        }

        fc.setControlKeepAliveTimeout(60);

        fc.setSoTimeout(2000);
        fc.setDataTimeout(2000);

        if(!fc.setFileType(config.fileType)){
            throw new FatalFTPException("Unable to set file type:"+ config.fileType);
        }
        return fc;
    }
}
