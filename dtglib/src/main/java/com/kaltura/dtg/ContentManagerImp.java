package com.kaltura.dtg;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;



class ContentManagerImp extends ContentManager {


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


    private static ContentManager sInstance;
    private Context mContext;
    private DownloadProvider mProvider;
    private File mItemsDir;
    private boolean mStarted;
    
    private final HashSet<DownloadStateListener> mStateListeners = new HashSet<>(1);
    private final DownloadStateListener mDownloadStateRelay = new DownloadStateListener() {

        // Pass the state to all listeners.

        @Override
        public void onDownloadComplete(DownloadItem item) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onDownloadComplete(item);
            }
        }

        @Override
        public void onProgressChange(DownloadItem item, long downloadedBytes) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onProgressChange(item, downloadedBytes);
            }
        }

        @Override
        public void onDownloadStart(DownloadItem item) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onDownloadStart(item);
            }
        }

        @Override
        public void onDownloadPause(DownloadItem item) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onDownloadPause(item);
            }
        }

        @Override
        public void onDownloadStop(DownloadItem item) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onDownloadStop(item);
            }
        }

        @Override
        public void onDownloadMetadata(DownloadItem item, Exception error) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onDownloadMetadata(item, error);
            }
        }

        @Override
        public void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onTracksAvailable(item, trackSelector);
            }
        }
    };
    
    
    private ContentManagerImp(Context context) {
        mContext = context.getApplicationContext();
        File filesDir = mContext.getFilesDir();
        mItemsDir = new File(filesDir, "dtg/items");

        // make sure all directories are there.
        filesDir.mkdirs();
        mItemsDir.mkdirs();
        
        AppBuildConfig.init(context);
    }
    

    // Public API

    @Override
    public void addDownloadStateListener(DownloadStateListener listener) {
        mStateListeners.add(listener);
    }

    @Override
    public void removeDownloadStateListener(DownloadStateListener listener) {
        mStateListeners.remove(listener);
    }

    @Override
    public void stop() {
        mProvider.stop();
        mProvider = null;
        mStarted = false;
    }

    @Override
    public void start() {

        if (mStarted) {
            return;
        }

        mProvider = DownloadProviderFactory.getProvider(mContext);
        mProvider.setDownloadStateListener(mDownloadStateRelay);
        mProvider.start();

        // Resume all downloads that were in progress on stop.
        List<DownloadItem> downloads = getDownloads(DownloadState.IN_PROGRESS);
        for (DownloadItem download : downloads) {
            download.startDownload();
        }

        mStarted = true;
    }

    @Override
    public void pauseDownloads() {
        List<DownloadItem> downloads = getDownloads(DownloadState.IN_PROGRESS);
        for (DownloadItem item : downloads) {
            mProvider.pauseDownload(item);
        }
    }

    @Override
    public void resumeDownloads() {
        List<DownloadItem> downloads = getDownloads(DownloadState.PAUSED);
        for (DownloadItem item : downloads) {
            mProvider.resumeDownload(item);
        }
    }

    @Override
    public DownloadItem findItem(String itemId) {
        return mProvider.findItem(itemId);
    }

    @Override
    public long getDownloadedItemSize(String itemId) {
        return mProvider.getDownloadedItemSize(itemId);
    }

    @Override
    public long getEstimatedItemSize(String itemId) {
        return mProvider.getEstimatedItemSize(itemId);
    }

    @Override
    public DownloadItem createItem(String itemId, String contentURL) {
        return mProvider.createItem(itemId, contentURL);
    }

    @Override
    public void removeItem(String itemId) {
        DownloadItem item = findItem(itemId);
        // TODO: can the lower-level methods use itemId and not item?
        mProvider.removeItem(item);
    }

    private File getItemDir(String itemId) {
        // TODO: safe itemId?
        File itemDir = new File(mItemsDir, itemId);
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
        if (mProvider == null) {
            return new ArrayList<>();
        }
        return mProvider.getDownloads(states);
    }

    @Override
    public String getPlaybackURL(String itemId) {
        return mProvider.getPlaybackURL(itemId);
    }

    @Override
    public File getLocalFile(String itemId) {
        return mProvider.getLocalFile(itemId);
    }
}

