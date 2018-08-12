package com.kaltura.dtg;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.kaltura.dtg.DownloadItem.TrackSelector;
import com.kaltura.dtg.DownloadItem.TrackType;
import com.kaltura.dtg.dash.DashDownloader;
import com.kaltura.dtg.hls.HlsDownloader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public abstract class AbrDownloader {

    protected static final int MAX_MANIFEST_SIZE = 10 * 1024 * 1024;
    private static final String TAG = "AbrDownloader";
    private final DownloadItemImp item;
    protected final String manifestUrl;
    protected byte[] originManifestBytes;
    protected boolean trackSelectionApplied;
    protected long itemDurationMS;
    protected long estimatedDownloadSize;
    protected final File targetDir;
    protected Map<TrackType, List<BaseTrack>> selectedTracks;
    protected Map<TrackType, List<BaseTrack>> availableTracks;
    protected LinkedHashSet<DownloadTask> downloadTasks;
    protected TrackUpdatingData trackUpdatingData;    // only used in update mode
    protected Mode mode = Mode.create;

    protected AbrDownloader(DownloadItemImp item) {
        this.item = item;
        this.targetDir = new File(item.getDataDir());
        setDownloadTasks(new LinkedHashSet<DownloadTask>());
        this.manifestUrl = item.getContentURL();
    }

    static AbrDownloader newDownloader(DownloadItemImp item, ContentManager.Settings settings) {
        String fileName = Uri.parse(item.getContentURL()).getLastPathSegment();
        switch (AssetFormat.byFilename(fileName)) {
            case dash:
                return new DashDownloader(item);
            case hls:
                return new HlsDownloader(item, settings.defaultHlsAudioBitrateEstimation);
        }

        return null;
    }

    @Nullable
    static TrackSelector newTrackUpdater(DownloadItemImp item, ContentManager.Settings settings) {
        AbrDownloader downloader = newDownloader(item, settings);
        if (downloader == null) {
            return null;
        }

        try {
            downloader.initForUpdate();
        } catch (IOException e) {
            Log.e(TAG, "Error initializing updater", e);
            return null;
        }

        return new TrackSelectorImp(downloader);
    }

    void create(DownloadService downloadService, DownloadStateListener downloadStateListener) throws IOException {

        downloadManifest();
        parseOriginManifest();
        createTracks();

        selectDefaultTracks();

        TrackSelector trackSelector = new TrackSelectorImp(this);

        item.setTrackSelector(trackSelector);

        downloadStateListener.onTracksAvailable(item, trackSelector);

        this.apply();

        item.setTrackSelector(null);


        List<BaseTrack> availableTracks = Utils.flattenTrackList(this.getAvailableTracksMap());
        List<BaseTrack> selectedTracks = this.getSelectedTracksFlat();

        downloadService.addTracksToDB(item, availableTracks, selectedTracks);

        item.setEstimatedSizeBytes(estimatedDownloadSize);
        item.setDurationMS(itemDurationMS);

        downloadService.updateItemDurationInDB(item);


        LinkedHashSet<DownloadTask> downloadTasks = this.downloadTasks;
        //Log.d(TAG, "tasks:" + downloadTasks);

        item.setPlaybackPath(this.storedLocalManifestName());

        downloadService.addDownloadTasksToDB(item, new ArrayList<>(downloadTasks));
    }

    protected void applyInitialTrackSelection() throws IOException {
        if (trackSelectionApplied) {
            return;
        }
        createLocalManifest();
        createDownloadTasks();
        trackSelectionApplied = true;
    }

    private void initForUpdate() throws IOException {
        this.mode = Mode.update;
        this.loadStoredOriginManifest();
        this.parseOriginManifest();

        this.setSelectedTracksMap(new HashMap<DownloadItem.TrackType, List<BaseTrack>>());
        this.setAvailableTracksMap(new HashMap<DownloadItem.TrackType, List<BaseTrack>>());
        Map<DownloadItem.TrackType, List<BaseTrack>> originalSelectedTracks = new HashMap<>();

        for (DownloadItem.TrackType type : DownloadItem.TrackType.values()) {
            List<BaseTrack> availableTracks = this.item.getService().readTracksFromDB(item.getItemId(), type, null, this.getAssetFormat());
            this.getAvailableTracksMap().put(type, availableTracks);

            List<BaseTrack> selectedTracks = this.item.getService().readTracksFromDB(item.getItemId(), type, BaseTrack.TrackState.SELECTED, this.getAssetFormat());
            this.setSelectedTracksForType(type, selectedTracks);
            originalSelectedTracks.put(type, selectedTracks);
        }

        trackUpdatingData = new TrackUpdatingData(originalSelectedTracks);
    }

    private void loadStoredOriginManifest() throws IOException {
        originManifestBytes = Utils.readFile(new File(targetDir, storedOriginManifestName()), MAX_MANIFEST_SIZE);
        Log.d(TAG, "loadStoredOriginManifest: " + this.originManifestBytes.length + " bytes");
    }

    protected abstract void createTracks();

    private void downloadManifest() throws IOException {
        File targetFile = new File(getTargetDir(), storedOriginManifestName());
        originManifestBytes = Utils.downloadToFile(Uri.parse(manifestUrl), targetFile, MAX_MANIFEST_SIZE);
    }

    List<BaseTrack> getDownloadedTracks(@NonNull DownloadItem.TrackType type) {

        if (mode == Mode.create) {
            Log.w(TAG, "Initial selector has no downloaded tracks!");
            return null;
        }

        List<BaseTrack> downloadedTracks = new ArrayList<>();

        for (BaseTrack track : getSelectedTracksByType(type)) {
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
                if (!getSelectedTracksByType(trackType).contains(track)) {
                    unselect.add(track);
                }
            }

            tracksToUnselect.put(trackType, unselect);
        }

        final DownloadService service = item.getService();
        service.updateTracksInDB(item.getItemId(), tracksToUnselect, BaseTrack.TrackState.NOT_SELECTED);
        service.updateTracksInDB(item.getItemId(), selectedTracks, BaseTrack.TrackState.SELECTED);

        // Add DownloadTasks
        createDownloadTasks();
        service.addDownloadTasksToDB(item, new ArrayList<>(downloadTasks));

        // Update duration
        item.setDurationMS(itemDurationMS);
        service.updateItemDurationInDB(item);

        // Update item size
        item.setEstimatedSizeBytes(estimatedDownloadSize);
        service.updateItemEstimatedSizeInDB(item);

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

    protected abstract String storedOriginManifestName();

    protected abstract String storedLocalManifestName();

    private void selectDefaultTracks() {

        final Map<TrackType, List<BaseTrack>> availableTracks = getAvailableTracksMap();
        final Map<TrackType, List<BaseTrack>> selectedTracks = new HashMap<>();

        // Below, "best" means highest bitrate.

        // Video: select best track.
        List<BaseTrack> availableVideoTracks = availableTracks.get(TrackType.VIDEO);
        if (availableVideoTracks.size() > 0) {
            BaseTrack selectedVideoTrack = Collections.max(availableVideoTracks, DownloadItem.Track.bitrateComparator);
            selectedTracks.put(TrackType.VIDEO, Collections.singletonList(selectedVideoTrack));
        }

        // Audio: X=(language of first track); select best track with language==X.
        List<BaseTrack> availableAudioTracks = availableTracks.get(TrackType.AUDIO);
        if (availableAudioTracks.size() > 0) {
            String firstAudioLang = availableAudioTracks.get(0).getLanguage();
            List<BaseTrack> tracks;
            if (firstAudioLang != null) {
                tracks = BaseTrack.filterByLanguage(firstAudioLang, availableAudioTracks);
            } else {
                tracks = availableAudioTracks;
            }

            BaseTrack selectedAudioTrack = Collections.max(tracks, DownloadItem.Track.bitrateComparator);
            selectedTracks.put(TrackType.AUDIO, Collections.singletonList(selectedAudioTrack));
        }

        // Text: select first track.
        List<BaseTrack> availableTextTracks = availableTracks.get(TrackType.TEXT);
        if (availableTextTracks.size() > 0) {
            BaseTrack selectedTextTrack = availableTextTracks.get(0);
            selectedTracks.put(TrackType.TEXT, Collections.singletonList(selectedTextTrack));
        }

        setSelectedTracksMap(selectedTracks);
    }

    @NonNull
    protected List<BaseTrack> getSelectedTracksFlat() {
        return Utils.flattenTrackList(selectedTracks);
    }

    List<BaseTrack> getSelectedTracksByType(TrackType type) {
        return selectedTracks.get(type);
    }

    private void setSelectedTracksMap(Map<TrackType, List<BaseTrack>> selectedTracks) {
        this.selectedTracks = selectedTracks;
    }

    void setSelectedTracksForType(@NonNull TrackType type, @NonNull List<BaseTrack> tracks) {
        if (trackUpdatingData != null) {
            trackUpdatingData.trackSelectionChanged = true;
        }
        selectedTracks.put(type, new ArrayList<>(tracks));
    }

    List<BaseTrack> getAvailableTracksByType(TrackType type) {
        return Collections.unmodifiableList(availableTracks.get(type));
    }

    protected void setItemDurationMS(long itemDurationMS) {
        this.itemDurationMS = itemDurationMS;
    }

    protected void setEstimatedDownloadSize(long estimatedDownloadSize) {
        this.estimatedDownloadSize = estimatedDownloadSize;
    }

    protected File getTargetDir() {
        return targetDir;
    }

    protected Map<TrackType, List<BaseTrack>> getAvailableTracksMap() {
        return availableTracks;
    }

    protected void setAvailableTracksMap(Map<TrackType, List<BaseTrack>> availableTracks) {
        this.availableTracks = availableTracks;
    }

    protected List<BaseTrack> getAvailableTracksFlat() {
        return Utils.flattenTrackList(availableTracks);
    }

    protected void setDownloadTasks(LinkedHashSet<DownloadTask> downloadTasks) {
        this.downloadTasks = downloadTasks;
    }

    protected void setAvailableTracksByType(TrackType type, ArrayList<BaseTrack> tracks) {
        availableTracks.put(type, tracks);
    }

    protected enum Mode {
        create,
        update
    }

    private static class TrackUpdatingData {
        private final Map<DownloadItem.TrackType, List<BaseTrack>> originalSelectedTracks;
        private boolean trackSelectionChanged;

        private TrackUpdatingData(Map<TrackType, List<BaseTrack>> originalSelectedTracks) {
            this.originalSelectedTracks = originalSelectedTracks;
        }
    }
}
