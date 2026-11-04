package com.hushgate.app;

import android.app.Notification;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class HushService extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
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

        try {
            cancelNotification(sbn.getKey());
        } catch (Exception e) {
            // some system notifications refuse removal
        }

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

    private static String str(Bundle extras, String key) {
        if (extras == null) {
            return "";
        }
        CharSequence cs = extras.getCharSequence(key);
        return cs == null ? "" : cs.toString();
    }
}
