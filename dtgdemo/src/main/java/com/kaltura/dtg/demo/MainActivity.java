package com.kaltura.dtg.demo;

import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.kaltura.dtg.ContentManager;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadState;
import com.kaltura.dtg.DownloadStateListener;
import com.kaltura.netkit.connect.response.ResultElement;
import com.kaltura.playkit.LocalAssetsManager;
import com.kaltura.playkit.PKDrmParams;
import com.kaltura.playkit.PKMediaConfig;
import com.kaltura.playkit.PKMediaEntry;
import com.kaltura.playkit.PKMediaFormat;
import com.kaltura.playkit.PKMediaSource;
import com.kaltura.playkit.PlayKitManager;
import com.kaltura.playkit.Player;
import com.kaltura.playkit.player.MediaSupport;
import com.kaltura.playkit.providers.api.ovp.SimpleOvpSessionProvider;
import com.kaltura.playkit.providers.base.OnMediaLoadCompletion;
import com.kaltura.playkit.providers.ovp.KalturaOvpMediaProvider;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


class ItemLoader {

    private static final String TAG = "ItemLoader";

    private static PKMediaSource findPreferredSource(List<PKMediaSource> sources) {
        PKMediaFormat[] formats = {PKMediaFormat.dash, PKMediaFormat.hls, PKMediaFormat.mp4, PKMediaFormat.mp3, PKMediaFormat.wvm};
        for (PKMediaFormat format : formats) {
            for (PKMediaSource source : sources) {
                if (source.hasDrmParams()) {
                    for (PKDrmParams params : source.getDrmData()) {
                        if (params.isSchemeSupported()) {
                            return source;  // this source has at least one supported DRM scheme
                        }
                    }
                } else {
                    return source;  // No DRM
                }
            }
        }
        return null;
    }

    private static List<Item> loadOVPItems(int partnerId, String... entries) {
        SimpleOvpSessionProvider sessionProvider = new SimpleOvpSessionProvider("https://cdnapisec.kaltura.com", partnerId, null);
        CountDownLatch latch = new CountDownLatch(entries.length);
        final List<Item> items = new ArrayList<>();

        for (int i = 0; i < entries.length; i++) {
            items.add(null);
            final String entryId = entries[i];
            final int index = i;
            new KalturaOvpMediaProvider().setSessionProvider(sessionProvider).setEntryId(entryId).load(new OnMediaLoadCompletion() {
                @Override
                public void onComplete(ResultElement<PKMediaEntry> response) {
                    PKMediaEntry mediaEntry = response.getResponse();
                    if (mediaEntry != null) {
                        PKMediaSource source = findPreferredSource(mediaEntry.getSources());

                        if (source == null) {
                            Log.w(TAG, "onComplete: No playable source for " + mediaEntry);
                            return; // don't add because we don't have a source
                        }

                        // forceReducedLicenseDuration(source, 600);

                        Item item = new Item(source, mediaEntry.getName());
                        items.set(index, item);

                    } else {
                        Log.d("LOAD", entryId);
                    }
                }
            });

        }

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (Iterator<Item> iterator = items.iterator(); iterator.hasNext();) {
            Item item = iterator.next();
            if (item == null) {
                // Remove the current element from the iterator and the list.
                iterator.remove();
            }
        }
        return items;
    }

    private static void forceReducedLicenseDuration(PKMediaSource source, int seconds) {
        for (PKDrmParams params : source.getDrmData()) {
            if (params.getScheme() == PKDrmParams.Scheme.WidevineCENC) {
                String url = params.getLicenseUri();
                url = url.replace("https://", "http://");
                url = url + "&rental_duration=" + seconds;
                params.setLicenseUri(url);
            }
        }
    }

