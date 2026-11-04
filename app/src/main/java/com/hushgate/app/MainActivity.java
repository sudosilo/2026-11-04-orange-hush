package com.hushgate.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

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

    private Store store;
    private TextView status;
    private Button accessBtn;
    private Button clearBtn;
    private EditText search;
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
        t.setTextColor(0xFF111111);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(16), dp(40), dp(16), dp(8));

        root.addView(text("Hush", 26, true));

        status = text("", 14, false);
        root.addView(status);

        accessBtn = new Button(this);
        accessBtn.setText("Open notification access settings");
        accessBtn.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(accessBtn);

        Switch on = new Switch(this);
        on.setText("Hush on");
        on.setTextColor(0xFF111111);
        on.setChecked(store.hushOn());
        on.setOnCheckedChangeListener((b, c) -> store.setHushOn(c));
        on.setPadding(0, dp(8), 0, dp(8));
        root.addView(on);

        Switch all = new Switch(this);
        all.setText("Block every app");
        all.setTextColor(0xFF111111);
        all.setChecked(store.blockAll());
        all.setOnCheckedChangeListener((b, c) -> store.setBlockAll(c));
        all.setPadding(0, dp(8), 0, dp(8));
        root.addView(all);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button appsTab = new Button(this);
        appsTab.setText("Apps");
        appsTab.setOnClickListener(v -> setTab(false));
        Button logTab = new Button(this);
        logTab.setText("Held");
        logTab.setOnClickListener(v -> setTab(true));
        tabs.addView(appsTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tabs.addView(logTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(tabs);

        search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        search.setTextColor(0xFF111111);
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
        root.addView(search);

        clearBtn = new Button(this);
        clearBtn.setText("Clear held list");
        clearBtn.setOnClickListener(v -> {
            store.clearLog();
            logRows = store.recent(500);
            adapter.notifyDataSetChanged();
        });
        clearBtn.setVisibility(View.GONE);
        root.addView(clearBtn);

        list = new ListView(this);
        list.setBackgroundColor(Color.WHITE);
        list.setOnItemClickListener((p, v, pos, id) -> {
            if (!showLog && pos < shownApps.size()) {
                AppRow a = shownApps.get(pos);
                store.setBlocked(a.pkg, !store.isBlocked(a.pkg));
                adapter.notifyDataSetChanged();
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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
        setTab(false);
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = accessOn();
        status.setText(on ? "Notification access: on" : "Notification access: off. Grant it below.");
        accessBtn.setVisibility(on ? View.GONE : View.VISIBLE);
        if (on) {
            try {
                NotificationListenerService.requestRebind(new ComponentName(this, HushService.class));
            } catch (Exception e) {
                // ignore
            }
        }
        if (showLog) {
            logRows = store.recent(500);
            adapter.notifyDataSetChanged();
        }
    }

    private boolean accessOn() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void setTab(boolean log) {
        showLog = log;
        search.setVisibility(log ? View.GONE : View.VISIBLE);
        clearBtn.setVisibility(log ? View.VISIBLE : View.GONE);
        if (log) {
            logRows = store.recent(500);
        }
        list.setAdapter(adapter);
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
                applyFilter();
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
        TextView name;
        TextView pkg;
        Switch sw;
        if (cv == null) {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            name = text("", 16, false);
            pkg = text("", 11, false);
            pkg.setTextColor(0xFF666666);
            col.addView(name);
            col.addView(pkg);
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            sw = new Switch(this);
            row.addView(sw);
            row.setTag(new Object[]{name, pkg, sw});
        } else {
            row = (LinearLayout) cv;
        }
        Object[] t = (Object[]) row.getTag();
        name = (TextView) t[0];
        pkg = (TextView) t[1];
        sw = (Switch) t[2];
        final AppRow a = shownApps.get(i);
        name.setText(a.label);
        pkg.setText(a.pkg);
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(store.isBlocked(a.pkg));
        sw.setOnCheckedChangeListener((b, c) -> store.setBlocked(a.pkg, c));
        return row;
    }

    private View logView(int i, View cv) {
        LinearLayout row;
        TextView head;
        TextView title;
        TextView body;
        if (cv == null) {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            head = text("", 11, false);
            head.setTextColor(0xFF666666);
            title = text("", 15, true);
            body = text("", 14, false);
            row.addView(head);
            row.addView(title);
            row.addView(body);
            row.setTag(new Object[]{head, title, body});
        } else {
            row = (LinearLayout) cv;
        }
        Object[] t = (Object[]) row.getTag();
        head = (TextView) t[0];
        title = (TextView) t[1];
        body = (TextView) t[2];
        Store.LogRow r = logRows.get(i);
        head.setText(r.app + "  " + fmt.format(new Date(r.ts)));
        title.setText(r.title);
        body.setText(r.body);
        return row;
    }
}
