package com.scurab.java.ftpleecher;

import com.scurab.java.ftpleecher.test.FtpDownloadThreadTest;
import com.scurab.java.ftpleecher.tools.TextUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;

import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Factory for creating {@link DownloadTask} objects
 */
public class FTPFactory {

    private static final boolean FAKE = false;

    private FTPContext mConfig;

    private final String mFolderSeparator;

    private static final String FTP_SEPARATOR = "/";

    public FTPFactory(FTPContext config) {
        checkConfig(config);
        mConfig = config.clone();
        mFolderSeparator = System.getProperty("file.separator");
    }

    private static int GROUP_ID_COUNTER = 0;

    private void checkConfig(FTPContext config) {
        if (config.username == null && config.password != null
                || config.username != null && config.password == null) {
            throw new IllegalArgumentException("Username or password is null!");
        }
    }

    /**
     * Creates collection of download threads
     *
     * @return
     * @throws IOException
     * @throws FatalFTPException
     */
    public List<DownloadTask> createTask(FTPFile ftpfile, String fullpath, String downloadTo) throws IOException, FatalFTPException {
        mConfig.outputDirectory = downloadTo;
        FTPClient fc = openFtpClient(mConfig);

        FTPFile[] files = fc.listFiles(fullpath);
        if (fc.getReplyCode() >= 300) {
            throw new FatalFTPException(TextUtils.getFtpCodeName(fc.getReplyCode()) + "\n" + fc.getReplyString());
        }
        Arrays.sort(files, FILE_COMPARATOR);
        List<DownloadTask> result = new ArrayList<DownloadTask>();

        //it's a file
        if (files.length == 1 || files.length == 0) {
            FTPFile file = files.length == 1 ? files[0] : ftpfile;
            FTPContext newCfg = mConfig.clone();
            newCfg.remoteFullPath = fullpath;
            newCfg.fileName = file.getName();
            newCfg.groupId = ++GROUP_ID_COUNTER;
            result.add(createTaskForFile(newCfg, file));
        } else if (files.length > 1) {
            //it was folder and we got content of this folder
            //update downloadTo folder
            downloadTo = createFolderIfNeccessary(downloadTo + mFolderSeparator + ftpfile.getName());
            mConfig.outputDirectory = downloadTo;

            for (FTPFile file : files) {
                FTPContext newCfg = mConfig.clone();
                newCfg.groupId = ++GROUP_ID_COUNTER;
                //update fullpath
                newCfg.remoteFullPath = fullpath + FTP_SEPARATOR + file.getName();

                if (file.isFile()) {
                    result.add(createTaskForFile(newCfg, file));
                } else {
                    newCfg.outputDirectory += mFolderSeparator + file.getName();
                    result.addAll(createTasksForDirectory(newCfg, fc, file));
                }
            }
        }

        return result;
    }

    private String createFolderIfNeccessary(String folder) throws FatalFTPException {
        File f = new File(folder);
        if (!f.exists() && !f.mkdir()) {
            throw new FatalFTPException("Unable to create folder " + folder);
        }
        return f.getAbsolutePath();
    }

    /**
     * @param config always put clone, because values are changed in this method
     * @param file
     * @return
     */
    private DownloadTask createTaskForFile(final FTPContext config, final FTPFile file) {
        List<FTPDownloadThread> result = new ArrayList<FTPDownloadThread>();

        long size = file.getSize();
        final int parts = (int) Math.ceil((size / (double) config.globalPieceLength));

        config.parts = parts;
        config.fileName = file.getName();

        if (!config.remoteFullPath.endsWith(file.getName())) {
            config.remoteFullPath = config.remoteFullPath + (config.remoteFullPath.endsWith(FTP_SEPARATOR) ? "" : FTP_SEPARATOR) + file.getName();
        }

        if (parts > 1) {
            for (int i = 0, n = parts - 1; i < n; i++) {//last one has diff pieceLen
                FTPContext fc = config.clone();
                fc.part = i;
                fc.currentPieceLength = config.globalPieceLength;
                result.add(createThread(fc));
            }
            //update lastone
            FTPContext fc = config.clone();
            fc.currentPieceLength = (size - ((parts - 1L) * config.globalPieceLength));
            fc.part = fc.parts - 1;
            result.add(createThread(fc));
        } else {
            //clone created in parent method
            config.currentPieceLength = size;
            result.add(createThread(config));
        }
        return new DownloadTask(result);
    }

