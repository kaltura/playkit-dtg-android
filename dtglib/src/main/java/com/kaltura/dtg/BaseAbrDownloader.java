package com.kaltura.dtg;

import android.support.annotation.NonNull;

import com.kaltura.android.exoplayer.dash.mpd.RangedUri;
import com.kaltura.dtg.DownloadItem.TrackType;
import com.kaltura.dtg.dash.DashFactory;
import com.kaltura.dtg.hls.HlsFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public abstract class BaseAbrDownloader {
    private long itemDurationMS;
    private long estimatedDownloadSize;
    private File targetDir;
    private Map<TrackType, List<BaseTrack>> selectedTracks;
    private Map<TrackType, List<BaseTrack>> availableTracks;
    private LinkedHashSet<DownloadTask> downloadTasks;

    protected abstract List<BaseTrack> getDownloadedTracks(TrackType type);

    protected abstract void apply() throws IOException;

    protected abstract void parseOriginManifest() throws IOException;

    protected abstract void createDownloadTasks() throws MalformedURLException;

    protected abstract void createLocalManifest() throws IOException;

    public abstract DownloadItem.TrackSelector getTrackSelector();


    static BaseAbrDownloader createUpdater(DownloadItemImp item) throws IOException {
        if (item.getPlaybackPath().endsWith(".mpd")) {
            return DashFactory.createUpdater(item);
        } else if (item.getPlaybackPath().endsWith(".m3u8")) {
            return HlsFactory.createUpdater(item);
        }

        throw new IllegalArgumentException("Unknown asset type");
    }

    protected BaseAbrDownloader(File targetDir) {
        this.setTargetDir(targetDir);
        setDownloadTasks(new LinkedHashSet<DownloadTask>());
    }

    protected void selectDefaultTracks() {

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
    protected List<BaseTrack> getSelectedTracksFlat() {
        return Utils.flattenTrackList(selectedTracks);
    }

    protected List<BaseTrack> getSelectedTracks(TrackType type) {
        return selectedTracks.get(type);
    }

    protected Map<TrackType, List<BaseTrack>> getSelectedTracksMap() {
        return selectedTracks;
    }

    protected void setSelectedTracks(@NonNull TrackType type, @NonNull List<BaseTrack> tracks) {
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

    protected long getEstimatedDownloadSize() {
        return estimatedDownloadSize;
    }

    protected void setEstimatedDownloadSize(long estimatedDownloadSize) {
        this.estimatedDownloadSize = estimatedDownloadSize;
    }

    protected File getTargetDir() {
        return targetDir;
    }

    protected void setTargetDir(File targetDir) {
        this.targetDir = targetDir;
    }

    protected void setSelectedTracksMap(Map<TrackType, List<BaseTrack>> selectedTracks) {
        this.selectedTracks = selectedTracks;
    }

    protected Map<TrackType, List<BaseTrack>> getAvailableTracks() {
        return availableTracks;
    }

    protected void setAvailableTracks(Map<TrackType, List<BaseTrack>> availableTracks) {
        this.availableTracks = availableTracks;
    }

    protected LinkedHashSet<DownloadTask> getDownloadTasks() {
        return downloadTasks;
    }

    protected void setDownloadTasks(LinkedHashSet<DownloadTask> downloadTasks) {
        this.downloadTasks = downloadTasks;
    }


    protected void addTask(RangedUri url, String file, String trackId) throws MalformedURLException {
        File targetFile = new File(getTargetDir(), file);
        DownloadTask task = new DownloadTask(new URL(url.getUriString()), targetFile);
        task.setTrackRelativeId(trackId);
        getDownloadTasks().add(task);
    }

    protected void setAvailableTracks(TrackType type, ArrayList<BaseTrack> tracks) {
        availableTracks.put(type, tracks);
    }
}
