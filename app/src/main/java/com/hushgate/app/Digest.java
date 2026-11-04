package com.hushgate.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.List;

public class Digest {

    public static final String[] LABELS = {
            "Off",
            "Every hour",
            "Every 3 hours",
            "Every 6 hours",
            "Every 12 hours",
            "Daily at 8 AM"
    };

    private static final long HOUR = 3600000L;
    private static final long[] INTERVALS = {0L, HOUR, 3 * HOUR, 6 * HOUR, 12 * HOUR, 24 * HOUR};
    private static final int DAILY_MODE = 5;
    private static final int ALARM_CODE = 1;
    private static final int OPEN_CODE = 2;
    private static final int NOTIFY_ID = 1001;
    private static final String CHANNEL = "digest";

    private static PendingIntent alarmIntent(Context c, int flags) {
        return PendingIntent.getBroadcast(c, ALARM_CODE,
                new Intent(c, DigestReceiver.class), flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Replace any existing alarm with one that matches the saved preset. */
    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(c, PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        int mode = Store.get(c).digestMode();
        if (mode <= 0 || mode >= INTERVALS.length) {
            return;
        }
        long now = System.currentTimeMillis();
        long first;
        if (mode == DAILY_MODE) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 8);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            first = cal.getTimeInMillis();
        } else {
            first = now + INTERVALS[mode];
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, INTERVALS[mode], pi);
    }

    /** Recreate the alarm only if it is missing, for example after a reboot. */
    public static void ensure(Context c) {
        if (Store.get(c).digestMode() <= 0) {
            return;
        }
        PendingIntent existing = alarmIntent(c, PendingIntent.FLAG_NO_CREATE);
        if (existing == null) {
            schedule(c);
        }
    }

    /** Posts one summary notification. Returns null on success, otherwise a short reason. */
    public static String post(Context c) {
        Store store = Store.get(c);
        List<Store.LogRow> rows = store.since(store.lastDigest(), 200);
        if (rows.isEmpty()) {
            return "Nothing held since the last digest";
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!nm.areNotificationsEnabled()) {
            return "Allow notifications for Hush first";
        }
        nm.createNotificationChannel(
                new NotificationChannel(CHANNEL, "Held digest", NotificationManager.IMPORTANCE_DEFAULT));

        Intent open = new Intent(c, MainActivity.class);
        open.putExtra("tab", "held");
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, OPEN_CODE, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int n = rows.size();
        Notification.InboxStyle style = new Notification.InboxStyle();
        int shown = Math.min(6, n);
        for (int i = 0; i < shown; i++) {
            Store.LogRow r = rows.get(i);
            String what = r.title.isEmpty() ? r.body : r.title;
            style.addLine(r.app + ": " + what);
        }
        if (n > shown) {
            style.setSummaryText("+" + (n - shown) + " more in Hush");
        }
        Store.LogRow top = rows.get(0);
        String topText = top.app + ": " + (top.title.isEmpty() ? top.body : top.title);

        Notification note = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(n + (n == 1 ? " held notification" : " held notifications"))
                .setContentText(topText)
                .setStyle(style)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIFY_ID, note);
        store.setLastDigest(System.currentTimeMillis());
        return null;
    }
}
