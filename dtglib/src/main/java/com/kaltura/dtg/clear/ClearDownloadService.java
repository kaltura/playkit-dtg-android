package com.kaltura.dtg.clear;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;


// Implementation note: a service class must be public.
public class ClearDownloadService extends Service implements DownloadTask.Listener {

    private static final String TAG = "ClearDownloadService";

    private static final int MAX_CONCURRENT_DOWNLOADS = 1;
    static final String ACTION_NOTIFY_DOWNLOAD_PROGRESS = "com.kaltura.dtg.ACTION_NOTIFY_DOWNLOAD_PROGRESS";
    static final String ACTION_DOWNLOAD = "com.kaltura.dtg.ACTION_DOWNLOAD";
    static final String ACTION_START_SERVICE = "com.kaltura.dtg.ACTION_START_SERVICE";
    static final String EXTRA_DOWNLOAD_TASKS = "com.kaltura.dtg.EXTRA_DOWNLOAD_TASKS";
    static final String EXTRA_DOWNLOAD_TASK_ID = "com.kaltura.dtg.EXTRA_DOWNLOAD_TASK_ID";
    static final String EXTRA_DOWNLOAD_TASK_STATE = "com.kaltura.dtg.EXTRA_DOWNLOAD_TASK_STATE";
    static final String EXTRA_DOWNLOAD_TASK_NEW_BYTES = "com.kaltura.dtg.EXTRA_DOWNLOAD_TASK_NEW_BYTES";
    static final String EXTRA_ITEM_ID = "com.kaltura.dtg.EXTRA_ITEM_ID";
    static final String ACTION_PAUSE_DOWNLOAD = "com.kaltura.dtg.ACTION_PAUSE_DOWNLOAD";
    static final String ACTION_RESUME_DOWNLOAD = "com.kaltura.dtg.ACTION_RESUME_DOWNLOAD";

    private ExecutorService mExecutor;
    private ItemFutureMap futureMap = new ItemFutureMap();

    public ClearDownloadService() {
        super();
    }

    @Override
    public void onTaskProgress(String taskId, DownloadTask.State newState, int newBytes) {
        Log.v(TAG, "onTaskProgress:" + taskId + "; newBytes=" + newBytes);
        Intent intent = new Intent(ACTION_NOTIFY_DOWNLOAD_PROGRESS);
        intent.putExtra(EXTRA_DOWNLOAD_TASK_ID, taskId);
        intent.putExtra(EXTRA_DOWNLOAD_TASK_STATE, newState.ordinal());
        intent.putExtra(EXTRA_DOWNLOAD_TASK_NEW_BYTES, newBytes);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            Log.d(TAG, "Intent is null!");
            return START_NOT_STICKY;
        }

        // common argument
        ArrayList<DownloadTask> chunks = intent.getParcelableArrayListExtra(EXTRA_DOWNLOAD_TASKS);

        // optional
        String itemId = intent.getStringExtra(EXTRA_ITEM_ID);

        String action = intent.getAction();
        Log.d(TAG, "onStartCommand(action=" + action + ", itemId=" + itemId + 
                ", chunks.size=" + (chunks==null ? 0 : chunks.size())+ ")");

        switch (action) {
            
            case ACTION_START_SERVICE:
                break;
            
            case ACTION_DOWNLOAD:
                if (chunks == null) {
                    break;
                }
                for (DownloadTask task : chunks) {
                    task.itemId = itemId;
                    FutureTask future = futureTask(itemId, task);
                    mExecutor.execute(future);
                    futureMap.add(itemId, future);
                }
                break;
            
            case ACTION_PAUSE_DOWNLOAD:
                if (itemId != null) {
                    futureMap.cancelItem(itemId);
                } else {
                    futureMap.cancelAll();
                }
                // Maybe add PAUSE_ALL with mExecutor.purge(); and remove futures
                break;

            case ACTION_RESUME_DOWNLOAD:
                // TODO: only resume requested tasks, but that's more complicated.
                //    mExecutor.resume();
                break;
            default:
                Log.w(TAG, "Unknown intent action: " + action);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_DOWNLOADS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy()");

        mExecutor.shutdownNow();
        mExecutor = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    
    private FutureTask futureTask(final String itemId, final DownloadTask task) {
        Callable<Void> callable = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                task.download(ClearDownloadService.this);
                return null;
            }
        };
        return new FutureTask<Void>(callable) {
            @Override
            protected void done() {
                futureMap.remove(itemId, this);
            }
        };
    }
}
