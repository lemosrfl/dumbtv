package tv.dumbtv.home;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.UserManager;

/**
 * Watches for Amazon's launcher coming back to the foreground and puts dumbtv home back on top.
 *
 * Fire OS 6.7 refuses third-party accessibility services, so Guard cannot run there. This service
 * needs no accessibility: it reads the foreground package from UsageStats (granted with
 * `appops set tv.dumbtv.home GET_USAGE_STATS allow`) and calls moveTaskToFront, which is not an
 * activity launch and therefore escapes the 5 s app-switch delay. The alarm path is only the
 * first-start fallback, before a Home task exists.
 */
public final class Keeper extends Service {
    private static final String AMAZON_PKG = "com.amazon.tv.launcher";
    // Only the home activity, never Settings/search/other KFTV screens.
    private static final String AMAZON_HOME = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    private static final long POLL_MS = 250;
    private static final long DEBOUNCE_MS = 1500;

    private static volatile boolean paused;
    private HandlerThread thread;
    private Handler worker;
    private UsageStatsManager usage;
    private long lastLaunch;
    private long lastButtonRedirect;
    private String topClass;
    private BroadcastReceiver unlockReceiver;
    private boolean waitingForUnlock;

    /** Lets Amazon's home stay on screen until the user opens another app, like Guard.pause(). */
    static void pause() {
        paused = true;
    }

    static void start(Context c) {
        // Fire OS 6 (Android 7.1) only; 7 and 8 use the accessibility Guard. Android 8.x would
        // also need a notification channel and refuses background startService.
        if (android.os.Build.VERSION.SDK_INT >= 26) return;
        c.startService(new Intent(c, Keeper.class));
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            try {
                check();
            } catch (Throwable ignored) {
                // Never let one bad query kill the watchdog.
            }
            worker.postDelayed(this, POLL_MS);
        }
    };

    @Override
    @SuppressWarnings("deprecation") // the channel-less builder is the only one Android 7.1 has
    public void onCreate() {
        super.onCreate();
        usage = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        startForeground(1, new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("dumbtv")
                .setContentText("home keeper")
                .build());
        // Keep the binder queries off the UI thread: this process draws the home screen.
        thread = new HandlerThread("keeper");
        thread.start();
        worker = new Handler(thread.getLooper());
        UserManager users = (UserManager) getSystemService(Context.USER_SERVICE);
        if (users != null && !users.isUserUnlocked()) {
            waitingForUnlock = true;
            unlockReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (waitingForUnlock) {
                        waitingForUnlock = false;
                        unregisterReceiver(this);
                        unlockReceiver = null;
                        startWatching();
                    }
                }
            };
            registerReceiver(unlockReceiver, new android.content.IntentFilter(Intent.ACTION_USER_UNLOCKED));
        } else {
            startWatching();
        }
    }

    private void startWatching() {
        worker.post(poll);
        // Start as soon as the system says the user is unlocked. Boot completion can arrive
        // much later than the lock-screen boot signal on Fire OS.
        worker.post(this::firstStart);
    }

    @Override
    public void onDestroy() {
        if (unlockReceiver != null) {
            try {
                unregisterReceiver(unlockReceiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    /**
     * Logs to logcat always, and to a file under the app's own external dir only
     * while the {@code debug} marker exists there (touch it to enable, delete it
     * to stop). The file survives reboots, which is the point.
     */
    static void note(String msg) {
        android.util.Log.i("dumbtvtest", msg);
        java.io.File marker = new java.io.File(
                android.os.Environment.getExternalStorageDirectory(),
                "Android/data/tv.dumbtv.home/files/debug");
        if (!marker.exists()) return;
        try {
            java.io.File f = new java.io.File(marker.getParentFile(), "keeper.log");
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            java.io.FileOutputStream out = new java.io.FileOutputStream(f, true);
            try {
                out.write((System.currentTimeMillis() + " " + msg + "\n").getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
        }
    }

    /** After a boot or an update there is no Home task yet, and UsageStats saw no new event. */
    private void firstStart() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return;
        try {
            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                if (task.getTaskInfo().numActivities > 0) return;
            }
        } catch (Throwable t) {
            note("keeper: could not inspect home tasks: " + t);
        }
        android.util.Log.i("dumbtvtest", "keeper: first start, no active Home task");
        launch(System.currentTimeMillis());
    }

    private void check() {
        long now = System.currentTimeMillis();
        String top = top(now);
        if (top == null) return;
        if (ButtonRemap.matches(this, top)) {
            if (now - lastButtonRedirect < 2500) return;
            lastButtonRedirect = now;
            ButtonRemap.redirect(this, top);
            return;
        }
        if (!AMAZON_PKG.equals(top)) {
            if (!"com.android.systemui".equals(top) && !"android".equals(top)) paused = false;
            return;
        }
        if (!AMAZON_HOME.equals(topClass)) return;
        if (paused) return;
        if (now - lastLaunch < DEBOUNCE_MS) return;
        launch(now);
    }

    private String top(long now) {
        if (usage == null) return null;
        UsageEvents events = usage.queryEvents(now - 4000, now);
        UsageEvents.Event e = new UsageEvents.Event();
        String top = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                top = e.getPackageName();
                topClass = e.getClassName();
            }
        }
        return top;
    }

    private void launch(long now) {
        lastLaunch = now;
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            try {
                for (ActivityManager.AppTask task : manager.getAppTasks()) {
                    if (task.getTaskInfo().numActivities == 0) continue;
                    task.moveToFront();
                    android.util.Log.i("dumbtvtest", "keeper: existing home task moved to front");
                    return;
                }
            } catch (Throwable t) {
                note("keeper: move home task failed: " + t);
            }
        }

        // At boot there is no Home task yet. The alarm is only a first-start fallback;
        // moving an existing task avoids Android's post-HOME activity-start delay.
        Intent i = new Intent(this, Home.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.setExact(AlarmManager.RTC, now, pi);
        android.util.Log.i("dumbtvtest", "keeper: alarm set");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
