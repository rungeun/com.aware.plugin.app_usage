package com.aware.plugin.app_usage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Activity for managing app blacklist
 * Shows all installed apps and allows user to select which ones to exclude from tracking
 */
public class AppBlacklistActivity extends Activity {

    private ListView appListView;
    private AppListAdapter adapter;
    private List<AppInfo> appList;
    private List<AppInfo> filteredAppList;
    private Set<String> selectedApps;
    private EditText searchEditText;
    private Spinner sortSpinner;
    private TextView selectedAppsDisplay;
    private boolean isWhitelistMode;
    
    // Sort options
    private static final int SORT_BY_NAME = 0;
    private static final int SORT_BY_INSTALL_TIME = 1;
    private int currentSortOption = SORT_BY_NAME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Determine current filter mode
        isWhitelistMode = com.aware.plugin.app_usage.Settings.isWhitelistMode(this);
        
        // Create layout programmatically
        createLayout();
        
        // Set title based on mode
        if (isWhitelistMode) {
            setTitle("Manage Whitelist");
        } else {
            setTitle("Manage Blacklist");
        }
        
        // Load current app list
        selectedApps = com.aware.plugin.app_usage.Settings.getAppList(this);
        
        // Load all apps and create adapter
        loadInstalledApps();
        filteredAppList = new ArrayList<>(appList);
        adapter = new AppListAdapter();
        appListView.setAdapter(adapter);
        
        // Setup search functionality
        setupSearch();
        
        // Setup sort functionality
        setupSort();
        
