package com.hushgate.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/** Walks the date and time screen by itself to put the clock back on the chosen zone. */
public class ClockFixer extends AccessibilityService {

    static ClockFixer instance;

    private static final long TICK = 700L;
    private static final long GIVE_UP = 25000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean active = false;
    private boolean pending = false;
    private String want = "";
    private long startedAt = 0L;
    private BroadcastReceiver unlock;

    @Override
    protected void onServiceConnected() {
        instance = this;
        unlock = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (pending) {
                    pending = false;
                    ClockLock.enforce(ClockFixer.this);
                }
            }
        };
        registerReceiver(unlock, new IntentFilter(Intent.ACTION_USER_PRESENT));
        ClockLock.start(this);
    }

    @Override
    public void onDestroy() {
        instance = null;
        try {
            unregisterReceiver(unlock);
        } catch (Exception e) {
            // already gone
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    public static boolean on() {
        return instance != null;
    }

    /** Returns true if a fix run was started or is already running. */
    public boolean fix(String zone) {
        if (active) {
            return true;
        }
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked()) {
            pending = true;
            return true;
        }
        active = true;
        want = zone;
        startedAt = System.currentTimeMillis();
        Intent open = new Intent(Settings.ACTION_DATE_SETTINGS);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(open);
        } catch (Exception e) {
            active = false;
            return false;
        }
        h.postDelayed(this::tick, TICK);
        return true;
    }

    private static String[] keywords(String zone) {
        switch (zone) {
            case "America/Los_Angeles":
                return new String[]{"Los Angeles", "Pacific"};
            case "America/Phoenix":
                return new String[]{"Phoenix"};
            case "America/Denver":
                return new String[]{"Denver"};
            case "America/Chicago":
                return new String[]{"Chicago"};
            case "America/New_York":
                return new String[]{"New York"};
            default:
                return new String[]{zone};
        }
    }

    private void tick() {
        if (!active) {
            return;
        }
        if (System.currentTimeMillis() - startedAt > GIVE_UP) {
            finish();
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !root.getPackageName().toString().contains("settings")) {
            h.postDelayed(this::tick, TICK);
            return;
        }
        if (ClockLock.autoZoneOn(this)) {
            AccessibilityNodeInfo n = exact(root, "Automatic time zone");
            if (n != null) {
                click(n);
            } else {
                scroll(root);
            }
            h.postDelayed(this::tick, TICK);
            return;
        }
        if (want.equals(ClockLock.currentZone())) {
            finish();
            return;
        }
        AccessibilityNodeInfo target = null;
        for (String k : keywords(want)) {
            target = contains(root, k);
            if (target != null) {
                break;
            }
        }
        if (target == null) {
            target = exact(root, "Select time zone");
        }
        if (target == null) {
            target = exact(root, "Time zone");
        }
        if (target != null) {
            click(target);
        } else {
            scroll(root);
        }
        h.postDelayed(this::tick, TICK);
    }

    private void finish() {
        active = false;
        performGlobalAction(GLOBAL_ACTION_HOME);
        h.postDelayed(() -> ClockLock.enforce(this), 1500L);
    }

    private static AccessibilityNodeInfo exact(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(text);
        if (found == null) {
            return null;
        }
        for (AccessibilityNodeInfo n : found) {
            CharSequence t = n.getText();
            if (t != null && t.toString().trim().equalsIgnoreCase(text)) {
                return n;
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo contains(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(text);
        if (found == null || found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    private static void click(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        while (cur != null) {
            if (cur.isClickable()) {
                cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return;
            }
            cur = cur.getParent();
        }
    }

    private static void scroll(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> queue = new ArrayList<AccessibilityNodeInfo>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo n = queue.remove(0);
            if (n == null) {
                continue;
            }
            if (n.isScrollable()) {
                n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                return;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                queue.add(n.getChild(i));
            }
        }
    }
}
