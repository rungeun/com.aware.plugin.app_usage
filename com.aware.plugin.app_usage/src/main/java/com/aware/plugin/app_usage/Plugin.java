package com.aware.plugin.app_usage;

import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import android.accounts.Account;

import com.aware.Applications;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * App Usage Tracking Plugin using UsageStatsManager
 * Accurately tracks app start/end times with proper filtering and session management
 */
public class Plugin extends Aware_Plugin {

    private static final String TAG = "AWARE::App Usage";
    
    // Broadcast actions
    public static final String ACTION_AWARE_PLUGIN_APP_USAGE = "ACTION_AWARE_PLUGIN_APP_USAGE";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_APPLICATION_NAME = "application_name";
    public static final String EXTRA_IS_SYSTEM_APP = "is_system_app";
    public static final String EXTRA_APP_USAGE = "app_usage";

    // Internal action for alarm receiver
    private static final String ACTION_CHECK_APP_USAGE = "com.aware.plugin.app_usage.CHECK_USAGE";

    // SharedPreferences
    private static final String PREFS_NAME = "AppUsagePlugin";
    private static final String PREF_LAST_CHECK_TIME = "last_check_time";

    // System services
    private UsageStatsManager usageStatsManager;
    private PackageManager packageManager;
    private AlarmManager alarmManager;
    
    // Session management
    private AppUsageSessionManager sessionManager;
    
    // Configuration
    private long checkInterval = 10 * 1000; // 10 seconds default for better app switching detection
    
    // Screen state receiver
    private ScreenStateReceiver screenStateReceiver;


    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Provider.getAuthority(this);
        
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        packageManager = getPackageManager();
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        
        sessionManager = new AppUsageSessionManager(this);
        
