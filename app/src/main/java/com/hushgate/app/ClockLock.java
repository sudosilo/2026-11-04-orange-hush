package com.hushgate.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.TimeZone;

/** Holds the phone clock on one chosen time zone no matter what the network says. */
public class ClockLock {

    public static final String[] ZONE_LABELS = {
            "Las Vegas (Pacific)",
            "Arizona (Phoenix)",
            "Denver (Mountain)",
            "Chicago (Central)",
            "New York (Eastern)"
    };
    public static final String[] ZONE_IDS = {
            "America/Los_Angeles",
            "America/Phoenix",
            "America/Denver",
            "America/Chicago",
            "America/New_York"
    };

    private static final String CHANNEL = "clock_lock";
    private static final int NOTIFY_ID = 2001;
    private static final int OPEN_CODE = 3;

    private static boolean started = false;

    public static synchronized void start(Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (!started) {
            started = true;
            Handler h = new Handler(Looper.getMainLooper());
            ContentObserver watcher = new ContentObserver(h) {
                @Override
                public void onChange(boolean selfChange) {
                    enforce(c);
                }
            };
            ContentResolver cr = c.getContentResolver();
            cr.registerContentObserver(Settings.Global.getUriFor(Settings.Global.AUTO_TIME_ZONE), false, watcher);
            IntentFilter f = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            c.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    enforce(c);
                }
            }, f);
        }
        enforce(c);
    }

    public static boolean canWrite(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean autoZoneOn(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 0) == 1;
    }

    public static String currentZone() {
        TimeZone.setDefault(null);
        return TimeZone.getDefault().getID();
    }

    public static void enforce(Context c) {
        Store store = Store.get(c);
        if (!store.clockLock()) {
            return;
        }
        if (autoZoneOn(c) && canWrite(c)) {
            try {
                Settings.Global.putInt(c.getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 0);
            } catch (Exception e) {
                // permission missing
            }
        }
        String want = store.clockZone();
        if (!want.equals(currentZone())) {
            try {
                AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                am.setTimeZone(want);
            } catch (Exception e) {
                // this phone does not let apps set the zone directly
            }
        }
        if (!want.equals(currentZone()) || autoZoneOn(c)) {
            ClockFixer fixer = ClockFixer.instance;
            if (fixer != null) {
                fixer.fix(want);
            }
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFY_ID);
        } else {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFY_ID);
        }
    }

    private static String label(String id) {
        for (int i = 0; i < ZONE_IDS.length; i++) {
            if (ZONE_IDS[i].equals(id)) {
                return ZONE_LABELS[i];
            }
        }
        return id;
    }

    private static void warn(Context c, String want) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!nm.areNotificationsEnabled()) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Clock lock", NotificationManager.IMPORTANCE_HIGH);
        ch.setLightColor(0xFF1E6FFF);
        nm.createNotificationChannel(ch);
        Intent open = new Intent(Settings.ACTION_DATE_SETTINGS);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, OPEN_CODE, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setColor(0xFF1E6FFF)
                .setContentTitle("Clock moved off " + label(want))
                .setContentText("Tap to set it back to " + label(want))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIFY_ID, note);
    }
}
