AWARE Plugin: App Usage
==========================

This plugin measures the application usage time.

# Settings
Parameters adjustable on the dashboard and client:
- **status_plugin_app_usage**: (boolean) activate/deactivate plugin

# Providers
##  Device Usage Data
> content://com.aware.plugin.app_usage.provider.device_usage/plugin_app_usage

Field | Type | Description
----- | ---- | -----------
_id | INTEGER | primary key auto-incremented
timestamp | REAL | unix timestamp in milliseconds of sample
device_id | TEXT | AWARE device ID
package_name | TEXT | Application’s package name    
application_name | TEXT | Application’s localized name
is_system_app | BOOLEAN	 | Device’s pre-installed application
app_on | REAL | The time the app was turned on
app_off	| REAL | The time the app was turned off
app_usage	| REAL | app usage time