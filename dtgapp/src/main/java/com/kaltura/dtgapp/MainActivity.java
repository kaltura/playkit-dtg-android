package com.kaltura.dtgapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.kaltura.dtg.ContentManager;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadState;
import com.kaltura.dtg.DownloadStateListener;
import com.kaltura.dtg.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;


@SuppressLint("Assert")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DTGApp";

    JSONObject getTestData() {
        try {
            return new JSONObject().put("one", 1).put("two", 2).put("cloud", "☁︎");
        } catch (JSONException e) {
            return null;
        }
    }
    
    void setButtonAction(int buttonId, View.OnClickListener onClickListener) {
        Button button = (Button) findViewById(buttonId);
        assert button != null;
        button.setOnClickListener(onClickListener);
    }
    
    void uiLog(Object obj) {
        final String text = obj!=null ? obj.toString() : "<null>";
        final TextView textView = ((TextView) findViewById(R.id.text_log));
        assert textView != null;
        textView.post(new Runnable() {
            @Override
            public void run() {
                textView.append(text + "\n\n");
            }
        });
    }
    
    List<String> itemIdList(List<DownloadItem> items) {
        List<String> ids = new ArrayList<>(items.size());
        for (DownloadItem item : items) {
            ids.add(item.getItemId());
        }
        return ids;
    }
    
    
    class SpinnerItem {
        private final String url;
        private final String itemId;

        SpinnerItem(String itemId, String url) {
            this.itemId = itemId;
            this.url = url;
        }

        @Override
        public String toString() {
            return itemId;
        }
    }
    
    SpinnerItem getSelectedItem() {
        Spinner itemSpinner = (Spinner) findViewById(R.id.spinner_item);
        assert itemSpinner != null;
        return (SpinnerItem) itemSpinner.getSelectedItem();
    }

    private void loadTestItems(final ArrayAdapter<SpinnerItem> itemAdapter) {

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            final Uri uri = intent.getData();

            new AsyncTask<Void, Void, byte[]>() {
                @Override
                protected byte[] doInBackground(Void... params) {
                    try {
                        return Utils.downloadToFile(new URL(uri.toString()), new File(getFilesDir(), "last.dtglist.json"), 10000);
                    } catch (IOException e) {
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(byte[] bytes) {
                    addTestItems(bytes, itemAdapter);
                }
            }.execute();
        } else {
            try {
                InputStream inputStream = getResources().getAssets().open("items.json");
                byte[] buffer = new byte[inputStream.available()];
                if (inputStream.read(buffer) > 0) {
                    addTestItems(buffer, itemAdapter);
                }
            } catch (IOException e) {
            }
        }
    }

    private void addTestItems(byte[] buffer, ArrayAdapter<SpinnerItem> itemAdapter) {
        
        try {
            JSONArray items = new JSONArray(new String(buffer));

            for (int i=0, n=items.length(); i < n; i++) {
                JSONObject jo = items.getJSONObject(i);
                String id = jo.keys().next();
                String url = jo.getString(id);
                itemAdapter.add(new SpinnerItem(id, url));
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error reading items.json, spinner will be empty", e);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);


        final ContentManager contentManager = ContentManager.getInstance(this);

        final HashMap<String, Long> downloadStartTime = new HashMap<>();

        contentManager.setMaxConcurrentDownloads(2);
        
        contentManager.addDownloadStateListener(new DownloadStateListener() {
            @Override
            public void onDownloadComplete(DownloadItem item) {
                long startTime = downloadStartTime.remove(item.getItemId());
                long downloadTime = System.currentTimeMillis() - startTime;
                Log.d(TAG, "onDownloadComplete: " + item.getItemId() + "; " + item.getDownloadedSizeBytes()/1024 + "; " + downloadTime/1000f);
                uiLog("Downloaded " + item.getItemId() + " in " + downloadTime/1000f + " seconds");
            }

            @Override
            public void onProgressChange(DownloadItem item, long downloadedBytes) {
                long estimatedSizeBytes = item.getEstimatedSizeBytes();
                long percent = estimatedSizeBytes > 0 ? 100 * downloadedBytes / estimatedSizeBytes : 0;
                Log.d(TAG, "onProgressChange: " + item.getItemId() + "; " + percent + "; " + item.getDownloadedSizeBytes()/1024);
            }

            @Override
            public void onDownloadStart(DownloadItem item) {
                downloadStartTime.put(item.getItemId(), System.currentTimeMillis());
                Log.d(TAG, "onDownloadStart: " + item.getItemId() + "; " + item.getDownloadedSizeBytes()/1024);
                uiLog(item);
            }

            @Override
            public void onDownloadPause(DownloadItem item) {
                Log.d(TAG, "onDownloadPause: " + item.getItemId() + "; " + item.getDownloadedSizeBytes()/1024);
            }

            @Override
            public void onDownloadStop(DownloadItem item) {
                Log.d(TAG, "onDownloadStop: " + item.getItemId() + "; " + item.getDownloadedSizeBytes()/1024);
            }

            @Override
            public void onDownloadMetadata(DownloadItem item, Exception error) {
                if (error == null) {
                    uiLog("Metadata for " + item.getItemId() + " is loaded");
                    uiLog(item);
                    
                    // Pre-download interactive track selection
                    // Select second audio track, if there are at least 2.
                    DownloadItem.TrackSelector trackSelector = item.getTrackSelector();
                    if (trackSelector != null) {
                        List<DownloadItem.Track> audioTracks = trackSelector.getAvailableTracks(DownloadItem.TrackType.AUDIO);
                        if (audioTracks.size() >= 2) {
                            trackSelector.setSelectedTracks(DownloadItem.TrackType.AUDIO, Collections.singletonList(audioTracks.get(1)));
                        }

                        try {
                            trackSelector.apply();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                } else {
                    uiLog("Failed loading metadata for " + item.getItemId() + ": " + error);
                }
            }

            @Override
            public void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector) {
                
                // Policy-based selection
                // Select first and last audio
                
                List<DownloadItem.Track> audioTracks = trackSelector.getAvailableTracks(DownloadItem.TrackType.AUDIO);
                if (audioTracks.size() > 0) {
                    List<DownloadItem.Track> selection = new ArrayList<>();
                    selection.add(audioTracks.get(0));
                    if (audioTracks.size() > 1) {
                        selection.add(audioTracks.get(audioTracks.size()-1));
                    }
                    trackSelector.setSelectedTracks(DownloadItem.TrackType.AUDIO, selection);
                }

                // Select lowest-resolution video
                List<DownloadItem.Track> videoTracks = trackSelector.getAvailableTracks(DownloadItem.TrackType.VIDEO);
                DownloadItem.Track minVideo = Collections.min(videoTracks, DownloadItem.Track.bitrateComparator);
                trackSelector.setSelectedTracks(DownloadItem.TrackType.VIDEO, Collections.singletonList(minVideo));

            }
        });
        
        contentManager.start(new ContentManager.OnStartedListener() {
            @Override
            public void onStarted() {
                Log.d(TAG, "Service started");
            }
        });

        setButtonAction(R.id.totalStorageSize, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uiLog("Total Size: " + contentManager.getEstimatedItemSize(null));
            }
        });

        Spinner itemSpinner = (Spinner) findViewById(R.id.spinner_item);
        ArrayAdapter<SpinnerItem> itemAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        itemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        assert itemSpinner != null;
        itemSpinner.setAdapter(itemAdapter);

        loadTestItems(itemAdapter);

        setButtonAction(R.id.button_new_item, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpinnerItem selected = getSelectedItem();
                DownloadItem item = contentManager.findItem(selected.itemId);
                if (item == null) {
                    item = contentManager.createItem(selected.itemId, selected.url);
                    uiLog("Item created");
                    uiLog(item);
                } else {
                    uiLog("Item already exists");
                }
            }
        });
        
        setButtonAction(R.id.button_load_info, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final DownloadItem item = contentManager.findItem(getSelectedItem().itemId);
                item.loadMetadata();
            }
        });

        setButtonAction(R.id.button_show_existing, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpinnerItem selected = getSelectedItem();
                DownloadItem item = contentManager.findItem(selected.itemId);
                uiLog(item);
            }
        });

        setButtonAction(R.id.button_start_existing, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpinnerItem spinner = getSelectedItem();
                DownloadItem item = contentManager.findItem(spinner.itemId);
                if (item == null) {
                    uiLog("Item not found");
                } else {
                    item.startDownload();
                }
            }
        });

        setButtonAction(R.id.button_remove_item, new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                SpinnerItem selected = getSelectedItem();
                contentManager.removeItem(selected.itemId);
                uiLog("DONE");
            }
        });

        setButtonAction(R.id.button_pause_download, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpinnerItem spinner = getSelectedItem();

                DownloadItem item = contentManager.findItem(spinner.itemId);
                if (item!=null) {
                    item.pauseDownload();
                }
            }
        });

        setButtonAction(R.id.button_pause_all, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                contentManager.pauseDownloads();
            }
        });

        setButtonAction(R.id.button_resume_all, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                contentManager.resumeDownloads();
            }
        });

        setButtonAction(R.id.button_write_app_data, new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                SpinnerItem selected = getSelectedItem();

                DownloadItem item = contentManager.findItem(selected.itemId);
                
                JSONObject td = getTestData();
                
                // As file
                File appDataDir = contentManager.getAppDataDir(item.getItemId());
                String result;
                try {
                    FileWriter writer = new FileWriter(new File(appDataDir, "date.txt"));
                    String data = td.toString();
                    writer.write(data);
                    writer.close();
                    result = String.format("Data written to AppData(%s): '%s'", selected.itemId, data);
                } catch (IOException e) {
                    e.printStackTrace();
                    result = e.toString();
                }
                uiLog(result);
            }
        });

        setButtonAction(R.id.button_read_app_data, new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                SpinnerItem selected;
                selected = getSelectedItem();
                JSONObject td = getTestData();

                File appDataDir = contentManager.getAppDataDir(selected.itemId);

                String result;
                try {
                    FileReader reader = new FileReader(new File(appDataDir, "date.txt"));
                    char[] buffer = new char[1024];
                    int len = reader.read(buffer);
                    reader.close();
                    assert new String(buffer).equals(td.toString());
                    result = String.format("Data read from AppData(%s): '%s'", selected.itemId, new String(buffer, 0, len));
                } catch (IOException e) {
                    e.printStackTrace();
                    result = e.toString();
                }
                uiLog(result);
            }
        });
        
        setButtonAction(R.id.button_list_downloads_new, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<DownloadItem> downloads = contentManager.getDownloads(DownloadState.NEW, DownloadState.INFO_LOADED);
                uiLog(itemIdList(downloads));
            }
        });

        setButtonAction(R.id.button_list_downloads_in_progress, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<DownloadItem> downloads = contentManager.getDownloads(DownloadState.IN_PROGRESS);
                List<String> progress = new ArrayList<>(downloads.size());
                for (DownloadItem downloadItem : downloads) {
                    double percent = downloadItem.getDownloadedSizeBytes()*100f/downloadItem.getEstimatedSizeBytes();
                    progress.add(String.format(Locale.ENGLISH, "<%s %.2f%%>", downloadItem.getItemId(), percent));
                }
                uiLog(progress);
            }
        });

        setButtonAction(R.id.button_list_downloads_paused, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<DownloadItem> downloads = contentManager.getDownloads(DownloadState.PAUSED);
                List<String> progress = new ArrayList<>(downloads.size());
                for (DownloadItem downloadItem : downloads) {
                    double percent = downloadItem.getDownloadedSizeBytes()*100f/downloadItem.getEstimatedSizeBytes();
                    progress.add(String.format(Locale.ENGLISH, "<%s %.2f%%>", downloadItem.getItemId(), percent));
                }
                uiLog(progress);
            }
        });

        setButtonAction(R.id.button_list_downloads_done, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<DownloadItem> downloads = contentManager.getDownloads(DownloadState.NEW, DownloadState.COMPLETED);
                uiLog(itemIdList(downloads));
            }
        });

        setButtonAction(R.id.button_clear_log, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText log = (EditText) findViewById(R.id.text_log);
                assert log != null;
                log.setText("");
            }
        });
        
        setButtonAction(R.id.button_do_action, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText = (EditText) findViewById(R.id.custom_action);
                String action = editText.getText().toString();
                doCustomAction(action);
            }
        });
    }


    private void doCustomAction(String action) {
        String itemId = getSelectedItem().itemId;
        DownloadItem item = ContentManager.getInstance(this).findItem(itemId);

        DownloadItem.TrackSelector trackSelector = item.getTrackSelector();
        List<DownloadItem.Track> downloadedTracks = trackSelector.getDownloadedTracks(DownloadItem.TrackType.AUDIO);
        Log.d(TAG, "downloadedTracks=" + downloadedTracks);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy()");
        
        ContentManager.getInstance(this).stop();
    }
}