        // Initial update of selected apps display
        updateSelectedAppsDisplay();
    }
    
    /**
     * Create the layout programmatically
     */
    private void createLayout() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);
        
        // Create ListView first
        appListView = new ListView(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1.0f
        );
        appListView.setLayoutParams(listParams);
        
        // Create header view that contains all the controls
        LinearLayout headerView = createHeaderView();
        appListView.addHeaderView(headerView);
        
        mainLayout.addView(appListView);
        
        // Create selected apps display section
        TextView sectionTitle = new TextView(this);
        String titleText = isWhitelistMode ? "Selected Apps (Whitelist):" : "Selected Apps (Blacklist):";
        sectionTitle.setText(titleText);
        sectionTitle.setTextSize(14);
        sectionTitle.setTextColor(0xFF333333);
        sectionTitle.setPadding(8, 16, 8, 8);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sectionTitle.setLayoutParams(titleParams);
        mainLayout.addView(sectionTitle);
        
        // Create a ScrollView to contain the selected apps text
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFFF5F5F5);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120 // Fixed height for scroll area
        );
        scrollParams.setMargins(8, 0, 8, 16);
        scrollView.setLayoutParams(scrollParams);
        
        // Create text view for selected apps inside the ScrollView
        selectedAppsDisplay = new TextView(this);
        selectedAppsDisplay.setText("No apps selected");
        selectedAppsDisplay.setTextSize(12);
        selectedAppsDisplay.setTextColor(0xFF666666);
        selectedAppsDisplay.setPadding(8, 8, 8, 8);
        selectedAppsDisplay.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        // Add click-to-copy functionality
        selectedAppsDisplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copySelectedAppsToClipboard();
            }
        });
        
        // Add visual feedback for clickable text
        selectedAppsDisplay.setClickable(true);
        selectedAppsDisplay.setFocusable(true);
        selectedAppsDisplay.setBackgroundResource(android.R.drawable.btn_default);
        
        // Add TextView to ScrollView, then ScrollView to main layout
        scrollView.addView(selectedAppsDisplay);
        mainLayout.addView(scrollView);
        
        setContentView(mainLayout);
    }
    
    /**
     * Create header view with all controls (search, sort, buttons)
     */
    private LinearLayout createHeaderView() {
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.VERTICAL);
        headerLayout.setPadding(0, 0, 0, 16);
        
        headerLayout.addView(createSearchEditText());
        headerLayout.addView(createSortSpinner());
        headerLayout.addView(createSystemAppsButton());
        headerLayout.addView(createFunctionButtons());
        
        return headerLayout;
    }

    /**
     * Create search EditText
     */
    private EditText createSearchEditText() {
        searchEditText = new EditText(this);
        String searchHint = isWhitelistMode ? "Search apps to include..." : "Search apps to exclude...";
        searchEditText.setHint(searchHint);
        searchEditText.setTextSize(14);
        searchEditText.setPadding(16, 16, 16, 16);
        searchEditText.setMinHeight(120);
        
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        searchParams.setMargins(0, 0, 0, 8);
        searchEditText.setLayoutParams(searchParams);
        
        return searchEditText;
    }

    /**
     * Create sort spinner
     */
    private Spinner createSortSpinner() {
        sortSpinner = new Spinner(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        spinnerParams.setMargins(0, 0, 0, 16);
        sortSpinner.setLayoutParams(spinnerParams);
        
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, 
                android.R.layout.simple_spinner_item,
                new String[]{"Sort by Name", "Sort by Install Time"}
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(spinnerAdapter);
        
        return sortSpinner;
    }

    /**
     * Create system apps button
     */
    private Button createSystemAppsButton() {
        Button systemAppsButton = new Button(this);
        String buttonText = isWhitelistMode ? "Select All System Apps" : "Exclude All System Apps";
        systemAppsButton.setText(buttonText);
        systemAppsButton.setTextSize(12);
        
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, 0, 0, 16);
        systemAppsButton.setLayoutParams(buttonParams);
        systemAppsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSystemAppsConfirmationDialog();
            }
        });
        
        return systemAppsButton;
    }
    
    /**
     * Create function buttons row
     */
    private LinearLayout createFunctionButtons() {
        LinearLayout buttonsContainer = new LinearLayout(this);
        buttonsContainer.setOrientation(LinearLayout.VERTICAL);
        buttonsContainer.setPadding(0, 8, 0, 8);
        
        // First row: Select All, Deselect All
        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        
        Button selectAllButton = new Button(this);
        selectAllButton.setText("Select All");
        selectAllButton.setTextSize(11);
        LinearLayout.LayoutParams selectAllParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        selectAllParams.setMargins(0, 0, 4, 0);
        selectAllButton.setLayoutParams(selectAllParams);
        selectAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAllApps();
            }
        });
        
        Button deselectAllButton = new Button(this);
        deselectAllButton.setText("Deselect All");
        deselectAllButton.setTextSize(11);
        LinearLayout.LayoutParams deselectAllParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        deselectAllParams.setMargins(4, 0, 0, 0);
        deselectAllButton.setLayoutParams(deselectAllParams);
        deselectAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deselectAllApps();
            }
        });
        
        firstRow.addView(selectAllButton);
        firstRow.addView(deselectAllButton);
        
        // Second row: Reload JSON, Direct Input
        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        
        Button reloadJsonButton = new Button(this);
        reloadJsonButton.setText("Reload JSON");
        reloadJsonButton.setTextSize(11);
        LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        reloadParams.setMargins(0, 4, 4, 0);
        reloadJsonButton.setLayoutParams(reloadParams);
        reloadJsonButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reloadFromJson();
            }
        });
        
        Button directInputButton = new Button(this);
        directInputButton.setText("Direct Input");
        directInputButton.setTextSize(11);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        inputParams.setMargins(4, 4, 0, 0);
        directInputButton.setLayoutParams(inputParams);
        directInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDirectInputDialog();
            }
        });
        
        secondRow.addView(reloadJsonButton);
        secondRow.addView(directInputButton);
        
        buttonsContainer.addView(firstRow);
        buttonsContainer.addView(secondRow);
        
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.setMargins(0, 0, 0, 8);
        buttonsContainer.setLayoutParams(containerParams);
        
        return buttonsContainer;
    }
    
    /**
     * Setup search functionality
     */
    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString().toLowerCase(Locale.ROOT));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    
    /**
     * Setup sort functionality
     */
    private void setupSort() {
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentSortOption = position;
                sortAndRefreshApps();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    
    /**
     * Filter apps based on search query
     */
    private void filterApps(String query) {
        filteredAppList.clear();
        
        if (query.isEmpty()) {
            filteredAppList.addAll(appList);
        } else {
            for (AppInfo app : appList) {
                if (app.appName.toLowerCase(Locale.ROOT).contains(query) || 
                    app.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                    filteredAppList.add(app);
                }
            }
        }
        
        sortAndRefreshApps();
    }
    
    /**
     * Sort apps and refresh the list
     */
    private void sortAndRefreshApps() {
        // Separate selected and unselected apps
        List<AppInfo> selectedApps = new ArrayList<>();
        List<AppInfo> unselectedApps = new ArrayList<>();
        
        for (AppInfo app : filteredAppList) {
            if (app.isSelected) {
                selectedApps.add(app);
            } else {
                unselectedApps.add(app);
            }
        }
        
        // Sort each group
        Comparator<AppInfo> comparator = getComparator();
        Collections.sort(selectedApps, comparator);
        Collections.sort(unselectedApps, comparator);
        
        // Combine: selected apps first, then unselected
        filteredAppList.clear();
        filteredAppList.addAll(selectedApps);
        filteredAppList.addAll(unselectedApps);
        
        adapter.notifyDataSetChanged();
        
        // Update selected apps display
        updateSelectedAppsDisplay();
    }
    
    /**
     * Get comparator based on current sort option
     */
    private Comparator<AppInfo> getComparator() {
        switch (currentSortOption) {
            case SORT_BY_INSTALL_TIME:
                return new Comparator<AppInfo>() {
                    @Override
                    public int compare(AppInfo a, AppInfo b) {
                        return Long.compare(b.installTime, a.installTime); // Newest first
                    }
                };
            case SORT_BY_NAME:
            default:
                return new Comparator<AppInfo>() {
                    @Override
                    public int compare(AppInfo a, AppInfo b) {
                        return a.appName.compareToIgnoreCase(b.appName);
                    }
                };
        }
    }
    
    /**
     * Copy selected apps list to clipboard
     */
    private void copySelectedAppsToClipboard() {
        String selectedAppsText = selectedAppsDisplay.getText().toString();
        
        // Don't copy if no apps are selected
        if (selectedAppsText.equals("No apps selected") || selectedAppsText.isEmpty()) {
            Toast.makeText(this, "No apps to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Copy to clipboard
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Selected Apps", selectedAppsText);
        clipboard.setPrimaryClip(clip);
        
        // Show confirmation toast
        String modeText = isWhitelistMode ? "whitelist" : "blacklist";
        Toast.makeText(this, "App " + modeText + " copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Update the selected apps display at the bottom of the screen
     */
    private void updateSelectedAppsDisplay() {
        List<String> selectedPackages = new ArrayList<>();
        
        // Collect selected app package names
        for (AppInfo app : appList) {
            if (app.isSelected) {
                selectedPackages.add(app.packageName);
            }
        }
        
        // Update display text
        if (selectedPackages.isEmpty()) {
            selectedAppsDisplay.setText("No apps selected");
        } else {
            // Sort packages alphabetically for consistent display
            Collections.sort(selectedPackages);
            
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String packageName : selectedPackages) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(packageName);
                first = false;
            }
            
            String displayText = sb.toString();
            selectedAppsDisplay.setText(displayText);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Save app list when activity is paused
        saveAppList();
        String message = isWhitelistMode ? "Whitelist updated" : "Blacklist updated";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Load all installed applications
     */
    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        
        appList = new ArrayList<>();
        
        for (ApplicationInfo appInfo : installedApps) {
            try {
                String packageName = appInfo.packageName;
                String appName = pm.getApplicationLabel(appInfo).toString();
                Drawable appIcon = pm.getApplicationIcon(appInfo);
                boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                
                // Let user control all apps including keyboards and system UI
                // No automatic filtering
                
                // Get install time
                long installTime = 0;
                try {
                    PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
                    installTime = packageInfo.firstInstallTime;
                } catch (Exception e) {
                    installTime = 0;
                }
                
                AppInfo app = new AppInfo(packageName, appName, appIcon, isSystemApp, installTime);
                app.isSelected = selectedApps.contains(packageName);
                appList.add(app);
                
            } catch (Exception e) {
                // Skip apps that can't be loaded
            }
        }
        
        // Initial sort by name
        Collections.sort(appList, getComparator());
    }
    

    /**
     * Save current app list selection
     */
    private void saveAppList() {
        // Clear current app list
        Set<String> currentAppList = com.aware.plugin.app_usage.Settings.getAppList(this);
        for (String pkg : new ArrayList<>(currentAppList)) {
            com.aware.plugin.app_usage.Settings.removeFromAppList(this, pkg);
        }
        
        // Add newly selected apps to app list
        for (AppInfo app : appList) {
            if (app.isSelected) {
                com.aware.plugin.app_usage.Settings.addToAppList(this, app.packageName);
            }
        }
        
        // 데이터베이스에 변경 사항 저장
        com.aware.plugin.app_usage.Settings.saveFilterSettingsToDatabase(this);
    }

    /**
     * Adapter for app list
     */
    private class AppListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return filteredAppList.size();
        }

        @Override
        public AppInfo getItem(int position) {
            return filteredAppList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            ViewHolder holder;

            if (view == null) {
                view = LayoutInflater.from(AppBlacklistActivity.this).inflate(
                    android.R.layout.simple_list_item_multiple_choice, parent, false);
                holder = new ViewHolder();
                holder.icon = new ImageView(AppBlacklistActivity.this);
                holder.name = new TextView(AppBlacklistActivity.this);
                holder.packageName = new TextView(AppBlacklistActivity.this);
                holder.checkBox = new CheckBox(AppBlacklistActivity.this);
                
                // Create custom layout
                view = createCustomView(holder);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            final AppInfo app = getItem(position);
            
            holder.icon.setImageDrawable(app.appIcon);
            holder.name.setText(app.appName);
            holder.packageName.setText(app.packageName);
            
            // Set checkbox state
            holder.checkBox.setChecked(app.isSelected);
            
            // Handle checkbox clicks
            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    app.isSelected = ((CheckBox) v).isChecked();
                    // Re-sort to move selected items to top
                    sortAndRefreshApps();
                }
            });
            
            // Handle row clicks
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ViewHolder h = (ViewHolder) v.getTag();
                    app.isSelected = !app.isSelected;
                    h.checkBox.setChecked(app.isSelected);
                    // Re-sort to move selected items to top
                    sortAndRefreshApps();
                }
            });

            return view;
        }
        
        /**
         * Create custom view for app list item
         */
        private View createCustomView(ViewHolder holder) {
            // Create a horizontal linear layout
            android.widget.LinearLayout layout = new android.widget.LinearLayout(AppBlacklistActivity.this);
            layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            layout.setPadding(16, 8, 16, 8);
            
            // App icon
            holder.icon.setLayoutParams(new android.widget.LinearLayout.LayoutParams(64, 64));
            layout.addView(holder.icon);
            
            // Text container
            android.widget.LinearLayout textLayout = new android.widget.LinearLayout(AppBlacklistActivity.this);
            textLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
            textLayout.setPadding(16, 0, 0, 0);
            android.widget.LinearLayout.LayoutParams textParams = new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            textLayout.setLayoutParams(textParams);
            
            // App name
            holder.name.setTextSize(16);
            holder.name.setTextColor(0xFF000000);
            textLayout.addView(holder.name);
            
            // Package name
            holder.packageName.setTextSize(12);
            holder.packageName.setTextColor(0xFF666666);
            textLayout.addView(holder.packageName);
            
            layout.addView(textLayout);
            
            // Checkbox
            holder.checkBox.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            layout.addView(holder.checkBox);
            
            return layout;
        }
    }

    /**
     * ViewHolder pattern for efficient list scrolling
     */
    private static class ViewHolder {
        ImageView icon;
        TextView name;
        TextView packageName;
        CheckBox checkBox;
    }
    
    /**
     * Show confirmation dialog for system apps bulk selection
     */
    private void showSystemAppsConfirmationDialog() {
        String message;
        String positiveButton;
        
        if (isWhitelistMode) {
            message = "Do you want to select all system apps for inclusion in tracking?";
            positiveButton = "Select All";
        } else {
            message = "Do you want to exclude all system apps from tracking?";
            positiveButton = "Exclude All";
        }
        
        new AlertDialog.Builder(this)
                .setTitle("System Apps Selection")
                .setMessage(message)
                .setPositiveButton(positiveButton, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectAllSystemApps();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Select/deselect all system apps based on current mode
     */
    private void selectAllSystemApps() {
        for (AppInfo app : appList) {
            if (app.isSystemApp) {
                app.isSelected = true;
            }
        }
        
        // Refresh the list to show changes
        sortAndRefreshApps();
        
        String message = isWhitelistMode ? 
            "All system apps selected for inclusion" : 
            "All system apps excluded from tracking";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Select all apps in the current filtered list
     */
    private void selectAllApps() {
        for (AppInfo app : appList) {
            app.isSelected = true;
        }
        
        // Refresh the list to show changes
        sortAndRefreshApps();
        
        Toast.makeText(this, "All apps selected", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Deselect all apps
     */
    private void deselectAllApps() {
        for (AppInfo app : appList) {
            app.isSelected = false;
        }
        
        // Refresh the list to show changes
        sortAndRefreshApps();
        
        Toast.makeText(this, "All apps deselected", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Reload app list from JSON configuration
     */
    private void reloadFromJson() {
        // Get JSON setting from AWARE
        String appListFromConfig = com.aware.Aware.getSetting(this, 
                com.aware.plugin.app_usage.Settings.APP_LIST_SETTING);
        
        if (appListFromConfig == null || appListFromConfig.trim().isEmpty()) {
            Toast.makeText(this, "No JSON configuration found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Reload from JSON")
                .setMessage("This will replace current selection with apps from JSON configuration. Continue?")
                .setPositiveButton("Reload", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Clear current selection
                        for (AppInfo app : appList) {
                            app.isSelected = false;
                        }
                        
                        // Load from JSON
                        com.aware.plugin.app_usage.Settings.setAppListFromString(AppBlacklistActivity.this, appListFromConfig);
                        
                        // Update UI selection state
                        Set<String> newSelectedApps = com.aware.plugin.app_usage.Settings.getAppList(AppBlacklistActivity.this);
                        for (AppInfo app : appList) {
                            app.isSelected = newSelectedApps.contains(app.packageName);
                        }
                        
                        // Refresh display
                        sortAndRefreshApps();
                        
                        Toast.makeText(AppBlacklistActivity.this, "Apps reloaded from JSON configuration", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Show dialog for direct text input of app list
     */
    private void showDirectInputDialog() {
        // Get current selection and show dialog
        String currentSelection = com.aware.plugin.app_usage.Settings.getAppListAsString(this);
        showDirectInputDialogWithText(currentSelection);
    }
    
    /**
     * Show dialog for direct text input with specified initial text
     */
    private void showDirectInputDialogWithText(String initialText) {
        // Create ScrollView container for the EditText
        ScrollView scrollView = new ScrollView(this);
        
        // Create EditText for input with strict size limits
        final EditText inputEditText = new EditText(this);
        inputEditText.setHint("Enter comma-separated app package names");
        inputEditText.setLines(4); // Reduced initial lines
        inputEditText.setMaxLines(6); // Reduced maximum lines to prevent overflow
        inputEditText.setSingleLine(false);
        inputEditText.setHorizontallyScrolling(false);
        inputEditText.setVerticalScrollBarEnabled(true);
        inputEditText.setScrollBarStyle(EditText.SCROLLBARS_INSIDE_INSET);
        
        // Set strict size constraints for EditText
        LinearLayout.LayoutParams editTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        // Set maximum height to prevent text box from growing too large
        float density = getResources().getDisplayMetrics().density;
        int maxHeightInDp = 120; // Maximum height for text input area
        int maxHeightInPx = (int) (maxHeightInDp * density);
        editTextParams.height = maxHeightInPx;
        inputEditText.setLayoutParams(editTextParams);
        inputEditText.setMaxHeight(maxHeightInPx); // Additional constraint
        
        // Set conservative fixed height for ScrollView
        int scrollHeightInDp = 130; // Slightly larger than EditText to accommodate padding
        int scrollHeightInPx = (int) (scrollHeightInDp * density);
        
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                scrollHeightInPx
        );
        scrollView.setLayoutParams(scrollParams);
        
        // Add EditText to ScrollView
        scrollView.addView(inputEditText);
        
        // Pre-fill with provided text if any
        if (initialText != null && !initialText.isEmpty()) {
            inputEditText.setText(initialText);
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Direct App List Input")
                .setMessage("Enter app package names separated by commas:")
                .setView(scrollView)
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String inputText = inputEditText.getText().toString().trim();
                        
                        // Clear current selection
                        for (AppInfo app : appList) {
                            app.isSelected = false;
                        }
                        
                        if (!inputText.isEmpty()) {
                            // Process input text (validate and filter existing apps)
                            com.aware.plugin.app_usage.Settings.setAppListFromString(AppBlacklistActivity.this, inputText);
                            
                            // Update UI selection state
                            Set<String> newSelectedApps = com.aware.plugin.app_usage.Settings.getAppList(AppBlacklistActivity.this);
                            for (AppInfo app : appList) {
                                app.isSelected = newSelectedApps.contains(app.packageName);
                            }
                        }
                        
                        // Refresh display
                        sortAndRefreshApps();
                        
                        Toast.makeText(AppBlacklistActivity.this, "App list updated from direct input", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Close current dialog and show new one with empty text
                        dialog.dismiss();
                        showDirectInputDialogWithText("");
                    }
                })
                .show();
    }

    /**
     * App information data class
     */
    private static class AppInfo {
        String packageName;
        String appName;
        Drawable appIcon;
        boolean isSystemApp;
        boolean isSelected;
        long installTime;

        AppInfo(String packageName, String appName, Drawable appIcon, boolean isSystemApp, long installTime) {
            this.packageName = packageName;
            this.appName = appName;
            this.appIcon = appIcon;
            this.isSystemApp = isSystemApp;
            this.isSelected = false;
            this.installTime = installTime;
        }
    }
}