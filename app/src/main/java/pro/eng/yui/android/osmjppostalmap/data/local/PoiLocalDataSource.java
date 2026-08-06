package pro.eng.yui.android.osmjppostalmap.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import pro.eng.yui.android.osmjppostalmap.domain.Util;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo;
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
     * 指定した都道府県・サブ領域のPOIを全置換し、最終更新日時を記録する。
     */
    public void upsertArea(int prefCode, String subName, String name, List<OsmPoi> pois, long timestamp) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            String subStr = (subName == null) ? "" : subName;
            // 指定のサブ領域のみ削除
            db.delete(PoiDbHelper.TABLE_POI,
                    PoiDbHelper.COL_PREF_CODE + " = ? AND (" + PoiDbHelper.COL_SUB_NAME + " = ? OR " + PoiDbHelper.COL_SUB_NAME + " IS NULL)",
                    new String[]{String.valueOf(prefCode), subStr});

            for (OsmPoi poi : pois) {
                db.insertWithOnConflict(PoiDbHelper.TABLE_POI, null,
                        toValues(prefCode, subName, poi), SQLiteDatabase.CONFLICT_REPLACE);
            }
            ContentValues meta = new ContentValues();
            meta.put(PoiDbHelper.COL_META_PREF_CODE, prefCode);
            meta.put(PoiDbHelper.COL_META_SUB_NAME, subStr);
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
    public void upsertPoi(int prefCode, String subName, OsmPoi poi) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.insertWithOnConflict(PoiDbHelper.TABLE_POI, null,
                toValues(prefCode, subName, poi), SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * 複数のPOIを一括で追加/更新する。
     */
    public void upsertPois(int prefCode, String subName, List<OsmPoi> pois) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (OsmPoi poi : pois) {
                db.insertWithOnConflict(PoiDbHelper.TABLE_POI, null,
                        toValues(prefCode, subName, poi), SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 複数のPOIを一括で追加する。既に存在する場合は無視する。
     */
    public void insertPoisIfNotExist(int prefCode, String subName, List<OsmPoi> pois) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (OsmPoi poi : pois) {
                db.insertWithOnConflict(PoiDbHelper.TABLE_POI, null,
                        toValues(prefCode, subName, poi), SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 指定した都道府県のPOIとメタ情報をまとめて削除する。
     */
    public void deletePrefecture(int prefCode) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            String[] args = new String[]{String.valueOf(prefCode)};
            db.delete(PoiDbHelper.TABLE_POI,
                    PoiDbHelper.COL_PREF_CODE + " = ?", args);
            db.delete(PoiDbHelper.TABLE_PREF_META,
                    PoiDbHelper.COL_META_PREF_CODE + " = ?", args);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void deleteArea(int prefCode, String subName) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            String subStr = (subName == null) ? "" : subName;
            db.delete(PoiDbHelper.TABLE_POI,
                    PoiDbHelper.COL_PREF_CODE + " = ? AND (" + PoiDbHelper.COL_SUB_NAME + " = ? OR " + PoiDbHelper.COL_SUB_NAME + " IS NULL)",
                    new String[]{String.valueOf(prefCode), subStr});
            db.delete(PoiDbHelper.TABLE_PREF_META,
                    PoiDbHelper.COL_META_PREF_CODE + " = ? AND " + PoiDbHelper.COL_META_SUB_NAME + " = ?",
                    new String[]{String.valueOf(prefCode), subStr});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /* ---------- 読み込み ---------- */

    public boolean hasArea(int prefCode, String subName) {
        return getAreaMeta(prefCode, subName) != null;
    }

    public PrefMeta getAreaMeta(int prefCode, String subName) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String subStr = (subName == null) ? "" : subName;
        try (Cursor c = db.query(PoiDbHelper.TABLE_PREF_META,
                null,
                PoiDbHelper.COL_META_PREF_CODE + " = ? AND " + PoiDbHelper.COL_META_SUB_NAME + " = ?",
                new String[]{String.valueOf(prefCode), subStr},
                null, null, null, "1")) {
            if (c.moveToFirst()) {
                int iCode = c.getColumnIndexOrThrow(PoiDbHelper.COL_META_PREF_CODE);
                int iSub = c.getColumnIndexOrThrow(PoiDbHelper.COL_META_SUB_NAME);
                int iName = c.getColumnIndexOrThrow(PoiDbHelper.COL_META_NAME);
                int iTs = c.getColumnIndexOrThrow(PoiDbHelper.COL_META_LAST_UPDATED);
                return new PrefMeta(c.getInt(iCode), c.getString(iName), c.getString(iSub), c.getLong(iTs));
            }
        }
        return null;
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

    public List<OsmPoi> getByArea(int prefCode, String subName) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<OsmPoi> result = new ArrayList<>();
        String subStr = (subName == null) ? "" : subName;
        String selection = PoiDbHelper.COL_PREF_CODE + " = ? AND (" + PoiDbHelper.COL_SUB_NAME + " = ? OR " + PoiDbHelper.COL_SUB_NAME + " IS NULL)";
        String[] selectionArgs = new String[]{String.valueOf(prefCode), subStr};
        try (Cursor c = db.query(PoiDbHelper.TABLE_POI, null,
                selection, selectionArgs,
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
        // 1地点に複数の都道府県が判定される可能性を考慮し、IDとTypeで重複排除する
        Map<String, OsmPoi> result = new java.util.LinkedHashMap<>();
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
                if (poi != null) {
                    String key = poi.getType() + ":" + poi.getId();
                    result.put(key, poi);
                }
            }
        }
        return new ArrayList<>(result.values());
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

    /**
     * 条件を指定してPOIを検索する。
     * @param query 検索文字列
     * @param postOnly 郵便局/ポストのみに絞り込むか
     * @param searchAddress 住所カラムも検索対象に含めるか
     */
    public List<OsmPoi> searchPois(String query, boolean postOnly, boolean searchAddress) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<OsmPoi> result = new ArrayList<>();

        StringBuilder selection = new StringBuilder();
        List<String> args = new ArrayList<>();

        if (postOnly) {
            selection.append("(");
            selection.append(PoiDbHelper.COL_AMENITY).append(" = ? OR ").append(PoiDbHelper.COL_AMENITY).append(" = ?");
            // 移行期間用：カラムが未設定の場合のフォールバック
            selection.append(" OR ").append(PoiDbHelper.COL_NODE).append(" LIKE ? OR ").append(PoiDbHelper.COL_NODE).append(" LIKE ?");
            selection.append(")");
            args.add("post_office");
            args.add("post_box");
            args.add("%\"amenity\":\"post_office\"%");
            args.add("%\"amenity\":\"post_box\"%");
        }

        if (query != null && !query.trim().isEmpty()) {
            String q = query.trim();
            if (selection.length() > 0) selection.append(" AND ");
            selection.append("(");
            // 名前一致
            selection.append(PoiDbHelper.COL_NAME).append(" LIKE ?");
            args.add("%" + q + "%");

            // かな一致
            selection.append(" OR ").append(PoiDbHelper.COL_KANA).append(" LIKE ?");
            args.add("%" + q + "%");

            if (searchAddress) {
                // 住所一致
                selection.append(" OR ").append(PoiDbHelper.COL_ADDR_TEXT).append(" LIKE ?");
                args.add("%" + q + "%");
            }

            // その他タグ（JSON）一致
            selection.append(" OR ").append(PoiDbHelper.COL_NODE).append(" LIKE ?");
            args.add("%" + q + "%");

            selection.append(")");
        }

        try (Cursor c = db.query(PoiDbHelper.TABLE_POI, null,
                selection.length() > 0 ? selection.toString() : null,
                args.isEmpty() ? null : args.toArray(new String[0]),
                null, null, null)) {
            while (c.moveToNext()) {
                OsmPoi poi = fromCursor(c);
                if (poi != null) { result.add(poi); }
            }
        }
        return result;
    }

    /**
     * バージョン8, 9で追加されたカラムを既存データに反映する。
     */
    public void migrateToLatest() {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.query(PoiDbHelper.TABLE_POI, null,
                    PoiDbHelper.COL_NAME + " IS NULL OR " + PoiDbHelper.COL_KANA + " IS NULL", null, null, null, null)) {
                while (c.moveToNext()) {
                    OsmPoi poi = fromCursor(c);
                    if (poi != null) {
                        ContentValues v = new ContentValues();
                        Map<String, String> tags = poi.getTags();
                        v.put(PoiDbHelper.COL_NAME, tags.get("name"));
                        v.put(PoiDbHelper.COL_AMENITY, tags.get("amenity"));
                        v.put(PoiDbHelper.COL_ADDR_TEXT, JpPostalUtil.getAddressText(tags));
                        v.put(PoiDbHelper.COL_KANA, Util.getKana(poi));
                        db.update(PoiDbHelper.TABLE_POI, v,
                                PoiDbHelper.COL_TYPE + " = ? AND " + PoiDbHelper.COL_ID + " = ?",
                                new String[]{poi.getType(), String.valueOf(poi.getId())});
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<PrefMeta> getAllPrefMeta() {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<PrefMeta> result = new ArrayList<>();
        try (Cursor c = db.query(PoiDbHelper.TABLE_PREF_META, null,
                null, null, null, null,
                PoiDbHelper.COL_META_PREF_CODE + " ASC, " + PoiDbHelper.COL_META_SUB_NAME + " ASC")) {
            int iCode = c.getColumnIndexOrThrow(PoiDbHelper.COL_META_PREF_CODE);
            int iSub = c.getColumnIndexOrThrow(PoiDbHelper.COL_META_SUB_NAME);
            int iName = c.getColumnIndexOrThrow(PoiDbHelper.COL_META_NAME);
            int iTs = c.getColumnIndexOrThrow(PoiDbHelper.COL_META_LAST_UPDATED);
            while (c.moveToNext()) {
                result.add(new PrefMeta(c.getInt(iCode), c.getString(iName), c.getString(iSub), c.getLong(iTs)));
            }
        }
        return result;
    }

    /* ---------- グリッドキャッシュ ---------- */

    public void upsertGridPref(long gridKey, String prefNames) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(PoiDbHelper.COL_GRID_KEY, gridKey);
        v.put(PoiDbHelper.COL_PREF_NAME, prefNames);
        db.insertWithOnConflict(PoiDbHelper.TABLE_GRID_PREF, null,
                v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void upsertPlaces(List<PlaceInfo> places) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(PoiDbHelper.TABLE_PLACE, null, null);
            for (PlaceInfo place : places) {
                ContentValues v = new ContentValues();
                v.put(PoiDbHelper.COL_PLACE_PREF_CODE, place.getPrefCode());
                v.put(PoiDbHelper.COL_PLACE_SUB_NAME, place.getSubName());
                v.put(PoiDbHelper.COL_PLACE_NAME, place.getName());
                v.put(PoiDbHelper.COL_PLACE_LAT, place.getLat());
                v.put(PoiDbHelper.COL_PLACE_LON, place.getLon());
                v.put(PoiDbHelper.COL_PLACE_MIN_LAT, place.getMinLat());
                v.put(PoiDbHelper.COL_PLACE_MAX_LAT, place.getMaxLat());
                v.put(PoiDbHelper.COL_PLACE_MIN_LON, place.getMinLon());
                v.put(PoiDbHelper.COL_PLACE_MAX_LON, place.getMaxLon());
                v.put(PoiDbHelper.COL_PLACE_KANA, place.getKana());
                db.insert(PoiDbHelper.TABLE_PLACE, null, v);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<PlaceInfo> searchPlaces(String query) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<PlaceInfo> result = new ArrayList<>();
        String selection = PoiDbHelper.COL_PLACE_NAME + " LIKE ? OR " + PoiDbHelper.COL_PLACE_KANA + " LIKE ?";
        String q = "%" + query + "%";
        String[] args = new String[]{q, q};
        try (Cursor c = db.query(PoiDbHelper.TABLE_PLACE, null, selection, args, null, null, null)) {
            while (c.moveToNext()) {
                result.add(fromPlaceCursor(c));
            }
        }
        return result;
    }

    public List<PlaceInfo> getAllPlaces() {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<PlaceInfo> result = new ArrayList<>();
        try (Cursor c = db.query(PoiDbHelper.TABLE_PLACE, null, null, null, null, null, null)) {
            while (c.moveToNext()) {
                result.add(fromPlaceCursor(c));
            }
        }
        return result;
    }

    private PlaceInfo fromPlaceCursor(Cursor c) {
        int iLat = c.getColumnIndexOrThrow(PoiDbHelper.COL_PLACE_LAT);
        int iLon = c.getColumnIndexOrThrow(PoiDbHelper.COL_PLACE_LON);
        int iSub = c.getColumnIndexOrThrow(PoiDbHelper.COL_PLACE_SUB_NAME);
        Double lat = c.isNull(iLat) ? null : c.getDouble(iLat);
        Double lon = c.isNull(iLon) ? null : c.getDouble(iLon);
        String subName = c.isNull(iSub) ? null : c.getString(iSub);

        return new PlaceInfo(
                c.getInt(c.getColumnIndexOrThrow(PoiDbHelper.COL_PLACE_PREF_CODE)),
                subName,
                c.getString(c.getColumnIndexOrThrow(PoiDbHelper.COL_PLACE_NAME)),
                c.getString(c.getColumnIndexOrThrow(PoiDbHelper.COL_PLACE_KANA)),
                lat,
                lon,
                c.getDouble(c.getColumnIndexOrThrow(PoiDbHelper.COL_PLACE_MIN_LAT)),
                c.getDouble(c.getColumnIndexOrThrow(PoiDbHelper.COL_PLACE_MAX_LAT)),
                c.getDouble(c.getColumnIndexOrThrow(PoiDbHelper.COL_PLACE_MIN_LON)),
                c.getDouble(c.getColumnIndexOrThrow(PoiDbHelper.COL_PLACE_MAX_LON))
        );
    }

    public Map<Long, Set<String>> getAllGridPrefs() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Map<Long, Set<String>> result = new HashMap<>();
        try (Cursor c = db.query(PoiDbHelper.TABLE_GRID_PREF, null,
                null, null, null, null, null)) {
            int iKey = c.getColumnIndexOrThrow(PoiDbHelper.COL_GRID_KEY);
            int iName = c.getColumnIndexOrThrow(PoiDbHelper.COL_PREF_NAME);
            while (c.moveToNext()) {
                long key = c.getLong(iKey);
                String namesStr = c.getString(iName);
                Set<String> set = new HashSet<>();
                if (namesStr != null && !namesStr.isEmpty()) {
                    for (String s : namesStr.split(",")) {
                        set.add(s.trim());
                    }
                }
                result.put(key, set);
            }
        }
        return result;
    }

    /* ---------- シリアライズ ---------- */

    private ContentValues toValues(int prefCode, String subName, OsmPoi poi) {
        ContentValues v = new ContentValues();
        v.put(PoiDbHelper.COL_PREF_CODE, prefCode);
        v.put(PoiDbHelper.COL_SUB_NAME, subName);
        v.put(PoiDbHelper.COL_ID, poi.getId());
        v.put(PoiDbHelper.COL_TYPE, poi.getType());
        v.put(PoiDbHelper.COL_NODE, serializeNode(poi));
        v.put(PoiDbHelper.COL_LAT, poi.getLat());
        v.put(PoiDbHelper.COL_LON, poi.getLon());
        v.put(PoiDbHelper.COL_VER, poi.getVer());

        Map<String, String> tags = poi.getTags();
        if (tags != null) {
            v.put(PoiDbHelper.COL_NAME, tags.get("name"));
            v.put(PoiDbHelper.COL_AMENITY, tags.get("amenity"));
            v.put(PoiDbHelper.COL_ADDR_TEXT, JpPostalUtil.getAddressText(tags));
            v.put(PoiDbHelper.COL_KANA, Util.getKana(poi));
        }
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
