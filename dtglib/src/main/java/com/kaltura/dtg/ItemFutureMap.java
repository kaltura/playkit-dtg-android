package com.kaltura.dtg;

import androidx.annotation.NonNull;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Future;

class ItemFutureMap {
    private static final String TAG = "ItemFutureMap";
    private final Map<String, Set<Future>> map;

    ItemFutureMap() {
        map = new HashMap<>();
    }

    synchronized void add(@NonNull String itemId, @NonNull Future future) {
        Set<Future> futureList = map.get(itemId);
        if (futureList == null) {
            futureList = Collections.newSetFromMap(new WeakHashMap<Future, Boolean>());
            map.put(itemId, futureList);
        }
        futureList.add(future);
    }

    synchronized void cancelItem(@NonNull String itemId) {
        Set<Future> futureList = map.get(itemId);
        int count = futureList == null ? 0 : futureList.size();
        Log.d(TAG, "cancelItem: " + itemId + "; count=" + count);
        if (count > 0) {
            futureList = new LinkedHashSet<>(futureList);   // iterate over a copied list
            for (Future future : futureList) {
//                Log.d(TAG, "cancelItem: " + itemId + ": " + System.identityHashCode(future));
                future.cancel(true);
            }
        }
    }

    synchronized void remove(String itemId, Future future) {
        Set<Future> futureList = map.get(itemId);
        if (futureList != null) {
            futureList.remove(future);
            if (futureList.isEmpty()) {
                map.remove(itemId);
            }
        }
    }

    synchronized void cancelAll() {
        for (String itemId : new HashSet<>(map.keySet())) {
            cancelItem(itemId);
        }
    }
}