    private FTPDownloadThread createThread(FTPContext config){
        if(FAKE){
            return new FtpDownloadThreadTest(config);
        }else
            return new FTPDownloadThread(config);
    }

    private List<DownloadTask> createTasksForDirectory(final FTPContext config, final FTPClient fclient, final FTPFile file) throws IOException {
        FTPFile[] files = fclient.listFiles(config.remoteFullPath);
        Arrays.sort(files, FILE_COMPARATOR);

        List<DownloadTask> toReturn = new ArrayList<DownloadTask>();

        for (FTPFile f : files) {

            FTPContext newCfg = config.clone();
            newCfg.groupId = ++GROUP_ID_COUNTER;
            newCfg.remoteFullPath += FTP_SEPARATOR + f.getName();

            if (f.isFile()) {
                toReturn.add(createTaskForFile(newCfg, f));
            } else {
                newCfg.outputDirectory += mFolderSeparator + f.getName();
                toReturn.addAll(createTasksForDirectory(newCfg, fclient, f));
            }
        }
        return toReturn;
    }

    /**
     * Open ftp connection based on {@link FTPConnection}
     *
     * @param config
     * @return
     * @throws IOException
     */
    public static FTPClient openFtpClient(FTPConnection config) throws IOException, FatalFTPException {
        return openFtpClient(config.server, config.port, config.username, config.password, config.passive, config.fileType, config.ftps, config.ignoreSSLCertErrors);
    }

    public static FTPClient openFtpClient(FTPContext context) throws IOException, FatalFTPException {
        return openFtpClient(context.server, context.port, context.username, context.password, context.passive, context.fileType, context.ftps, context.ignoreSSLCertIssues);
    }

    private static FTPClient openFtpClient(String server, int port, String user, String pass, boolean passive, int fileType, boolean ftps, boolean ftpsIgnoreErrors) throws IOException, FatalFTPException {
        FTPClient fc = ftps ? new FTPSClient() : new FTPClient();
        if (ftps && ftpsIgnoreErrors) {
            ((FTPSClient) fc).setTrustManager(new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }
            });
        }

        fc.connect(server, port);
        if (user != null) {
            boolean succ = fc.login(user, pass);
            if(!succ || fc.getReplyCode() >= 300) {
                throw new FatalFTPException(TextUtils.getFtpCodeName(fc.getReplyCode()) + "\n" + fc.getReplyString());
            }
        }

        if (passive) {
            fc.enterLocalPassiveMode();
        } else {
            fc.enterLocalActiveMode();
        }

        fc.setControlKeepAliveTimeout(60);
        fc.setSoTimeout(2000);
        fc.setDataTimeout(2000);

        if (!fc.setFileType(fileType)) {
            throw new FatalFTPException("Unable to set file type:" + fileType);
        }
        return fc;
    }

    private static final Comparator<FTPFile> FILE_COMPARATOR = new Comparator<FTPFile>() {
        private final String[] FIRST_ARCHIVES = new String[]{"rar"};

        @Override
        public int compare(FTPFile o1, FTPFile o2) {
            if (o1.isDirectory() && o2.isFile()) {
                return -1;
            } else if (o1.isFile() && o2.isDirectory()) {
                return 1;
            } else {
                String o1Name = o1.getName();
                String o2Name = o2.getName();
                if (o1.isFile() && o2.isFile()) {
                    String o1SimpleName = getFileName(o1Name);
                    String o2SimpleName = getFileName(o2Name);
                    int result = o1SimpleName.compareToIgnoreCase(o2SimpleName);
                    if(result == 0) {
                        boolean i1 = isFirstArchiveFile(o1Name);
                        boolean i2 = isFirstArchiveFile(o2Name);
                        result = (i1 == i2) ? 0 : (i1 ? -1 : 1);
                        if (result == 0) {
                            result = o1Name.compareTo(o2Name);
                        }
                    }
                    return result;
                }
                return o1Name.compareTo(o2Name);
            }
        }

        private String getFileName(String name) {
            int extStart = name.lastIndexOf(".");
            return extStart > 0 ? name.substring(0, extStart) : name;
        }

        private boolean isFirstArchiveFile(String name) {
            if (name != null) {
                int extStart = name.lastIndexOf(".");
                if (extStart >= 0) {
                    String suffix = name.substring(extStart + 1).toLowerCase();
                    return Arrays.binarySearch(FIRST_ARCHIVES, suffix) >= 0;
                }
            }
            return false;
        }
    };
}
