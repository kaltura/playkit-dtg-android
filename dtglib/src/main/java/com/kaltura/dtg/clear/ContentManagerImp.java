package com.kaltura.dtg.clear;

import android.content.Context;
import android.text.TextUtils;

import com.kaltura.dtg.AppBuildConfig;
import com.kaltura.dtg.ContentManager;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadState;
import com.kaltura.dtg.DownloadStateListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;



public class ContentManagerImp extends ContentManager {


    private static ContentManager sInstance;
    private final HashSet<DownloadStateListener> stateListeners = new HashSet<>(1);
    private final DownloadStateListener downloadStateRelay = new DownloadStateListener() {

        // Pass the state to all listeners.

        @Override
        public void onDownloadComplete(DownloadItem item) {
            for (DownloadStateListener stateListener : stateListeners) {
                stateListener.onDownloadComplete(item);
            }
        }

        @Override
        public void onProgressChange(DownloadItem item, long downloadedBytes) {
            for (DownloadStateListener stateListener : stateListeners) {
                stateListener.onProgressChange(item, downloadedBytes);
            }
        }

        @Override
        public void onDownloadStart(DownloadItem item) {
            for (DownloadStateListener stateListener : stateListeners) {
                stateListener.onDownloadStart(item);
            }
        }

        @Override
        public void onDownloadPause(DownloadItem item) {
            for (DownloadStateListener stateListener : stateListeners) {
                stateListener.onDownloadPause(item);
            }
        }

        @Override
        public void onDownloadFailure(DownloadItem item, Exception error) {
            for (DownloadStateListener stateListener : stateListeners) {
                stateListener.onDownloadFailure(item, error);
            }
        }

        @Override
        public void onDownloadMetadata(DownloadItem item, Exception error) {
            for (DownloadStateListener stateListener : stateListeners) {
                stateListener.onDownloadMetadata(item, error);
            }
        }

        @Override
        public void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector) {
            for (DownloadStateListener stateListener : stateListeners) {
                stateListener.onTracksAvailable(item, trackSelector);
            }
        }
    };
    
    private int maxConcurrentDownloads;
    private Context context;
    private DefaultProviderProxy provider;
    private File itemsDir;
    private boolean started;
    private boolean autoResumeItemsInProgress = true;
    private Settings settings = new Settings();

    private ContentManagerImp(Context context) {
        this.context = context.getApplicationContext();
        File filesDir = this.context.getFilesDir();
        itemsDir = new File(filesDir, "dtg/items");

        // make sure all directories are there.
        filesDir.mkdirs();
        itemsDir.mkdirs();
        
        AppBuildConfig.init(context);
    }
    
    public static ContentManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ContentManager.class) {
                if (sInstance == null) {
                    sInstance = new ContentManagerImp(context);
                }
            }
        }
        return sInstance;
    }
    

    // Public API

    @Override
    public void addDownloadStateListener(DownloadStateListener listener) {
        stateListeners.add(listener);
    }

    @Override
    public void removeDownloadStateListener(DownloadStateListener listener) {
        stateListeners.remove(listener);
    }
    
    @Override
    public void stop() {
        if (provider == null) {
            started = false;
            return;
        }
        provider.stop();
        provider = null;
        started = false;
    }

    @Override
    public void start(final OnStartedListener onStartedListener) {

        if (started) {
            // Call the onstarted callback even if it has already been started
            if (onStartedListener != null) {
                onStartedListener.onStarted();
            }
            return;
        }

        provider = new DefaultProviderProxy(context, settings);
        provider.setDownloadStateListener(downloadStateRelay);
        provider.start(new OnStartedListener() {
                            @Override
                            public void onStarted() {
                                
                                if (autoResumeItemsInProgress) {
                                    // Resume all downloads that were in progress on stop.
                                    List < DownloadItem > downloads = getDownloads(DownloadState.IN_PROGRESS);
                                    for (DownloadItem download : downloads) {
                                        download.startDownload();
                                    }
                                }
                                
                                if (onStartedListener != null) {
                                    onStartedListener.onStarted();
                                }
                            }
                        });


        started = true;
    }

    @Override
    public void pauseDownloads() {
        if (provider == null) {
            return;
        }

        List<DownloadItem> downloads = getDownloads(DownloadState.IN_PROGRESS);
        for (DownloadItem item : downloads) {
            provider.pauseDownload(item);
        }
    }

    @Override
    public void resumeDownloads() {
        if (provider == null) {
            return;
        }

        List<DownloadItem> downloads = getDownloads(DownloadState.PAUSED);
        for (DownloadItem item : downloads) {
            provider.resumeDownload(item);
        }
    }

    @Override
    public DownloadItem findItem(String itemId) {
        if (provider == null || TextUtils.isEmpty(itemId)) {
            return null;
        }
        return provider.findItem(itemId);
    }

    @Override
    public long getDownloadedItemSize(String itemId) {
        if (provider == null) {
            return 0L;
        }

        return provider.getDownloadedItemSize(itemId);
    }

    @Override
    public long getEstimatedItemSize(String itemId) {
        if (provider == null || TextUtils.isEmpty(itemId)) {
            return 0L;
        }
        return provider.getEstimatedItemSize(itemId);
    }

    @Override
    public DownloadItem createItem(String itemId, String contentURL) {
        if (provider == null || TextUtils.isEmpty(itemId)) {
            return null;
        }

        return provider.createItem(itemId, contentURL);
    }

    @Override
    public void removeItem(String itemId) {
        if (provider == null || TextUtils.isEmpty(itemId)) {
            return;
        }

        DownloadItem item = findItem(itemId);
        if (item == null) {
            return;
        }
        // TODO: can the lower-level methods use itemId and not item?
        provider.removeItem(item);
    }

    private File getItemDir(String itemId) {
        // TODO: safe itemId?
        File itemDir = new File(itemsDir, itemId);
        return itemDir;
    }

    @Override
    public File getAppDataDir(String itemId) {
        File appDataDir = new File(getItemDir(itemId), "appData");
        appDataDir.mkdirs();
        return appDataDir;
    }

    @Override
    public List<DownloadItem> getDownloads(DownloadState... states) {
        if (provider == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(provider.getDownloads(states));
    }

    @Override
    public String getPlaybackURL(String itemId) {
        if (provider == null || TextUtils.isEmpty(itemId)) {
            return null;
        }

        return provider.getPlaybackURL(itemId);
    }

    @Override
    public File getLocalFile(String itemId) {
        if (provider == null || TextUtils.isEmpty(itemId)) {
            return null;
        }
        return provider.getLocalFile(itemId);
    }

    @Override
    public Settings getSettings() {
        if (started) {
            throw new IllegalStateException("Settings cannot be changed after the Content manager has been started.");
        }
        return settings;
    }

    @Override
    public void setAutoResumeItemsInProgress(boolean autoStartItemsInProgress) {
        this.autoResumeItemsInProgress = autoStartItemsInProgress;
    }
}

