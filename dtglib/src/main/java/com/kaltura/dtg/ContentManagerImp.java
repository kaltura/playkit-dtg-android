package com.kaltura.dtg;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;


public class ContentManagerImp extends ContentManager {
    private static final String TAG = "ContentManagerImp";

    private static ContentManager sInstance;
    private final HashSet<DownloadStateListener> stateListeners = new HashSet<>();
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
    private String sessionId;
    private String applicationName;
    private ServiceProxy provider;
    private File itemsDir;
    private boolean started;
    private boolean autoResumeItemsInProgress = true;
    private DownloadRequestParams.Adapter adapter;
    private Settings settings = new Settings();

    private ContentManagerImp(Context context) {
        this.context = context.getApplicationContext();

        File filesDir = this.context.getFilesDir();
        itemsDir = new File(filesDir, "dtg/items");

        // make sure all directories are there.
        Utils.mkdirsOrThrow(filesDir);
        Utils.mkdirsOrThrow(itemsDir);
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
        Log.d(TAG, "start Content Manager");
        this.sessionId = UUID.randomUUID().toString();
        this.applicationName = ("".equals(settings.applicationName)) ? context.getPackageName() : settings.applicationName;
        this.adapter = new KalturaDownloadRequestAdapter(sessionId, applicationName);
        if (provider != null) {
            // Call the onstarted callback even if it has already been started
            if (onStartedListener != null) {
                onStartedListener.onStarted();
            }
            return;
        }

        provider = new ServiceProxy(context, settings);
        provider.setDownloadStateListener(downloadStateRelay);
        provider.start(new OnStartedListener() {
            @Override
            public void onStarted() {
                started = true;
                if (autoResumeItemsInProgress) {
                    // Resume all downloads that were in progress on stop.
                    List<DownloadItem> downloads = getDownloads(DownloadState.IN_PROGRESS);
                    for (DownloadItem download : downloads) {
                        download.startDownload();
                    }
                }

                if (onStartedListener != null) {
                    onStartedListener.onStarted();
                }
            }
        });
    }

    @Override
    public void pauseDownloads() throws IllegalStateException {
        checkIfManagerStarted();
        assertProvider();

        List<DownloadItem> downloads = getDownloads(DownloadState.IN_PROGRESS);
        for (DownloadItem item : downloads) {
            provider.pauseDownload(item);
        }
    }

    @Override
    public void resumeDownloads() throws IllegalStateException {
        checkIfManagerStarted();
        assertProvider();

        List<DownloadItem> downloads = getDownloads(DownloadState.PAUSED);
        for (DownloadItem item : downloads) {
            provider.resumeDownload(item);
        }
    }

    @Override
    public DownloadItem findItem(String itemId) throws IllegalStateException {
        checkIfManagerStarted();
        assertProviderAndItem(itemId);
        return provider.findItem(itemId);
    }

    @Override
    public long getDownloadedItemSize(String itemId) throws IllegalStateException {
        checkIfManagerStarted();
        assertProvider();

        return provider.getDownloadedItemSize(itemId);
    }

    private void assertProvider() {
        if (provider == null) {
            throw new IllegalStateException("Provider Operation Not Valid");
        }
    }

    @Override
    public long getEstimatedItemSize(String itemId) throws IllegalStateException {
        checkIfManagerStarted();
        assertProviderAndItem(itemId);
        return provider.getEstimatedItemSize(itemId);
    }

    @Override
    public DownloadItem createItem(String itemId, String contentURL) throws IllegalStateException {
        checkIfManagerStarted();
        itemId = safeItemId(itemId);
        assertProviderAndItem(itemId);
        DownloadRequestParams downloadRequestParams = adapter.adapt(new DownloadRequestParams(Uri.parse(contentURL), null));
        return provider.createItem(itemId, downloadRequestParams.url.toString());
    }

    private String safeItemId(String itemId) {
        // The only forbidden chars are null and slash
        return itemId.replace('/', '-').replace('\0', '-');
    }

    @Override
    public void removeItem(String itemId) throws IllegalStateException {
        checkIfManagerStarted();
        assertProviderAndItem(itemId);

        DownloadItem item = findItem(itemId);
        if (item == null) {
            throw new IllegalStateException("DownloadItem Is Null");
        }

        provider.removeItem(item);
    }

    private File getItemDir(String itemId) {
        return new File(itemsDir, itemId);
    }

    @Override
    public File getAppDataDir(String itemId) {
        File appDataDir = new File(getItemDir(itemId), "appData");
        appDataDir.mkdirs();
        return appDataDir;
    }

    @Override
    public List<DownloadItem> getDownloads(DownloadState... states) throws IllegalStateException {
        checkIfManagerStarted();
        assertProvider();
        return new ArrayList<>(provider.getDownloads(states));
    }

    @Override
    public String getPlaybackURL(String itemId) throws IllegalStateException {
        checkIfManagerStarted();
        assertProviderAndItem(itemId);

        return provider.getPlaybackURL(itemId);
    }

    private void assertProviderAndItem(String itemId) {
        if (provider == null || TextUtils.isEmpty(itemId)) {
            throw new IllegalStateException("Provider Operation Not Valid");
        }
    }

    @Override
    public File getLocalFile(String itemId) throws IllegalStateException {
        checkIfManagerStarted();
        assertProviderAndItem(itemId);
        return provider.getLocalFile(itemId);
    }

    private void checkIfManagerStarted() {
        if (!started) {
            throw new IllegalStateException("Manager was not started.");
        }
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

    @Override
    public boolean isStarted() {
        return started;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getApplicationName() {
        return applicationName;
    }
}

