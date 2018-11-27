package com.kaltura.dtg.imp;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.kaltura.dtg.DownloadItem;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class Utils {
    private static final String TAG = "DTGUtils";

    private static long dirSize(File dir) {

        if (dir.exists()) {
            long result = 0;
            File[] fileList = dir.listFiles();
            for (File aFileList : fileList) {
                // Recursive call if it's a directory
                if (aFileList.isDirectory()) {
                    result += dirSize(aFileList);
                } else {
                    // Sum the file size in bytes
                    result += aFileList.length();
                }
            }
            return result; // return the file size
        }
        return 0;
    }

    static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        fileOrDirectory.delete();
    }

    static String format(String format, Object... args) {
        return String.format(Locale.ENGLISH, format, args);
    }

    static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    static int hash(Object... objects) {
        return Arrays.hashCode(objects);
    }

    @NonNull
    static String md5Hex(String input) {
        return bytesToHex(md5(input));
    }

    @NonNull
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
        }
        return sb.toString();
    }

    private static byte[] md5(String input) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Unlikely there's no MD5, or something is seriously wrong with the platform.", e);
            throw new Error("No MD5", e);
        }
        return md.digest(input.getBytes());
    }

    static void safeClose(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed resource closing");
                }
            }
        }
    }

    // Download the URL to targetFile and also return the contents.
    // If file is larger than maxReturnSize, the returned array will have maxReturnSize bytes,
    // but the file will have all of them.
    static byte[] downloadToFile(Uri uri, File targetFile, int maxReturnSize) throws IOException {

        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        HttpURLConnection conn = null;
        try {
            conn = openConnection(uri);
            conn.setRequestMethod("GET");
            conn.connect();
            inputStream = conn.getInputStream();

            fileOutputStream = new FileOutputStream(targetFile);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(10 * 1024); // 10kb: save some realloc'

            byte data[] = new byte[1024];
            int count;

            while ((count = inputStream.read(data)) != -1) {
                if (count > 0) {
                    fileOutputStream.write(data, 0, count);
                    int allowedInBuffer = maxReturnSize - byteArrayOutputStream.size();
                    if (allowedInBuffer > 0) {
                        byteArrayOutputStream.write(data, 0, Math.min(allowedInBuffer, count));
                    }
                }
            }

            return byteArrayOutputStream.toByteArray();
        } finally {
            // close everything
            safeClose(fileOutputStream);
            safeClose(inputStream);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    static byte[] downloadToFile(String url, File targetFile, @SuppressWarnings("SameParameterValue") int maxReturnSize) throws IOException {
        return downloadToFile(Uri.parse(url), targetFile, maxReturnSize);
    }

    static long httpHeadGetLength(Uri uri) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(uri);
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("Accept-Encoding", "");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode >= 400) {
                throw new IOException("Response code from HEAD request: " + responseCode);
            }
            String contentLength = connection.getHeaderField("Content-Length");
            if (!TextUtils.isEmpty(contentLength)) {
                return Long.parseLong(contentLength);
            } else {
                return -1;
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static HttpURLConnection openConnection(Uri uri) throws IOException {
        if (uri == null) {
            return null;
        }
        return (HttpURLConnection) new URL(uri.toString()).openConnection();
    }

    @NonNull
    private static ByteArrayOutputStream fullyReadInputStream(InputStream inputStream, int byteLimit) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte data[] = new byte[1024];
        int count;

        try {
            while ((count = inputStream.read(data)) != -1) {
                int maxCount = byteLimit - bos.size();
                if (count > maxCount) {
                    bos.write(data, 0, maxCount);
                    break;
                } else {
                    bos.write(data, 0, count);
                }
            }
            bos.flush();
        } finally {
            safeClose(bos);
            safeClose(inputStream);
        }
        return bos;
    }

    // Returns hex-encoded md5 of the input, appending the extension.
    // "a.mp4" ==> "2a1f28800d49717bbf88dc2c704f4390.mp4"
    static String getHashedFileName(String original) {
        String ext = getExtension(original);
        return md5Hex(original) + '.' + ext;
    }

    static String resolveUrl(String baseUrl, String maybeRelative) {
        if (maybeRelative == null) {
            return null;
        }
        Uri uri = Uri.parse(maybeRelative);
        if (uri.isAbsolute()) {
            return maybeRelative;
        }

        // resolve with baseUrl
        final Uri trackUri = Uri.parse(baseUrl);
        final List<String> pathSegments = new ArrayList<>(trackUri.getPathSegments());
        pathSegments.remove(pathSegments.size() - 1);
        final String pathWithoutLastSegment = TextUtils.join("/", pathSegments);
        uri = trackUri.buildUpon().encodedPath(pathWithoutLastSegment).appendEncodedPath(maybeRelative).build();
        return uri.toString();
    }

    static boolean mkdirs(File dir) {
        return dir.mkdirs() || dir.isDirectory();
    }

    static void mkdirsOrThrow(File dir) throws DirectoryNotCreatableException {
        if (!mkdirs(dir)) {
            throw new DirectoryNotCreatableException(dir);
        }
    }

    static byte[] readFile(File file, @SuppressWarnings("SameParameterValue") int byteLimit) throws IOException {
        FileInputStream inputStream = new FileInputStream(file);
        return fullyReadInputStream(inputStream, byteLimit).toByteArray();
    }

    static long estimateTrackSize(int trackBitrate, long durationMS) {
        return trackBitrate * durationMS / 1000 / 8;    // first multiply, then divide
    }

    static Set<Integer> makeRange(int first, int last) {
        if (last < first) {
            return Collections.singleton(first);
        }
        Set<Integer> range = new HashSet<>();
        for (int i = first; i <= last; i++) {
            range.add(i);
        }
        return range;
    }

    @NonNull
    static List<BaseTrack> flattenTrackList(Map<DownloadItem.TrackType, List<BaseTrack>> tracksMap) {
        List<BaseTrack> tracks = new ArrayList<>();
        for (Map.Entry<DownloadItem.TrackType, List<BaseTrack>> entry : tracksMap.entrySet()) {
            tracks.addAll(entry.getValue());
        }
        return tracks;
    }

    @SuppressWarnings("WeakerAccess")
    public static class DirectoryNotCreatableException extends IOException {

        private static final long serialVersionUID = -1369279756939511377L;

        private DirectoryNotCreatableException(File dir) {
            super("Can't create directory " + dir);
        }
    }

    static String getExtension(String url) {

        if (url == null) {
            return null;
        }

        final Uri uri = Uri.parse(url);
        if (uri == null) {
            return null;
        }

        final String lastPathSegment = uri.getLastPathSegment();
        if (TextUtils.isEmpty(lastPathSegment)) {
            return "";
        }

        final int dot = lastPathSegment.lastIndexOf('.');

        return dot >= 0 ? lastPathSegment.substring(dot + 1) : "";
    }
}
