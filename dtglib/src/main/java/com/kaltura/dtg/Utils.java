package com.kaltura.dtg;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
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

import static android.content.Context.UI_MODE_SERVICE;

public class Utils {
    private static final String TAG = "DTGUtils";
    private static final int MAX_REDIRECTS = 20;
    private static final int HTTP_STATUS_TEMPORARY_REDIRECT = 307;
    private static final int HTTP_STATUS_PERMANENT_REDIRECT = 308;

    private static final String USER_AGENT_KEY = "User-Agent";
    private static String USER_AGENT;
    private static Map<String, String> defaultHeaders;

    static String createTable(String name, String... colDefs) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(name).append("(");
        for (int i = 0; i < colDefs.length; i += 2) {
            if (i > 0) {
                sb.append(",\n");
            }
            sb.append(colDefs[i]).append(" ").append(colDefs[i + 1]);
        }
        sb.append(");");
        String str = sb.toString();
        Log.i("DBUtils", "Create table:\n" + str);
        return str;
    }

    static String createUniqueIndex(String tableName, String... colNames) {

        String str = "CREATE UNIQUE INDEX " +
                "unique_" + tableName + "_" + TextUtils.join("_", colNames) +
                " ON " + tableName +
                " (" + TextUtils.join(",", colNames) + ");";

        Log.i("DBUtils", "Create index:\n" + str);
        return str;
    }

    static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            final File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        fileOrDirectory.delete();
    }

    public static String format(String format, Object... args) {
        return String.format(Locale.ENGLISH, format, args);
    }

    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    public static int hash(Object... objects) {
        return Arrays.hashCode(objects);
    }

    @NonNull
    public static String md5Hex(String input) {
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

    public static void safeClose(Closeable... closeables) {
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
    public static byte[] downloadToFile(Uri uri, File targetFile, int maxReturnSize, boolean crossProtocolRedirectEnabled) throws IOException {
        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        HttpURLConnection conn = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(10 * 1024); // 10kb: save some realloc'

        try {
            conn = openConnection(uri);
            conn.setRequestMethod("GET");
            conn.connect();

            if (crossProtocolRedirectEnabled) {
                // We need to handle redirects ourselves to allow cross-protocol redirects.
                int redirectCount = 0;
                while (redirectCount++ <= MAX_REDIRECTS) {
                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_MULT_CHOICE
                            || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                            || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                            || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                            || responseCode == HTTP_STATUS_TEMPORARY_REDIRECT
                            || responseCode == HTTP_STATUS_PERMANENT_REDIRECT) {
                        conn.disconnect();
                        conn = handleRequestRedirects(conn);
                    } else {
                        break;
                    }
                }
            }

            inputStream = conn.getInputStream();

            fileOutputStream = new FileOutputStream(targetFile);

            byte[] data = new byte[1024];
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
        } finally {
            // close everything
            safeClose(fileOutputStream, inputStream);
            if (conn != null) {
                conn.disconnect();
            }
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static HttpURLConnection handleRequestRedirects(HttpURLConnection conn) throws IOException {
        String newUrl = conn.getHeaderField("Location"); // Get newUrl from location header
        if (newUrl == null) {
            throw new ProtocolException("Null location redirect");
        }
        // Form the new url.
        URL url = new URL(conn.getURL(), newUrl);
        // Check that the protocol of the new url is supported.
        String protocol = url.getProtocol();
        if (!"https".equals(protocol) && !"http".equals(protocol)) {
            throw new ProtocolException("Unsupported protocol redirect: " + protocol);
        }

        conn = openConnection(Uri.parse(newUrl));
        conn.setRequestMethod("GET");

        conn.connect();
        return conn;
    }

    public static byte[] downloadToFile(String url, File targetFile, int maxReturnSize, boolean crossProtocolRedirectEnabled) throws IOException {
        return downloadToFile(Uri.parse(url), targetFile, maxReturnSize, crossProtocolRedirectEnabled);
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

        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(uri.toString()).openConnection();
        if (defaultHeaders != null) {
            for (Map.Entry<String, String> stringStringEntry : defaultHeaders.entrySet()) {
                if (!TextUtils.isEmpty(stringStringEntry.getKey()) && stringStringEntry.getValue() != null) {
                    httpURLConnection.addRequestProperty(stringStringEntry.getKey(), stringStringEntry.getValue());
                }
            }
        }
        return httpURLConnection;
    }

    @NonNull
    private static ByteArrayOutputStream fullyReadInputStream(InputStream inputStream, int byteLimit) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
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

    static String toBase64(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    @NonNull
    static List<BaseTrack> flattenTrackList(Map<DownloadItem.TrackType, List<BaseTrack>> tracksMap) {
        List<BaseTrack> tracks = new ArrayList<>();
        for (Map.Entry<DownloadItem.TrackType, List<BaseTrack>> entry : tracksMap.entrySet()) {
            tracks.addAll(entry.getValue());
        }
        return tracks;
    }

    public static String resolveUrl(String baseUrl, String maybeRelative) {
        if (maybeRelative == null || baseUrl == null) {
            return null;
        }
        Uri maybeRelativeUri = Uri.parse(maybeRelative);
        if (maybeRelativeUri.isAbsolute()) {
            return maybeRelative;
        }

        // resolve with baseUrl
        Uri baseUrlUri = Uri.parse(baseUrl);
        String baseUriQueryParam = baseUrlUri.getEncodedQuery();
        String maybeRelativeQueryParam = maybeRelativeUri.getEncodedQuery();

        if (!TextUtils.isEmpty(maybeRelativeQueryParam) && !TextUtils.isEmpty(baseUriQueryParam)) {
            baseUrlUri = removeQueryParam(baseUrlUri);
        }

        final List<String> pathSegments = new ArrayList<>(baseUrlUri.getPathSegments());
        pathSegments.remove(pathSegments.size() - 1);
        final String pathWithoutLastSegment = TextUtils.join("/", pathSegments);
        maybeRelativeUri = baseUrlUri.buildUpon().encodedPath(pathWithoutLastSegment).appendEncodedPath(maybeRelative).build();
        return maybeRelativeUri.toString();
    }

    private static Uri removeQueryParam(Uri uri) {
        return uri.buildUpon().clearQuery().build();
    }

    public static boolean mkdirs(File dir) {
        return dir.mkdirs() || dir.isDirectory();
    }

    public static void mkdirsOrThrow(File dir) throws DirectoryNotCreatableException {
        if (!mkdirs(dir)) {
            throw new DirectoryNotCreatableException(dir);
        }
    }

    public static byte[] readFile(File file, int byteLimit) throws IOException {
        FileInputStream inputStream = new FileInputStream(file);
        return fullyReadInputStream(inputStream, byteLimit).toByteArray();
    }

    public static Set<Integer> makeRange(int first, int last) {
        if (last < first) {
            return Collections.singleton(first);
        }
        Set<Integer> range = new HashSet<>();
        for (int i = first; i <= last; i++) {
            range.add(i);
        }
        return range;
    }

    @SuppressWarnings("WeakerAccess")
    public static class DirectoryNotCreatableException extends IOException {
        private DirectoryNotCreatableException(File dir) {
            super("Can't create directory " + dir);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class LowDiskSpaceException extends IOException {
        LowDiskSpaceException() {
            super("Not enough disk space available");
        }
    }

    public static String getExtension(String url) {

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

    static void buildUserAgent(Context context) {

        if (USER_AGENT != null) {
            return;
        }

        USER_AGENT = getUserAgent(context);
        defaultHeaders = Collections.singletonMap(USER_AGENT_KEY, USER_AGENT);
    }

    private static String getUserAgent(Context context) {
        String applicationName;
        try {
            String packageName = context.getPackageName();
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            applicationName = packageName + "/" + info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            applicationName = "?";
        }

        return ContentManager.CLIENT_TAG + " " + applicationName + " " + System.getProperty("http.agent") + " " + getDeviceType(context);
    }

    private static String getDeviceType(Context context) {
        String deviceType = "Mobile";

        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);
        if (uiModeManager == null) {
            return deviceType;
        }
        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            deviceType = "TV";
        } else {
            TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (manager != null && manager.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE) {
                deviceType = "Tablet";
            }
        }
        return deviceType;
    }
}
