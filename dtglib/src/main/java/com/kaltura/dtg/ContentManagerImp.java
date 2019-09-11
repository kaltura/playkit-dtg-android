package com.kaltura.dtg;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;


public class ContentManagerImp extends ContentManager {
    private static final String TAG = "ContentManagerImp";

    private static ContentManager sInstance;
    private final HashSet<DownloadStateListener> stateListeners = new HashSet<>();

    interface Post {
        void post(DownloadStateListener listener);
    }

    private void postToListeners(Post event) {
        // Safe iteration
        for (DownloadStateListener listener : new HashSet<>(stateListeners)) {
            if (listener != null) {
                event.post(listener);
            }
        }
    }

    private final DownloadStateListener downloadStateRelay = new DownloadStateListener() {

        // Pass the state to all listeners.

        @Override
        public void onDownloadComplete(DownloadItem item) {
            postToListeners(L -> L.onDownloadComplete(item));
        }

        @Override
        public void onProgressChange(DownloadItem item, long downloadedBytes) {
            postToListeners(L -> L.onProgressChange(item, downloadedBytes));
        }

        @Override
        public void onDownloadStart(DownloadItem item) {
            postToListeners(L -> L.onDownloadStart(item));
        }

        @Override
        public void onDownloadPause(DownloadItem item) {
            postToListeners(L -> L.onDownloadPause(item));
        }

        @Override
        public void onDownloadFailure(DownloadItem item, Exception error) {
            postToListeners(L -> L.onDownloadFailure(item, error));
        }

        @Override
        public void onDownloadMetadata(DownloadItem item, Exception error) {
            postToListeners(L -> L.onDownloadMetadata(item, error));
        }

        @Override
        public void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector) {
            postToListeners(L -> L.onTracksAvailable(item, trackSelector));
        }
    };

    private final Context context;
    private String sessionId;
    private String applicationName;
    private ServiceProxy serviceProxy;
    private boolean started;
    private boolean autoResumeItemsInProgress = true;
    private DownloadRequestParams.Adapter adapter;
    private final Settings settings = new Settings();

    private ContentManagerImp(Context context) {
        this.context = context.getApplicationContext();
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
        if (serviceProxy == null) {
            started = false;
            return;
        }
        serviceProxy.stop();
        serviceProxy = null;
        started = false;
    }

    @Override
    public void start(final OnStartedListener onStartedListener) throws IOException {
        Log.d(TAG, "start Content Manager");

        Storage.setup(context, settings);

        this.sessionId = UUID.randomUUID().toString();
        this.applicationName = ("".equals(settings.applicationName)) ? context.getPackageName() : settings.applicationName;
        this.adapter = new KalturaDownloadRequestAdapter(sessionId, applicationName);

        if (started) {
            // Call the onStarted callback even if it has already been started
            if (onStartedListener != null) {
                onStartedListener.onStarted();
            }
            return;
        }

        synchronized (this) {
            if (serviceProxy == null) {
                serviceProxy = new ServiceProxy(context, settings.copy());
                serviceProxy.setDownloadStateListener(downloadStateRelay);
            }

            serviceProxy.start(() -> {
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
            });
        }
    }

    @Override
    public void pauseDownloads() throws IllegalStateException {
        checkIfManagerStarted();
        assertProvider();

        List<DownloadItem> downloads = getDownloads(DownloadState.IN_PROGRESS);
        for (DownloadItem item : downloads) {
            serviceProxy.pauseDownload(item);
        }
    }

    @Override
    public void resumeDownloads() throws IllegalStateException {
        checkIfManagerStarted();
        assertProvider();

        List<DownloadItem> downloads = getDownloads(DownloadState.PAUSED);
        for (DownloadItem item : downloads) {
            serviceProxy.resumeDownload(item);
        }
    }

    @Override
    public DownloadItem findItem(String itemId) throws IllegalStateException {
        checkIfManagerStarted();
        assertProviderAndItem(itemId);
        return serviceProxy.findItem(itemId);
    }

    @Override
    public long getDownloadedItemSize(String itemId) throws IllegalStateException {
        checkIfManagerStarted();
        assertProvider();

        return serviceProxy.getDownloadedItemSize(itemId);
    }

    private void assertProvider() {
        if (serviceProxy == null) {
            throw new IllegalStateException("Provider Operation Not Valid");
        }
    }

    @Override
    public long getEstimatedItemSize(String itemId) throws IllegalStateException {
        checkIfManagerStarted();
        assertProviderAndItem(itemId);
        return serviceProxy.getEstimatedItemSize(itemId);
    }

    @Override
    public DownloadItem createItem(String itemId, String contentURL) throws IllegalStateException, IOException {
        checkIfManagerStarted();
        itemId = safeItemId(itemId);
        assertProviderAndItem(itemId);
        DownloadRequestParams downloadRequestParams = adapter.adapt(new DownloadRequestParams(Uri.parse(contentURL), null));
        return serviceProxy.createItem(itemId, downloadRequestParams.url.toString());
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

        serviceProxy.removeItem(item);
    }

    private File getItemDir(String itemId) {
        return new File(Storage.getItemsDir(), itemId);
    }

    @Override
    public File getAppDataDir(String itemId) {
        File appDataDir = new File(getItemDir(itemId), "appData");
        Utils.mkdirs(appDataDir);
        return appDataDir;
    }

    @Override
    public List<DownloadItem> getDownloads(DownloadState... states) throws IllegalStateException {
        checkIfManagerStarted();
        assertProvider();
        return new ArrayList<>(serviceProxy.getDownloads(states));
    }

    @Override
    public String getPlaybackURL(String itemId) throws IllegalStateException {
        checkIfManagerStarted();
        assertProviderAndItem(itemId);

        return serviceProxy.getPlaybackURL(itemId);
    }

    private void assertProviderAndItem(String itemId) {
        if (serviceProxy == null || TextUtils.isEmpty(itemId)) {
            throw new IllegalStateException("Provider Operation Not Valid");
        }
    }

    @Override
    public File getLocalFile(String itemId) throws IllegalStateException {
        checkIfManagerStarted();
        assertProviderAndItem(itemId);
        return serviceProxy.getLocalFile(itemId);
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

