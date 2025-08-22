package com.aware.plugin.app_usage;

import android.app.usage.UsageEvents;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages app usage sessions with proper lifecycle handling
 * Handles ACTIVITY_RESUMED, ACTIVITY_PAUSED, screen events, and multi-window scenarios
 */
public class AppUsageSessionManager {
    
    private static final String TAG = "AWARE::SessionManager";
    
    // Session configuration - NO MERGING, preserve raw usage patterns
    private static final long SESSION_MIN_DURATION = 1000; // 1 second minimum (very short sessions)
    // REMOVED SESSION_TIMEOUT - No timeout to prevent session merging
    // Every app open/close must be recorded as separate entry per requirements
    
    // Plugin reference for saving sessions
    private final Plugin plugin;
    
    // Active sessions map: package name -> session info
    private final Map<String, AppSession> activeSessions = new ConcurrentHashMap<>();
    
    // Removed: No session merging to preserve accurate usage patterns
    
    // Screen state
    private boolean isScreenOn = true;
    
    public AppUsageSessionManager(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Process list of usage events
     */
    public void processEvents(List<Plugin.UsageEventData> events) {
        if (events.isEmpty()) return;
        
        // Sort events by timestamp
        Collections.sort(events, (a, b) -> Long.compare(a.timestamp, b.timestamp));
        
        Log.d(TAG, "Processing " + events.size() + " events");
        
        for (Plugin.UsageEventData event : events) {
            processEvent(event);
        }
        
        // No cleanup needed - sessions end immediately on MOVE_TO_BACKGROUND
    }
    
    /**
     * Process individual event
     */
    private void processEvent(Plugin.UsageEventData event) {
        // Simple and accurate approach:
        // MOVE_TO_FOREGROUND = App visible on screen = Start session
        // MOVE_TO_BACKGROUND = App no longer visible = End session immediately
        // SCREEN_NON_INTERACTIVE = Screen off = End all sessions immediately
        // Multiple apps can be visible simultaneously (PIP + normal app)
        
        // Process screen events first as they affect all apps
        if (event.eventType == 16) {
            // SCREEN_NON_INTERACTIVE - Screen turned off
            handleScreenOff();
        } else if (event.eventType == 15) {
            // SCREEN_INTERACTIVE - Screen turned on
            handleScreenOn();
        } else if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            // App became visible - start session only if screen is on
            handleAppVisible(event);
        } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
            // App no longer visible - end session immediately
            handleAppHidden(event);
        }
        // Ignore ACTIVITY_RESUMED/PAUSED - they're unreliable for visibility
    }
    
    /**
     * Handle app becoming visible on screen
     */
    private void handleAppVisible(Plugin.UsageEventData event) {
        // IMPORTANT: Screen off should always end sessions completely
        // If screen is off, don't start new sessions
        if (!isScreenOn) {
            Log.d(TAG, "Screen is off, ignoring app visible event: " + event.appName);
            return;
        }
        
        AppSession existingSession = activeSessions.get(event.packageName);
        
        if (existingSession != null) {
            // Update existing session only if screen stayed on
            existingSession.lastActivityTime = event.timestamp;
            Log.d(TAG, "App visible (continuing): " + event.appName);
        } else {
            // Create new session - app became visible
            AppSession newSession = new AppSession(
                event.packageName,
                event.appName,
                event.isSystemApp,
                event.timestamp
            );
            activeSessions.put(event.packageName, newSession);
            Log.d(TAG, "App visible (new session): " + event.appName + " at " + new java.util.Date(event.timestamp));
        }
    }
    
    /**
     * Handle app no longer visible on screen - end session immediately
     */
    private void handleAppHidden(Plugin.UsageEventData event) {
        AppSession session = activeSessions.get(event.packageName);
        if (session != null) {
            // App is no longer visible - end session immediately
            finalizeSession(session, event.timestamp);
            activeSessions.remove(event.packageName);
            long duration = event.timestamp - session.startTime;
            Log.d(TAG, "App hidden, session ended: " + event.appName + 
                       " (duration: " + duration / 1000 + "s)");
        }
    }
    
    /**
     * Handle screen turning off
     */
    public void handleScreenOff() {
        isScreenOn = false;
        Log.d(TAG, "Screen off - finalizing all active sessions");
        // Must finalize and clear all sessions when screen turns off
        // This ensures accurate session boundaries matching user behavior
        long endTime = System.currentTimeMillis();
        finalizeAllSessions(endTime);
        // Clear the map to ensure no sessions persist after screen off
        activeSessions.clear();
    }
    
    /**
     * Handle screen turning on
     */
    public void handleScreenOn() {
        isScreenOn = true;
        Log.d(TAG, "Screen on");
    }
    
    /**
     * Finalize all active sessions
     */
    public void finalizeAllActiveSessions() {
        finalizeAllSessions(System.currentTimeMillis());
    }
    
    /**
     * Finalize all active sessions with specific end time
     */
    private void finalizeAllSessions(long endTime) {
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, AppSession> entry : activeSessions.entrySet()) {
            AppSession session = entry.getValue();
            finalizeSession(session, endTime);
            toRemove.add(entry.getKey());
        }
        
        for (String packageName : toRemove) {
            activeSessions.remove(packageName);
        }
    }
    
    // REMOVED: cleanupOldSessions and shouldFinalizeSession methods
    // Sessions now end immediately on MOVE_TO_BACKGROUND events
    // No timeout-based cleanup to prevent session merging
    
    /**
     * Finalize a session and save it
     */
    private void finalizeSession(AppSession session, long endTime) {
        long duration = endTime - session.startTime;
        
        // Only save sessions that meet minimum duration
        if (duration < SESSION_MIN_DURATION) {
            Log.d(TAG, "Session too short, not saving: " + session.appName + " (" + duration + "ms)");
            return;
        }
        
        // Let user-controlled app_list handle all filtering
        // No automatic system UI filtering in session manager
        
        Log.d(TAG, "Finalizing session: " + session.appName + 
               " (" + duration / 1000 + "s)");
        
        // Save the session directly - NO MERGING to preserve accurate usage patterns
        plugin.saveAppUsageSession(
            session.packageName,
            session.appName,
            session.isSystemApp,
            session.startTime,
            endTime
        );
    }
    
    // Removed all session merging logic to preserve accurate usage patterns
    
    /**
     * App session information
     */
    private static class AppSession {
        final String packageName;
        final String appName;
        final boolean isSystemApp;
        final long startTime;
        long lastActivityTime;
        
        AppSession(String packageName, String appName, boolean isSystemApp, long startTime) {
            this.packageName = packageName;
            this.appName = appName;
            this.isSystemApp = isSystemApp;
            this.startTime = startTime;
            this.lastActivityTime = startTime;
        }
    }
    
    // Removed CompletedSessionInfo class - no merging needed
    
    /**
     * Get current active sessions count (for debugging)
     */
    public int getActiveSessionsCount() {
        return activeSessions.size();
    }
    
    /**
     * Get active session info (for debugging)
     */
    public Map<String, String> getActiveSessionsInfo() {
        Map<String, String> info = new HashMap<>();
        for (Map.Entry<String, AppSession> entry : activeSessions.entrySet()) {
            AppSession session = entry.getValue();
            info.put(entry.getKey(), session.appName + " (started: " + new Date(session.startTime) + ")");
        }
        return info;
    }
}