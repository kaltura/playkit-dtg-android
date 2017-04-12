package com.kaltura.dtg.clear;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.util.Log;

import com.kaltura.dtg.ContentManager;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadProvider;
import com.kaltura.dtg.DownloadState;
import com.kaltura.dtg.DownloadStateListener;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.List;

class DefaultProviderProxy implements DownloadProvider {

    private static final String TAG = "DefaultProviderProxy";
    private Context context;
    private EventBus bus = EventBus.getDefault();
    private boolean started;
    private ClearDownloadService service;
    private DownloadStateListener listener;
//    private final CountDownLatch serviceStartLock = new CountDownLatch(1);
    
    private ContentManager.OnStartedListener onStartedListener;

    DefaultProviderProxy(Context context) {
        this.context = context;
    }
    
    @Subscribe(threadMode = ThreadMode.ASYNC)
    void on(ClearDownloadService.ServiceStarted service) {
        Log.d(TAG, "*** ServiceStarted");
        this.service = service.service;
        this.service.setDownloadStateListener(listener);
        if (this.onStartedListener != null) {
            this.onStartedListener.onStarted();
        }
//        serviceStartLock.countDown();
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    void on(ClearDownloadService.ServiceStopped service) {
        Log.w(TAG, "Service stopped");
        this.service = service.service;
    }

    @Override
    public void start(ContentManager.OnStartedListener startedListener) {
        
        this.onStartedListener = startedListener;
        
        bus.register(this);

        if (!started) {

            Intent intent = new Intent(context, ClearDownloadService.class);

            intent.setAction("start");

            Log.d(TAG, "*** Starting service");
            context.startService(intent);
        }
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
