package com.kaltura.dtg;

import android.content.Context;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

class Storage {
    private static long REQUIRED_DATA_PARTITION_SIZE_BYTES = 200 * 1024 * 1024;
    private static File itemsDir;
    private static File dataDir;
    private static File downloadsDir;
    private static File extFilesDir;

    @NonNull
    static File getExtFilesDir() {
        return extFilesDir;
    }

    @NonNull
    static File getItemsDir() {
        return itemsDir;
    }

    @NonNull
    static File getDataDir() {
        return dataDir;
    }

    @NonNull
    static File getDownloadsDir() {
        return downloadsDir;
    }

    static boolean isLowDiskSpace(long requiredBytes) {
        final long downloadsDirFreeSpace = downloadsDir.getFreeSpace();
        final long dataDirFreeSpace = dataDir.getFreeSpace();
        return downloadsDirFreeSpace < requiredBytes || dataDirFreeSpace < REQUIRED_DATA_PARTITION_SIZE_BYTES;
    }

    static void setup(Context context, ContentManager.Settings settings) throws IOException {
        File filesDir = context.getFilesDir();
        itemsDir = new File(filesDir, "dtg/items");

        // Create all directories
        Utils.mkdirsOrThrow(filesDir);
        Utils.mkdirsOrThrow(itemsDir);

        dataDir = new File(filesDir, "dtg/clear");
        Utils.mkdirsOrThrow(dataDir);

        extFilesDir = context.getExternalFilesDir(null);
        if (extFilesDir == null) {
            throw new FileNotFoundException("No external files dir, can't continue");
        }

        downloadsDir = new File(extFilesDir, "dtg/clear");
        Utils.mkdirsOrThrow(downloadsDir);
        if (settings.createNoMediaFileInDownloadsDir) {
            //noinspection ResultOfMethodCallIgnored
            new File(extFilesDir, ".nomedia").createNewFile();
        }
    }
}
