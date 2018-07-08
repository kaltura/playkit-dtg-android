package com.kaltura.dtg;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TrackSelectorImp implements DownloadItem.TrackSelector {
    private BaseAbrDownloader downloader;

    public TrackSelectorImp(BaseAbrDownloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public List<DownloadItem.Track> getAvailableTracks(@NonNull final DownloadItem.TrackType type) {
        List<BaseTrack> tracks = downloader.getAvailableTracks(type);
        return new ArrayList<DownloadItem.Track>(tracks);
    }

    @Override
    public void setSelectedTracks(@NonNull DownloadItem.TrackType type, @NonNull List<DownloadItem.Track> tracks) {

        List<BaseTrack> trackList = new ArrayList<>(tracks.size());
        for (DownloadItem.Track track : tracks) {
            trackList.add((BaseTrack) track);
        }

        downloader.setSelectedTracks(type, trackList);
    }

    @Override
    public List<DownloadItem.Track> getDownloadedTracks(@NonNull DownloadItem.TrackType type) {
        List<BaseTrack> tracks = downloader.getDownloadedTracks(type);
        return new ArrayList<DownloadItem.Track>(tracks);
    }

    @Override
    public void apply() throws IOException {
        downloader.apply();
    }
}
