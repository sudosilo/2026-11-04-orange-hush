package com.hushgate.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    static class AppRow {
        String label;
        String pkg;
    }

    private static final int BLUE = 0xFF1E6FFF;
    private static final int RED = 0xFFD32F2F;
    private static final int RED_BG = 0xFFFFEBEE;
    private static final int GREEN = 0xFF2E7D32;
    private static final int GREY = 0xFF9E9E9E;
    private static final int INK = 0xFF111111;

    private Store store;
    private TextView banner;
    private TextView accessPill;
    private TextView hushPill;
    private TextView allPill;
    private TextView lockPill;
    private TextView fixerPill;
    private boolean fixerUnlockShown = false;
    private TextView clockNow;
    private TextView appsTab;
    private TextView heldTab;
    private EditText search;
    private TextView clearBtn;
    private ListView list;
    private boolean showLog = false;
    private final List<AppRow> allApps = new ArrayList<AppRow>();
    private final List<AppRow> shownApps = new ArrayList<AppRow>();
    private List<Store.LogRow> logRows = new ArrayList<Store.LogRow>();
    private BaseAdapter adapter;
    private final SimpleDateFormat fmt = new SimpleDateFormat("MMM d, h:mm a", Locale.US);

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(INK);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    /** A fixed size state badge. Same size in every state so nothing on screen shifts. */
    private TextView pill() {
        TextView p = new TextView(this);
        p.setGravity(Gravity.CENTER);
        p.setTextSize(14);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setSingleLine(true);
        p.setLayoutParams(new LinearLayout.LayoutParams(dp(130), dp(40)));
        return p;
    }

    private void paint(TextView p, String word, int color, boolean filled) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setStroke(dp(2), color);
        g.setColor(filled ? color : Color.WHITE);
        p.setBackground(g);
        p.setText(word);
        p.setTextColor(filled ? Color.WHITE : color);
    }

    private void onOff(TextView p, boolean on) {
        if (on) {
            paint(p, "ON", BLUE, true);
        } else {
            paint(p, "OFF", GREY, false);
        }
    }

    private LinearLayout row(String label, View right) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(5), 0, dp(5));
        TextView t = text(label, 16, false);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(right);
        return r;
    }

    private TextView heading(String s) {
        TextView h = text(s, 13, true);
        h.setTextColor(BLUE);
        h.setPadding(0, dp(10), 0, dp(2));
        return h;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(16), dp(36), dp(16), dp(8));

        TextView title = text("Hush", 26, true);
        title.setTextColor(BLUE);
        root.addView(title);

        banner = text("", 15, true);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(12), 0, dp(12), 0);
        banner.setMaxLines(2);
        banner.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(banner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        root.addView(heading("ALERTS"));

        accessPill = pill();
        accessPill.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(row("Notification access", accessPill));

        hushPill = pill();
        hushPill.setOnClickListener(v -> {
            store.setHushOn(!store.hushOn());
            refresh();
        });
        root.addView(row("Hush blocking", hushPill));

        allPill = pill();
        allPill.setOnClickListener(v -> {
            store.setBlockAll(!store.blockAll());
            refresh();
        });
        root.addView(row("Block every app", allPill));

        Spinner digest = new Spinner(this);
        digest.setAdapter(new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, Digest.LABELS));
        digest.setSelection(store.digestMode());
        digest.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                int old = store.digestMode();
                if (pos == old) {
                    return;
                }
                store.setDigestMode(pos);
                if (old == 0) {
                    store.setLastDigest(System.currentTimeMillis());
                }
                Digest.schedule(MainActivity.this);
                if (pos > 0 && Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        LinearLayout digestRow = new LinearLayout(this);
        digestRow.setOrientation(LinearLayout.HORIZONTAL);
        digestRow.setGravity(Gravity.CENTER_VERTICAL);
        digestRow.addView(text("Digest", 16, false));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sp.leftMargin = dp(12);
        digestRow.addView(digest, sp);
        TextView sendNow = pill();
        paint(sendNow, "SEND NOW", BLUE, false);
        sendNow.setOnClickListener(v -> {
            String problem = Digest.post(this);
            Toast.makeText(this, problem == null ? "Digest sent" : problem, Toast.LENGTH_SHORT).show();
        });
        digestRow.addView(sendNow);
        root.addView(digestRow);

        root.addView(heading("CLOCK"));

        lockPill = pill();
        lockPill.setOnClickListener(v -> {
            store.setClockLock(!store.clockLock());
            ClockLock.start(this);
            refresh();
        });
        root.addView(row("Clock lock", lockPill));

        Spinner zone = new Spinner(this);
        zone.setAdapter(new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, ClockLock.ZONE_LABELS));
        int zi = 0;
        for (int i = 0; i < ClockLock.ZONE_IDS.length; i++) {
            if (ClockLock.ZONE_IDS[i].equals(store.clockZone())) {
                zi = i;
            }
        }
        zone.setSelection(zi);
        zone.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (ClockLock.ZONE_IDS[pos].equals(store.clockZone())) {
                    return;
                }
                store.setClockZone(ClockLock.ZONE_IDS[pos]);
                ClockLock.start(MainActivity.this);
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        LinearLayout zoneRow = new LinearLayout(this);
        zoneRow.setOrientation(LinearLayout.HORIZONTAL);
        zoneRow.setGravity(Gravity.CENTER_VERTICAL);
        zoneRow.addView(text("Hold clock on", 16, false));
        LinearLayout.LayoutParams zp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        zp.leftMargin = dp(12);
        zoneRow.addView(zone, zp);
        root.addView(zoneRow);

        fixerPill = pill();
        fixerPill.setOnClickListener(v -> {
            if (!ClockFixer.on() && !fixerUnlockShown) {
                fixerUnlockShown = true;
                Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName()));
                Toast.makeText(this, "Top right menu, Allow restricted settings, then come back and tap Clock fixer again",
                        Toast.LENGTH_LONG).show();
                startActivity(info);
            } else {
                fixerUnlockShown = false;
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        root.addView(row("Clock fixer", fixerPill));

        clockNow = text("", 14, true);
        clockNow.setSingleLine(true);
        clockNow.setEllipsize(TextUtils.TruncateAt.END);
        clockNow.setPadding(0, dp(2), 0, dp(6));
        root.addView(clockNow);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        appsTab = new TextView(this);
        heldTab = new TextView(this);
        for (TextView t : new TextView[]{appsTab, heldTab}) {
            t.setGravity(Gravity.CENTER);
            t.setTextSize(15);
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        appsTab.setText("APPS");
        heldTab.setText("HELD ALERTS");
        appsTab.setOnClickListener(v -> setTab(false));
        heldTab.setOnClickListener(v -> setTab(true));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        tp.rightMargin = dp(4);
        tabs.addView(appsTab, tp);
        tabs.addView(heldTab, new LinearLayout.LayoutParams(0, dp(44), 1f));
        root.addView(tabs);

        FrameLayout slot = new FrameLayout(this);
        search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        search.setTextColor(INK);
        search.setHintTextColor(0xFF888888);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });
        slot.addView(search, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        clearBtn = new TextView(this);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setTypeface(Typeface.DEFAULT_BOLD);
        paint(clearBtn, "CLEAR HELD LIST", BLUE, false);
        clearBtn.setOnClickListener(v -> {
            store.clearLog();
            logRows = store.recent(500);
            adapter.notifyDataSetChanged();
        });
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        cp.topMargin = dp(4);
        slot.addView(clearBtn, cp);
        root.addView(slot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        list = new ListView(this);
        list.setBackgroundColor(Color.WHITE);
        list.setOnItemClickListener((p, v, pos, id) -> {
            if (!showLog && pos < shownApps.size()) {
                AppRow a = shownApps.get(pos);
                store.setBlocked(a.pkg, !store.isBlocked(a.pkg));
                refresh();
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return showLog ? logRows.size() : shownApps.size();
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public int getViewTypeCount() {
                return 2;
            }

            @Override
            public int getItemViewType(int i) {
                return showLog ? 1 : 0;
            }

            @Override
            public View getView(int i, View cv, ViewGroup parent) {
                return showLog ? logView(i, cv) : appView(i, cv);
            }
        };

        setContentView(root);
        setTab("held".equals(getIntent().getStringExtra("tab")));
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accessOn()) {
            try {
                NotificationListenerService.requestRebind(new ComponentName(this, HushService.class));
            } catch (Exception e) {
                // ignore
            }
        }
        ClockLock.start(this);
        if (showLog) {
            logRows = store.recent(500);
        }
        refresh();
    }

    private int blockedCount() {
        if (store.blockAll()) {
            return allApps.size();
        }
        int n = 0;
        for (AppRow a : allApps) {
            if (store.isBlocked(a.pkg)) {
                n++;
            }
        }
        return n;
    }

    private void refresh() {
        boolean access = accessOn();
        boolean hush = store.hushOn();
        boolean all = store.blockAll();

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        if (!access) {
            bg.setColor(RED_BG);
            banner.setTextColor(RED);
            banner.setText("NOT BLOCKING. Notification access is off. Tap the access badge.");
        } else if (!hush) {
            bg.setColor(0xFFF1F1F1);
            banner.setTextColor(INK);
            banner.setText("NOT BLOCKING. Every alert comes through.");
        } else if (all) {
            bg.setColor(0xFFE3EEFF);
            banner.setTextColor(BLUE);
            banner.setText("BLOCKING EVERY APP. All alerts are held.");
        } else {
            bg.setColor(0xFFE3EEFF);
            banner.setTextColor(BLUE);
            banner.setText("BLOCKING " + blockedCount() + " APPS. Red rows are held, green rows come through.");
        }
        banner.setBackground(bg);

        onOff(accessPill, access);
        onOff(hushPill, hush);
        onOff(allPill, all);
        onOff(lockPill, store.clockLock());
        onOff(fixerPill, ClockFixer.on());

        String now = ClockLock.currentZone();
        String want = store.clockZone();
        if (!store.clockLock()) {
            clockNow.setTextColor(GREY);
            clockNow.setText("Clock lock off. Clock now on " + now);
        } else if (want.equals(now)) {
            clockNow.setTextColor(GREEN);
            clockNow.setText("Clock correct: " + now);
        } else {
            clockNow.setTextColor(RED);
            clockNow.setText("Clock wrong: on " + now + ", should be " + want);
        }

        styleTab(appsTab, !showLog);
        styleTab(heldTab, showLog);
        applyFilter();
    }

    private void styleTab(TextView t, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(8));
        g.setStroke(dp(2), BLUE);
        g.setColor(selected ? BLUE : Color.WHITE);
        t.setBackground(g);
        t.setTextColor(selected ? Color.WHITE : BLUE);
    }

    private boolean accessOn() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void setTab(boolean log) {
        showLog = log;
        search.setVisibility(log ? View.INVISIBLE : View.VISIBLE);
        clearBtn.setVisibility(log ? View.VISIBLE : View.INVISIBLE);
        if (log) {
            logRows = store.recent(500);
        }
        list.setAdapter(adapter);
        styleTab(appsTab, !showLog);
        styleTab(heldTab, showLog);
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<AppRow> rows = new ArrayList<AppRow>();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai.packageName.equals(getPackageName())) {
                    continue;
                }
                AppRow r = new AppRow();
                r.pkg = ai.packageName;
                r.label = String.valueOf(pm.getApplicationLabel(ai));
                rows.add(r);
            }
            Collections.sort(rows, (a, b) -> a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(rows);
                refresh();
            });
        }).start();
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shownApps.clear();
        for (int pass = 0; pass < 2; pass++) {
            for (AppRow a : allApps) {
                boolean blocked = store.isBlocked(a.pkg);
                if ((pass == 0) != blocked) {
                    continue;
                }
                if (q.isEmpty()
                        || a.label.toLowerCase(Locale.ROOT).contains(q)
                        || a.pkg.toLowerCase(Locale.ROOT).contains(q)) {
                    shownApps.add(a);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private View appView(int i, View cv) {
        LinearLayout row;
        if (cv == null) {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView name = text("", 16, false);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            TextView pkg = text("", 11, false);
            pkg.setTextColor(0xFF666666);
            pkg.setSingleLine(true);
            pkg.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(name);
            col.addView(pkg);
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView state = pill();
            row.addView(state);
            row.setTag(new Object[]{name, pkg, state});
        } else {
            row = (LinearLayout) cv;
        }
        Object[] t = (Object[]) row.getTag();
        TextView name = (TextView) t[0];
        TextView pkg = (TextView) t[1];
        TextView state = (TextView) t[2];
        AppRow a = shownApps.get(i);
        name.setText(a.label);
        pkg.setText(a.pkg);
        boolean picked = store.isBlocked(a.pkg);
        boolean held = store.hushOn() && accessOn() && (store.blockAll() || picked);
        if (held) {
            row.setBackgroundColor(RED_BG);
            paint(state, picked ? "BLOCKED" : "BLOCKED ALL", RED, true);
        } else if (picked) {
            row.setBackgroundColor(0xFFF5F5F5);
            paint(state, "PICKED, OFF", GREY, true);
        } else {
            row.setBackgroundColor(Color.WHITE);
            paint(state, "ALLOWED", GREEN, false);
        }
        return row;
    }

    private View logView(int i, View cv) {
        LinearLayout row;
        if (cv == null) {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            TextView head = text("", 11, false);
            head.setTextColor(BLUE);
            TextView title = text("", 15, true);
            TextView body = text("", 14, false);
            row.addView(head);
            row.addView(title);
            row.addView(body);
            row.setTag(new Object[]{head, title, body});
        } else {
            row = (LinearLayout) cv;
        }
        Object[] t = (Object[]) row.getTag();
        Store.LogRow r = logRows.get(i);
        ((TextView) t[0]).setText(r.app + "  " + fmt.format(new Date(r.ts)));
        ((TextView) t[1]).setText(r.title);
        ((TextView) t[2]).setText(r.body);
        return row;
    }
}
