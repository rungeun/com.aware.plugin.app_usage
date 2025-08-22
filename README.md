AWARE Plugin: App Usage
==========================

This plugin measures the application usage time.

# Settings
Parameters adjustable on the dashboard and client:
- **status_plugin_app_usage**: (boolean) activate/deactivate plugin
- **plugin_app_usage_frequency**: (integer) data collection frequency in minutes (default: 1)
- **app_filter_mode**: (string) filter mode for app tracking - "blacklist" or "whitelist" (default: "blacklist")
- **app_list**: (string) comma-separated list of package names to include/exclude based on filter mode

# Providers
## App Usage Data
> content://com.aware.plugin.app_usage.provider.app_usage/plugin_app_usage

Field | Type | Description
----- | ---- | -----------
_id | INTEGER | primary key auto-incremented
timestamp | REAL | unix timestamp in milliseconds of sample
device_id | TEXT | AWARE device ID
package_name | TEXT | Application's package name    
application_name | TEXT | Application's localized name
category | TEXT | Application category
is_system_app | BOOLEAN | Device's pre-installed application
app_on | TEXT | The time the app was turned on (ISO date format)
app_off | TEXT | The time the app was turned off (ISO date format)
app_usage | REAL | app usage time in milliseconds

## App Filter Settings
> content://com.aware.plugin.app_usage.provider.app_usage/plugin_app_filter_settings

Field | Type | Description
----- | ---- | -----------
_id | INTEGER | primary key auto-incremented
timestamp | REAL | unix timestamp in milliseconds of sample
device_id | TEXT | AWARE device ID
filter_mode | TEXT | Filter mode for app tracking
app_list | TEXT | List of apps to include/exclude
app_count | INTEGER | Number of apps in filter list
last_modified | REAL | Last modification timestamp

## License

This project is a modified version of the [AWARE device usage plugin](https://github.com/denzilferreira/com.aware.plugin.device_usage), 
which is part of the [AWARE Framework](https://github.com/awareframework/aware-client).

- Original work: Copyright (c) 2011 AWARE Mobile Context Instrumentation Middleware/Framework  
- Modifications: Copyright (c) 2025 RunGeun

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) file for details.