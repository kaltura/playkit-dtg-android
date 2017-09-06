package com.kaltura.dtg;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;

/**
 * Created by noamt on 5/13/15.
 */
public class Utils {
    private static final String TAG = "DTGUtils";

    public static String createTable(String name, String... coldefs) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(name).append("(");
        for (int i = 0; i < coldefs.length; i += 2) {
            if (i > 0) {
                sb.append(",\n");
            }
            sb.append(coldefs[i]).append(" ").append(coldefs[i + 1]);
        }
        sb.append(");");
        String str = sb.toString();
        Log.i("DBUtils", "Create table:\n" + str);
        return str;
    }

    public static String createUniqueIndex(String tableName, String... colNames) {

        String str = "CREATE UNIQUE INDEX " +
                "unique_" + tableName + "_" + TextUtils.join("_", colNames) +
                " ON " + tableName +
                " (" + TextUtils.join(",", colNames) + ");";

        Log.i("DBUtils", "Create index:\n" + str);
        return str;
    }

    public static HashMap<String, Object> map(Object... keyValuePairs) {
        HashMap<String, Object> map = new HashMap<>();

        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }

        return map;
    }

    public static String dropTables(String... tables) {
        // TODO: what's this doing?
        StringBuilder sb = new StringBuilder();
        for (String table : tables) {
            // semicolon supported in sqlite?
            //sb.append("DROP TABLE IF EXISTS ").append(table).append(";\n");
        }
        String str = sb.toString();
        Log.i("DBUtils", "Drop tables:\n" + str);
        return str;
    }
    
    public static long dirSize(File dir) {

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

    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }
    
    public static String format(String format, Object... args) {
        return String.format(Locale.ENGLISH, format, args);
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

    public static byte[] md5(String input) {
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
    public static byte[] downloadToFile(URL url, File targetFile, int maxReturnSize) throws IOException {

        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
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

    public static long httpHeadGetLength(URL url) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
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

    @NonNull
    public static ByteArrayOutputStream fullyReadInputStream(InputStream inputStream, int byteLimit) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte data[] = new byte[1024];
        int count;

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
        bos.close();
        inputStream.close();
        return bos;
    }

    // Returns hex-encoded md5 of the input, appending the extension.
    // "a.mp4" ==> "2a1f28800d49717bbf88dc2c704f4390.mp4"
    public static String getHashedFileName(String original) {
        String ext = getFileExtension(original);
        return md5Hex(original) + ext;
    }

    @NonNull
    private static String getFileExtension(String pathOrURL) {
        // if it's a URL, get only the path. Uri does this correctly, even if the argument is a simple path.
        String path = Uri.parse(pathOrURL).getPath();

        return path.substring(path.lastIndexOf('.'));
    }
}
