package com.kaltura.dtg;

import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;

public class DownloadTask {
    public static final int UNKNOWN_ORDER = -1;
    private static final String TAG = "DownloadTask";
    private static final int PROGRESS_REPORT_COUNT = 100;

    final String taskId;
    final Uri url;
    final File targetFile;
    String itemId;

    String trackRelativeId;
    int order;

    private Listener listener;  // this is the service

    private int retryCount = 0;
    private ContentManager.Settings downloadSettings;
    private int futureId;   // only used for debugging purposes

    public DownloadTask(Uri url, File targetFile, int order) {
        this.url = url;
        this.targetFile = targetFile;
        this.taskId = Utils.md5Hex(targetFile.getAbsolutePath());
        this.order = order;
    }

    DownloadTask(String url, String targetFile, int order) {
        this(Uri.parse(url), new File(targetFile), order);
    }


    public void setTrackRelativeId(String trackRelativeId) {
        this.trackRelativeId = trackRelativeId;
    }

    public void setOrder(int order) {
        this.order = order;
    }


    @Override
    public String toString() {
        return "<DownloadTask id='" + taskId + "' url='" + url + "' target='" + targetFile + "'>";
    }

    private boolean createParentDir(File targetFile) {
        return Utils.mkdirs(targetFile.getParentFile());
    }

    void download() throws HttpRetryException {

        Uri uri = this.url;
        File targetFile = this.targetFile;
//        Log.d(TAG, "Task " + taskId + ": download " + url + " to " + targetFile);

        // Create parent dir if needed
        if (!createParentDir(targetFile)) {
            Log.e(TAG, "Can't create parent dir");
            reportProgress(State.ERROR, 0, new FileNotFoundException(targetFile.getAbsolutePath()));
            return;
        }

        reportProgress(State.STARTED, 0, null);

        long localFileSize = targetFile.length();

        // If file is already downloaded, make sure it's not larger than the remote.
        if (localFileSize > 0) {
            try {
                long remoteFileSize = Utils.httpHeadGetLength(uri);

                // finish before even starting, if file is already complete.
                if (localFileSize == remoteFileSize) {
                    // We're done.
                    reportProgress(State.COMPLETED, 0, null);
                    return;
                } else if (localFileSize > remoteFileSize) {
                    // This is really odd. Delete and try again.
                    Log.w(TAG, "Target file is longer than remote. Deleting the target.");
                    if (!targetFile.delete()) {
                        Log.w(TAG, "Can't delete targetFile");
                    }
                    localFileSize = 0;
                }

            } catch (InterruptedIOException e) {
                Log.d(TAG, "Task " + taskId + " interrupted (1)");
                reportProgress(State.STOPPED, 0, null);
                return;
            } catch (IOException e) {
                Log.e(TAG, "HEAD request failed for " + uri, e);
                // Nothing to do, but this is not fatal. Just continue.
            }
        }


        // Start the actual download.
        InputStream inputStream = null;
        HttpURLConnection conn = null;
        FileOutputStream fileOutputStream = null;

        State stopReason = null;
        Exception stopError = null;

        boolean interruptedBetweenCycles = false;

        int progressReportBytes = 0;
        try {
            conn = Utils.openConnection(uri);
            conn.setReadTimeout(downloadSettings.httpTimeoutMillis);
            conn.setConnectTimeout(downloadSettings.httpTimeoutMillis);
            conn.setDoInput(true);

            if (localFileSize > 0) {
                // Resume
                conn.setRequestProperty("Range", "Bytes=" + localFileSize + "-");
            }
            conn.connect();

            int response = conn.getResponseCode();
            if (response >= 400) {
                throw new IOException(Utils.format("Response code for %s is %d", uri, response));
            }

            inputStream = conn.getInputStream();
            fileOutputStream = new FileOutputStream(targetFile, true);

            byte[] buffer = new byte[10240]; // 10k buffer

            int byteCount;
            progressReportBytes = 0;
            int progressReportCounter = 0;

            while (true) {
                if (Thread.interrupted()) {
                    interruptedBetweenCycles = true;
                    break;
                }

                byteCount = inputStream.read(buffer);

                progressReportCounter++;

                if (byteCount < 0) {
                    // EOF
                    break;
                }

                if (byteCount > 0) {
                    fileOutputStream.write(buffer, 0, byteCount);
                    progressReportBytes += byteCount;
                }

                if (progressReportBytes > 0 && progressReportCounter >= PROGRESS_REPORT_COUNT) {
//                    Log.v(TAG, "progressReportBytes:" + progressReportBytes + "; progressReportCounter:" + progressReportCounter);
                    reportProgress(State.IN_PROGRESS, progressReportBytes, null);
                    progressReportBytes = 0;
                    progressReportCounter = 0;
                }
            }

            if (interruptedBetweenCycles) {
//                Log.d(TAG, "Task " + taskId + " interrupted between read cycles: " + futureId);
                reportProgress(State.STOPPED, 0, null);
            } else {
                stopReason = State.COMPLETED;
            }

        } catch (SocketTimeoutException e) {
            // Not a fatal error -- consider retry.
            retryCount++;
            if (retryCount < downloadSettings.maxDownloadRetries) {
                throw new HttpRetryException(e.getMessage(), 1, uri.toString());
            }
//            Log.d(TAG, "Task " + taskId + " failed", e);
            stopReason = State.ERROR;
            stopError = e;

        } catch (InterruptedIOException e) {
            // Not an error -- task is cancelled.
//            Log.d(TAG, "Task " + taskId + " interrupted: " + futureId);
            stopReason = State.STOPPED;

        } catch (IOException e) {
//            Log.d(TAG, "Task " + taskId + " failed", e);
            stopReason = State.ERROR;
            stopError = e;

        } finally {
            Utils.safeClose(inputStream, fileOutputStream);
            if (conn != null) {
                conn.disconnect();
            }

            // Maybe some bytes are still waiting to be reported
            if (progressReportBytes > 0) {
                reportProgress(State.IN_PROGRESS, progressReportBytes, stopError);
            }
            if (stopReason != null) {
                reportProgress(stopReason, 0, stopError);
            }
        }
    }

    private void reportProgress(final State state, final int newBytes, Exception stopError) {
//        Log.d(TAG, "progress: " + this.taskId + ", " + state + ", " + newBytes + ", " + stopError);
        listener.onTaskProgress(this, state, newBytes, stopError);
    }

    public Listener getListener() {
        return listener;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DownloadTask that = (DownloadTask) o;
        return Utils.equals(url, that.url) &&
                Utils.equals(targetFile, that.targetFile);
    }

    @Override
    public int hashCode() {

        return Utils.hash(url, targetFile);
    }

    void setDownloadSettings(ContentManager.Settings downloadSettings) {
        this.downloadSettings = downloadSettings;
    }

    public void setFutureId(int futureId) {
        this.futureId = futureId;
    }

    enum State {
        IDLE, STARTED, IN_PROGRESS, COMPLETED, STOPPED, ERROR
    }

    interface Listener {
        void onTaskProgress(DownloadTask task, State newState, int newBytes, Exception stopError);
    }
}
