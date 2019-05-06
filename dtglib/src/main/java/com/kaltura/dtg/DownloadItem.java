package com.kaltura.dtg;

import android.support.annotation.NonNull;

import java.util.Comparator;
import java.util.List;

public interface DownloadItem {

    String getItemId();

    String getContentURL();

    void startDownload();

    long getDurationMS();

    long getEstimatedSizeBytes();

    long getDownloadedSizeBytes();

    DownloadState getState();

    void loadMetadata();

    void pauseDownload();

    long getAddedTime();

    TrackSelector getTrackSelector();

    enum TrackType {
        VIDEO, AUDIO, TEXT
    }

    interface TrackSelector {
        List<Track> getAvailableTracks(@NonNull TrackType type);

        List<Track> getDownloadedTracks(@NonNull TrackType type);

        List<Track> getSelectedTracks(@NonNull TrackType type);

        void setSelectedTracks(@NonNull TrackType type, @NonNull List<Track> tracks);

        void apply(OnTrackSelectionListener listener);
    }

    interface OnTrackSelectionListener {
        void onTrackSelectionComplete(Exception e);
    }

    interface Track {
        Comparator<Track> bitrateComparator = (lhs, rhs) -> (int) (lhs.getBitrate() - rhs.getBitrate());

        Comparator<Track> heightComparator = (lhs, rhs) -> lhs.getHeight() - rhs.getHeight();


        TrackType getType();

        String getLanguage();

        long getBitrate();

        // Only applicable to VIDEO tracks.
        int getWidth();

        int getHeight();

        String getCodecs();
    }
}
