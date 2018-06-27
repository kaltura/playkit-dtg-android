package com.kaltura.dtg;

import android.support.annotation.NonNull;

import com.kaltura.android.exoplayer.dash.mpd.RangedUri;

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
    protected long itemDurationMS;
    protected long estimatedDownloadSize;
    protected File targetDir;
    protected Map<DownloadItem.TrackType, List<BaseTrack>> selectedTracks;
    protected Map<DownloadItem.TrackType, List<BaseTrack>> availableTracks;
    protected LinkedHashSet<DownloadTask> downloadTasks;

    protected BaseAbrDownloader(File targetDir) {
        this.targetDir = targetDir;
        downloadTasks = new LinkedHashSet<>();
    }

    protected abstract List<BaseTrack> getDownloadedTracks(DownloadItem.TrackType type);

    protected void selectDefaultTracks() {

        selectedTracks = new HashMap<>();
        for (DownloadItem.TrackType type : DownloadItem.TrackType.values()) {
            selectedTracks.put(type, new ArrayList<BaseTrack>(1));
        }


        // "Best" == highest bitrate.

        // Video: simply select best track.
        List<BaseTrack> availableVideoTracks = getAvailableTracks(DownloadItem.TrackType.VIDEO);
        if (availableVideoTracks.size() > 0) {
            BaseTrack selectedVideoTrack = Collections.max(availableVideoTracks, DownloadItem.Track.bitrateComparator);
            setSelectedTracks(DownloadItem.TrackType.VIDEO, Collections.singletonList(selectedVideoTrack));
        }

        // Audio: X=(language of first track); Select best track with language==X.
        List<BaseTrack> availableAudioTracks = getAvailableTracks(DownloadItem.TrackType.AUDIO);
        if (availableAudioTracks.size() > 0) {
            String firstAudioLang = availableAudioTracks.get(0).getLanguage();
            List<BaseTrack> tracks;
            if (firstAudioLang != null) {
                tracks = BaseTrack.filterByLanguage(firstAudioLang, availableAudioTracks);
            } else {
                tracks = availableAudioTracks;
            }

            BaseTrack selectedAudioTrack = Collections.max(tracks, DownloadItem.Track.bitrateComparator);
            setSelectedTracks(DownloadItem.TrackType.AUDIO, Collections.singletonList(selectedAudioTrack));
        }

        // Text: simply select first track.
        List<BaseTrack> availableTextTracks = getAvailableTracks(DownloadItem.TrackType.TEXT);
        if (availableTextTracks.size() > 0) {
            BaseTrack selectedTextTrack = availableTextTracks.get(0);
            setSelectedTracks(DownloadItem.TrackType.TEXT, Collections.singletonList(selectedTextTrack));
        }
    }

    protected abstract void apply() throws IOException;

    protected abstract void parseOriginManifest() throws IOException;

    protected abstract void createDownloadTasks() throws MalformedURLException;

    protected abstract void createLocalManifest() throws IOException;

    @NonNull
    protected List<BaseTrack> getSelectedTracks() {
        return Utils.flattenTrackList(selectedTracks);
    }

    protected void addTask(RangedUri url, String file, String trackId) throws MalformedURLException {
        File targetFile = new File(targetDir, file);
        DownloadTask task = new DownloadTask(new URL(url.getUriString()), targetFile);
        task.setTrackRelativeId(trackId);
        downloadTasks.add(task);
    }

    protected void setSelectedTracks(@NonNull DownloadItem.TrackType type, @NonNull List<BaseTrack> tracks) {
        selectedTracks.put(type, new ArrayList<>(tracks));
    }

    protected List<BaseTrack> getAvailableTracks(DownloadItem.TrackType type) {
        return Collections.unmodifiableList(availableTracks.get(type));
    }

    public abstract DownloadItem.TrackSelector getTrackSelector();
}
