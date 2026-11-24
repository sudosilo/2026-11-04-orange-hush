package com.hushgate.app;

import android.app.Notification;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class HushService extends NotificationListenerService {

    private static final long WEEK = 7L * 24L * 3600L * 1000L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onListenerConnected() {
        Digest.ensure(this);
        ClockLock.start(this);
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification s : active) {
                    handle(s);
                }
            }
        } catch (Exception e) {
            // service not ready, ignore
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn);
    }

    private void handle(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) {
            return;
        }
        Store store = Store.get(this);
        if (!store.hushOn()) {
            return;
        }
        if (!store.blockAll() && !store.isBlocked(pkg)) {
            return;
        }
        Notification n = sbn.getNotification();
        if (n == null) {
            return;
        }
        boolean foreground = (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
        String title = str(n.extras, Notification.EXTRA_TITLE);
        String body = str(n.extras, Notification.EXTRA_TEXT);

        suppress(sbn.getKey());

        if (foreground || (title.isEmpty() && body.isEmpty())) {
            return;
        }
        String app = pkg;
        try {
            PackageManager pm = getPackageManager();
            app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            // keep package name as label
        }
        store.log(pkg, app, title, body);
    }

    /**
     * Cancel first. Pinned system notifications such as Samsung App update refuse a plain
     * cancel, so if the notification is still showing a moment later it is snoozed for a
     * week, and snoozed again each time the owner app posts it back.
     */
    private void suppress(final String key) {
        try {
            cancelNotification(key);
        } catch (Exception e) {
            // some system notifications refuse removal
        }
        handler.postDelayed(() -> {
            if (!stillShown(key)) {
                return;
            }
            try {
                snoozeNotification(key, WEEK);
            } catch (Exception e) {
                // snooze refused too
            }
            handler.postDelayed(() -> {
                if (stillShown(key)) {
                    try {
                        cancelNotification(key);
                    } catch (Exception e) {
                        // nothing more Android allows here
                    }
                }
            }, 1500);
        }, 400);
    }

    private boolean stillShown(String key) {
        try {
            StatusBarNotification[] found = getActiveNotifications(new String[]{key});
            return found != null && found.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String str(Bundle extras, String key) {
        if (extras == null) {
            return "";
        }
        CharSequence cs = extras.getCharSequence(key);
        return cs == null ? "" : cs.toString();
    }
}
