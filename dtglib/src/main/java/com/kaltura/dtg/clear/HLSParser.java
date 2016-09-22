package com.kaltura.dtg.clear;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.kaltura.android.exoplayer.hls.HlsMasterPlaylist;
import com.kaltura.android.exoplayer.hls.HlsMediaPlaylist;
import com.kaltura.android.exoplayer.hls.HlsPlaylist;
import com.kaltura.android.exoplayer.hls.HlsPlaylistParser;
import com.kaltura.android.exoplayer.hls.Variant;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.Utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

/**
 * Created by noamt on 5/20/15.
 */

// Wrapper around ExoPlayer's HlsPlaylistParser. Also saves state (master playlist, selected, etc).
class HLSParser {

    public static final String FILTERED_MASTER_M3U8 = "master.m3u8";
    public static final String VARIANT_M3U8 = "variant.m3u8";
    public static final String ORIGINAL_MASTER_M3U8 = "ORIGINAL-MASTER.m3u8";
    private static final String TAG = "HLSParser";
    // All fields in HlsPlaylistParser are static final, it can be safely shared.
    private static final HlsPlaylistParser sPlaylistParser = new HlsPlaylistParser();
    private final DownloadItem mItem;
    private final File mTargetDirectory;
    private TreeSet<Variant> mSortedVariants;
    private HlsMasterPlaylist mMasterPlaylist;
    private HlsMediaPlaylist mMediaPlaylist;
    private File mMasterPlaylistFile;
    private String mMasterPlaylistData;
    private String mFilteredPlaylistPath;
    private Variant mSelectedVariant;
    private URL mMasterURL;
    private URL mVariantURL;

    public HLSParser(DownloadItem item, File targetDirectory) {
        mItem = item;
        mTargetDirectory = targetDirectory;
    }

    // Static utils
    static DownloadedPlaylist downloadAndParsePlaylist(int type, URL url, File targetDirectory, String itemId) throws IOException {

        Log.d(TAG, "Downloading playlist: " + url);
        File targetFile;
        if (type == HlsPlaylist.TYPE_MASTER) {
            targetFile = new File(targetDirectory, ORIGINAL_MASTER_M3U8);
        } else {
            targetFile = new File(targetDirectory, VARIANT_M3U8);
        }

        byte[] data = Utils.downloadToFile(url, targetFile, 10 * 1024 * 1024);
        DownloadedPlaylist downloadedPlaylist = new DownloadedPlaylist(data, targetFile, HLSParser.parse(url, data));
        if (downloadedPlaylist.playlist.type != type) {
            throw new IOException(Utils.format("Downloaded playlist (%d) does not match requested type (%d)",
                    downloadedPlaylist.playlist.type, type));
        }
        return downloadedPlaylist;
    }

    private static HlsPlaylist parse(URL url, byte[] data) throws IOException {
        HlsPlaylistParser parser = sPlaylistParser;
        InputStream inputStream = new ByteArrayInputStream(data);
        HlsPlaylist hlsPlaylist = parser.parse(url.toExternalForm(), inputStream);
        inputStream.close();
        return hlsPlaylist;
    }

    public String getFilteredPlaylistPath() {
        return mFilteredPlaylistPath;
    }

    public Variant getSelectedVariant() {
        return mSelectedVariant;
    }

    public URL getVariantURL() {
        return mVariantURL;
    }

    public URL getMasterURL() {
        return mMasterURL;
    }

    public File getTargetDirectory() {
        return mTargetDirectory;
    }

    public TreeSet<Variant> getSortedVariants() {
        return mSortedVariants;
    }

    // Download and parse master playlist
    public void parseMaster() throws IOException {

        mMasterURL = new URL(mItem.getContentURL());
        DownloadedPlaylist downloadedPlaylist = downloadAndParsePlaylist(HlsPlaylist.TYPE_MASTER, mMasterURL, mTargetDirectory, mItem.getItemId());

        mMasterPlaylistFile = downloadedPlaylist.targetFile;
        mMasterPlaylist = (HlsMasterPlaylist) downloadedPlaylist.playlist;
        mMasterPlaylistData = downloadedPlaylist.data;

        Comparator<Variant> bitrateComparator = new Comparator<Variant>() {
            @Override
            public int compare(Variant lhs, Variant rhs) {
                return rhs.format.bitrate - lhs.format.bitrate;
            }
        };

        mSortedVariants = new TreeSet<>(bitrateComparator);
        mSortedVariants.addAll(mMasterPlaylist.variants);
    }

    public HlsMasterPlaylist getMasterPlaylist() {
        return mMasterPlaylist;
    }

