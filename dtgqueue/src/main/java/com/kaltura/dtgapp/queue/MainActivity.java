package com.kaltura.dtgapp.queue;

import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.kaltura.dtg.ContentManager;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadState;
import com.kaltura.dtg.DownloadStateListener;
import com.kaltura.netkit.connect.response.ResultElement;
import com.kaltura.playkit.LocalAssetsManager;
import com.kaltura.playkit.PKMediaConfig;
import com.kaltura.playkit.PKMediaEntry;
import com.kaltura.playkit.PKMediaFormat;
import com.kaltura.playkit.PKMediaSource;
import com.kaltura.playkit.PlayKitManager;
import com.kaltura.playkit.Player;
import com.kaltura.playkit.api.ovp.SimpleOvpSessionProvider;
import com.kaltura.playkit.mediaproviders.base.OnMediaLoadCompletion;
import com.kaltura.playkit.mediaproviders.ovp.KalturaOvpMediaProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


class ItemLoader {
    
    private static PKMediaSource findFirstDash(List<PKMediaSource> sources) {
        for (PKMediaSource source : sources) {
            if (source.getMediaFormat() == PKMediaFormat.dash) {
                return source;
            }
        }
        throw new IllegalArgumentException("No dash source");
    }

    private static List<Item> loadOVPItems(int partnerId, String... entries) {
        SimpleOvpSessionProvider sessionProvider = new SimpleOvpSessionProvider("https://cdnapisec.kaltura.com", partnerId, null);
        CountDownLatch latch = new CountDownLatch(entries.length);
        final List<Item> items = new ArrayList<>();

        for (int i = 0; i < entries.length; i++) {
            items.add(null);
            String entryId = entries[i];
            final int index = i;
            new KalturaOvpMediaProvider().setSessionProvider(sessionProvider).setEntryId(entryId).load(new OnMediaLoadCompletion() {
                @Override
                public void onComplete(ResultElement<PKMediaEntry> response) {
                    PKMediaSource source = findFirstDash(response.getResponse().getSources());
                    items.set(index, new Item(source));
                }
            });

        }

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return items;
    }

    static List<Item>  loadItems() {
        List<Item> items = new ArrayList<>();
        
        // TODO: fill the list with Items -- each item has a single PKMediaSource with relevant DRM data.
        // Using OVP provider for simplicity
        items.addAll(loadOVPItems(2222401, "1_q81a5nbp"));
        
        // For simple cases (no DRM), no need for MediaSource.
        items.add(new Item("sintel-short-dash", "http://cdnapi.kaltura.com/p/2215841/playManifest/entryId/1_9bwuo813/format/mpegdash/protocol/http/a.mpd"));
        items.add(new Item("sintel-full-dash", "http://cdnapi.kaltura.com/p/2215841/playManifest/entryId/1_w9zx2eti/format/mpegdash/protocol/http/a.mpd"));
        items.add(new Item("kaltura", "http://cdnapi.kaltura.com/p/243342/sp/24334200/playManifest/entryId/1_sf5ovm7u/flavorIds/1_d2uwy7vv,1_jl7y56al/format/applehttp/protocol/http/a.m3u8"));
        
        return items;
    }
}

class Item {
    private final PKMediaSource mediaSource;
    
    DownloadState downloadState;
    boolean drmRegistered;

    Item(String id, String url) {
        this.mediaSource = new PKMediaSource().setId(id).setUrl(url);
    }
    
    Item(PKMediaSource mediaSource) {
        this.mediaSource = mediaSource;
    }

    public boolean isDrmRegistered() {
        return drmRegistered;
    }
    
    boolean isDrmProtected() {
        return mediaSource.hasDrmParams();
    }

    @Override
    public String toString() {
        String drmState = null;
        if (isDrmProtected()) {
            drmState = isDrmRegistered() ? "registered" : "unregistered";
        } else {
            drmState = "clear";
        }
        
        return getId() + " | " + downloadState + " | " + drmState;
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
        @Override
        public void onDownloadComplete(DownloadItem item) {
            itemStateChanged(item);
        }

        @Override
        public void onProgressChange(DownloadItem item, long downloadedBytes) {
            itemStateChanged(item);
        }

        @Override
        public void onDownloadStart(DownloadItem item) {
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
        public void onDownloadMetadata(DownloadItem item, Exception error) {
            itemStateChanged(item);
        }

        @Override
        public void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector) {

        }
    };

    private void itemStateChanged(DownloadItem downloadItem) {
        Item item = itemMap.get(downloadItem.getItemId());
        if (item != null) {
            item.downloadState = downloadItem.getState();
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
        play;
        
        @NonNull  static Action[] actions(Item item) {
            if (item.downloadState == null) {
                return new Action[] {add};
            }
            
            switch (item.downloadState) {
                case NEW:
                    return new Action[] {};
                case INFO_LOADED:
                    return new Action[] {start, pause, remove};
                case IN_PROGRESS:
                    return new Action[] {start, pause, remove};
                case PAUSED:
                    return new Action[] {start, remove};
                case COMPLETED:
                    if (!item.isDrmProtected() || item.isDrmRegistered()) {
                        return new Action[] {play, remove};
                    } else {
                        return new Action[] {register, remove};
                    }
                case FAILED:
                    return new Action[] {start, remove};
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

        itemArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        loadTestItems(itemArrayAdapter);

        contentManager = ContentManager.getInstance(this);
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

        final DownloadItem downloadItem = contentManager.findItem(item.getId());
        if (downloadItem != null) {
            item.downloadState = downloadItem.getState();
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
                                item.downloadState = null;
                                notifyDataSetChanged();
                                break;
                            case pause:
                                contentManager.findItem(item.getId()).pauseDownload();
                                break;
                            case register:
                                registerAsset(item);
                                break;
                            case play:
                                playDownloadedItem(item.getId());
                                break;
                        }
                    }
                })
                .setCancelable(true)
                .show();

    }

    private void registerAsset(final Item item) {

        localAssetsManager.registerAsset(item.getMediaSource(), contentManager.getLocalFile(item.getId()).getAbsolutePath(), item.getId(), new LocalAssetsManager.AssetRegistrationListener() {
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

    private void playDownloadedItem(String itemId) {

        PKMediaSource localMediaSource = localAssetsManager.getLocalMediaSource(itemId, contentManager.getLocalFile(itemId).getAbsolutePath());

        PKMediaEntry entry = new PKMediaEntry().setId(itemId).setMediaType(PKMediaEntry.MediaEntryType.Vod).setSources(Collections.singletonList(localMediaSource));


        Player player = PlayKitManager.loadPlayer(this, null);
        
        
        player.prepare(new PKMediaConfig().setMediaEntry(entry));
        player.play();

        ViewGroup playerRoot = (ViewGroup) findViewById(R.id.player_root);
        playerRoot.addView(player.getView());

    }

    private void loadTestItems(final ArrayAdapter<Item> itemAdapter) {

        List<Item> items = ItemLoader.loadItems();
        itemAdapter.addAll(items);
        for (Item item : items) {
            itemMap.put(item.getId(), item);
        }
    }
}