        // Register screen state receiver
        screenStateReceiver = new ScreenStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, filter);
        
        Log.d(TAG, "App Usage Plugin created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
        
        // Check if plugin is enabled
        if (Aware.getSetting(getApplicationContext(), com.aware.plugin.app_usage.Settings.STATUS_PLUGIN_APP_USAGE).length() == 0) {
            Aware.setSetting(getApplicationContext(), com.aware.plugin.app_usage.Settings.STATUS_PLUGIN_APP_USAGE, true);
        } else {
            if (Aware.getSetting(getApplicationContext(), com.aware.plugin.app_usage.Settings.STATUS_PLUGIN_APP_USAGE).equalsIgnoreCase("false")) {
                Log.d(TAG, "Plugin disabled in settings");
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        // Check usage stats permission
        if (!hasUsageStatsPermission()) {
            Log.e(TAG, "Usage stats permission not granted! Plugin will not function properly.");
            Log.e(TAG, "Please grant usage stats permission manually through Settings.");
            // Don't automatically open settings - let user handle this manually
            return START_STICKY;
        }

        // Configure check interval
        configureCheckInterval();
        
        // Setup AWARE sync if in study mode
        setupAwareSync();
        
        // Get last check time from intent or preferences
        long lastCheckTime = getLastCheckTime(intent);
        
        // Start monitoring
        startPeriodicChecks();
        checkAppUsage(lastCheckTime);
        
        // Save initial filter settings to database
        try {
            com.aware.plugin.app_usage.Settings.saveFilterSettingsToDatabase(this);
            Log.d(TAG, "Initial filter settings saved on plugin start");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save initial filter settings: " + e.getMessage(), e);
        }
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Finalize all active sessions
        if (sessionManager != null) {
            sessionManager.finalizeAllActiveSessions();
        }
        
        // Unregister receivers
        if (screenStateReceiver != null) {
            unregisterReceiver(screenStateReceiver);
        }
        
        // Cancel alarms
        cancelPeriodicChecks();
        
        // Disable sync
        disableAwareSync();
        
        Aware.setSetting(this, com.aware.plugin.app_usage.Settings.STATUS_PLUGIN_APP_USAGE, false);
        
        Log.d(TAG, "App Usage Plugin destroyed");
    }

    /**
     * Check if app has usage stats permission
     */
    private boolean hasUsageStatsPermission() {
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(getPackageName(), 0);
            AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    applicationInfo.uid, applicationInfo.packageName);
            return (mode == AppOpsManager.MODE_ALLOWED);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Request usage stats permission
     */
    private void requestUsageStatsPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * Configure check interval from settings
     */
    private void configureCheckInterval() {
        String freq = Aware.getSetting(this, com.aware.plugin.app_usage.Settings.FREQUENCY_PLUGIN_APP_USAGE);
        if (freq.length() > 0) {
            try {
                // Convert minutes to milliseconds, minimum 10 seconds for accurate detection
                checkInterval = Math.max(10000, Long.parseLong(freq) * 60 * 1000);
                Log.d(TAG, "Check interval set to " + (checkInterval / 1000) + " seconds");
            } catch (NumberFormatException e) {
                checkInterval = 10 * 1000;
            }
        }
    }

    /**
     * Setup AWARE synchronization
     */
    private void setupAwareSync() {
        if (Aware.isStudy(this)) {
            Account aware_account = Aware.getAWAREAccount(getApplicationContext());
            String authority = Provider.getAuthority(getApplicationContext());
            
            long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;
            
            ContentResolver.setIsSyncable(aware_account, authority, 1);
            ContentResolver.setSyncAutomatically(aware_account, authority, true);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(aware_account, authority)
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            } else {
                ContentResolver.addPeriodicSync(aware_account, authority, Bundle.EMPTY, frequency);
            }
            
            Log.d(TAG, "AWARE sync configured with frequency: " + (frequency/60) + " minutes");
        }
    }

    /**
     * Get last check time from intent or preferences
     */
    private long getLastCheckTime(Intent intent) {
        long lastCheckTime = 0;
        
        if (intent != null && intent.hasExtra("last_check_time")) {
            lastCheckTime = intent.getLongExtra("last_check_time", 0);
        }
        
        if (lastCheckTime == 0) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            lastCheckTime = prefs.getLong(PREF_LAST_CHECK_TIME, 0);
            
            if (lastCheckTime == 0) {
                // First run: collect last 5 minutes
                lastCheckTime = System.currentTimeMillis() - (5 * 60 * 1000);
            } else {
                // Limit to last hour to avoid processing too much data
                long maxPeriod = System.currentTimeMillis() - (60 * 60 * 1000);
                if (lastCheckTime < maxPeriod) {
                    lastCheckTime = maxPeriod;
                }
            }
        }
        
        return lastCheckTime;
    }

    /**
     * Start periodic usage checks using AlarmManager
     */
    private void startPeriodicChecks() {
        Intent alarmIntent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(pendingIntent);

        long triggerTime = SystemClock.elapsedRealtime() + checkInterval;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
            );
        }

        Log.d(TAG, "Next check scheduled in " + (checkInterval / 1000) + " seconds");
    }

    /**
     * Cancel periodic checks
     */
    private void cancelPeriodicChecks() {
        Intent alarmIntent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }

    /**
     * Disable AWARE sync
     */
    private void disableAwareSync() {
        if (Aware.isStudy(this)) {
            ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this),
                    Provider.getAuthority(this), false);
            ContentResolver.removePeriodicSync(
                    Aware.getAWAREAccount(this),
                    Provider.getAuthority(this),
                    Bundle.EMPTY
            );
        }
    }

    /**
     * Main method to check app usage using UsageStatsManager
     */
    private void checkAppUsage(long fromTime) {
        if (!hasUsageStatsPermission()) {
            Log.e(TAG, "No usage stats permission!");
            return;
        }

        long currentTime = System.currentTimeMillis();
        
        // Skip if time interval is too short
        if (currentTime - fromTime < 1000) {
            return;
        }

        Log.d(TAG, "Checking app usage from " + new Date(fromTime) + " to " + new Date(currentTime));

        // Query usage events
        UsageEvents usageEvents = usageStatsManager.queryEvents(fromTime, currentTime);
        if (usageEvents == null) {
            Log.e(TAG, "UsageEvents is null!");
            return;
        }

        // Process events
        List<UsageEventData> events = extractUsageEvents(usageEvents);
        sessionManager.processEvents(events);

        // Save last check time
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putLong(PREF_LAST_CHECK_TIME, currentTime).apply();

        // Schedule next check
        startPeriodicChecks();
    }

    /**
     * Extract relevant usage events from UsageEvents
     */
    private List<UsageEventData> extractUsageEvents(UsageEvents usageEvents) {
        List<UsageEventData> events = new ArrayList<>();
        UsageEvents.Event event = new UsageEvents.Event();
        int totalEvents = 0;
        int filteredEvents = 0;

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            totalEvents++;

            String packageName = event.getPackageName();
            
            // Debug: log ALL events to see what's happening
            if (totalEvents <= 25) { // Limit to first 25 events to avoid spam
                Log.d(TAG, "Event #" + totalEvents + ": pkg=" + packageName + " type=" + event.getEventType() + " time=" + new java.util.Date(event.getTimeStamp()));
            }
            
            // Debug: log keyboard events specifically
            if (packageName != null && packageName.contains("honeyboard")) {
                Log.d(TAG, "KEYBOARD EVENT FOUND: " + packageName + " type=" + event.getEventType() + " time=" + new java.util.Date(event.getTimeStamp()));
            }
            
            if (packageName == null || !isRelevantEventType(event.getEventType())) {
                if (packageName != null && packageName.contains("honeyboard")) {
                    Log.d(TAG, "KEYBOARD EVENT FILTERED - null packageName or irrelevant event type: " + event.getEventType());
                }
                continue;
            }

            // Skip if app is blacklisted by user
            if (isAppBlacklisted(packageName)) {
                if (packageName.contains("honeyboard")) {
                    Log.d(TAG, "Keyboard event filtered - blacklisted");
                }
                continue;
            }

            // Get app info and create event data
            UsageEventData eventData = createUsageEventData(event);
            if (eventData != null) {
                events.add(eventData);
                filteredEvents++;
                
                if (packageName.contains("honeyboard")) {
                    Log.d(TAG, "Keyboard event processed successfully: " + packageName);
                }
            } else {
                if (packageName.contains("honeyboard")) {
                    Log.d(TAG, "Keyboard event filtered - createUsageEventData returned null");
                }
            }
        }

        Log.d(TAG, "Event extraction: " + totalEvents + " total, " + filteredEvents + " processed");
        return events;
    }

    /**
     * Create UsageEventData from UsageEvents.Event
     */
    private UsageEventData createUsageEventData(UsageEvents.Event event) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(event.getPackageName(), 0);
            boolean isSystemApp = isSystemApp(appInfo);
            String appName = packageManager.getApplicationLabel(appInfo).toString();

            return new UsageEventData(
                    event.getPackageName(),
                    appName,
                    isSystemApp,
                    event.getEventType(),
                    event.getTimeStamp()
            );
        } catch (PackageManager.NameNotFoundException e) {
            // Package not found, skip
            return null;
        }
    }


    /**
     * Check if application is a system app
     */
    private boolean isSystemApp(ApplicationInfo appInfo) {
        boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean isUpdatedSystemApp = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

        // Updated system apps are usually user apps
        if (isUpdatedSystemApp) {
            return false;
        }

        // Apps installed in /data/app are user apps
        if (appInfo.sourceDir != null && appInfo.sourceDir.startsWith("/data/app")) {
            return false;
        }

        return isSystem;
    }

    /**
     * Check if app should be excluded from tracking based on filter mode
     */
    private boolean isAppBlacklisted(String packageName) {
        // Get current filter mode and app list
        boolean isWhitelistMode = com.aware.plugin.app_usage.Settings.isWhitelistMode(this);
        Set<String> appList = com.aware.plugin.app_usage.Settings.getAppList(this);
        
        if (isWhitelistMode) {
            // In whitelist mode: exclude apps NOT in the list
            return appList == null || !appList.contains(packageName);
        } else {
            // In blacklist mode: exclude apps IN the list
            return appList != null && appList.contains(packageName);
        }
    }

    /**
     * Check if event type is relevant for session tracking
     */
    private boolean isRelevantEventType(int eventType) {
        return eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                eventType == 23 || // ACTIVITY_RESUMED
                eventType == 2 ||  // ACTIVITY_PAUSED
                eventType == 15 || // SCREEN_INTERACTIVE
                eventType == 16;   // SCREEN_NON_INTERACTIVE
    }

    /**
     * Usage event data structure
     */
    public static class UsageEventData {
        public final String packageName;
        public final String appName;
        public final boolean isSystemApp;
        public final int eventType;
        public final long timestamp;

        public UsageEventData(String packageName, String appName, boolean isSystemApp, 
                             int eventType, long timestamp) {
            this.packageName = packageName;
            this.appName = appName;
            this.isSystemApp = isSystemApp;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
    }

    /**
     * Screen state receiver to handle screen on/off events
     */
    private class ScreenStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                Log.d(TAG, "Screen turned off - finalizing all sessions");
                if (sessionManager != null) {
                    sessionManager.handleScreenOff();
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                Log.d(TAG, "Screen turned on");
                if (sessionManager != null) {
                    sessionManager.handleScreenOn();
                }
            }
        }
    }

    /**
     * Alarm receiver for periodic checks
     */
    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Alarm triggered - starting periodic check");

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long lastCheckTime = prefs.getLong(PREF_LAST_CHECK_TIME, 
                    System.currentTimeMillis() - (5 * 60 * 1000));

            Intent serviceIntent = new Intent(context, Plugin.class);
            serviceIntent.putExtra("last_check_time", lastCheckTime);
            context.startService(serviceIntent);
        }
    }

    /**
     * Save app usage session to database
     */
    public void saveAppUsageSession(String packageName, String appName, boolean isSystemApp, 
                                   long startTime, long endTime) {
        long duration = endTime - startTime;
        
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

        ContentValues values = new ContentValues();
        values.put(Provider.AppUsage_Data.TIMESTAMP, System.currentTimeMillis());
        values.put(Provider.AppUsage_Data.DEVICE_ID,
                Aware.getSetting(this, Aware_Preferences.DEVICE_ID));
        values.put(Provider.AppUsage_Data.PACKAGE_NAME, packageName);
        values.put(Provider.AppUsage_Data.CATEGORY, "not_registered");
        values.put(Provider.AppUsage_Data.APPLICATION_NAME, appName);
        values.put(Provider.AppUsage_Data.IS_SYSTEM_APP, isSystemApp ? 1 : 0);
        values.put(Provider.AppUsage_Data.APP_ON, isoFormat.format(new Date(startTime)));
        values.put(Provider.AppUsage_Data.APP_OFF, isoFormat.format(new Date(endTime)));
        values.put(Provider.AppUsage_Data.APP_USAGE, duration / 1000); // seconds

        try {
            getContentResolver().insert(Provider.AppUsage_Data.CONTENT_URI, values);

            Log.d(TAG, String.format("Session saved: %s (%s ~ %s, %d seconds)",
                    appName,
                    isoFormat.format(new Date(startTime)),
                    isoFormat.format(new Date(endTime)),
                    duration / 1000));

            // Send broadcast
            Intent broadcast = new Intent(ACTION_AWARE_PLUGIN_APP_USAGE);
            broadcast.putExtra(EXTRA_PACKAGE_NAME, packageName);
            broadcast.putExtra(EXTRA_APPLICATION_NAME, appName);
            broadcast.putExtra(EXTRA_IS_SYSTEM_APP, isSystemApp);
            broadcast.putExtra(EXTRA_APP_USAGE, duration);
            sendBroadcast(broadcast);

        } catch (Exception e) {
            Log.e(TAG, "Error saving session: " + e.getMessage());
        }
    }

}