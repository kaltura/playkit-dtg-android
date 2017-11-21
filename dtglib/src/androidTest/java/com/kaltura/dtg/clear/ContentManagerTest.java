package com.kaltura.dtg.clear;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.text.TextUtils;
import android.util.Log;

import com.kaltura.dtg.ContentManager;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadStateListener;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by Noam Tamim @ Kaltura on 19/10/2017.
 */

public class ContentManagerTest {
    private static final String TAG = "ContentManagerTest";
    private Context context;
    private long estimatedSizeBytes;
    private long downloadedBytes;
    private float metadataLoadingStartTime;
    private float metadataLoadingEndTime;
    private float downloadStartedTime;
    private float downloadFinishedTime;
    private Exception error;
    private float downloadSpeed;
    private float estimatedTimeLeft;

    private float bytesToMB(long bytes) {
        return bytes / 1024f / 1024f;
    }
    
    private float now() {
        return System.nanoTime() / 1000000000f;
    }
    
    @Before
    public void setUp() throws Exception {
        this.context = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void download() throws Exception {
        test("t1", "http://cdnapi.kaltura.com/p/2215841/playManifest/entryId/1_9bwuo813/format/mpegdash/protocol/http/a.mpd");
//        test("t2", "http://cdnapi.kaltura.com/p/2215841/playManifest/entryId/1_w9zx2eti/format/mpegdash/protocol/http/a.mpd");
        test("t3", "http://cdnapi.kaltura.com/p/1758922/playManifest/entryId/0_ksthpwh8/format/mpegdash/protocol/http/a.mpd");
    }

    private void test(String itemId, String contentURL) throws Exception {
        
        DefaultDownloadService service = new DefaultDownloadService(context);
        service.setDownloadSettings(new ContentManager.Settings());
        service.start();

        final CountDownLatch latch = new CountDownLatch(1);
        
        service.setDownloadStateListener(new DownloadStateListener() {
            @Override
            public void onDownloadComplete(DownloadItem item) {
                downloadFinishedTime = now();
                latch.countDown();
            }

            @Override
            public void onProgressChange(DownloadItem item, long downloadedBytes) {
                Log.d(TAG, "onProgressChange:" + downloadedBytes);
                ContentManagerTest.this.downloadedBytes = downloadedBytes;
                downloadSpeed = downloadedBytes / 1024 / 1024 / (now() - downloadStartedTime);
                estimatedTimeLeft = (estimatedSizeBytes-downloadedBytes) / (downloadSpeed * 1024 * 1024);
                Log.i(TAG, String.format("Progress: %.3f%%; Speed: %.3f mbytes/sec; Time left: %.3f sec", 100f*downloadedBytes/estimatedSizeBytes, downloadSpeed, estimatedTimeLeft));
            }

            @Override
            public void onDownloadStart(DownloadItem item) {
                downloadStartedTime = now();
            }

            @Override
            public void onDownloadPause(DownloadItem item) {

            }

            @Override
            public void onDownloadFailure(DownloadItem item, Exception error) {
                ContentManagerTest.this.error = error;
                latch.countDown();
            }

            @Override
            public void onDownloadMetadata(DownloadItem item, Exception error) {
                metadataLoadingEndTime = now();
                estimatedSizeBytes = item.getEstimatedSizeBytes();
                item.startDownload();
            }

            @Override
            public void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector) {

            }
        });

        DefaultDownloadItem item = service.findItem(itemId);
        if (item != null) {
            service.removeItem(item);
        }
        item = service.createItem(itemId, contentURL);
        
        metadataLoadingStartTime = now();
        item.loadMetadata();


        
        try {
            if (!latch.await(10, TimeUnit.MINUTES)) {
                Log.e(TAG, "Download has timed out");
            }
        } catch (InterruptedException e) {
            error = e;
            e.printStackTrace();
        }

        Assert.assertNull("Download has failed", error);
        
        Assert.assertEquals(downloadedBytes/1024/1024/(downloadFinishedTime - downloadStartedTime), downloadSpeed, 1);
        
        // Report
        String[] report = {
                String.format("Metadata loading time: %.3f sec", (metadataLoadingEndTime - metadataLoadingStartTime)),
                String.format("Download time: %.3f sec", (downloadFinishedTime - downloadStartedTime)),
                String.format("Estimated size: %.3f mbytes", bytesToMB(estimatedSizeBytes)),
                String.format("Actual size: %.3f mbytes", bytesToMB(downloadedBytes)),
                String.format("Actual/estimated relative delta: %.3f", 100f*(downloadedBytes-estimatedSizeBytes)/estimatedSizeBytes),
                String.format("Download speed: %.3f mbytes/sec", downloadSpeed)
        };
        
        Log.i(TAG, TextUtils.join("\n", report));

        service.stop();
    }
}
