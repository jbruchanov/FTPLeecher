package com.scurab.java.ftpleecher;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

/**
 * Base class representing one particular task<br/>
 * Task can contain n files/folders
 */
public class DownloadTask implements FTPDownloadListener {

    private List<FTPDownloadThread> mData;

    private List<FTPDownloadThread> mWorkingThreads;

    public DownloadTask(Collection<FTPDownloadThread> data) {
        mData = new ArrayList<FTPDownloadThread>(data);
        mWorkingThreads = new ArrayList<FTPDownloadThread>(data);
        bind();
    }

    private void bind() {
        for (FTPDownloadThread t : mData) {
            t.registerListener(this);
        }
    }

    //region notification

    @Override
    public void onError(FTPDownloadThread source, Exception e) {
    }

    @Override
    public void onFatalError(FTPDownloadThread source, FatalFTPException e) {
    }

    @Override
    public void onDownloadProgress(FTPDownloadThread source, double down, double downPerSec) {
    }

    @Override
    public void onStatusChange(FTPDownloadThread source, FTPDownloadThread.State state) {
        //ignore these states, becuase are set from this class
        if (state == FTPDownloadThread.State.Merging) {
            return;
        } else if (state == FTPDownloadThread.State.Finished) {
            synchronized (mWorkingThreads) {
                mWorkingThreads.remove(source);
            }
        }

        boolean merge = false;
        if (state == FTPDownloadThread.State.Downloaded) {
            synchronized (mWorkingThreads) {
                if (!mWorkingThreads.remove(source)) {
                    System.err.println("This thread is not from this task!" + source.getContext().toString());
                }
                merge = mWorkingThreads.size() == 0 && mData.size() > 1;
            }
        }

        //we are done in this task, now is time to merge
        if (merge) {
            //must be called in diff thread to let finish current downloading thread
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    //wait for sec to finish last thread
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    onMergeFiles();
                }
            });
            t.setName("MergeThread");
            t.start();
        }
    }

    //endregion notification

    public void onMergeFiles() {
        HashMap<Long, FTPDownloadThread[]> subGroups = getSubGroups();
        for (Long l : subGroups.keySet()) {
            try {
                FTPDownloadThread[] arr = subGroups.get(l);
                mergeFiles(arr);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void unregisterListener(FTPDownloadThread[] parts) {
        for (FTPDownloadThread ft : parts) {
            ft.unregisterListener(this);
        }
    }

    private void mergeFiles(FTPDownloadThread[] parts) throws Exception {
        final String sep = System.getProperty("file.separator");
        FTPContext context = parts[0].getContext();
        File outputFile = new File(context.outputDirectory + sep + context.fileName);
        if (outputFile.exists()) {
            outputFile.renameTo(new File(context.outputDirectory + sep + context.fileName + ".old" + System.currentTimeMillis()));
        }
        FileOutputStream fos = new FileOutputStream(outputFile);
        for (int i = 0, n = parts.length; i < n; i++) {
            FTPDownloadThread thread = parts[i];
            try {
                thread.setFtpState(FTPDownloadThread.State.Merging);
                context = thread.getContext();
                FileInputStream fis = new FileInputStream(context.localFile);
                int copied = IOUtils.copy(fis, fos);
                if (context.currentPieceLength != copied) {
                    System.err.println(String.format("Copied:%s, Should be:%s", copied, context.currentPieceLength));
                }
                fis.close();
                thread.setFtpState(FTPDownloadThread.State.Finished);
            } catch (Exception e) {
                thread.setFtpState(FTPDownloadThread.State.Error);
                throw e;
            }
        }
        try {
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
            ;
        }
    }

    /**
     * Get subgroup, each soubgroup is one file separated to parts
     *
     * @return
     */
    private HashMap<Long, FTPDownloadThread[]> getSubGroups() {
        boolean someNotFinished = false;
        HashMap<Long, FTPDownloadThread[]> result = new HashMap<Long, FTPDownloadThread[]>();
        for (FTPDownloadThread ft : mData) {
            FTPContext c = ft.getContext();
            if (c.parts > 1) {
                FTPDownloadThread[] arr = result.get(c.groupId);
                if (arr == null) {
                    arr = new FTPDownloadThread[c.parts];
                    result.put(c.groupId, arr);
                }
                arr[c.part] = ft;
                someNotFinished |= ft.getFtpState() != FTPDownloadThread.State.Downloaded;
            }
        }

        //TODO:this is strange, we have something not downloaded yet, maybe restart?
        if (someNotFinished) {
            //FIXME:
        }
        return result;
    }


    public List<FTPDownloadThread> getData() {
        return Collections.unmodifiableList(mData);
    }
}
