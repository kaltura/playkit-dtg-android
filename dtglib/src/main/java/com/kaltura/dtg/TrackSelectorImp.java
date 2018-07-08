package com.kaltura.dtg;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TrackSelectorImp implements DownloadItem.TrackSelector {
    private AbrDownloader downloader;

    public TrackSelectorImp(AbrDownloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public List<DownloadItem.Track> getAvailableTracks(@NonNull final DownloadItem.TrackType type) {
        List<BaseTrack> tracks = downloader.getAvailableTracksByType(type);
        return new ArrayList<DownloadItem.Track>(tracks);
    }

    @Override
    public void setSelectedTracks(@NonNull DownloadItem.TrackType type, @NonNull List<DownloadItem.Track> tracks) {

        List<BaseTrack> trackList = new ArrayList<>(tracks.size());
        for (DownloadItem.Track track : tracks) {
            trackList.add((BaseTrack) track);
        }

        downloader.setSelectedTracksForType(type, trackList);
    }

    @Override
    public List<DownloadItem.Track> getDownloadedTracks(@NonNull DownloadItem.TrackType type) {
        List<BaseTrack> tracks = downloader.getDownloadedTracks(type);
        return new ArrayList<DownloadItem.Track>(tracks);
    }

    @Override
    public List<DownloadItem.Track> getSelectedTracks(@NonNull DownloadItem.TrackType type) {
        return new ArrayList<DownloadItem.Track>(downloader.getSelectedTracksByType(type));
    }

    @Override
    public void apply() throws IOException {
        downloader.apply();
    }
}
