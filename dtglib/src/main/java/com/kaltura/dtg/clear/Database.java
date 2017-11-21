package com.kaltura.dtg.clear;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadState;
import com.kaltura.dtg.Utils;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


/**
 * Created by noamt on 5/20/15.
 */
class Database {
    static final int DB_VERSION = 2;
    static final String TBL_DOWNLOAD_FILES = "Files";
    static final String COL_FILE_URL = "FileURL";
    static final String COL_TARGET_FILE = "TargetFile";
    static final String TBL_ITEMS = "Items";
    static final String COL_ITEM_ID = "ItemID";
    static final String COL_CONTENT_URL = "ContentURL";
    static final String COL_ITEM_STATE = "ItemState";
    static final String COL_ITEM_ADD_TIME = "TimeAdded";
    static final String COL_ITEM_FINISH_TIME = "TimeFinished";
    static final String COL_ITEM_DATA_DIR = "ItemDataDir";
    static final String COL_ITEM_ESTIMATED_SIZE = "ItemEstimatedSize";
    static final String COL_ITEM_DOWNLOADED_SIZE = "ItemDownloadedSize";
    static final String COL_ITEM_PLAYBACK_PATH = "ItemPlaybackPath";
//    static final String TBL_STORAGE = "Storage";
//    static final String COL_STORAGE_ITEM_ID = "StorageId";
//    static final String COL_STORAGE_OWNER_ID = "OwnerId";
//    static final String COL_STORAGE_ITEM_KEY = "ItemKey";
//    static final String COL_STORAGE_ITEM = "Item";
    static final String[] ALL_ITEM_COLS = new String[]{COL_ITEM_ID, COL_CONTENT_URL,
            COL_ITEM_STATE, COL_ITEM_ADD_TIME, COL_ITEM_ESTIMATED_SIZE, COL_ITEM_DOWNLOADED_SIZE,
            COL_ITEM_PLAYBACK_PATH, COL_ITEM_DATA_DIR};
    static final String TAG = "Database";
    static final String TBL_TRACK = "Track";
    static final String COL_TRACK_ID = "TrackId";
    static final String COL_TRACK_EXTRA = "TrackExtra";
    static final String COL_TRACK_STATE = "TrackState";
    static final String COL_TRACK_TYPE = "TrackType";
    static final String COL_TRACK_LANGUAGE = "TrackLanguage";
    static final String COL_TRACK_BITRATE = "TrackBitrate";
    static final String COL_TRACK_REL_ID = "TrackRelativeId";
    static final String COL_FILE_COMPLETE = "FileComplete";

    private final SQLiteOpenHelper helper;
    private final SQLiteDatabase database;


