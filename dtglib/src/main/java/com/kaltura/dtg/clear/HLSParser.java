package com.kaltura.dtg.clear;

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
    private final DownloadItem item;
    private final File targetDirectory;
    private TreeSet<Variant> sortedVariants;
    private HlsMasterPlaylist masterPlaylist;
    private HlsMediaPlaylist mediaPlaylist;
    private File masterPlaylistFile;
    private String masterPlaylistData;
    private String filteredPlaylistPath;
    private Variant selectedVariant;
    private URL masterURL;
    private URL variantURL;

    public HLSParser(DownloadItem item, File targetDirectory) {
        this.item = item;
        this.targetDirectory = targetDirectory;
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
        return filteredPlaylistPath;
    }

    public Variant getSelectedVariant() {
        return selectedVariant;
    }

    public URL getVariantURL() {
        return variantURL;
    }

    public URL getMasterURL() {
        return masterURL;
    }

    public File getTargetDirectory() {
        return targetDirectory;
    }

    public TreeSet<Variant> getSortedVariants() {
        return sortedVariants;
    }

    // Download and parse master playlist
    public void parseMaster() throws IOException {

        masterURL = new URL(item.getContentURL());
        DownloadedPlaylist downloadedPlaylist = downloadAndParsePlaylist(HlsPlaylist.TYPE_MASTER, masterURL, targetDirectory, item.getItemId());

        masterPlaylistFile = downloadedPlaylist.targetFile;
        masterPlaylist = (HlsMasterPlaylist) downloadedPlaylist.playlist;
        masterPlaylistData = downloadedPlaylist.data;

        Comparator<Variant> bitrateComparator = new Comparator<Variant>() {
            @Override
            public int compare(Variant lhs, Variant rhs) {
                return rhs.format.bitrate - lhs.format.bitrate;
            }
        };

        sortedVariants = new TreeSet<>(bitrateComparator);
        sortedVariants.addAll(masterPlaylist.variants);
    }

    public HlsMasterPlaylist getMasterPlaylist() {
        return masterPlaylist;
    }

    public void selectVariant(Variant selectedVariant) {
        this.selectedVariant = selectedVariant;

        // Remove other variants from file.
        sortedVariants.remove(selectedVariant);

        // Remove all bitrates except the selected.
        String[] lines = masterPlaylistData.split("[\r\n]+");
        // Removing all lines that reference the non-selected bitrates.
        // This is very inefficient -- O(n^2) -- but n is expected to be very small (< 10).
        for (Variant variant : sortedVariants) {
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
        StringBuilder filteredPlaylist = new StringBuilder(masterPlaylistData.length());
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
        File filteredPlaylistFile = new File(masterPlaylistFile.getParentFile(), FILTERED_MASTER_M3U8);

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

        variantURL = new URL(masterURL, selectedVariant.url);
        DownloadedPlaylist downloadedPlaylist = downloadAndParsePlaylist(HlsPlaylist.TYPE_MEDIA, variantURL, targetDirectory, item.getItemId());

        mediaPlaylist = (HlsMediaPlaylist) downloadedPlaylist.playlist;

        // modify pathnames
        String[] lines = downloadedPlaylist.data.split("[\r\n]+");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            line = line.trim();
            if (!line.isEmpty() && line.charAt(0) != '#') {
                lines[i] = Utils.getHashedFileName(line);
            }
//            Log.d(TAG, String.format("rename in playlist: '%s' ==> '%s'", line, lines[i]));
        }
        String modifiedData = TextUtils.join("\n", lines);

        saveToFile(modifiedData, downloadedPlaylist.targetFile);

    }

    public DownloadItem getItem() {
        return item;
    }

    public HlsMediaPlaylist getMediaPlaylist() {
        return mediaPlaylist;
    }

    public ArrayList<DownloadTask> createDownloadTasks() throws MalformedURLException {
        // create download tasks for all chunks.
        List<HlsMediaPlaylist.Segment> segments = mediaPlaylist.segments;
        // Using LinkedHashSet (which is an Ordered Set) to prevent duplicates.
        // TODO: be smarter about duplicates. 
        LinkedHashSet<DownloadTask> downloadTasks = new LinkedHashSet<>(segments.size());

        for (HlsMediaPlaylist.Segment segment : segments) {

            URL segmentURL = new URL(variantURL, segment.url);
            File segmentFile = new File(targetDirectory, Utils.getHashedFileName(segment.url));

//            Log.d(TAG, String.format("rename in file: '%s' ==> '%s' (%s ==> %s)",
//                    segmentURL, segmentFile, segment.url, Utils.getHashedFileName(segment.url)));

            downloadTasks.add(new DownloadTask(segmentURL, segmentFile));
        }

        return new ArrayList<>(downloadTasks);
    }

    public long getEstimatedSizeBytes() {
        return (long) (selectedVariant.format.bitrate / 8.0 * mediaPlaylist.durationUs / 1000 / 1000);
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
