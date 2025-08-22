/**
 * @author: RunGeun
 */
package com.aware.plugin.app_usage;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

public class Provider extends ContentProvider {

    /**
     * Authority of this content provider
     */
    public static String AUTHORITY = "com.aware.plugin.app_usage.provider.app_usage";

    /**
     * ContentProvider database version. Increment every time you modify the database structure
     */
    public static final int DATABASE_VERSION = 12;

    public static final class AppUsage_Data implements BaseColumns {
        private AppUsage_Data() {
        }

        /**
         * Your ContentProvider table content URI.<br/>
         * The last segment needs to match your database table name
         */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/plugin_app_usage");

        /**
         * How your data collection is identified internally in Android (vnd.android.cursor.dir). <br/>
         * It needs to be /vnd.aware.plugin.XXX where XXX is your plugin name (no spaces!).
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.plugin.app_usage";

        /**
         * How each row is identified individually internally in Android (vnd.android.cursor.item). <br/>
         * It needs to be /vnd.aware.plugin.XXX where XXX is your plugin name (no spaces!).
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.plugin.app_usage";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String PACKAGE_NAME = "package_name";
        public static final String CATEGORY = "category";
        public static final String APPLICATION_NAME = "application_name";
        public static final String IS_SYSTEM_APP = "is_system_app";
        public static final String APP_ON = "app_on";
        public static final String APP_OFF = "app_off";
        public static final String APP_USAGE = "app_usage";
    }
    
    /**
     * App Filter Settings table
     */
    public static final class AppFilterSettings_Data implements BaseColumns {
        private AppFilterSettings_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/plugin_app_filter_settings");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.plugin.app_filter_settings";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.plugin.app_filter_settings";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String FILTER_MODE = "filter_mode";
        public static final String APP_LIST = "app_list";
        public static final String APP_COUNT = "app_count";
        public static final String LAST_MODIFIED = "last_modified";
    }

    //ContentProvider query indexes
    private static final int APP_USAGE = 1;
    private static final int APP_USAGE_ID = 2;
    private static final int APP_FILTER_SETTINGS = 3;
    private static final int APP_FILTER_SETTINGS_ID = 4;

    /**
     * Database stored in external folder: /AWARE/plugin_app_usage.db
     */
    public static final String DATABASE_NAME = "plugin_app_usage.db";

    /**
     * Database tables:<br/>
     * - plugin_app_usage
     * - plugin_app_filter_settings
     */
    public static final String[] DATABASE_TABLES = {"plugin_app_usage", "plugin_app_filter_settings"};

    /**
     * Database table fields
     */
    public static final String[] TABLES_FIELDS = {
            AppUsage_Data._ID + " integer primary key autoincrement," +
                    AppUsage_Data.TIMESTAMP + " real default 0," +
                    AppUsage_Data.DEVICE_ID + " text default ''," +
                    AppUsage_Data.PACKAGE_NAME + " text default ''," +
                    AppUsage_Data.CATEGORY + " text default ''," +
                    AppUsage_Data.APPLICATION_NAME + " text default ''," +
                    AppUsage_Data.IS_SYSTEM_APP + " integer default 0," +
                    AppUsage_Data.APP_ON + " text default ''," +  // real에서 text로 변경
                    AppUsage_Data.APP_OFF + " text default ''," + // real에서 text로 변경
                    AppUsage_Data.APP_USAGE + " real default 0",
            
            // New table for filter settings
            AppFilterSettings_Data._ID + " integer primary key autoincrement," +
                    AppFilterSettings_Data.TIMESTAMP + " real default 0," +
                    AppFilterSettings_Data.DEVICE_ID + " text default ''," +
                    AppFilterSettings_Data.FILTER_MODE + " text default 'blacklist'," +
                    AppFilterSettings_Data.APP_LIST + " text default ''," +
                    AppFilterSettings_Data.APP_COUNT + " integer default 0," +
                    AppFilterSettings_Data.LAST_MODIFIED + " integer default 0"
    };

    private static UriMatcher sUriMatcher = null;
    private static HashMap<String, String> appUsageTableMap = null;
    private static HashMap<String, String> filterSettingsTableMap = null;
    private DatabaseHelper dbHelper;
    private static SQLiteDatabase database;

    /**
     * Returns the provider authority that is dynamic
     * @return
     */
    public static String getAuthority(Context context) {
        AUTHORITY = context.getPackageName() + ".provider.app_usage";
        return AUTHORITY;
    }

    private void initialiseDatabase() {
        if (dbHelper == null) {
            dbHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
        }
        if (database == null) {
            database = dbHelper.getWritableDatabase();
        }
    }

