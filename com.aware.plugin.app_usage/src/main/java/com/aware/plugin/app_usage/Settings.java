package com.aware.plugin.app_usage;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ui.AppCompatPreferenceActivity;

import java.util.HashSet;
import java.util.Set;

public class Settings extends AppCompatPreferenceActivity implements OnSharedPreferenceChangeListener {

    /**
     * State of this plugin
     */
    public static final String STATUS_PLUGIN_APP_USAGE = "status_plugin_app_usage";

    /**
     * Update frequency
     */
    public static final String FREQUENCY_PLUGIN_APP_USAGE = "plugin_app_usage_frequency";

    /**
     * App filter mode (blacklist or whitelist)
     */
    public static final String APP_FILTER_MODE = "app_filter_mode";

    /**
     * App list setting key for AWARE configuration (JSON)
     */
    public static final String APP_LIST_SETTING = "app_list";
    
    /**
     * App list preference key (used for both blacklist and whitelist)
     */
    public static final String APP_LIST_PREF = "app_list";

    /**
     * SharedPreferences name
     */
    private static final String PREFS_NAME = "AppUsagePlugin";

    /**
     * 디바이스별 SharedPreferences 이름 생성
     */
    private static String getDeviceSpecificPrefsName(Context context) {
        String deviceId = Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
        if (deviceId == null || deviceId.isEmpty()) {
            return PREFS_NAME; // fallback to default
        }
        return PREFS_NAME + "_" + deviceId;
    }

    private static CheckBoxPreference status;
    private static EditTextPreference frequency;
    private static ListPreference appFilterMode;

    // 디바이스별 앱 리스트에 앱 추가
    public static void addToAppList(Context context, String packageName) {
        String prefsName = getDeviceSpecificPrefsName(context);
        SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        Set<String> appList = prefs.getStringSet(APP_LIST_PREF, new HashSet<String>());
        Set<String> newAppList = new HashSet<>(appList); // 복사본 생성 (Android 버그 회피)
        newAppList.add(packageName);
        prefs.edit().putStringSet(APP_LIST_PREF, newAppList).apply();
        android.util.Log.d("AppUsage", "Added to device-specific app list: " + packageName);
    }

    // 디바이스별 앱 리스트에서 앱 제거
    public static void removeFromAppList(Context context, String packageName) {
        String prefsName = getDeviceSpecificPrefsName(context);
        SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        Set<String> appList = prefs.getStringSet(APP_LIST_PREF, new HashSet<String>());
        Set<String> newAppList = new HashSet<>(appList); // 복사본 생성 (Android 버그 회피)
        newAppList.remove(packageName);
        prefs.edit().putStringSet(APP_LIST_PREF, newAppList).apply();
        android.util.Log.d("AppUsage", "Removed from device-specific app list: " + packageName);
    }

    // 디바이스별 앱 리스트 가져오기
    public static Set<String> getAppList(Context context) {
        String prefsName = getDeviceSpecificPrefsName(context);
        SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        return prefs.getStringSet(APP_LIST_PREF, new HashSet<String>());
    }

    // 현재 필터 모드 가져오기 (blacklist 또는 whitelist)
    public static String getFilterMode(Context context) {
        return Aware.getSetting(context, APP_FILTER_MODE);
    }

    // 필터 모드가 블랙리스트인지 확인
    public static boolean isBlacklistMode(Context context) {
        return "blacklist".equals(getFilterMode(context));
    }

    // 필터 모드가 화이트리스트인지 확인
    public static boolean isWhitelistMode(Context context) {
        return "whitelist".equals(getFilterMode(context));
    }
    
    /**
     * SQLite에서 마지막 저장된 필터 설정 조회
     */
    private static android.database.Cursor getLastFilterSettings(Context context, String deviceId) {
        return context.getContentResolver().query(
            Provider.AppFilterSettings_Data.CONTENT_URI,
            new String[] {
                Provider.AppFilterSettings_Data.FILTER_MODE,
                Provider.AppFilterSettings_Data.APP_LIST
            },
            Provider.AppFilterSettings_Data.DEVICE_ID + "=?",
            new String[] { deviceId },
            Provider.AppFilterSettings_Data.TIMESTAMP + " DESC LIMIT 1"
        );
    }
    
