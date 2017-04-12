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
import com.kaltura.dtg.DownloadProvider;
import com.kaltura.dtg.DownloadState;
import com.kaltura.dtg.DownloadStateListener;

import java.io.File;
import java.util.List;

class DefaultProviderProxy implements DownloadProvider {

    private static final String TAG = "DefaultProviderProxy";
    private Context context;

    private ClearDownloadService service;
    private DownloadStateListener listener;
    
    private ContentManager.OnStartedListener onStartedListener;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((ClearDownloadService.LocalBinder) binder).getService();
            service.setDownloadStateListener(listener);
            onStartedListener.onStarted();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            DefaultProviderProxy.this.service = null;
        }
    };

    DefaultProviderProxy(Context context) {
        this.context = context.getApplicationContext();
    }
    
    @Override
    public void start(ContentManager.OnStartedListener startedListener) {
        
        this.onStartedListener = startedListener;

        Intent intent = new Intent(context, ClearDownloadService.class);

        Log.d(TAG, "*** Starting service");

        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void stop() {
        service.stop();
    }

    @Override
    public void loadItemMetadata(DownloadItem item) {
        service.loadItemMetadata(item);
    }

    @Override
    public DownloadState startDownload(String itemId) {
        return service.startDownload(itemId);
    }

    @Override
    public void pauseDownload(DownloadItem item) {
        service.pauseDownload(item);
    }

    @Override
    public void resumeDownload(DownloadItem item) {
        service.resumeDownload(item);
    }

    @Override
    public void removeItem(DownloadItem item) {
        service.removeItem(item);
    }

    @Override
    public DownloadItem findItem(String itemId) {
        return service.findItem(itemId);
    }

    @Override
    public long getDownloadedItemSize(@Nullable String itemId) {
        return service.getDownloadedItemSize(itemId);
    }

    @Override
    public DownloadItem createItem(String itemId, String contentURL) {
        return service.createItem(itemId, contentURL);
    }

    @Override
    public List<DownloadItem> getDownloads(DownloadState[] states) {
        return service.getDownloads(states);
    }

    @Override
    public String getPlaybackURL(String itemId) {
        return service.getPlaybackURL(itemId);
    }

    @Override
    public File getLocalFile(String itemId) {
        return service.getLocalFile(itemId);
    }

    @Override
    public void dumpState() {
        service.dumpState();
    }

    @Override
    public void setDownloadStateListener(DownloadStateListener listener) {
        this.listener = listener;
        if (service != null) {
            service.setDownloadStateListener(listener);
        }
    }

    @Override
    public long getEstimatedItemSize(@Nullable String itemId) {
        return service.getEstimatedItemSize(itemId);
    }

    @Override
    public void updateItemState(String itemId, DownloadState state) {
        service.updateItemState(itemId, state);
    }
}
