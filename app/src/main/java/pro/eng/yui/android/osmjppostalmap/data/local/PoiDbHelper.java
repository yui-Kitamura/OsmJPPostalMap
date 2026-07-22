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
    public static final int DB_VERSION = 2;

    /* poi table */
    public static final String TABLE_POI = "poi";
    public static final String COL_PREF_CODE = "pref_code";
    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";      // "node" / "way"
    public static final String COL_NODE = "node";      // {lat,lon,tags} をJSON化したもの
    public static final String COL_LAT = "lat";
    public static final String COL_LON = "lon";
    public static final String COL_VER = "ver";

    /* pref_meta table */
    public static final String TABLE_PREF_META = "pref_meta";
    public static final String COL_META_PREF_CODE = "pref_code";
    public static final String COL_META_NAME = "name";
    public static final String COL_META_LAST_UPDATED = "last_updated"; // epoch millis

    public PoiDbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_POI + " ("
                + COL_PREF_CODE + " INTEGER NOT NULL, "
                + COL_ID + " INTEGER NOT NULL, "
                + COL_TYPE + " TEXT NOT NULL, "
                + COL_NODE + " TEXT NOT NULL, "
                + COL_LAT + " REAL NOT NULL, "
                + COL_LON + " REAL NOT NULL, "
                + COL_VER + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + COL_TYPE + ", " + COL_ID + ")"
                + ")");
        db.execSQL("CREATE INDEX idx_poi_pref ON " + TABLE_POI + "(" + COL_PREF_CODE + ")");
        db.execSQL("CREATE INDEX idx_poi_coords ON " + TABLE_POI + "(" + COL_LAT + ", " + COL_LON + ")");

        db.execSQL("CREATE TABLE " + TABLE_PREF_META + " ("
                + COL_META_PREF_CODE + " INTEGER PRIMARY KEY, "
                + COL_META_NAME + " TEXT NOT NULL, "
                + COL_META_LAST_UPDATED + " INTEGER NOT NULL"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1のみ。将来のスキーマ変更時に移行処理を追加する。
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POI);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PREF_META);
        onCreate(db);
    }
}