    /**
     * 필터 설정 변경 사항을 데이터베이스에 저장 (변경사항이 있을 때만)
     */
    public static void saveFilterSettingsToDatabase(Context context) {
        android.util.Log.d("AppUsage", "saveFilterSettingsToDatabase called");
        
        String deviceId = getDeviceId(context);
        if (deviceId == null) {
            return;
        }
        
        FilterSettings currentSettings = getCurrentFilterSettings(context);
        FilterSettings lastSettings = getLastStoredFilterSettings(context, deviceId);
        
        if (!hasFilterSettingsChanged(currentSettings, lastSettings)) {
            android.util.Log.d("AppUsage", "No changes in filter settings, skipping database save");
            return;
        }
        
        insertFilterSettingsToDatabase(context, deviceId, currentSettings);
    }

    /**
     * Get device ID with validation
     */
    private static String getDeviceId(Context context) {
        String deviceId = Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
        if (deviceId == null || deviceId.isEmpty()) {
            android.util.Log.w("AppUsage", "No device ID found, skipping filter settings save");
            return null;
        }
        return deviceId;
    }

    /**
     * Get current filter settings
     */
    private static FilterSettings getCurrentFilterSettings(Context context) {
        String filterMode = getFilterMode(context);
        if (filterMode == null || filterMode.isEmpty()) {
            filterMode = "blacklist";
        }
        
        String appListString = getAppListAsString(context);
        Set<String> appList = getAppList(context);
        int appCount = appList != null ? appList.size() : 0;
        
        return new FilterSettings(filterMode, appListString, appCount);
    }

