package com.scurab.java.ftpleecher;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 11.2.13
 * Time: 20:34
 * To change this template use File | Settings | File Templates.
 */
public class FTPFactory {

    private FTPContext mConfig;

    private final String mFolderSeparator;

    private static final String FTP_SEPARATOR = "/";

    public FTPFactory(FTPContext config){
        checkConfig(config);
        mConfig = config.clone();

        mFolderSeparator = System.getProperty("file.separator");
    }

    private void checkConfig(FTPContext config){
        if (config.username == null && config.password != null
                || config.username != null && config.password == null) {
            throw new IllegalArgumentException("Username or password is null!");
        }
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
        }else{
            if(files.length == 1){ //it's file
                FTPFile file = files[0];
                FTPContext newCfg = mConfig.clone();
                newCfg.remoteFullPath = fullpath;
                newCfg.fileName = file.getName();
                newCfg.groupId = System.currentTimeMillis();
                result.addAll(createThreadsForFile(newCfg, file));
            }else if(files.length > 1){//it was folder and we got content of this folder
                downloadTo = createFolderIfNeccessary(downloadTo + mFolderSeparator + fullpath.replace(FTP_SEPARATOR, mFolderSeparator));
                mConfig.outputDirectory = downloadTo;
                for(FTPFile file : files){
                    FTPContext newCfg = mConfig.clone();
                    newCfg.groupId = System.currentTimeMillis();
                    newCfg.remoteFullPath = fullpath + FTP_SEPARATOR + file.getName(); //update fullpath
                    if(file.isFile()){
                        result.addAll(createThreadsForFile(newCfg, file));
                    }else{
                        newCfg.outputDirectory += mFolderSeparator + file.getName();
                        result.addAll(createThreadsForDirectory(newCfg, fc, file));
                    }
                }
            }
        }


        DownloadTask task = new DownloadTask(fullpath, result);
        return task;
    }

    private String createFolderIfNeccessary(String folder) throws FatalFTPException {
        File f = new File(folder);
        if(!f.exists() && !f.mkdir()){
            throw new FatalFTPException("Unable to create folder " + folder);
        }
        return f.getAbsolutePath();
    }

    /**
     *
     * @param config always put clone, because values are changed in this method
     * @param file
     * @return
     */
    private List<FTPDownloadThread> createThreadsForFile(final FTPContext config, final FTPFile file){
        long size = file.getSize();

        final int parts = (int)Math.ceil((size / (double)config.globalPieceLength));
        config.parts = parts;
        config.fileName = file.getName();

        List<FTPDownloadThread> result = new ArrayList<FTPDownloadThread>();

        if(!config.remoteFullPath.endsWith(file.getName())){
            config.remoteFullPath = config.remoteFullPath + (config.remoteFullPath.endsWith(FTP_SEPARATOR) ? "" : FTP_SEPARATOR) + file.getName();
        }

        if(parts > 1){
            for (int i = 0, n = parts - 1; i < n; i++) {//last one has diff pieceLen
                FTPContext fc = config.clone();
                fc.part = i;
                fc.currentPieceLength = config.globalPieceLength;
                result.add(new FTPDownloadThread(fc));
            }
            //update lastone
            FTPContext fc = config.clone();
            fc.currentPieceLength = (int)(size - ((parts-1)*(double)config.globalPieceLength));
            fc.part = fc.parts-1;
            result.add(new FTPDownloadThread(fc));
        }else{
            //clone created in parent method
            config.currentPieceLength = (int)size;
            result.add(new FTPDownloadThread(config));
        }

        return result;
    }

    private List<FTPDownloadThread> createThreadsForDirectory(final FTPContext config, final FTPClient fclient, final FTPFile file) throws IOException {
        FTPFile[] files = fclient.listFiles(config.remoteFullPath);
        List<FTPDownloadThread> result = new ArrayList<FTPDownloadThread>();
        for(FTPFile f : files){
            FTPContext newCfg = config.clone();
            newCfg.groupId = System.currentTimeMillis();
            if(f.isFile()){
                newCfg.remoteFullPath += FTP_SEPARATOR + f.getName(); //update fullpath
                result.addAll(createThreadsForFile(config, f));
            }else{
                newCfg.remoteFullPath += FTP_SEPARATOR+ f.getName(); //update fullpath + (config.remoteFullPath.endsWith("/") ? "" : "/") + f.getName();
                newCfg.outputDirectory += mFolderSeparator + f.getName();
                result.addAll(createThreadsForDirectory(newCfg, fclient, f));
            }
        }
        return result;
    }

    /**
     * Open ftp connection based on {@link FTPContext}
     * @param config
     * @return
     * @throws IOException
     */
    public static FTPClient openFtpClient(FTPConnection config) throws IOException, FatalFTPException {
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