    Database(File dbFile, final Context context) {
        helper = new SQLiteOpenHelper(context, dbFile.getAbsolutePath(), null, DB_VERSION) {

            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL(Utils.createTable(
                        TBL_ITEMS,
                        COL_ITEM_ID, "TEXT PRIMARY KEY",
                        COL_CONTENT_URL, "TEXT NOT NULL",
                        COL_ITEM_STATE, "TEXT NOT NULL",
                        COL_ITEM_ADD_TIME, "INTEGER NOT NULL",
                        COL_ITEM_FINISH_TIME, "INTEGER NOT NULL DEFAULT 0",
                        COL_ITEM_DATA_DIR, "TEXT NOT NULL",
                        COL_ITEM_ESTIMATED_SIZE, "INTEGER NOT NULL DEFAULT 0",
                        COL_ITEM_DOWNLOADED_SIZE, "INTEGER NOT NULL DEFAULT 0",
                        COL_ITEM_PLAYBACK_PATH, "TEXT"
                ));

                createFilesTable(db);

                createTrackTable(db);
            }

            private void createFilesTable(SQLiteDatabase db) {
                db.execSQL(Utils.createTable(
                        TBL_DOWNLOAD_FILES,
                        COL_ITEM_ID, "TEXT NOT NULL REFERENCES " + TBL_ITEMS + "(" + COL_ITEM_ID + ") ON DELETE CASCADE",
                        COL_FILE_URL, "TEXT NOT NULL",
                        COL_TARGET_FILE, "TEXT NOT NULL",
                        COL_TRACK_REL_ID, "TEXT",
                        COL_FILE_COMPLETE, "INTEGER NOT NULL DEFAULT 0"
                ));
                db.execSQL(Utils.createUniqueIndex(TBL_DOWNLOAD_FILES, COL_ITEM_ID, COL_FILE_URL));
            }

            private void createTrackTable(SQLiteDatabase db) {
                db.execSQL(Utils.createTable(
                        TBL_TRACK,
                        COL_TRACK_ID, "INTEGER PRIMARY KEY",
                        COL_TRACK_STATE, "TEXT NOT NULL",   // DashDownloader.TrackState
                        COL_TRACK_TYPE, "TEXT NOT NULL",    // DownloadItem.TrackType
                        COL_TRACK_LANGUAGE, "TEXT",
                        COL_TRACK_BITRATE, "INTEGER",
                        COL_TRACK_REL_ID, "TEXT NOT NULL",
                        COL_TRACK_EXTRA, "TEXT",
                        COL_ITEM_ID, "TEXT NOT NULL REFERENCES " + TBL_ITEMS + "(" + COL_ITEM_ID + ") ON DELETE CASCADE"
                ));
                db.execSQL(Utils.createUniqueIndex(TBL_TRACK, COL_ITEM_ID, COL_TRACK_REL_ID));
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                db.beginTransaction();
                
                if (newVersion == 2) {
                    // Upgrade 1 -> 2: Track table was missing
                    createTrackTable(db);
                    
                    // recreate Files table
                    db.execSQL("DROP INDEX IF EXISTS unique_Files_ItemID_FileURL");
                    db.execSQL("ALTER TABLE " + TBL_DOWNLOAD_FILES + " RENAME TO OLD_" + TBL_DOWNLOAD_FILES);
                    createFilesTable(db);
                    
                    db.execSQL("INSERT INTO " + TBL_DOWNLOAD_FILES + "(" + COL_ITEM_ID + "," + COL_FILE_URL + "," + COL_TARGET_FILE + ") " +
                            "SELECT ItemID, FileURL, TargetFile FROM Files");
                    db.execSQL("DROP TABLE OLD_" + TBL_DOWNLOAD_FILES);
                }
                
                db.setTransactionSuccessful();
                db.endTransaction();
            }

            @Override
            public void onConfigure(SQLiteDatabase db) {
                super.onConfigure(db);
                db.setForeignKeyConstraintsEnabled(true);
                db.setLocale(Locale.US);
            }
        };
        database = helper.getWritableDatabase();
    }

    private static void safeClose(Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }
    }

    private static String[] strings(String... strings) {
        return strings;
    }

    synchronized private boolean doTransaction(Transaction transaction) {
        if (database == null) {
            return false;
        }

        boolean success;
        try {
            database.beginTransaction();

            success = transaction.execute(database);

            if (success) {
                database.setTransactionSuccessful();
            }
        } finally {
            database.endTransaction();
        }
        return success;
    }

    synchronized void close() {
        database.close();
        helper.close();
    }

    synchronized void addDownloadTasksToDB(final DownloadItem item, final List<DownloadTask> downloadTasks) {
        doTransaction(new Transaction() {
            @Override
            public boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                for (DownloadTask task : downloadTasks) {
                    values.put(COL_ITEM_ID, item.getItemId());
                    values.put(COL_FILE_URL, task.url.toExternalForm());
                    values.put(COL_TARGET_FILE, task.targetFile.getAbsolutePath());
                    values.put(COL_TRACK_REL_ID, task.trackRelativeId);
                    try {
                        long rowid = db.insertWithOnConflict(TBL_DOWNLOAD_FILES, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                        if (rowid <= 0) {
//                            Log.d(TAG, "Warning: task not added:" + task.targetFile);
                        }
                    } catch (SQLException e) {
                        Log.e(TAG, "Failed to INSERT task: " + task.targetFile, e);
                    }
                }
                return true;
            }
        });
    }

    synchronized ArrayList<DownloadTask> readPendingDownloadTasksFromDB(final String itemId) {

        final ArrayList<DownloadTask> downloadTasks = new ArrayList<>();

        SQLiteDatabase db = database;
        Cursor cursor = null;

        try {
            cursor = db.query(TBL_DOWNLOAD_FILES, new String[]{COL_FILE_URL, COL_TARGET_FILE},
                    COL_ITEM_ID + "==? AND " + COL_FILE_COMPLETE + "==0", new String[]{itemId}, null, null, "ROWID");
            
            while (cursor.moveToNext()) {
                String url = cursor.getString(0);
                String file = cursor.getString(1);
                try {
                    DownloadTask task = new DownloadTask(url, file);
                    task.itemId = itemId;
                    downloadTasks.add(task);
                } catch (MalformedURLException e) {
                    Log.w(TAG, "Malformed URL while reading downloads from db", e);
                }
            }

        } finally {
            safeClose(cursor);
        }

        return downloadTasks;
    }

    synchronized void markTaskAsComplete(final DownloadTask downloadTask) {
        
        doTransaction(new Transaction() {
            @Override
            public boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                values.put(COL_FILE_COMPLETE, 1);
                
                db.updateWithOnConflict(TBL_DOWNLOAD_FILES, values, COL_TARGET_FILE + "==?",
                        new String[]{downloadTask.targetFile.getAbsolutePath()},
                        SQLiteDatabase.CONFLICT_IGNORE);
                return true;
            }
        });
    }

    synchronized DefaultDownloadItem findItemInDB(String itemId) {

        SQLiteDatabase db = database;
        Cursor cursor = null;
        DefaultDownloadItem item = null;

        try {
            cursor = db.query(TBL_ITEMS,
                    ALL_ITEM_COLS,
                    COL_ITEM_ID + "==?", new String[]{itemId}, null, null, null);

            if (cursor.moveToFirst()) {
                item = readItem(cursor);
            }

        } finally {
            safeClose(cursor);
        }

        return item;
    }

    synchronized void addItemToDB(final DefaultDownloadItem item, final File itemDataDir) {

        doTransaction(new Transaction() {
            @Override
            public boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues(5);
                values.put(COL_ITEM_ID, item.getItemId());
                values.put(COL_CONTENT_URL, item.getContentURL());
                values.put(COL_ITEM_ADD_TIME, item.getAddedTime());
                values.put(COL_ITEM_STATE, item.getState().name());
                values.put(COL_ITEM_DATA_DIR, itemDataDir.getAbsolutePath());
                values.put(COL_ITEM_PLAYBACK_PATH, item.getPlaybackPath());
                db.insert(TBL_ITEMS, null, values);
                return true;
            }
        });
    }

    synchronized void removeItemFromDB(final DefaultDownloadItem item) {

        doTransaction(new Transaction() {
            @Override
            public boolean execute(SQLiteDatabase db) {
                db.delete(TBL_ITEMS, COL_ITEM_ID + "=?", new String[]{item.getItemId()});
                
                // There's an "on delete cascade" between TBL_ITEMS and TBL_DOWNLOAD_FILES,
                // but it wasn't active in the previous schema.
                db.delete(TBL_DOWNLOAD_FILES, COL_ITEM_ID + "=?", new String[]{item.getItemId()});
                return true; 
            }
        });
    }

    synchronized void updateItemState(final String itemId, final DownloadState itemState) {
        doTransaction(new Transaction() {
            @Override
            public boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                values.put(COL_ITEM_STATE, itemState.name());

                db.update(TBL_ITEMS, values, COL_ITEM_ID + "==?", new String[]{itemId});

                return true;
            }
        });
    }

    synchronized void setDownloadFinishTime(final String itemId) {
        doTransaction(new Transaction() {
            @Override
            public boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                values.put(COL_ITEM_FINISH_TIME, System.currentTimeMillis());

                int res = db.update(TBL_ITEMS, values, COL_ITEM_ID + "==?", new String[]{itemId});

                return res > 0;
            }
        });
    }

    synchronized void setEstimatedSize(final String itemId, final long estimatedSizeBytes) {
        doTransaction(new Transaction() {
            @Override
            public boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                values.put(COL_ITEM_ESTIMATED_SIZE, estimatedSizeBytes);
                db.update(TBL_ITEMS, values, COL_ITEM_ID + "==?", new String[]{itemId});
                return true;
            }
        });
    }

    synchronized void updateDownloadedFileSize(final String itemId, final long downloadedFileSize) {
        doTransaction(new Transaction() {
            @Override
            public boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                values.put(COL_ITEM_DOWNLOADED_SIZE, downloadedFileSize);
                db.update(TBL_ITEMS, values, COL_ITEM_ID + "==?", new String[]{itemId});
                return true;
            }
        });
    }
    
    // If itemId is null, sum all items.
    long getEstimatedItemSize(@Nullable String itemId) {
        return getItemColumnLong(itemId, COL_ITEM_ESTIMATED_SIZE);
    }

    long getDownloadedItemSize(@Nullable String itemId) {
        return getItemColumnLong(itemId, COL_ITEM_DOWNLOADED_SIZE);
    }

    // If itemId is null, sum all items.
    synchronized long getItemColumnLong(@Nullable String itemId, @NonNull String col) {
        SQLiteDatabase db = database;
        Cursor cursor = null;
        try {
            if (itemId != null) {
                cursor = db.query(TBL_ITEMS, new String[]{col}, COL_ITEM_ID + "==?", new String[]{itemId}, null, null, null);
            } else {
                cursor = db.rawQuery("SELECT SUM(" + col + ") FROM " + TBL_ITEMS, null);
            }
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
            return 0;
        } finally {
            safeClose(cursor);
        }
    }

    synchronized void updateItemInfo(final DefaultDownloadItem item, final String[] columns) {
        if (columns==null || columns.length == 0) {
            throw new IllegalArgumentException("columns.length must be >0");
        }
        doTransaction(new Transaction() {
            @Override
            public boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues(columns.length);
                for (String column : columns) {
                    switch (column) {
                        case COL_ITEM_ADD_TIME:
                            values.put(COL_ITEM_ADD_TIME, item.getAddedTime());
                            break;
                        case COL_ITEM_STATE:
                            values.put(COL_ITEM_STATE, item.getState().name());
                            break;
                        case COL_ITEM_ESTIMATED_SIZE:
                            values.put(COL_ITEM_ESTIMATED_SIZE, item.getEstimatedSizeBytes());
                            break;
                        case COL_ITEM_DOWNLOADED_SIZE:
                            values.put(COL_ITEM_DOWNLOADED_SIZE, item.getDownloadedSizeBytes());
                            break;
                        case COL_ITEM_PLAYBACK_PATH:
                            values.put(COL_ITEM_PLAYBACK_PATH, item.getPlaybackPath());
                            break;
                        case COL_ITEM_DATA_DIR:
                            values.put(COL_ITEM_DATA_DIR, item.getDataDir());
                            break;

                        // invalid -- can't change those. 
                        case COL_ITEM_ID:
                        case COL_CONTENT_URL:
                            return false;   // fail the transaction
                    }
                }
                if (values.size() == 0) {
                    Log.e(TAG, "No values; columns=" + Arrays.toString(columns));
                    return false;
                }
                db.update(TBL_ITEMS, values, COL_ITEM_ID + "==?", new String[]{item.getItemId()});
                return true;
            }
        });
    }

    private DefaultDownloadItem readItem(Cursor cursor) {
        String[] columns = cursor.getColumnNames();

        // the bare minimum: itemId and contentURL
        String itemId = cursor.getString(cursor.getColumnIndexOrThrow(COL_ITEM_ID));
        String contentURL = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT_URL));

        DefaultDownloadItem item = new DefaultDownloadItem(itemId, contentURL);
        for (int i = 0; i < columns.length; i++) {
            switch (columns[i]) {
                case COL_ITEM_ID:
                case COL_CONTENT_URL:
                    // we already have those. 
                    break;
                case COL_ITEM_STATE:
                    item.setState(DownloadState.valueOf(cursor.getString(i)));
                    break;
                case COL_ITEM_ESTIMATED_SIZE:
                    item.setEstimatedSizeBytes(cursor.getLong(i));
                    break;
                case COL_ITEM_DOWNLOADED_SIZE:
                    item.setDownloadedSizeBytes(cursor.getLong(i));
                    break;
                case COL_ITEM_ADD_TIME:
                    item.setAddedTime(cursor.getLong(i));
                    break;
                case COL_ITEM_DATA_DIR:
                    item.setDataDir(cursor.getString(i));
                    break;
                case COL_ITEM_PLAYBACK_PATH:
                    item.setPlaybackPath(cursor.getString(i));
                    break;
                case COL_ITEM_FINISH_TIME:
                    item.setFinishedTime(cursor.getLong(i));
                    break;
            }
        }
        return item;
    }

    synchronized ArrayList<DefaultDownloadItem> readItemsFromDB(DownloadState[] states) {
        // TODO: unify some code with findItem()

        String stateNames[] = new String[states.length];
        for (int i = 0; i < states.length; i++) {
            stateNames[i] = states[i].name();
        }
        String placeholders = "(" + TextUtils.join(",", Collections.nCopies(stateNames.length, "?")) + ")";

        ArrayList<DefaultDownloadItem> items = new ArrayList<>();

        SQLiteDatabase db = database;
        Cursor cursor = null;

        try {
            cursor = db.query(TBL_ITEMS,
                    ALL_ITEM_COLS,
                    COL_ITEM_STATE + " IN " + placeholders, stateNames, null, null, null);

            // TODO: consider using a LinkedList and/or doing a COUNT query to pre-allocate the list.

            while (cursor.moveToNext()) {
                DefaultDownloadItem item = readItem(cursor);
                items.add(item);
            }
        } finally {
            safeClose(cursor);
        }


        return items;
    }

    synchronized int countPendingFiles(String itemId) {
        return countPendingFiles(itemId, null);
    }
    
    synchronized int countPendingFiles(String itemId, @Nullable String trackId) {

        SQLiteDatabase db = database;
        Cursor cursor = null;
        int count = 0;

        try {
            if (trackId != null) {
                String sql = "SELECT COUNT(*) FROM " + TBL_DOWNLOAD_FILES +
                        " WHERE " + COL_ITEM_ID + "==? AND " + COL_FILE_COMPLETE + "==0 AND " + COL_TRACK_REL_ID + "==?";
                cursor = db.rawQuery(sql, new String[]{itemId, trackId});
                
            } else {
                String sql = "SELECT COUNT(*) FROM " + TBL_DOWNLOAD_FILES +
                        " WHERE " + COL_ITEM_ID + "==? AND " + COL_FILE_COMPLETE + "==0";
                cursor = db.rawQuery(sql, new String[]{itemId});
            }

            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }

        } finally {
            safeClose(cursor);
        }

        return count;
    }

    synchronized void addTracks(final DefaultDownloadItem item, final List<DashTrack> availableTracks, final List<DashTrack> selectedTracks) {
        doTransaction(new Transaction() {
            @Override
            public boolean execute(SQLiteDatabase db) {
                
                for (DashTrack track : availableTracks) {
                    ContentValues values = track.toContentValues();
                    values.put(COL_ITEM_ID, item.getItemId());
                    values.put(COL_TRACK_STATE, DashDownloader.TrackState.NOT_SELECTED.name());
                    try {
                        db.insertOrThrow(TBL_TRACK, null, values);
                    } catch (SQLiteConstraintException e) {
                        Log.w(TAG, "Insert failed", e);
                    }
                }

                for (DashTrack track : selectedTracks) {
                    ContentValues values = new ContentValues();
                    values.put(COL_TRACK_STATE, DashDownloader.TrackState.SELECTED.name());
                    db.update(TBL_TRACK, values, COL_ITEM_ID + "=? AND " + COL_TRACK_REL_ID + "=?", 
                            strings(item.getItemId(), track.getRelativeId()));
                }
                
                return true;
            }
        });
    }
    
    synchronized List<DashTrack> readTracks(String itemId, DownloadItem.TrackType type, @Nullable DashDownloader.TrackState state) {
        Cursor cursor = null;
        List<DashTrack> tracks = new ArrayList<>(10);
        try {
            List<String> selectionCols = new ArrayList<>();
            List<String> selectionArgs = new ArrayList<>();
            
            selectionCols.add(COL_ITEM_ID);
            selectionArgs.add(itemId);
            
            if (type != null) {
                selectionCols.add(COL_TRACK_TYPE);
                selectionArgs.add(type.name());
            }

            if (state != null) {
                selectionCols.add(COL_TRACK_STATE);
                selectionArgs.add(state.name());
            }

            String selection = TextUtils.join("=? AND ", selectionCols) + "=?";
            String[] selectionArgsArray = selectionArgs.toArray(new String[selectionArgs.size()]);
            cursor = database.query(TBL_TRACK,
                    DashTrack.REQUIRED_DB_FIELDS,
                    selection,
                    selectionArgsArray,
                    null, null, COL_TRACK_ID + " ASC");
            // TODO: 13/09/2016 Consider order by type+bitrate 
            
            while (cursor.moveToNext()) {
                DashTrack track = new DashTrack(cursor);
                tracks.add(track);
            }
            
        } finally {
            safeClose(cursor);
        }
        
        return tracks;
    }

    synchronized void updateTracksState(final String itemId, final List<DashTrack> tracks, final DashDownloader.TrackState newState) {
        doTransaction(new Transaction() {
            @Override
            public boolean execute(SQLiteDatabase db) {
                
                ContentValues values = new ContentValues();
                values.put(COL_TRACK_STATE, newState.name());

                for (DashTrack track : tracks) {
                    db.update(TBL_TRACK, values, COL_ITEM_ID + "=? AND " + COL_TRACK_REL_ID + "=?", strings(itemId, track.getRelativeId()));
                }
                
                return true;
            }
        });
    }

    interface Transaction {
        boolean execute(SQLiteDatabase db);
    }
        
}
