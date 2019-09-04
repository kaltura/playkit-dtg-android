package com.kaltura.dtg;

import androidx.annotation.NonNull;

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

    AssetFormat getAssetFormat();

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

        Comparator<Track> widthComparator = (lhs, rhs) -> lhs.getWidth() - rhs.getWidth();

        TrackType getType();

        String getLanguage();       // AUDIO and TEXT

        long getBitrate();          // AUDIO (dash) and VIDEO (dash, hls)

        int getWidth();             // Only applicable to VIDEO tracks.

        int getHeight();            // Only applicable to VIDEO tracks.

        String getCodecs();         // AUDIO and VIDEO

        String getAudioGroupId();   // Only applicable to HLS (audio and video)
    }
}