    public void selectVariant(Variant selectedVariant) {
        mSelectedVariant = selectedVariant;

        // Remove other variants from file.
        mSortedVariants.remove(selectedVariant);

        // Remove all bitrates except the selected.
        String[] lines = mMasterPlaylistData.split("[\r\n]+");
        // Removing all lines that reference the non-selected bitrates.
        // This is very inefficient -- O(n^2) -- but n is expected to be very small (< 10).
        for (Variant variant : mSortedVariants) {
            for (int i = 0; i < lines.length; i++) {

                String line = lines[i];
                if (line == null) {
                    continue;
                }

                if (line.matches(".+?BANDWIDTH=" + variant.format.bitrate + "\\b.+")) {
                    lines[i] = null;
                } else if (line.equals(variant.url)) {
                    lines[i] = null;
                }
            }
        }


        // Rebuild playlist
        StringBuilder filteredPlaylist = new StringBuilder(mMasterPlaylistData.length());
        for (String line : lines) {
            if (line != null) {
                if (!line.isEmpty() && line.charAt(0) != '#') {
                    // this is the variant's playlist
                    line = VARIANT_M3U8;
//                    line = line.trim();
//                    line = getHashedFileName(line);
                }
                filteredPlaylist.append(line).append('\n');
            }
        }

        String filteredString = filteredPlaylist.toString();

        // Save back to file.
        File filteredPlaylistFile = new File(mMasterPlaylistFile.getParentFile(), FILTERED_MASTER_M3U8);

        saveToFile(filteredString, filteredPlaylistFile);

    }

    private void saveToFile(String data, File file) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(file);
            writer.print(data);
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed rewriting master playlist", e);
            // TODO: return the error somehow
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public void parseVariant() throws IOException {

        mVariantURL = new URL(mMasterURL, mSelectedVariant.url);
        DownloadedPlaylist downloadedPlaylist = downloadAndParsePlaylist(HlsPlaylist.TYPE_MEDIA, mVariantURL, mTargetDirectory, mItem.getItemId());

        mMediaPlaylist = (HlsMediaPlaylist) downloadedPlaylist.playlist;

        // modify pathnames
        String[] lines = downloadedPlaylist.data.split("[\r\n]+");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            line = line.trim();
            if (!line.isEmpty() && line.charAt(0) != '#') {
                lines[i] = getHashedFileName(line);
            }
            Log.d(TAG, String.format("rename in playlist: '%s' ==> '%s'", line, lines[i]));
        }
        String modifiedData = TextUtils.join("\n", lines);

        saveToFile(modifiedData, downloadedPlaylist.targetFile);

    }

    public DownloadItem getItem() {
        return mItem;
    }

    public HlsMediaPlaylist getMediaPlaylist() {
        return mMediaPlaylist;
    }

    public ArrayList<DownloadTask> createDownloadTasks() throws MalformedURLException {
        // create download tasks for all chunks.
        List<HlsMediaPlaylist.Segment> segments = mMediaPlaylist.segments;
        // Using LinkedHashSet (which is an Ordered Set) to prevent duplicates.
        // TODO: be smarter about duplicates. 
        LinkedHashSet<DownloadTask> downloadTasks = new LinkedHashSet<>(segments.size());

        for (HlsMediaPlaylist.Segment segment : segments) {

            URL segmentURL = new URL(mVariantURL, segment.url);
            File segmentFile = new File(mTargetDirectory, getHashedFileName(segment.url));

            Log.d(TAG, String.format("rename in file: '%s' ==> '%s' (%s ==> %s)",
                    segmentURL, segmentFile, segment.url, getHashedFileName(segment.url)));

            downloadTasks.add(new DownloadTask(segmentURL, segmentFile));
        }

        return new ArrayList<>(downloadTasks);
    }

    // Returns hex-encoded md5 of the input, appending the extension.
    // "a.mp4" ==> "2a1f28800d49717bbf88dc2c704f4390.mp4"
    public String getHashedFileName(String original) {
        String ext = getFileExtension(original);
        return Utils.md5Hex(original) + ext;
    }

    @NonNull
    private String getFileExtension(String pathOrURL) {
        // if it's a URL, get only the path. Uri does this correctly, even if the argument is a simple path.
        String path = Uri.parse(pathOrURL).getPath();

        return path.substring(path.lastIndexOf('.'));
    }

    public long getEstimatedSizeBytes() {
        return (long) (mSelectedVariant.format.bitrate / 8.0 * mMediaPlaylist.durationUs / 1000 / 1000);
    }

    public String getPlaybackPath() {
        return FILTERED_MASTER_M3U8;
    }

    static class DownloadedPlaylist {
        final String data;
        final File targetFile;
        final HlsPlaylist playlist;

        public DownloadedPlaylist(byte[] data, File targetFile, HlsPlaylist playlist) {
            this.data = new String(data);
            this.targetFile = targetFile;
            this.playlist = playlist;
        }
    }
}
