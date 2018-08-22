package com.kaltura.dtg;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.util.List;

class ServiceProxy {

    private static final String TAG = "ServiceProxy";
    private final ContentManager.Settings settings;
    private final Context context;
    private DownloadService service;
    private DownloadStateListener listener;

    private ContentManager.OnStartedListener onStartedListener;
    private int maxConcurrentDownloads;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((DownloadService.LocalBinder) binder).getService();
            service.setDownloadStateListener(listener);
            service.setSettings(settings);
            service.start();
            onStartedListener.onStarted();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service.stop();
            service = null;
        }
    };

    ServiceProxy(Context context, ContentManager.Settings settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }

    public void start(ContentManager.OnStartedListener startedListener) {

        if (service != null) {
            Log.d(TAG, "Already started");
            return;
        }

        this.onStartedListener = startedListener;

        Intent intent = new Intent(context, DownloadService.class);

        Log.d(TAG, "*** Starting service");

        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        // ServiceProxy.onServiceConnected() will set downloadSettings and listener, then call service.start()
    }

    public void stop() {
        if (service == null) {
            Log.d(TAG, "Not started");
            return;
        }

        context.unbindService(serviceConnection);
        // DownloadService.onUnbind() will call stop().
    }

    public void loadItemMetadata(DownloadItem item) {
        service.loadItemMetadata((DownloadItemImp) item);
    }

    public void pauseDownload(DownloadItem item) {
        service.pauseDownload((DownloadItemImp) item);
    }

    public void resumeDownload(DownloadItem item) {
        service.resumeDownload((DownloadItemImp) item);
    }

    public void removeItem(DownloadItem item) {
        service.removeItem((DownloadItemImp) item);
    }

    public DownloadItem findItem(String itemId) {
        return service.findItem(itemId);
    }

    public long getDownloadedItemSize(@Nullable String itemId) {
        return service.getDownloadedItemSize(itemId);
    }

    public DownloadItem createItem(String itemId, String contentURL) throws Utils.DirectoryNotCreatableException {
        return service.createItem(itemId, contentURL);
    }

    public List<? extends DownloadItem> getDownloads(DownloadState[] states) {
        return service.getDownloads(states);
    }

    public String getPlaybackURL(String itemId) {
        return service.getPlaybackURL(itemId);
    }

    public File getLocalFile(String itemId) {
        return service.getLocalFile(itemId);
    }

    public void setDownloadStateListener(DownloadStateListener listener) {
        this.listener = listener;
        if (service != null) {
            service.setDownloadStateListener(listener);
        }
    }

    public long getEstimatedItemSize(@Nullable String itemId) {
        return service.getEstimatedItemSize(itemId);
    }
}
