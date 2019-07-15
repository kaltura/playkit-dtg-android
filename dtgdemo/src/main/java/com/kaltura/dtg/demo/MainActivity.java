package com.kaltura.dtg.demo;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.security.ProviderInstaller;
import com.kaltura.dtg.ContentManager;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadState;
import com.kaltura.dtg.DownloadStateListener;
import com.kaltura.playkit.LocalAssetsManager;
import com.kaltura.playkit.PKDrmParams;
import com.kaltura.playkit.PKMediaConfig;
import com.kaltura.playkit.PKMediaEntry;
import com.kaltura.playkit.PKMediaFormat;
import com.kaltura.playkit.PKMediaSource;
import com.kaltura.playkit.PlayKitManager;
import com.kaltura.playkit.Player;
import com.kaltura.playkit.PlayerEvent;
import com.kaltura.playkit.player.AudioTrack;
import com.kaltura.playkit.player.BaseTrack;
import com.kaltura.playkit.player.PKTracks;
import com.kaltura.playkit.player.TextTrack;
import com.kaltura.playkit.providers.api.phoenix.APIDefines;
import com.kaltura.playkit.providers.ott.PhoenixMediaProvider;
import com.kaltura.playkit.providers.ovp.KalturaOvpMediaProvider;
import com.kaltura.playkit.providers.api.SimpleSessionProvider;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.kaltura.playkit.player.MediaSupport.*;

class DemoParams {
    //    static int forceReducedLicenseDurationSeconds = 600;
    static int forceReducedLicenseDurationSeconds = 0;