    @Override
    public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {
        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case APP_USAGE:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;
            case APP_FILTER_SETTINGS:
                count = database.delete(DATABASE_TABLES[1], selection, selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case APP_USAGE:
                return AppUsage_Data.CONTENT_TYPE;
            case APP_USAGE_ID:
                return AppUsage_Data.CONTENT_ITEM_TYPE;
            case APP_FILTER_SETTINGS:
                return AppFilterSettings_Data.CONTENT_TYPE;
            case APP_FILTER_SETTINGS_ID:
                return AppFilterSettings_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public synchronized Uri insert(Uri uri, ContentValues new_values) {
        initialiseDatabase();

        ContentValues values = (new_values != null) ? new ContentValues(new_values) : new ContentValues();

        database.beginTransaction();

        switch (sUriMatcher.match(uri)) {
            case APP_USAGE:
                long _id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        AppUsage_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (_id > 0) {
                    Uri dataUri = ContentUris.withAppendedId(
                            AppUsage_Data.CONTENT_URI, _id);
                    getContext().getContentResolver().notifyChange(dataUri, null, false);
                    Log.d(Aware.TAG, "App Usage Provider - Insert successful, ID: " + _id);
                    return dataUri;
                } else {
                    Log.e(Aware.TAG, "App Usage Provider - Insert failed, returned ID: " + _id);
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            case APP_FILTER_SETTINGS:
                Log.d(Aware.TAG, "Provider - Inserting filter settings data");
                Log.d(Aware.TAG, "Provider - Values: " + values.toString());
                long filter_id = database.insertWithOnConflict(DATABASE_TABLES[1],
                        AppFilterSettings_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (filter_id > 0) {
                    Uri dataUri = ContentUris.withAppendedId(
                            AppFilterSettings_Data.CONTENT_URI, filter_id);
                    getContext().getContentResolver().notifyChange(dataUri, null, false);
                    Log.d(Aware.TAG, "Provider - Filter settings insert successful, ID: " + filter_id);
                    return dataUri;
                } else {
                    Log.e(Aware.TAG, "Provider - Filter settings insert failed, returned ID: " + filter_id);
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public boolean onCreate() {

        AUTHORITY = getContext().getPackageName() + ".provider.app_usage";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[0], APP_USAGE); //URI for all records
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[0] + "/#", APP_USAGE_ID); //URI for a single record
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[1], APP_FILTER_SETTINGS); //URI for filter settings
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[1] + "/#", APP_FILTER_SETTINGS_ID); //URI for a single filter setting

        appUsageTableMap = new HashMap<String, String>();
        appUsageTableMap.put(AppUsage_Data._ID, AppUsage_Data._ID);
        appUsageTableMap.put(AppUsage_Data.TIMESTAMP, AppUsage_Data.TIMESTAMP);
        appUsageTableMap.put(AppUsage_Data.DEVICE_ID, AppUsage_Data.DEVICE_ID);
        appUsageTableMap.put(AppUsage_Data.PACKAGE_NAME, AppUsage_Data.PACKAGE_NAME);
        appUsageTableMap.put(AppUsage_Data.CATEGORY, AppUsage_Data.CATEGORY);
        appUsageTableMap.put(AppUsage_Data.APPLICATION_NAME, AppUsage_Data.APPLICATION_NAME);
        appUsageTableMap.put(AppUsage_Data.IS_SYSTEM_APP, AppUsage_Data.IS_SYSTEM_APP);
        appUsageTableMap.put(AppUsage_Data.APP_ON, AppUsage_Data.APP_ON);
        appUsageTableMap.put(AppUsage_Data.APP_OFF, AppUsage_Data.APP_OFF);
        appUsageTableMap.put(AppUsage_Data.APP_USAGE, AppUsage_Data.APP_USAGE);
        
        filterSettingsTableMap = new HashMap<String, String>();
        filterSettingsTableMap.put(AppFilterSettings_Data._ID, AppFilterSettings_Data._ID);
        filterSettingsTableMap.put(AppFilterSettings_Data.TIMESTAMP, AppFilterSettings_Data.TIMESTAMP);
        filterSettingsTableMap.put(AppFilterSettings_Data.DEVICE_ID, AppFilterSettings_Data.DEVICE_ID);
        filterSettingsTableMap.put(AppFilterSettings_Data.FILTER_MODE, AppFilterSettings_Data.FILTER_MODE);
        filterSettingsTableMap.put(AppFilterSettings_Data.APP_LIST, AppFilterSettings_Data.APP_LIST);
        filterSettingsTableMap.put(AppFilterSettings_Data.APP_COUNT, AppFilterSettings_Data.APP_COUNT);
        filterSettingsTableMap.put(AppFilterSettings_Data.LAST_MODIFIED, AppFilterSettings_Data.LAST_MODIFIED);

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        initialiseDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (sUriMatcher.match(uri)) {
            case APP_USAGE:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(appUsageTableMap);
                break;
            case APP_FILTER_SETTINGS:
                qb.setTables(DATABASE_TABLES[1]);
                qb.setProjectionMap(filterSettingsTableMap);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Cursor c = qb.query(database, projection, selection, selectionArgs,
                    null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (IllegalStateException e) {
            if (Aware.DEBUG)
                Log.e(Aware.TAG, e.getMessage());
            return null;
        }
    }

    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case APP_USAGE:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            case APP_FILTER_SETTINGS:
                count = database.update(DATABASE_TABLES[1], values, selection,
                        selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }
}