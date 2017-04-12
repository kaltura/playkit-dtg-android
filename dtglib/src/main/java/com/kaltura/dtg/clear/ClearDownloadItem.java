package com.kaltura.dtg.clear;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadState;

import java.io.IOException;
import java.util.Date;

/**
 * Created by noamt on 30/05/2016.
 */
class ClearDownloadItem implements DownloadItem, Parcelable {

    private static final String TAG = "ClearDownloadItem";
    private final String mItemId;
    private final String mContentURL;

    private ClearDownloadService mProvider;
    private DownloadState mState = DownloadState.NEW;
    private long mAddedTime;
    private long mFinishedTime;
    private long mEstimatedSizeBytes;
    private long mDownloadedSizeBytes;
    private boolean mBroken;
    
    private String mDataDir;
    private String mPlaybackPath;
    
    private TrackSelector mTrackSelector;

    ClearDownloadItem(String itemId, String contentURL) {
        this.mItemId = itemId;
        this.mContentURL = contentURL;
    }

    private ClearDownloadItem(Parcel in) {
        mItemId = in.readString();
        mContentURL = in.readString();
        mAddedTime = in.readLong();
        mFinishedTime = in.readLong();
        mEstimatedSizeBytes = in.readLong();
        mDownloadedSizeBytes = in.readLong();
        mBroken = in.readByte() != 0;
        mDataDir = in.readString();
        mPlaybackPath = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mItemId);
        dest.writeString(mContentURL);
        dest.writeLong(mAddedTime);
        dest.writeLong(mFinishedTime);
        dest.writeLong(mEstimatedSizeBytes);
        dest.writeLong(mDownloadedSizeBytes);
        dest.writeByte((byte) (mBroken ? 1 : 0));
        dest.writeString(mDataDir);
        dest.writeString(mPlaybackPath);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ClearDownloadItem> CREATOR = new Creator<ClearDownloadItem>() {
        @Override
        public ClearDownloadItem createFromParcel(Parcel in) {
            return new ClearDownloadItem(in);
        }

        @Override
        public ClearDownloadItem[] newArray(int size) {
            return new ClearDownloadItem[size];
        }
    };

    @Override
    public String toString() {
        return "<" + getClass().getName() + " mItemId=" + getItemId() + " mContentURL=" + getContentURL() +
                " state=" + mState.name() + " mAddedTime=" + new Date(mAddedTime) +
                " mEstimatedSizeBytes=" + mEstimatedSizeBytes +
                " mDownloadedSizeBytes=" + mDownloadedSizeBytes + ">";
    }

    @Override
    public String getItemId() {
        return mItemId;
    }

    @Override
    public String getContentURL() {
        return mContentURL;
    }

    String getDataDir() {
        return mDataDir;
    }

    void setDataDir(String dataDir) {
        mDataDir = dataDir;
    }

    String getPlaybackPath() {
        return mPlaybackPath;
    }

    void setPlaybackPath(String playbackPath) {
        mPlaybackPath = playbackPath;
    }
    
    void setProvider(ClearDownloadService provider) {
        this.mProvider = provider;
    }

    void setEstimatedSizeBytes(long bytes) {
        mEstimatedSizeBytes = bytes;
    }

    void setDownloadedSizeBytes(long bytes) {
        mDownloadedSizeBytes = bytes;
    }

    void setState(DownloadState state) {
        this.mState = state;
    }

    void updateItemState(DownloadState state) {
        mProvider.updateItemState(this.getItemId(), state);
    }
    
    void setAddedTime(long addedTime) {
        this.mAddedTime = addedTime;
    }
    
    void setFinishedTime(long finishedTime) {
        this.mFinishedTime = finishedTime;
    }

    void setBroken(boolean broken) {
        this.mBroken = broken;
    }
    
    long incDownloadBytes(long downloadedBytes) {
        long updated = mDownloadedSizeBytes + downloadedBytes;
        this.mDownloadedSizeBytes = updated;
        return updated;
    }
    
    @Override
    public void startDownload() {
        mBroken = false;
        mProvider.startDownload(this.getItemId());
//        this.setState(state);
    }

    @Override
    public long getEstimatedSizeBytes() {
        return mEstimatedSizeBytes;
    }

    @Override
    public long getDownloadedSizeBytes() {
        return mDownloadedSizeBytes;
    }

    @Override
    public DownloadState getState() {
        return mState;
    }

    @Override
    public void loadMetadata() {
        mProvider.loadItemMetadata(this);
    }

    @Override
    public void pauseDownload() {
        mProvider.pauseDownload(this);
    }

    @Override
    public long getAddedTime() {
        return mAddedTime;
    }

    boolean isBroken() {
        return mBroken;
    }

    @Override
    public TrackSelector getTrackSelector() {
        
        if (mPlaybackPath==null || !mPlaybackPath.endsWith(".mpd")) {
            Log.w(TAG, "Track selection is only supported for dash");
            return null;
        }
        
        // If selection is in progress, return the current selector.
        
        if (mTrackSelector == null) {
            DashDownloadUpdater dashDownloadUpdater = null;
            try {
                dashDownloadUpdater = new DashDownloadUpdater(this);
            } catch (IOException e) {
                Log.e(TAG, "Error initializing DashDownloadUpdater", e);
                return null;
            }
            TrackSelector trackSelector = dashDownloadUpdater.getTrackSelector();

            setTrackSelector(trackSelector);
        }

        return mTrackSelector;
    }

    void setTrackSelector(TrackSelector trackSelector) {
        mTrackSelector = trackSelector;
    }

    ClearDownloadService getService() {
        return mProvider;
    }
}
