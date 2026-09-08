package cn.lisiyi.photokeep.data;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Reconstructible cache, never a source of deletion intent. Keys include MediaStore version and generation. */
public final class HashCache extends SQLiteOpenHelper {
    public HashCache(Context context) { super(context, "media-hashes.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE hashes (cache_key TEXT PRIMARY KEY, sha TEXT NOT NULL)"); }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { throw new IllegalStateException("Unsupported cache version"); }
    public String get(String key) {
        try (Cursor c = getReadableDatabase().query("hashes", new String[]{"sha"}, "cache_key=?", new String[]{key}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }
    public void put(String key, String sha) {
        ContentValues values = new ContentValues(); values.put("cache_key", key); values.put("sha", sha);
        getWritableDatabase().insertWithOnConflict("hashes", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