    static List<Item>  loadItems() {
        List<Item> items = new ArrayList<>();

        // TODO: fill the list with Items -- each item has a single PKMediaSource with relevant DRM data.
        // Using OVP provider for simplicity
//        items.addAll(loadOVPItems(2222401, "1_q81a5nbp", "0_3cb7ganx"));

        // For simple cases (no DRM), no need for MediaSource.
        items.add(new Item("sintel-short-dash", "http://cdnapi.kaltura.com/p/2215841/playManifest/entryId/1_9bwuo813/format/mpegdash/protocol/http/a.mpd"));
        items.add(new Item("sintel-full-dash", "http://cdnapi.kaltura.com/p/2215841/playManifest/entryId/1_w9zx2eti/format/mpegdash/protocol/http/a.mpd"));
        items.add(new Item("kaltura-hls", "http://cdnapi.kaltura.com/p/243342/sp/24334200/playManifest/entryId/1_sf5ovm7u/flavorIds/1_d2uwy7vv,1_jl7y56al/format/applehttp/protocol/http/a.m3u8"));
        items.add(new Item("qa-multi-hls", "http://cdntesting.qa.mkaltura.com/p/1091/sp/109100/playManifest/entryId/0_df8g87v8/protocol/http/format/applehttp/flavorIds/0_tidbxf22,0_no2vm0z5,0_3s3crq4v,0_unnvmi49/a.m3u8"));
        items.add(new Item("aes-hls", "https://noamtamim.github.io/random/hls/test-enc-aes/multi.m3u8"));

        // An item with given URL and License URL.
//        items.add(new Item(
//                "test1", "Test 1", PKMediaFormat.dash, PKDrmParams.Scheme.WidevineCENC,
//                "<CONTENT-URL>",
//                "<LICENCE-URL>"
//        ));
        
        return items;
    }
}

class Item {
    private final PKMediaSource mediaSource;
    private final String name;

    DownloadState downloadState;
    boolean drmRegistered;
    long estimatedSize;
    long downloadedSize;

    Item(String id, String url) {
        this.mediaSource = new PKMediaSource().setId(id).setUrl(url);
        this.name = id;
    }
    
    Item(PKMediaSource mediaSource, String name) {
        this.mediaSource = mediaSource;
        this.name = name;
    }
    
    Item(String id, String name, PKMediaFormat format, PKDrmParams.Scheme scheme, String url, String licenseUrl) {
        this.mediaSource = new PKMediaSource()
                .setId(id)
                .setMediaFormat(format)
                .setUrl(url)
                .setDrmData(Collections.singletonList(new PKDrmParams(licenseUrl, scheme)));
        this.name = name;
    }

    boolean isDrmRegistered() {
        return drmRegistered;
    }
    
    boolean isDrmProtected() {
        return mediaSource.hasDrmParams();
    }

    @Override
    public String toString() {
        String drmState ;
        if (isDrmProtected()) {
            drmState = isDrmRegistered() ? "R" : "U";
        } else {
            drmState = "C";
        }
        
        String progress;
        if (estimatedSize > 0) {
            float percentComplete = 100f * downloadedSize / estimatedSize;
            progress = String.format(Locale.ENGLISH, "%.3f%% / %.3fmb", percentComplete, estimatedSize / 1024.0 / 1024);
        } else {
            progress = "-?-";
        }
        
        return String.format(Locale.ENGLISH, "[%s] %s -- %s -- DRM:%s", progress, getId(), downloadState, drmState);
    }

    String getId() {
        return mediaSource.getId();
    }

    String getUrl() {
        return mediaSource.getUrl();
    }

    PKMediaSource getMediaSource() {
        return mediaSource;
    }
}


public class MainActivity extends ListActivity {
    
    private static final String TAG = "MainActivity";
    private ContentManager contentManager;
    private LocalAssetsManager localAssetsManager;
    private Context context = this;

    private ArrayAdapter<Item> itemArrayAdapter;
    private Map<String, Item> itemMap = new HashMap<>();

