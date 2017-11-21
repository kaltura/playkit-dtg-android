package com.kaltura.dtg.clear;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.kaltura.dtg.ContentManager;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadState;
import com.kaltura.dtg.DownloadStateListener;

import java.io.File;
import java.util.List;

class DefaultProviderProxy {

    private static final String TAG = "DefaultProviderProxy";
    private Context context;
    private final ContentManager.Settings settings;

    private DefaultDownloadService service;
    private DownloadStateListener listener;

    private ContentManager.OnStartedListener onStartedListener;
    private int maxConcurrentDownloads;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((DefaultDownloadService.LocalBinder) binder).getService();
            service.setDownloadStateListener(listener);
            service.setDownloadSettings(settings);
            service.start();
            onStartedListener.onStarted();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service.stop();
            service = null;
        }
    };

    DefaultProviderProxy(Context context, ContentManager.Settings settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }

    public void start(ContentManager.OnStartedListener startedListener) {
        
        if (service != null) {
            Log.d(TAG, "Already started");
            return;
        }
        
        this.onStartedListener = startedListener;

        Intent intent = new Intent(context, DefaultDownloadService.class);

        Log.d(TAG, "*** Starting service");

        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        // DefaultProviderProxy.onServiceConnected() will set downloadSettings and listener, then call service.start()
    }

    public void stop() {
        if (service == null) {
            Log.d(TAG, "Not started");
            return;
        }
        
        context.unbindService(serviceConnection);
        // DefaultDownloadService.onUnbind() will call stop().
    }

    public void loadItemMetadata(DownloadItem item) {
        service.loadItemMetadata((DefaultDownloadItem) item);
    }

    public DownloadState startDownload(String itemId) {
        return service.startDownload(itemId);
    }

    public void pauseDownload(DownloadItem item) {
        service.pauseDownload((DefaultDownloadItem) item);
    }

    public void resumeDownload(DownloadItem item) {
        service.resumeDownload((DefaultDownloadItem) item);
    }

    public void removeItem(DownloadItem item) {
        service.removeItem((DefaultDownloadItem) item);
    }

    public DownloadItem findItem(String itemId) {
        return service.findItem(itemId);
    }

    public long getDownloadedItemSize(@Nullable String itemId) {
        return service.getDownloadedItemSize(itemId);
    }

    public DownloadItem createItem(String itemId, String contentURL) {
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

    public void dumpState() {
        service.dumpState();
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

    public void updateItemState(String itemId, DownloadState state) {
        service.updateItemState(itemId, state);
    }
}
