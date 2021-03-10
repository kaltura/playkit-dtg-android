package com.kaltura.dtg;

import java.util.concurrent.Future;

/**
 * Class to hold future object and its state (cancelled/not cancelled)
 * For metaDataDownloadExecutorService in DownloadService
 */
class MetaDataFutureMap {
    /**
    Future Object of the queued metadata download request
     */
    private Future future;
    /**
    Cancelled state of future object
     */
    private boolean isCancelled;

    public MetaDataFutureMap(Future future, boolean isCancelled) {
        this.future = future;
        this.isCancelled = isCancelled;
    }

    public void setCancelled(boolean cancelled) {
        isCancelled = cancelled;
    }

    public Future getFuture() {
        return future;
    }

    public boolean isCancelled() {
        return isCancelled;
    }
}
