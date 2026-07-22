package pro.eng.yui.android.osmjppostalmap.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.eng.yui.android.osmjppostalmap.domain.model.PrefMeta;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;

/**
 * {@link PoiDbHelper} をラップしたPOIキャッシュのアクセス層。
 *
 * <p>{@link OsmPoi} は {@code id/type/ver} をカラム、{@code lat/lon/tags} を
 * {@code node} カラムのJSONとして保存し、6引数コンストラクタで復元する
 * （Gson依存を避け、Android組込みの {@code org.json} を使用）。</p>
 */
public class PoiLocalDataSource {

    private final PoiDbHelper helper;

    public PoiLocalDataSource(Context context) {
        this.helper = new PoiDbHelper(context);
    }

    /* ---------- 書き込み ---------- */

    /**
     * 指定した都道府県のPOIを全置換し、最終更新日時を記録する。
     */
    public void upsertPrefecture(int prefCode, String name, List<OsmPoi> pois, long timestamp) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(PoiDbHelper.TABLE_POI,
                    PoiDbHelper.COL_PREF_CODE + " = ?",
                    new String[]{String.valueOf(prefCode)});
            for (OsmPoi poi : pois) {
                db.insertWithOnConflict(PoiDbHelper.TABLE_POI, null,
                        toValues(prefCode, poi), SQLiteDatabase.CONFLICT_REPLACE);
            }
            ContentValues meta = new ContentValues();
            meta.put(PoiDbHelper.COL_META_PREF_CODE, prefCode);
            meta.put(PoiDbHelper.COL_META_NAME, name);
            meta.put(PoiDbHelper.COL_META_LAST_UPDATED, timestamp);
            db.insertWithOnConflict(PoiDbHelper.TABLE_PREF_META, null,
                    meta, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 単一POIを追加/更新する（編集・新規作成の即時反映用）。
     */
    public void upsertPoi(int prefCode, OsmPoi poi) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.insertWithOnConflict(PoiDbHelper.TABLE_POI, null,
                toValues(prefCode, poi), SQLiteDatabase.CONFLICT_REPLACE);
    }

    /* ---------- 読み込み ---------- */

    public boolean hasPrefecture(int prefCode) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(PoiDbHelper.TABLE_PREF_META,
                new String[]{PoiDbHelper.COL_META_PREF_CODE},
                PoiDbHelper.COL_META_PREF_CODE + " = ?",
                new String[]{String.valueOf(prefCode)},
                null, null, null, "1")) {
            return c.moveToFirst();
        }
    }

    public List<OsmPoi> getByPrefCode(int prefCode) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<OsmPoi> result = new ArrayList<>();
        try (Cursor c = db.query(PoiDbHelper.TABLE_POI, null,
                PoiDbHelper.COL_PREF_CODE + " = ?",
                new String[]{String.valueOf(prefCode)},
                null, null, null)) {
            while (c.moveToNext()) {
                OsmPoi poi = fromCursor(c);
                if (poi != null) { result.add(poi); }
            }
        }
        return result;
    }

    /**
     * 座標範囲（Bounding Box）に含まれるPOIを取得する。
     */
    public List<OsmPoi> getByBoundingBox(double latMin, double latMax, double lonMin, double lonMax) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<OsmPoi> result = new ArrayList<>();
        String selection = PoiDbHelper.COL_LAT + " BETWEEN ? AND ? AND " +
                          PoiDbHelper.COL_LON + " BETWEEN ? AND ?";
        String[] selectionArgs = new String[]{
                String.valueOf(latMin), String.valueOf(latMax),
                String.valueOf(lonMin), String.valueOf(lonMax)
        };
        try (Cursor c = db.query(PoiDbHelper.TABLE_POI, null,
                selection, selectionArgs, null, null, null)) {
            while (c.moveToNext()) {
                OsmPoi poi = fromCursor(c);
                if (poi != null) { result.add(poi); }
            }
        }
        return result;
    }

    /**
     * キャッシュされているすべてのPOIを取得する。
     */
    public List<OsmPoi> getAllPois() {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<OsmPoi> result = new ArrayList<>();
        try (Cursor c = db.query(PoiDbHelper.TABLE_POI, null,
                null, null, null, null, null)) {
            while (c.moveToNext()) {
                OsmPoi poi = fromCursor(c);
                if (poi != null) { result.add(poi); }
            }
        }
        return result;
    }

    public List<PrefMeta> getAllPrefMeta() {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<PrefMeta> result = new ArrayList<>();
        try (Cursor c = db.query(PoiDbHelper.TABLE_PREF_META, null,
                null, null, null, null,
                PoiDbHelper.COL_META_NAME + " ASC")) {
            int iCode = c.getColumnIndexOrThrow(PoiDbHelper.COL_META_PREF_CODE);
            int iName = c.getColumnIndexOrThrow(PoiDbHelper.COL_META_NAME);
            int iTs = c.getColumnIndexOrThrow(PoiDbHelper.COL_META_LAST_UPDATED);
            while (c.moveToNext()) {
                result.add(new PrefMeta(c.getInt(iCode), c.getString(iName), c.getLong(iTs)));
            }
        }
        return result;
    }

    /* ---------- シリアライズ ---------- */

    private ContentValues toValues(int prefCode, OsmPoi poi) {
        ContentValues v = new ContentValues();
        v.put(PoiDbHelper.COL_PREF_CODE, prefCode);
        v.put(PoiDbHelper.COL_ID, poi.getId());
        v.put(PoiDbHelper.COL_TYPE, poi.getType());
        v.put(PoiDbHelper.COL_NODE, serializeNode(poi));
        v.put(PoiDbHelper.COL_LAT, poi.getLat());
        v.put(PoiDbHelper.COL_LON, poi.getLon());
        v.put(PoiDbHelper.COL_VER, poi.getVer());
        return v;
    }

    /** {@code lat/lon/tags} を {@code node} カラムのJSONへ変換する。 */
    private String serializeNode(OsmPoi poi) {
        JSONObject json = new JSONObject();
        try {
            json.put("lat", poi.getLat());
            json.put("lon", poi.getLon());
            JSONObject tags = new JSONObject();
            Map<String, String> src = poi.getTags();
            if (src != null) {
                for (Map.Entry<String, String> e : src.entrySet()) {
                    tags.put(e.getKey(), e.getValue());
                }
            }
            json.put("tags", tags);
        } catch (JSONException ignore) {
            // キーはPOI由来で常に非nullのため実際には発生しない
        }
        return json.toString();
    }

    private OsmPoi fromCursor(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(PoiDbHelper.COL_ID));
        String type = c.getString(c.getColumnIndexOrThrow(PoiDbHelper.COL_TYPE));
        long ver = c.getLong(c.getColumnIndexOrThrow(PoiDbHelper.COL_VER));
        double lat = c.getDouble(c.getColumnIndexOrThrow(PoiDbHelper.COL_LAT));
        double lon = c.getDouble(c.getColumnIndexOrThrow(PoiDbHelper.COL_LON));
        String node = c.getString(c.getColumnIndexOrThrow(PoiDbHelper.COL_NODE));
        try {
            JSONObject json = new JSONObject(node);
            Map<String, String> tags = new HashMap<>();
            JSONObject tagsJson = json.optJSONObject("tags");
            if (tagsJson != null) {
                for (java.util.Iterator<String> it = tagsJson.keys(); it.hasNext(); ) {
                    String k = it.next();
                    tags.put(k, tagsJson.optString(k));
                }
            }
            return new OsmPoi(id, lat, lon, type, tags, ver);
        } catch (JSONException e) {
            return null;
        }
    }
}