    private DownloadStateListener cmListener = new DownloadStateListener() {

        long start;

        @Override
        public void onDownloadComplete(DownloadItem item) {
            Log.e(TAG, "onDownloadComplete: " + (SystemClock.elapsedRealtime() - start));
            itemStateChanged(item);
        }

        @Override
        public void onProgressChange(DownloadItem item, long downloadedBytes) {
            itemStateChanged(item);
        }

        @Override
        public void onDownloadStart(DownloadItem item) {
            start = SystemClock.elapsedRealtime();
            itemStateChanged(item);
        }

        @Override
        public void onDownloadPause(DownloadItem item) {
            itemStateChanged(item);
        }

        @Override
        public void onDownloadFailure(DownloadItem item, Exception error) {
            itemStateChanged(item);
        }

        @Override
        public void onDownloadMetadata(final DownloadItem item, Exception error) {
            itemStateChanged(item);

            if (error != null) {
                toast(error.toString());
                return;
            }

            final List<DownloadItem.Track> tracks = new ArrayList<>();
            final DownloadItem.TrackSelector trackSelector = item.getTrackSelector();
            if (trackSelector == null) {
                return;
            }

            List<Boolean> boolSelectedTracks = new ArrayList<>();
            List<String> trackNames = new ArrayList<>();
            final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
            for (DownloadItem.TrackType type : DownloadItem.TrackType.values()) {
                final List<DownloadItem.Track> availableTracks = trackSelector.getAvailableTracks(type);
                final List<DownloadItem.Track> selectedTracks = trackSelector.getSelectedTracks(type);
                for (DownloadItem.Track track : availableTracks) {
                    tracks.add(track);
                    boolSelectedTracks.add(selectedTracks.contains(track));
                    StringBuilder sb = new StringBuilder(track.getType().name());
                    final long bitrate = track.getBitrate();
                    if (bitrate > 0) {
                        sb.append(" | ").append(numberFormat.format(bitrate));
                    }
                    if (track.getType() == DownloadItem.TrackType.VIDEO) {
                        sb.append(" | ").append(track.getWidth()).append("x").append(track.getHeight());
                    }
                    final String language = track.getLanguage();
                    if (!TextUtils.isEmpty(language)) {
                        sb.append(" | ").append(language);
                    }
                    trackNames.add(sb.toString());

//                    trackNames.add(track.getType() + " | " + numberFormat.format(track.getBitrate())
//                             + ", " + track.getWidth() + "x" + track.getHeight() + ", " + track.getLanguage());
                }
            }

            final String[] allTrackNames = trackNames.toArray(new String[trackNames.size()]);
            final boolean[] selected = new boolean[boolSelectedTracks.size()];
            for (int i = 0; i < boolSelectedTracks.size(); i++) {
                selected[i] = boolSelectedTracks.get(i);
            }


            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // TODO: select tracks, if not selected before.
                    new AlertDialog.Builder(context)
                            .setTitle("Select tracks")
                            .setMultiChoiceItems(allTrackNames, selected, new DialogInterface.OnMultiChoiceClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                    selected[which] = isChecked;
                                }
                            })
                            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    saveSelection(tracks, selected, trackSelector);
                                    trackSelector.apply(new DownloadItem.OnTrackSelectionListener() {
                                        @Override
                                        public void onTrackSelectionComplete(Exception e) {
                                            itemStateChanged(item);
                                            notifyDataSetChanged();
                                        }
                                    });
                                }
                            })
                            .setNegativeButton("Default", null)
                            .setNeutralButton("Start", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    saveSelection(tracks, selected, trackSelector);
                                    trackSelector.apply(new DownloadItem.OnTrackSelectionListener() {
                                        @Override
                                        public void onTrackSelectionComplete(Exception e) {
                                            itemStateChanged(item);
                                            notifyDataSetChanged();
                                            item.startDownload();
                                        }
                                    });
                                }
                            })
                            .show();
                }
            });

        }

        @Override
        public void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector) {
            // TODO: select tracks

            // For example, leave the video selection as is; select all audio and text tracks.
            trackSelector.setSelectedTracks(DownloadItem.TrackType.AUDIO, trackSelector.getAvailableTracks(DownloadItem.TrackType.AUDIO));
            trackSelector.setSelectedTracks(DownloadItem.TrackType.TEXT, trackSelector.getAvailableTracks(DownloadItem.TrackType.TEXT));
        }
    };

    private static void saveSelection(List<DownloadItem.Track> tracks, boolean[] selected, DownloadItem.TrackSelector trackSelector) {
        for (DownloadItem.TrackType trackType : DownloadItem.TrackType.values()) {
            List<DownloadItem.Track> select = new ArrayList<>();
            for (int i = 0; i < tracks.size(); i++) {
                final DownloadItem.Track track = tracks.get(i);
                if (track.getType() == trackType && selected[i]) {
                    select.add(track);
                }
            }
            trackSelector.setSelectedTracks(trackType, select);
        }
    }

    private Player player;

    private void itemStateChanged(DownloadItem downloadItem) {
        Item item = itemMap.get(downloadItem.getItemId());
        if (item != null) {
            item.downloadState = downloadItem.getState();
            item.estimatedSize = downloadItem.getEstimatedSizeBytes();
            item.downloadedSize = downloadItem.getDownloadedSizeBytes();
            notifyDataSetChanged();
        }
    }
    
    private void notifyDataSetChanged() {
        getListView().post(new Runnable() {
            @Override
            public void run() {
                itemArrayAdapter.notifyDataSetChanged();
            }
        });
    }

    enum Action {
        add,
        start,
        remove,
        pause,
        register,
        checkStatus,
        unregister,
        playOffline,
        playOnline;

        @NonNull  static Action[] actions(Item item) {
            if (item.downloadState == null) {
                return new Action[] {add, unregister, playOnline};
            }
            
            switch (item.downloadState) {
                case NEW:
                    return new Action[] {remove, checkStatus, unregister, playOnline};
                case INFO_LOADED:
                    return new Action[] {start, pause, remove, register, checkStatus, unregister, playOnline};
                case IN_PROGRESS:
                    return new Action[] {start, pause, remove, register, checkStatus, unregister, playOnline};
                case PAUSED:
                    return new Action[] {start, remove, register, checkStatus, unregister, playOnline};
                case COMPLETED:
                    if (item.isDrmProtected()) {
                        if (item.isDrmRegistered()) {
                            return new Action[] {playOffline, checkStatus, unregister, playOnline};
                        } else {
                            return new Action[] {register, checkStatus, remove, playOnline};
                        }
                    } else {
                        return new Action[] {playOffline, remove, playOnline};
                    }
                case FAILED:
                    return new Action[] {start, remove, checkStatus, unregister, playOnline};
            }
            throw new IllegalStateException();
        }
        
        static String[] strings(Action[] actions) {
            List<String> stringActions = new ArrayList<>();
            for (Action action : actions) {
                stringActions.add(action.name());
            }
            return stringActions.toArray(new String[stringActions.size()]);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        itemArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        loadTestItems(itemArrayAdapter);
        if (!isNetworkAvailable()) {
            toast("NO NETWORK AVAILABLE");
        }
        contentManager = ContentManager.getInstance(this);
//        contentManager.getSettings().defaultHlsAudioBitrateEstimation = 128000;
        contentManager.getSettings().applicationName = "MyApplication";
        contentManager.getSettings().maxConcurrentDownloads = 4;
        contentManager.getSettings().createNoMediaFileInDownloadsDir = true;
        contentManager.addDownloadStateListener(cmListener);
        contentManager.start(new ContentManager.OnStartedListener() {
            @Override
            public void onStarted() {
                for (DownloadItem item : contentManager.getDownloads(DownloadState.values())) {
                    itemStateChanged(item);
                }

                setListAdapter(itemArrayAdapter);
            }
        });
        
        localAssetsManager = new LocalAssetsManager(context);
        
        findViewById(R.id.download_control).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] actions = {"Start service", "Stop service"};
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Download Action")
                        .setItems(actions, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                    case 0:
                                        contentManager.start(new ContentManager.OnStartedListener() {
                                            @Override
                                            public void onStarted() {
                                                toast("Service started");
                                            }
                                        });
                                        break;
                                    case 1:
                                        contentManager.stop();
                                        break;
                                }

                            }
                        }).show();
            }
        });
        
        findViewById(R.id.player_control).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] actions = {"Play", "Pause", "Seek -60", "Seek -15", "Seek +15", "Seek +60"};
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Player Action")
                        .setItems(actions, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (player == null) {
                                    return;
                                }
                                switch (which) {
                                    case 0:
                                        player.play();
                                        break;
                                    case 1:
                                        player.pause();
                                        break;
                                    case 2:
                                        player.seekTo(player.getCurrentPosition() - 60000);
                                        break;
                                    case 3:
                                        player.seekTo(player.getCurrentPosition() - 15000);
                                        break;
                                    case 4:
                                        player.seekTo(player.getCurrentPosition() + 15000);
                                        break;
                                    case 5:
                                        player.seekTo(player.getCurrentPosition() + 60000);
                                        break;
                                }
                            }
                        }).show();
                        
            }
        });
    }

    void addAndLoad(Item item) {
        DownloadItem downloadItem = contentManager.createItem(item.getId(), item.getUrl());
        downloadItem.loadMetadata();
        itemStateChanged(downloadItem);
    }

    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        ListAdapter adapter = l.getAdapter();
        final Item item = (Item) adapter.getItem(position);
        Log.d(TAG, "Clicked " + item);

        DownloadState state = null;

        if (!contentManager.isStarted()) {
            return;
        }

        final DownloadItem downloadItem = contentManager.findItem(item.getId());
        if (downloadItem != null) {
            item.downloadState = downloadItem.getState();
            Log.d(TAG, "duration: " + downloadItem.getDurationMS());
        }

        final Action[] actions = Action.actions(item);
        String[] actionNames = Action.strings(actions);

        new AlertDialog.Builder(this)
                .setTitle(item.toString())
                .setItems(actionNames, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Action action = actions[i];
                        Log.d(TAG, "" + action);
                        switch (action) {

                            case add:
                                addAndLoad(item);
                                break;
                            case start:
                                contentManager.findItem(item.getId()).startDownload();
                                break;
                            case remove:
                                contentManager.removeItem(item.getId());
                                item.downloadedSize = 0;
                                item.estimatedSize = 0;
                                item.downloadState = null;
                                notifyDataSetChanged();
                                break;
                            case pause:
                                contentManager.findItem(item.getId()).pauseDownload();
                                break;
                            case register:
                                registerAsset(item);
                                break;
                            case checkStatus:
                                checkStatus(item);
                                break;
                            case unregister:
                                unregisterAsset(item);
                                break;
                            case playOffline:
                                playDownloadedItem(item);
                                break;
                            case playOnline:
                                playOnlineItem(item);
                                break;
                        }
                    }
                })
                .setCancelable(true)
                .show();

    }

    private void checkStatus(Item item) {
        final File localFile = contentManager.getLocalFile(item.getId());
        if (localFile == null) {
            return;
        }
        String absolutePath = localFile.getAbsolutePath();
        localAssetsManager.checkAssetStatus(absolutePath, item.getId(), new LocalAssetsManager.AssetStatusListener() {
            @Override
            public void onStatus(String localAssetPath, long expiryTimeSeconds, long availableTimeSeconds, boolean isRegistered) {
                toast("" + expiryTimeSeconds +  " " + availableTimeSeconds);
            }
        });

    }

    private void playOnlineItem(Item item) {
        
        playItem(item.getId(), item.getMediaSource(), PKMediaEntry.MediaEntryType.Vod);
    }

    private void registerAsset(final Item item) {

        String absolutePath = contentManager.getLocalFile(item.getId()).getAbsolutePath();
        PKMediaSource mediaSource = item.getMediaSource();
        localAssetsManager.registerAsset(mediaSource, absolutePath, item.getId(), new LocalAssetsManager.AssetRegistrationListener() {
            @Override
            public void onRegistered(String localAssetPath) {
                item.drmRegistered = true;
                notifyDataSetChanged();
            }

            @Override
            public void onFailed(String localAssetPath, Exception error) {

            }
        });
    }
    
    private void unregisterAsset(final Item item) {


        final File localFile = contentManager.getLocalFile(item.getId());
        if (localFile == null) {
            toast("File not found");
            return;
        }
        final String localAssetPath = localFile.getAbsolutePath();
        localAssetsManager.unregisterAsset(localAssetPath, item.getId(), new LocalAssetsManager.AssetRemovalListener() {
            @Override
            public void onRemoved(String localAssetPath) {
                item.drmRegistered = false;
                notifyDataSetChanged();
            }
        });
    }

    private void toast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void playDownloadedItem(Item item) {

        final String itemId = item.getId();
        PKMediaSource localMediaSource = localAssetsManager.getLocalMediaSource(itemId, contentManager.getLocalFile(itemId).getAbsolutePath());

        playItem(itemId, localMediaSource, PKMediaEntry.MediaEntryType.Vod);
    }

    private void playItem(String itemId, PKMediaSource mediaSource, PKMediaEntry.MediaEntryType type) {
        PKMediaEntry entry = new PKMediaEntry().setId(itemId).setMediaType(type).setSources(Collections.singletonList(mediaSource));


        setupPlayer();


        player.prepare(new PKMediaConfig().setMediaEntry(entry));
        player.play();
    }

    private void setupPlayer() {
        if (player == null) {
            player = PlayKitManager.loadPlayer(this, null);
            ViewGroup playerRoot = findViewById(R.id.player_root);
            playerRoot.addView(player.getView());
        } else {
            player.stop();
        }
    }

    private void loadTestItems(final ArrayAdapter<Item> itemAdapter) {

        MediaSupport.initializeDrm(this, new MediaSupport.DrmInitCallback() {
            @Override
            public void onDrmInitComplete(Set<PKDrmParams.Scheme> supportedDrmSchemes, boolean provisionPerformed, Exception provisionError) {
                List<Item> items = ItemLoader.loadItems();
                itemAdapter.addAll(items);
                for (final Item item : items) {
                    if (item != null) {
                        itemMap.put(item.getId(), item);
                    }
                }
            }
        });
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
