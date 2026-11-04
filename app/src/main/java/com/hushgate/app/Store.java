package com.hushgate.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Store extends SQLiteOpenHelper {

    public static class LogRow {
        public long ts;
        public String app;
        public String title;
        public String body;
    }

    private static Store inst;

    private final SharedPreferences prefs;
    private String lastKey = "";

    public static synchronized Store get(Context c) {
        if (inst == null) {
            inst = new Store(c.getApplicationContext());
        }
        return inst;
    }

    private Store(Context c) {
        super(c, "hush.db", null, 1);
        prefs = c.getSharedPreferences("hush_prefs", Context.MODE_PRIVATE);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE log (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, pkg TEXT, app TEXT, title TEXT, body TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public boolean hushOn() {
        return prefs.getBoolean("on", true);
    }

    public void setHushOn(boolean v) {
        prefs.edit().putBoolean("on", v).apply();
    }

    public boolean blockAll() {
        return prefs.getBoolean("block_all", false);
    }

    public void setBlockAll(boolean v) {
        prefs.edit().putBoolean("block_all", v).apply();
    }

    public boolean isBlocked(String pkg) {
        return prefs.getStringSet("blocked", new HashSet<String>()).contains(pkg);
    }

    public synchronized void setBlocked(String pkg, boolean blocked) {
        Set<String> cur = new HashSet<String>(prefs.getStringSet("blocked", new HashSet<String>()));
        if (blocked) {
            cur.add(pkg);
        } else {
            cur.remove(pkg);
        }
        prefs.edit().putStringSet("blocked", cur).apply();
    }

    public synchronized void log(String pkg, String app, String title, String body) {
        String key = pkg + "|" + title + "|" + body;
        if (key.equals(lastKey)) {
            return;
        }
        lastKey = key;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("ts", System.currentTimeMillis());
        v.put("pkg", pkg);
        v.put("app", app);
        v.put("title", title);
        v.put("body", body);
        db.insert("log", null, v);
        db.execSQL("DELETE FROM log WHERE id NOT IN (SELECT id FROM log ORDER BY id DESC LIMIT 1000)");
    }

    public synchronized List<LogRow> recent(int limit) {
        List<LogRow> out = new ArrayList<LogRow>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT ts, app, title, body FROM log ORDER BY id DESC LIMIT " + limit, null);
        try {
            while (c.moveToNext()) {
                LogRow r = new LogRow();
                r.ts = c.getLong(0);
                r.app = c.getString(1);
                r.title = c.getString(2);
                r.body = c.getString(3);
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public int digestMode() {
        return prefs.getInt("digest_mode", 0);
    }

    public void setDigestMode(int mode) {
        prefs.edit().putInt("digest_mode", mode).apply();
    }

    public long lastDigest() {
        return prefs.getLong("last_digest", 0L);
    }

    public void setLastDigest(long ts) {
        prefs.edit().putLong("last_digest", ts).apply();
    }

    public synchronized List<LogRow> since(long ts, int limit) {
        List<LogRow> out = new ArrayList<LogRow>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT ts, app, title, body FROM log WHERE ts > ? ORDER BY id DESC LIMIT " + limit,
                new String[]{String.valueOf(ts)});
        try {
            while (c.moveToNext()) {
                LogRow r = new LogRow();
                r.ts = c.getLong(0);
                r.app = c.getString(1);
                r.title = c.getString(2);
                r.body = c.getString(3);
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized void clearLog() {
        getWritableDatabase().execSQL("DELETE FROM log");
        lastKey = "";
    }
}
