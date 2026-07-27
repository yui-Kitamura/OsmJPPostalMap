package pro.eng.yui.android.osmjppostalmap.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * POIキャッシュ用のSQLiteスキーマ定義。
 *
 * <ul>
 *   <li>{@code poi} … fetchしたPOI本体（都道府県ごと）。</li>
 *   <li>{@code pref_meta} … 都道府県ごとの最終更新日時（ダイアログ表示用）。</li>
 * </ul>
 */
public class PoiDbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "poi_cache.db";
    public static final int DB_VERSION = 4;

    /* poi table */
    public static final String TABLE_POI = "poi";
    public static final String COL_PREF_CODE = "pref_code";
    public static final String COL_SUB_NAME = "sub_name";
    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";      // "node" / "way"
    public static final String COL_NODE = "node";      // {lat,lon,tags} をJSON化したもの
    public static final String COL_LAT = "lat";
    public static final String COL_LON = "lon";
    public static final String COL_VER = "ver";

    /* pref_meta table */
    public static final String TABLE_PREF_META = "pref_meta";
    public static final String COL_META_PREF_CODE = "pref_code";
    public static final String COL_META_SUB_NAME = "sub_name";
    public static final String COL_META_NAME = "name";
    public static final String COL_META_LAST_UPDATED = "last_updated"; // epoch millis

    /* grid_pref_cache table */
    public static final String TABLE_GRID_PREF = "grid_pref_cache";
    public static final String COL_GRID_KEY = "grid_key";
    public static final String COL_PREF_NAME = "pref_name";

    public PoiDbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_POI + " ("
                + COL_PREF_CODE + " INTEGER NOT NULL, "
                + COL_SUB_NAME + " TEXT, "
                + COL_ID + " INTEGER NOT NULL, "
                + COL_TYPE + " TEXT NOT NULL, "
                + COL_NODE + " TEXT NOT NULL, "
                + COL_LAT + " REAL NOT NULL, "
                + COL_LON + " REAL NOT NULL, "
                + COL_VER + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + COL_TYPE + ", " + COL_ID + ")"
                + ")");
        db.execSQL("CREATE INDEX idx_poi_pref ON " + TABLE_POI + "(" + COL_PREF_CODE + ")");
        db.execSQL("CREATE INDEX idx_poi_pref_sub ON " + TABLE_POI + "(" + COL_PREF_CODE + ", " + COL_SUB_NAME + ")");
        db.execSQL("CREATE INDEX idx_poi_coords ON " + TABLE_POI + "(" + COL_LAT + ", " + COL_LON + ")");

        db.execSQL("CREATE TABLE " + TABLE_PREF_META + " ("
                + COL_META_PREF_CODE + " INTEGER NOT NULL, "
                + COL_META_SUB_NAME + " TEXT NOT NULL DEFAULT '', "
                + COL_META_NAME + " TEXT NOT NULL, "
                + COL_META_LAST_UPDATED + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + COL_META_PREF_CODE + ", " + COL_META_SUB_NAME + ")"
                + ")");

        db.execSQL("CREATE TABLE " + TABLE_GRID_PREF + " ("
                + COL_GRID_KEY + " INTEGER PRIMARY KEY, "
                + COL_PREF_NAME + " TEXT"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_GRID_PREF + " ("
                    + COL_GRID_KEY + " INTEGER PRIMARY KEY, "
                    + COL_PREF_NAME + " TEXT"
                    + ")");
        }
        if (oldVersion < 4) {
            // TABLE_POI: sub_name カラム追加
            try {
                db.execSQL("ALTER TABLE " + TABLE_POI + " ADD COLUMN " + COL_SUB_NAME + " TEXT");
            } catch (Exception ignored) {}
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_poi_pref_sub ON " + TABLE_POI + "(" + COL_PREF_CODE + ", " + COL_SUB_NAME + ")");

            // TABLE_PREF_META: PRIMARY KEY 変更 (pref_code -> pref_code, sub_name)
            db.execSQL("CREATE TABLE pref_meta_new ("
                    + COL_META_PREF_CODE + " INTEGER NOT NULL, "
                    + COL_META_SUB_NAME + " TEXT NOT NULL DEFAULT '', "
                    + COL_META_NAME + " TEXT NOT NULL, "
                    + COL_META_LAST_UPDATED + " INTEGER NOT NULL, "
                    + "PRIMARY KEY (" + COL_META_PREF_CODE + ", " + COL_META_SUB_NAME + ")"
                    + ")");
            // 既存データの移行。既存は sub_name が無いので空文字。
            db.execSQL("INSERT INTO pref_meta_new (" + COL_META_PREF_CODE + ", " + COL_META_SUB_NAME + ", " + COL_META_NAME + ", " + COL_META_LAST_UPDATED + ") "
                    + "SELECT " + COL_META_PREF_CODE + ", '', " + COL_META_NAME + ", " + COL_META_LAST_UPDATED + " FROM " + TABLE_PREF_META);
            db.execSQL("DROP TABLE " + TABLE_PREF_META);
            db.execSQL("ALTER TABLE pref_meta_new RENAME TO " + TABLE_PREF_META);
        }
    }
}
