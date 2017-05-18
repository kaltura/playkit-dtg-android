package com.kaltura.dtg;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Created by noamt on 5/10/15.
 */
public interface DownloadItem {

    String getItemId();

    String getContentURL();

    void startDownload();

    long getEstimatedSizeBytes();

    long getDownloadedSizeBytes();

    DownloadState getState();

    void loadMetadata();

    void pauseDownload();

    long getAddedTime();

    TrackSelector getTrackSelector();

    enum TrackType {
        VIDEO, AUDIO, TEXT,
        UNKNOWN
    }
    
    interface TrackSelector {
        List<Track> getAvailableTracks(@NonNull TrackType type);
        List<Track> getDownloadedTracks(@NonNull TrackType type);
        void setSelectedTracks(@NonNull TrackType type, @NonNull List<Track> tracks);
        void apply() throws IOException;
    }

    interface Track {
        Comparator<Track> bitrateComparator = new Comparator<DownloadItem.Track>() {
            @Override
            public int compare(DownloadItem.Track lhs, DownloadItem.Track rhs) {
                return lhs.getBitrate() == rhs.getBitrate() ? 0 :
                        lhs.getBitrate() < rhs.getBitrate() ? -1 : 1;
            }
        };

        Comparator<Track> heightComparator = new Comparator<DownloadItem.Track>() {
            @Override
            public int compare(DownloadItem.Track lhs, DownloadItem.Track rhs) {
                return lhs.getHeight() == rhs.getHeight() ? 0 :
                        lhs.getHeight() < rhs.getHeight() ? -1 : 1;
            }
        };



        TrackType getType();
    
        String getLanguage();

        long getBitrate();
        
        // Only applicable to VIDEO tracks.
        int getWidth();
        int getHeight();
    }
}