    static int defaultHlsAudioBitrateEstimation = 64000;
}

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
        SimpleSessionProvider sessionProvider = new SimpleSessionProvider("https://cdnapisec.kaltura.com", partnerId, null);
        CountDownLatch latch = new CountDownLatch(entries.length);
        final List<Item> items = new ArrayList<>();

        for (int i = 0; i < entries.length; i++) {
            items.add(null);
            final String entryId = entries[i];
            final int index = i;
            new KalturaOvpMediaProvider().setSessionProvider(sessionProvider).setEntryId(entryId).load(response -> {
                PKMediaEntry mediaEntry = response.getResponse();
                if (mediaEntry != null) {
                    PKMediaSource source = findPreferredSource(mediaEntry.getSources());

                    if (source == null) {
                        Log.w(TAG, "onComplete: No playable source for " + mediaEntry);
                        return; // don't add because we don't have a source
                    }

                    forceReducedLicenseDuration(source, DemoParams.forceReducedLicenseDurationSeconds);

                    Item item = new Item(source, mediaEntry.getName());
                    items.set(index, item);

                } else {
                    Log.d("LOAD", entryId);
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
        if (seconds <= 0) return;
        for (PKDrmParams params : source.getDrmData()) {
            if (params.getScheme() == PKDrmParams.Scheme.WidevineCENC) {
                String url = params.getLicenseUri();
                url = url.replace("https://", "http://");
                url = url + "&rental_duration=" + seconds;
                params.setLicenseUri(url);
            }
        }
    }

    private static List<Item> loadOTTItems(String phoenixBaseURL, int partnerId, String ks, String format, String... entries) {
        SimpleSessionProvider sessionProvider = new SimpleSessionProvider(phoenixBaseURL, partnerId, ks);
        CountDownLatch latch = new CountDownLatch(entries.length);
        final List<Item> items = new ArrayList<>();

        for (int i = 0; i < entries.length; i++) {
            items.add(null);
            final String mediaId = entries[i];
            final int index = i;

            new PhoenixMediaProvider()
                    .setSessionProvider(sessionProvider)
                    .setAssetId(mediaId)
                    .setProtocol(PhoenixMediaProvider.HttpProtocol.Https)
                    .setContextType(APIDefines.PlaybackContextType.Playback)
                    .setAssetType(APIDefines.KalturaAssetType.Media)
                    .setFormats(format).load(response -> {
                PKMediaEntry mediaEntry = response.getResponse();
                if (mediaEntry != null) {
                    PKMediaSource source = findPreferredSource(mediaEntry.getSources());

                    if (source == null) {
                        Log.w(TAG, "onComplete: No playable source for " + mediaEntry);
                        return; // don't add because we don't have a source
                    }

                    //forceReducedLicenseDuration(source, DemoParams.forceReducedLicenseDurationSeconds);

                    Item item = new Item(source, mediaEntry.getName());
                    items.set(index, item);

                } else {
                    Log.d("LOAD", mediaId);
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


    static List<Item>  loadItems() {
        List<Item> items = new ArrayList<>();

        // TODO: fill the list with Items -- each item has a single PKMediaSource with relevant DRM data.
        // Using OVP provider for simplicity
//        items.addAll(loadOVPItems(2222401, "1_q81a5nbp", "0_3cb7ganx"));
        // Using Phoenix provider for simplicity
        items.addAll(loadOTTItems("https://api-preprod.ott.kaltura.com/v5_1_0/api_v3/", 198, "",  "Mobile_Devices_Main_HD_Dash", "480989"));

        // For simple cases (no DRM), no need for MediaSource.
        //noinspection CollectionAddAllCanBeReplacedWithConstructor
        items.addAll(Arrays.asList(
                item("sintel-short-dash",   "http://cdnapi.kaltura.com/p/2215841/playManifest/entryId/1_9bwuo813/format/mpegdash/protocol/http/a.mpd"),
                item("sintel-short-hls",    "http://cdnapi.kaltura.com/p/2215841/playManifest/entryId/1_9bwuo813/format/applehttp/protocol/http/a.m3u8"),
                item("sintel-full-dash",    "http://cdnapi.kaltura.com/p/2215841/playManifest/entryId/1_w9zx2eti/format/mpegdash/protocol/http/a.mpd"),
                item("sintel-full-hls",     "http://cdnapi.kaltura.com/p/2215841/playManifest/entryId/1_w9zx2eti/format/applehttp/protocol/http/a.m3u8"),
                item("tears-multi-dash",    "http://cdntesting.qa.mkaltura.com/p/1091/sp/109100/playManifest/entryId/0_ttfy4uu0/protocol/http/format/mpegdash/flavorIds/0_yuv6fomw,0_i414yxdl,0_mwmzcwv0,0_g68ar3sh/a.mpd"),
                item("tears-multi-hls",     "http://cdntesting.qa.mkaltura.com/p/1091/sp/109100/playManifest/entryId/0_ttfy4uu0/protocol/http/format/applehttp/flavorIds/0_yuv6fomw,0_i414yxdl,0_mwmzcwv0,0_g68ar3sh/a.m3u8"),
                item("aes-hls",     "https://noamtamim.github.io/random/hls/test-enc-aes/multi.m3u8"),
                item("apple-dolby", "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8")
        ));

        // An item with given URL and License URL.
//        items.add(new Item(
//                "test1", "Test 1", PKMediaFormat.dash, PKDrmParams.Scheme.WidevineCENC,
//                "<CONTENT-URL>",
//                "<LICENCE-URL>"
//        ));

        return items;
    }

    @NonNull
    private static Item item(String id, String url) {
        return new Item(id, url);
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

    @NonNull
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

    private TextTrack currentTextTrack;
    private AudioTrack currentAudioTrack;

    private DownloadStateListener cmListener = new DownloadStateListener() {

        private long lastReportedProgress;
        long start;

        @Override
        public void onDownloadComplete(DownloadItem item) {
            Log.e(TAG, "onDownloadComplete: " + (SystemClock.elapsedRealtime() - start));
            itemStateChanged(item);
        }

        @Override
        public void onProgressChange(DownloadItem item, long downloadedBytes) {
            final long now = SystemClock.elapsedRealtime();
            if (now - lastReportedProgress > 200) {
                itemStateChanged(item);
                lastReportedProgress = now;
            }
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
        public void onDownloadFailure(final DownloadItem item, final Exception error) {
            Log.d(TAG, "onDownloadFailure: " + item);
            runOnUiThread(() -> Toast.makeText(context, "Download of " + item.getItemId() + " has failed: " + error.getMessage(), Toast.LENGTH_LONG).show());
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

            final String[] allTrackNames = trackNames.toArray(new String[0]);
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
                            .setMultiChoiceItems(allTrackNames, selected, (dialog, which, isChecked) -> selected[which] = isChecked)
                            .setPositiveButton("Save", (dialog, which) -> {
                                saveSelection(tracks, selected, trackSelector);
                                trackSelector.apply(e -> {
                                    itemStateChanged(item);
                                    notifyDataSetChanged();
                                });
                            })
                            .setNegativeButton("Default", null)
                            .setNeutralButton("Start", (dialog, which) -> {
                                saveSelection(tracks, selected, trackSelector);
                                trackSelector.apply(e -> {
                                    itemStateChanged(item);
                                    notifyDataSetChanged();
                                    item.startDownload();
                                });
                            })
                            .show();
                }
            });

        }

        @Override
        public void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector) {
            /*
            TODO: select tracks
            For example, leave the video selection as is; select all audio and text tracks.
            */

            trackSelector.setSelectedTracks(DownloadItem.TrackType.AUDIO, trackSelector.getAvailableTracks(DownloadItem.TrackType.AUDIO));
            trackSelector.setSelectedTracks(DownloadItem.TrackType.TEXT, trackSelector.getAvailableTracks(DownloadItem.TrackType.TEXT));
        }
    };
    private List<AudioTrack> audioTracks;
    private List<TextTrack> textTracks;

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
        getListView().post(() -> itemArrayAdapter.notifyDataSetChanged());
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
            return stringActions.toArray(new String[0]);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        ProviderInstaller.installIfNeededAsync(this, new ProviderInstaller.ProviderInstallListener() {
            @Override
            public void onProviderInstalled() {

            }

            @Override
            public void onProviderInstallFailed(int i, Intent intent) {
                Toast.makeText(context, "ProviderInstallFailed", Toast.LENGTH_LONG).show();
            }
        });

        itemArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        loadTestItems(itemArrayAdapter);
        if (!isNetworkAvailable()) {
            toast("NO NETWORK AVAILABLE");
        }

        contentManager = ContentManager.getInstance(this);
        contentManager.getSettings().defaultHlsAudioBitrateEstimation = DemoParams.defaultHlsAudioBitrateEstimation;
        contentManager.getSettings().applicationName = "MyApplication";
        contentManager.getSettings().maxConcurrentDownloads = 4;
        contentManager.getSettings().createNoMediaFileInDownloadsDir = true;

        contentManager.addDownloadStateListener(cmListener);

        try {
            contentManager.start(() -> {
                for (DownloadItem item : contentManager.getDownloads(DownloadState.values())) {
                    itemStateChanged(item);
                }

                setListAdapter(itemArrayAdapter);
            });
        } catch (IOException e) {
            e.printStackTrace();
            toast("Failed to get ContentManager");
            return;
        }

        localAssetsManager = new LocalAssetsManager(context);

        findViewById(R.id.download_control).setOnClickListener(v -> {
            String[] actions = {"Start service", "Stop service"};
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Download Action")
                    .setItems(actions, (dialog, which) -> {
                        switch (which) {
                            case 0:
                                try {
                                    contentManager.start(() -> toast("Service started"));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 1:
                                contentManager.stop();
                                break;
                        }

                    }).show();
        });

        findViewById(R.id.player_control).setOnClickListener(v -> {
            String[] actions = {"Play", "Pause", "Seek -60", "Seek -15", "Seek +15", "Seek +60", "Select audio", "Select text"};
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Player Action")
                    .setItems(actions, (dialog, which) -> {
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
                            case 6:
                                selectPlayerTrack(true);
                                break;
                            case 7:
                                selectPlayerTrack(false);
                                break;

                        }
                    }).show();

        });
    }

    private void selectPlayerTrack(boolean audio) {

        final List<? extends BaseTrack> tracks = audio ? audioTracks : textTracks;

        if (tracks == null) {
            return;
        }

        List<String> trackTitles = new ArrayList<>();
        final List<String> trackIds = new ArrayList<>();

        for (BaseTrack track : tracks) {
            String language = audio ? ((AudioTrack) track).getLanguage() : ((TextTrack) track).getLanguage();
            if (language != null) {
                trackIds.add(track.getUniqueId());
                trackTitles.add(language);
            }
        }

        if (trackIds.size() < 2) {
            return;
        }

        BaseTrack currentTrack = audio ? currentAudioTrack : currentTextTrack;

        int currentIndex = currentTrack != null ? trackIds.indexOf(currentTrack.getUniqueId()) : -1;
        final int[] selected = {currentIndex};

        new AlertDialog.Builder(context)
                .setTitle("Select track")
                .setSingleChoiceItems(trackTitles.toArray(new String[0]), selected[0], (dialogInterface, i) -> selected[0] = i)
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    if (selected[0] >= 0) {
                        player.changeTrack(trackIds.get(selected[0]));
                    }
                }).show();
    }

    void addAndLoad(Item item) {
        DownloadItem downloadItem;
        try {
            downloadItem = contentManager.createItem(item.getId(), item.getUrl());
            downloadItem.loadMetadata();
            itemStateChanged(downloadItem);
        } catch (IOException e) {
            toast("Failed to add item: " + e.toString());
            e.printStackTrace();
        }
    }


    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        ListAdapter adapter = l.getAdapter();
        final Item item = (Item) adapter.getItem(position);
        Log.d(TAG, "Clicked " + item);

        //DownloadState state = null;

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
                .setItems(actionNames, (dialogInterface, i) -> {
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
        localAssetsManager.checkAssetStatus(absolutePath, item.getId(), (localAssetPath, expiryTimeSeconds, availableTimeSeconds, isRegistered) -> toast("" + expiryTimeSeconds +  " " + availableTimeSeconds));

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
                toast("item:" + item.getId() + " Registered");
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
        localAssetsManager.unregisterAsset(localAssetPath, item.getId(), localAssetPath1 -> {
            item.drmRegistered = false;
            notifyDataSetChanged();
            toast("item:" + item.getId() + " Unregistered");
        });
    }

    private void toast(final String text) {
        runOnUiThread(() -> Toast.makeText(context, text, Toast.LENGTH_LONG).show());
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
            addPlayerEventListeners();
            ViewGroup playerRoot = findViewById(R.id.player_root);
            playerRoot.addView(player.getView());
        } else {
            player.stop();
        }
    }

    private void addPlayerEventListeners() {

        player.addListener(this, PlayerEvent.tracksAvailable, event -> {
            PKTracks tracksInfo = event.tracksInfo;
            audioTracks = tracksInfo.getAudioTracks();
            textTracks = tracksInfo.getTextTracks();

            if (currentAudioTrack == null && !audioTracks.isEmpty()) {
                currentAudioTrack = audioTracks.get(tracksInfo.getDefaultAudioTrackIndex());
            }
            if (currentTextTrack != null && !textTracks.isEmpty()) {
                currentTextTrack = textTracks.get(tracksInfo.getDefaultTextTrackIndex());
            }
        });

        player.addListener(this, PlayerEvent.audioTrackChanged, event -> {
            currentAudioTrack = event.newTrack;
            Log.d(TAG, "currentAudioTrack: " + currentAudioTrack.getUniqueId() + " " + currentAudioTrack.getLanguage());
        });

        player.addListener(this, PlayerEvent.textTrackChanged, event -> {
            currentTextTrack = event.newTrack;
            Log.d(TAG, "currentTextTrack: " + currentTextTrack);
        });
    }

    private void loadTestItems(final ArrayAdapter<Item> itemAdapter) {

        initializeDrm(this, (supportedDrmSchemes, provisionPerformed, provisionError) -> runOnUiThread(() -> {
            List<Item> items = ItemLoader.loadItems();
            itemAdapter.addAll(items);
            for (final Item item : items) {
                if (item != null) {
                    itemMap.put(item.getId(), item);
                }
            }
        }));
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