    /**
     * Get last stored filter settings from database
     */
    private static FilterSettings getLastStoredFilterSettings(Context context, String deviceId) {
        android.database.Cursor cursor = getLastFilterSettings(context, deviceId);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int filterModeIndex = cursor.getColumnIndex(Provider.AppFilterSettings_Data.FILTER_MODE);
                    int appListIndex = cursor.getColumnIndex(Provider.AppFilterSettings_Data.APP_LIST);
                    
                    String filterMode = (filterModeIndex >= 0) ? cursor.getString(filterModeIndex) : "";
                    String appList = (appListIndex >= 0) ? cursor.getString(appListIndex) : "";
                    
                    if (filterMode == null) filterMode = "";
                    if (appList == null) appList = "";
                    
                    return new FilterSettings(filterMode, appList, 0);
                }
            } finally {
                cursor.close();
            }
        }
        return new FilterSettings("", "", 0);
    }

    /**
     * Check if filter settings have changed
     */
    private static boolean hasFilterSettingsChanged(FilterSettings current, FilterSettings last) {
        boolean filterModeChanged = !current.filterMode.equals(last.filterMode);
        boolean appListChanged = !current.appList.equals(last.appList);
        
        if (filterModeChanged) {
            android.util.Log.d("AppUsage", "Filter mode changed: " + last.filterMode + " -> " + current.filterMode);
        }
        if (appListChanged) {
            android.util.Log.d("AppUsage", "App list changed: " + last.appList + " -> " + current.appList);
        }
        
        return filterModeChanged || appListChanged;
    }

    /**
     * Insert filter settings to database
     */
    private static void insertFilterSettingsToDatabase(Context context, String deviceId, FilterSettings settings) {
        android.util.Log.d("AppUsage", "Filter settings data: mode=" + settings.filterMode + 
                           ", appCount=" + settings.appCount + ", appList=" + settings.appList);
        
        ContentValues values = new ContentValues();
        values.put(Provider.AppFilterSettings_Data.TIMESTAMP, System.currentTimeMillis());
        values.put(Provider.AppFilterSettings_Data.DEVICE_ID, deviceId);
        values.put(Provider.AppFilterSettings_Data.FILTER_MODE, settings.filterMode);
        values.put(Provider.AppFilterSettings_Data.APP_LIST, settings.appList);
        values.put(Provider.AppFilterSettings_Data.APP_COUNT, settings.appCount);
        values.put(Provider.AppFilterSettings_Data.LAST_MODIFIED, System.currentTimeMillis());
        
        android.util.Log.d("AppUsage", "Attempting to insert filter settings into database");
        
        try {
            android.net.Uri result = context.getContentResolver().insert(
                    Provider.AppFilterSettings_Data.CONTENT_URI, values);
            
            if (result != null) {
                android.util.Log.i("AppUsage", "Filter settings successfully inserted into database: " + result);
                android.util.Log.i("AppUsage", "Filter mode: " + settings.filterMode + ", App count: " + settings.appCount);
            } else {
                android.util.Log.e("AppUsage", "Failed to insert filter settings - insert returned null");
            }
        } catch (Exception e) {
            android.util.Log.e("AppUsage", "Exception inserting filter settings: " + e.getMessage(), e);
        }
    }

    /**
     * Filter settings data class
     */
    private static class FilterSettings {
        final String filterMode;
        final String appList;
        final int appCount;
        
        FilterSettings(String filterMode, String appList, int appCount) {
            this.filterMode = filterMode;
            this.appList = appList;
            this.appCount = appCount;
        }
    }
    
    // JSON 설정에서 쉼표로 구분된 앱 리스트를 디바이스별 SharedPreferences에 저장 (설치된 앱만)
    public static void setAppListFromString(Context context, String appListString) {
        if (appListString == null || appListString.trim().isEmpty()) {
            return;
        }
        
        String prefsName = getDeviceSpecificPrefsName(context);
        SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        Set<String> appSet = new HashSet<>();
        PackageManager pm = context.getPackageManager();
        
        int totalApps = 0;
        int addedApps = 0;
        int ignoredApps = 0;
        
        // 쉼표로 분리하고 공백 제거
        String[] apps = appListString.split(",");
        for (String app : apps) {
            String trimmedApp = app.trim();
            if (!trimmedApp.isEmpty()) {
                totalApps++;
                // 앱이 실제로 설치되어 있는지 확인
                if (isAppInstalled(pm, trimmedApp)) {
                    appSet.add(trimmedApp);
                    addedApps++;
                    android.util.Log.d("AppUsage", "Added to app list: " + trimmedApp);
                } else {
                    // 설치되지 않은 앱은 로그로 알림 (무시)
                    ignoredApps++;
                    android.util.Log.w("AppUsage", "App not installed, ignoring: " + trimmedApp);
                }
            }
        }
        
        prefs.edit().putStringSet(APP_LIST_PREF, appSet).apply();
        
        // 결과 요약 로그
        android.util.Log.i("AppUsage", String.format("App list loaded from config: %d total, %d added, %d ignored", 
                                                      totalApps, addedApps, ignoredApps));
    }
    
    // 앱이 설치되어 있는지 확인하는 헬퍼 메서드
    private static boolean isAppInstalled(PackageManager pm, String packageName) {
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    
    // SharedPreferences의 앱 리스트를 쉼표로 구분된 문자열로 반환
    public static String getAppListAsString(Context context) {
        Set<String> appList = getAppList(context);
        if (appList == null || appList.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String app : appList) {
            if (!first) {
                sb.append(",");
            }
            sb.append(app);
            first = false;
        }
        return sb.toString();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_app_usage);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Sync button
        findPreference("app_usage_sync").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // 수동 동기화 요청
                Intent sync = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
                sync.putExtra("PROVIDER_AUTHORITY", Provider.getAuthority(getApplicationContext()));
                sendBroadcast(sync);

                Account account = Aware.getAWAREAccount(getApplicationContext());
                if (account != null) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                    bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);

                    ContentResolver.requestSync(account, Provider.getAuthority(getApplicationContext()), bundle);

                    Toast.makeText(getApplicationContext(), "Sync requested", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "No AWARE account found", Toast.LENGTH_SHORT).show();
                }

                return true;
            }
        });

        // Permission button
        findPreference("app_usage_permission").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // 전체 패키지명 사용
                Intent intent = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
                startActivity(intent);
                return true;
            }
        });

        // App list management button
        findPreference("manage_app_list").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Settings.this, AppBlacklistActivity.class);
                startActivity(intent);
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        status = (CheckBoxPreference) findPreference(STATUS_PLUGIN_APP_USAGE);
        if (Aware.getSetting(this, STATUS_PLUGIN_APP_USAGE).length() == 0) {
            Aware.setSetting(this, STATUS_PLUGIN_APP_USAGE, true);
        }
        status.setChecked(Aware.getSetting(getApplicationContext(), STATUS_PLUGIN_APP_USAGE).equals("true"));

        frequency = (EditTextPreference) findPreference(FREQUENCY_PLUGIN_APP_USAGE);
        if (Aware.getSetting(this, FREQUENCY_PLUGIN_APP_USAGE).length() == 0) {
            Aware.setSetting(this, FREQUENCY_PLUGIN_APP_USAGE, "1");
        }
        frequency.setSummary("Every " + Aware.getSetting(this, FREQUENCY_PLUGIN_APP_USAGE) + " minute(s)");

        appFilterMode = (ListPreference) findPreference(APP_FILTER_MODE);
        if (Aware.getSetting(this, APP_FILTER_MODE).length() == 0) {
            Aware.setSetting(this, APP_FILTER_MODE, "blacklist");
        }
        appFilterMode.setSummary(appFilterMode.getEntry());
        
        // JSON 설정에서 앱 리스트 로드 (설정이 있는 경우)
        String appListFromConfig = Aware.getSetting(this, APP_LIST_SETTING);
        if (appListFromConfig != null && !appListFromConfig.isEmpty()) {
            setAppListFromString(this, appListFromConfig);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference preference = findPreference(key);
        if (preference == null) {
            Log.w("AppUsage", "Preference not found for key: " + key);
            return;
        }

        if (preference.getKey().equals(STATUS_PLUGIN_APP_USAGE)) {
            Aware.setSetting(this, key, sharedPreferences.getBoolean(key, false));
            status.setChecked(sharedPreferences.getBoolean(key, false));

            try {
                if (Aware.getSetting(this, STATUS_PLUGIN_APP_USAGE).equals("true")) {
                    Aware.startPlugin(getApplicationContext(), "com.aware.plugin.app_usage");
                } else {
                    Aware.stopPlugin(getApplicationContext(), "com.aware.plugin.app_usage");
                }
            } catch (Exception e) {
                Log.e("AppUsage", "Error starting/stopping plugin: " + e.getMessage());
            }
        }

        if (preference.getKey().equals(FREQUENCY_PLUGIN_APP_USAGE)) {
            Aware.setSetting(this, key, sharedPreferences.getString(key, "5"));
            frequency.setSummary("Every " + Aware.getSetting(this, FREQUENCY_PLUGIN_APP_USAGE) + " minute(s)");

            // 플러그인 재시작하여 새 수집 주기 적용
            try {
                if (Aware.getSetting(this, STATUS_PLUGIN_APP_USAGE).equals("true")) {
                    Aware.stopPlugin(getApplicationContext(), "com.aware.plugin.app_usage");
                    Aware.startPlugin(getApplicationContext(), "com.aware.plugin.app_usage");
                }
            } catch (Exception e) {
                Log.e("AppUsage", "Error restarting plugin for frequency change: " + e.getMessage());
            }
        }

        if (preference.getKey().equals(APP_FILTER_MODE)) {
            Aware.setSetting(this, key, sharedPreferences.getString(key, "blacklist"));
            appFilterMode.setSummary(appFilterMode.getEntry());
            // 필터 모드 변경 시 데이터베이스에 저장
            saveFilterSettingsToDatabase(this);
        }
        
        if (preference.getKey().equals(APP_LIST_SETTING)) {
            String appListString = sharedPreferences.getString(key, "");
            Aware.setSetting(this, key, appListString);
            // JSON 설정에서 온 앱 리스트를 SharedPreferences에 저장
            setAppListFromString(this, appListString);
        }
    }
}