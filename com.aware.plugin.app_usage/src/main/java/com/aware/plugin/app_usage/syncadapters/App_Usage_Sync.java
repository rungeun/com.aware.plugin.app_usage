package com.aware.plugin.app_usage.syncadapters;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import com.aware.plugin.app_usage.Provider;
import com.aware.syncadapters.AwareSyncAdapter;

/**
 * Created by RunGeun on 21/07/2025.
 * Modified for app usage tracking
 */

public class App_Usage_Sync extends Service {
    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);
                sSyncAdapter.init(
                        Provider.DATABASE_TABLES, Provider.TABLES_FIELDS,
                        new Uri[]{
                                Provider.AppUsage_Data.CONTENT_URI,
                                Provider.AppFilterSettings_Data.CONTENT_URI
                        }
                );
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return sSyncAdapter.getSyncAdapterBinder(); }
}