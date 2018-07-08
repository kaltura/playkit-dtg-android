package com.kaltura.dtg;

import android.support.annotation.NonNull;
import android.util.Log;

import com.kaltura.dtg.DownloadItem.TrackType;
import com.kaltura.dtg.dash.DashFactory;
import com.kaltura.dtg.hls.HlsFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public abstract class BaseAbrDownloader {

    private static final String TAG = "BaseAbrDownloader";

    protected static final int MAX_MANIFEST_SIZE = 10 * 1024 * 1024;
    private final DownloadItemImp item;
    protected String manifestUrl;

    private long itemDurationMS;
    private long estimatedDownloadSize;
    private File targetDir;
    private Map<TrackType, List<BaseTrack>> selectedTracks;
    private Map<TrackType, List<BaseTrack>> availableTracks;
    private LinkedHashSet<DownloadTask> downloadTasks;
    private TrackUpdatingData trackUpdatingData;    // only used in update mode

    protected byte[] originManifestBytes;
    private boolean trackSelectionApplied;

    protected void applyInitialTrackSelection() throws IOException {
        if (trackSelectionApplied) {
            return;
        }
        createLocalManifest();
        createDownloadTasks();
        trackSelectionApplied = true;
    }

    class TrackUpdatingData {
        Map<DownloadItem.TrackType, List<BaseTrack>> originalSelectedTracks;
        boolean trackSelectionChanged;

        TrackUpdatingData(Map<TrackType, List<BaseTrack>> originalSelectedTracks) {
            this.originalSelectedTracks = originalSelectedTracks;
        }
    }

    private Mode mode = Mode.create;

    protected enum Mode {
        create,
        update
    }

    public BaseAbrDownloader initForUpdate() throws IOException {
        this.mode = Mode.update;
        this.loadStoredOriginManifest();
        this.parseOriginManifest();

        this.setSelectedTracksMap(new HashMap<DownloadItem.TrackType, List<BaseTrack>>());
        this.setAvailableTracksMap(new HashMap<DownloadItem.TrackType, List<BaseTrack>>());
        Map<DownloadItem.TrackType, List<BaseTrack>> originalSelectedTracks = new HashMap<>();

        for (DownloadItem.TrackType type : DownloadItem.TrackType.values()) {
            List<BaseTrack> availableTracks = this.item.getService().readTracksFromDB(item.getItemId(), type, null, this.getAssetFormat());
            this.getAvailableTracks().put(type, availableTracks);

            List<BaseTrack> selectedTracks = this.item.getService().readTracksFromDB(item.getItemId(), type, BaseTrack.TrackState.SELECTED, this.getAssetFormat());
            this.setSelectedTracks(type, selectedTracks);
            originalSelectedTracks.put(type, selectedTracks);
        }

        trackUpdatingData = new TrackUpdatingData(originalSelectedTracks);

        return this;
    }

    private void loadStoredOriginManifest() throws IOException {
        FileInputStream inputStream = new FileInputStream(new File(targetDir, storedOriginManifestName()));
        this.originManifestBytes = Utils.fullyReadInputStream(inputStream, MAX_MANIFEST_SIZE).toByteArray();
        Log.d(TAG, "loadStoredOriginManifest: " + this.originManifestBytes.length + " bytes");
    }

    protected BaseAbrDownloader initForCreate() throws IOException {
        downloadManifest();
        parseOriginManifest();
        createTracks();

        selectDefaultTracks();

        return this;
    }

    protected abstract void createTracks();

    private void downloadManifest() throws IOException {
        URL url = new URL(manifestUrl);
        File targetFile = new File(getTargetDir(), storedOriginManifestName());
        originManifestBytes = Utils.downloadToFile(url, targetFile, MAX_MANIFEST_SIZE);
    }

    protected List<BaseTrack> getDownloadedTracks(@NonNull DownloadItem.TrackType type) {

        if (mode == Mode.create) {
            Log.w(TAG, "Initial selector has no downloaded tracks!");
            return null;
        }

        List<BaseTrack> downloadedTracks = new ArrayList<>();

        for (BaseTrack track : getSelectedTracks(type)) {
            if (item.getService().countPendingFiles(item.getItemId(), track) == 0) {
                downloadedTracks.add(track);
            }
        }

        return downloadedTracks;
    }

    private void applyTrackSelectionChanges() throws IOException {

        if (mode != Mode.update) {
            return;
        }

        // Update Track table
        if (!trackUpdatingData.trackSelectionChanged) {
            // No change
            return;
        }

        Map<DownloadItem.TrackType, List<BaseTrack>> tracksToUnselect = new HashMap<>();
        for (DownloadItem.TrackType trackType : DownloadItem.TrackType.values()) {
            List<BaseTrack> unselect = new ArrayList<>();
            for (BaseTrack track : trackUpdatingData.originalSelectedTracks.get(trackType)) {
                if (!getSelectedTracks(trackType).contains(track)) {
                    unselect.add(track);
                }
            }

            tracksToUnselect.put(trackType, unselect);
        }

        item.getService().updateTracksInDB(item.getItemId(), tracksToUnselect, BaseTrack.TrackState.NOT_SELECTED);
        item.getService().updateTracksInDB(item.getItemId(), getSelectedTracksMap(), BaseTrack.TrackState.SELECTED);

        // Add DownloadTasks
        createDownloadTasks();
        item.getService().addDownloadTasksToDB(item, new ArrayList<>(getDownloadTasks()));

        // Update item size
        item.setEstimatedSizeBytes(getEstimatedDownloadSize());
        item.getService().updateItemEstimatedSizeInDB(item);

        // Update localized manifest
        createLocalManifest();

        item.setTrackSelector(null);
    }

    public void apply() throws IOException {
        if (mode == Mode.update) {
            applyTrackSelectionChanges();
        } else {
            applyInitialTrackSelection();
        }

    }

    protected abstract void parseOriginManifest() throws IOException;

    protected abstract void createDownloadTasks() throws IOException;

    protected abstract void createLocalManifest() throws IOException;

    protected abstract AssetFormat getAssetFormat();

    public DownloadItem.TrackSelector getTrackSelector() {
        return new TrackSelectorImp(this);
    }


    static BaseAbrDownloader createUpdater(DownloadItemImp item) throws IOException {

        if (item.getPlaybackPath().endsWith(".mpd")) {
            return DashFactory.newUpdater(item);
        } else if (item.getPlaybackPath().endsWith(".m3u8")) {
            return HlsFactory.newUpdater(item);
        }

        throw new IllegalArgumentException("Unknown asset type");
    }

    protected BaseAbrDownloader(DownloadItemImp item) {
        this.item = item;
        this.targetDir = new File(item.getDataDir());
        setDownloadTasks(new LinkedHashSet<DownloadTask>());
        this.manifestUrl = item.getContentURL();
    }

    public abstract String storedOriginManifestName();

    private void selectDefaultTracks() {
        // FIXME: 30/06/2018 Create selectedTracks locally
        setSelectedTracksMap(new HashMap<TrackType, List<BaseTrack>>());
        for (TrackType type : TrackType.values()) {
            setSelectedTracks(type, new ArrayList<BaseTrack>(1));
        }


        // "Best" == highest bitrate.

        // Video: simply select best track.
        List<BaseTrack> availableVideoTracks = getAvailableTracks(TrackType.VIDEO);
        if (availableVideoTracks.size() > 0) {
            BaseTrack selectedVideoTrack = Collections.max(availableVideoTracks, DownloadItem.Track.bitrateComparator);
            setSelectedTracks(TrackType.VIDEO, Collections.singletonList(selectedVideoTrack));
        }

        // Audio: X=(language of first track); Select best track with language==X.
        List<BaseTrack> availableAudioTracks = getAvailableTracks(TrackType.AUDIO);
        if (availableAudioTracks.size() > 0) {
            String firstAudioLang = availableAudioTracks.get(0).getLanguage();
            List<BaseTrack> tracks;
            if (firstAudioLang != null) {
                tracks = BaseTrack.filterByLanguage(firstAudioLang, availableAudioTracks);
            } else {
                tracks = availableAudioTracks;
            }

            BaseTrack selectedAudioTrack = Collections.max(tracks, DownloadItem.Track.bitrateComparator);
            setSelectedTracks(TrackType.AUDIO, Collections.singletonList(selectedAudioTrack));
        }

        // Text: simply select first track.
        List<BaseTrack> availableTextTracks = getAvailableTracks(TrackType.TEXT);
        if (availableTextTracks.size() > 0) {
            BaseTrack selectedTextTrack = availableTextTracks.get(0);
            setSelectedTracks(TrackType.TEXT, Collections.singletonList(selectedTextTrack));
        }
    }

    @NonNull
    public List<BaseTrack> getSelectedTracksFlat() {
        return Utils.flattenTrackList(selectedTracks);
    }

    protected List<BaseTrack> getSelectedTracks(TrackType type) {
        return selectedTracks.get(type);
    }

    private Map<TrackType, List<BaseTrack>> getSelectedTracksMap() {
        return selectedTracks;
    }

    protected void setSelectedTracks(@NonNull TrackType type, @NonNull List<BaseTrack> tracks) {
        if (trackUpdatingData != null) {
            trackUpdatingData.trackSelectionChanged = true;
        }
        selectedTracks.put(type, new ArrayList<>(tracks));
    }

    protected List<BaseTrack> getAvailableTracks(TrackType type) {
        return Collections.unmodifiableList(availableTracks.get(type));
    }

    protected long getItemDurationMS() {
        return itemDurationMS;
    }

    protected void setItemDurationMS(long itemDurationMS) {
        this.itemDurationMS = itemDurationMS;
    }

    public long getEstimatedDownloadSize() {
        return estimatedDownloadSize;
    }

    protected void setEstimatedDownloadSize(long estimatedDownloadSize) {
        this.estimatedDownloadSize = estimatedDownloadSize;
    }

    protected File getTargetDir() {
        return targetDir;
    }

    private void setSelectedTracksMap(Map<TrackType, List<BaseTrack>> selectedTracks) {
        this.selectedTracks = selectedTracks;
    }

    public Map<TrackType, List<BaseTrack>> getAvailableTracks() {
        return availableTracks;
    }

    protected List<BaseTrack> getAvailableTracksFlat() {
        return Utils.flattenTrackList(availableTracks);
    }

    protected void setAvailableTracksMap(Map<TrackType, List<BaseTrack>> availableTracks) {
        this.availableTracks = availableTracks;
    }

    public LinkedHashSet<DownloadTask> getDownloadTasks() {
        return downloadTasks;
    }

    protected void setDownloadTasks(LinkedHashSet<DownloadTask> downloadTasks) {
        this.downloadTasks = downloadTasks;
    }


    protected void setAvailableTracks(TrackType type, ArrayList<BaseTrack> tracks) {
        availableTracks.put(type, tracks);
    }
}
