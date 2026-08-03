package pro.eng.yui.android.osmjppostalmap.data.repository;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.api.osm.ChangeSetInfo;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.BBox;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;

import com.google.gson.Gson;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import pro.eng.yui.android.osmjppostalmap.BuildConfig;
import pro.eng.yui.android.osmjppostalmap.data.local.PoiLocalDataSource;
import pro.eng.yui.android.osmjppostalmap.data.remote.DataDateApi;
import pro.eng.yui.android.osmjppostalmap.data.remote.DataDateResponse;
import pro.eng.yui.android.osmjppostalmap.data.remote.OverpassApi;
import pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo;
import pro.eng.yui.android.osmjppostalmap.domain.model.PrefMeta;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PoiRepositoryImpl implements PoiRepository {

    private final MutableLiveData<List<OsmPoi>> poisLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> successLiveData = new MutableLiveData<>();
    private final MutableLiveData<Long> cooldownRemainingLiveData = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> loadingStatusLiveData = new MutableLiveData<>("");
    private final MutableLiveData<Location> locationLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> currentPrefectureLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> currentSubAreaLiveData = new MutableLiveData<>();
    private LocationManager locationManager;
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            locationLiveData.postValue(location);
            updateCurrentArea(location);
        }
    };
    private Context context;
    private String accessToken;
    public void setAccessToken(String token) {
        this.accessToken = token;
    }
    private static long lastFetchTime = 0;
    /** APIコールの最小間隔ms */
    private static final long MIN_INTERVAL_MS = 10 * 1000;
    /** 一度に描画する上限POI数（超過時はズーム要求） */
    private static final int MAX_RENDER = 500;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final CountDownLatch boundaryLatch = new CountDownLatch(1);
    private Runnable cooldownRunnable;
    private final AtomicInteger pendingOperations = new AtomicInteger();

    /** ローカルキャッシュ（{@link #init(Context)} で初期化） */
    private PoiLocalDataSource local;
    /** Overpass API client */
    private OverpassApi overpassApi;

    private OverpassApi getOverpassApi() {
        if (overpassApi == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://overpass-api.de/api/")
                    .addConverterFactory(retrofit2.converter.scalars.ScalarsConverterFactory.create())
                    .build();
            overpassApi = retrofit.create(OverpassApi.class);
        }
        return overpassApi;
    }

    /** 現在描画対象としている都道府県コード（再描画の再構成に使用） */
    private final Set<Integer> currentPrefCodes = new LinkedHashSet<>();
    /** 現在表示・計算対象としている地理的範囲 */
    private double activeLatMin = -90, activeLatMax = 90, activeLonMin = -180, activeLonMax = 180;
    private boolean areaFilterEnabled = false;

    /** 逆ジオコーディング結果のキャッシュ。固定座標系（1海里）へのアライメントによりヒット率を高める。 */
    private final Map<Long, Set<String>> gridPrefCache = new java.util.concurrent.ConcurrentHashMap<>();
    /** 都道府県の境界ボックス（BBox）キャッシュ。 */
    private final Map<String, BBox> prefBoundaryCache = new java.util.concurrent.ConcurrentHashMap<>();
    /** 都道府県コードから都道府県名へのマッピング */
    private final Map<Integer, String> prefCodeNameMap = new java.util.concurrent.ConcurrentHashMap<>();
    /** 都道府県コード -> (サブエリアコード -> サブエリア名) のマッピング */
    private final Map<Integer, Map<Integer, String>> prefSubCodeNameMap = new java.util.concurrent.ConcurrentHashMap<>();
    /** 市区町村情報のキャッシュ（検索用） */
    private final List<PlaceInfo> placeCache = Collections.synchronizedList(new ArrayList<>());

    private static PoiRepositoryImpl instance;

    public static synchronized PoiRepositoryImpl getInstance() {
        if (instance == null) {
            instance = new PoiRepositoryImpl();
        }
        return instance;
    }

    /**
     * ローカルキャッシュ（SQLite）を初期化する。アプリ起動時に一度呼ぶこと。
     * 既存の {@link #getInstance()} 呼び出しを壊さないため、Context注入はここで行う。
     */
    public static synchronized void init(Context context) {
        PoiRepositoryImpl repo = getInstance();
        repo.context = context.getApplicationContext();
        if (repo.local == null) {
            repo.local = new PoiLocalDataSource(repo.context);
            repo.preloadBoundaries();
            repo.loadAllFromCache();
            repo.loadGridCacheFromDb();
            repo.loadPlacesFromCache();
        }
        if (repo.locationManager == null) {
            repo.locationManager = (LocationManager) repo.context.getSystemService(Context.LOCATION_SERVICE);
        }
    }

    private PoiRepositoryImpl() {
        this(null);
    }

    private PoiRepositoryImpl(String accessToken) {
        this.accessToken = accessToken;
    }

    private void preloadBoundaries() {
        runOnExecutor("境界データを読み込み中", () -> {
            try {
                Map<String, Integer> prefNameAndCode = JpPostalUtil.getPrefectures().join();
                Map<Integer, String> prefCodeAndName = new HashMap<>();
                for (Map.Entry<String, Integer> entry : prefNameAndCode.entrySet()) {
                    prefCodeAndName.put(entry.getValue(), entry.getKey());
                }
                prefCodeNameMap.putAll(prefCodeAndName);

                String bboxJson = JpPostalUtil.getRawPrefecturesJson().join();
                if (bboxJson == null || bboxJson.isEmpty()) {
                    boundaryLatch.countDown();
                    return;
                }

                Gson gson = new Gson();
                JsonObject bboxRoot = gson.fromJson(bboxJson, JsonObject.class);
                if (bboxRoot == null) {
                    boundaryLatch.countDown();
                    return;
                }

                for (Map.Entry<String, JsonElement> prefEntry : bboxRoot.entrySet()) {
                    int prefCode = Integer.parseInt(prefEntry.getKey());

                    String prefName = prefCodeAndName.get(prefCode);
                    if (prefName == null) { continue; }

                    Map<Integer, String> subNamesByCode = new HashMap<>();

                    JsonObject prefObj = prefEntry.getValue().getAsJsonObject();
                    if(prefObj.keySet().size() > 1){
                        Map<String, Integer> subCodesByName = JpPostalUtil.getSubAreas(prefName).join();
                        if (subCodesByName != null) {
                            for (Map.Entry<String, Integer> subEntry : subCodesByName.entrySet()) {
                                subNamesByCode.put(subEntry.getValue(), subEntry.getKey());
                            }
                        }
                    }

                    JsonObject subObj = prefObj.getAsJsonObject("sub");
                    boolean hasOthers = subObj.keySet().size() > 1;

                    if (!subNamesByCode.isEmpty()) {
                        prefSubCodeNameMap.put(prefCode, new HashMap<>(subNamesByCode));
                    }

                    for (Map.Entry<String, JsonElement> subEntry : subObj.entrySet()) {
                        String subCodeKey = subEntry.getKey();
                        int subCode = Integer.parseInt(subCodeKey);
                        if (hasOthers && subCode == 0) {
                            continue;
                        }
                        String subName = subNamesByCode.get(subCode);

                        JsonObject boundaryObj = subEntry.getValue().getAsJsonObject();
                        if (hasBBox(boundaryObj)) {
                            if(subName == null) {
                                prefBoundaryCache.put(prefName, readBBox(boundaryObj));
                            }else{
                                prefBoundaryCache.put(prefName + ":" + subName, readBBox(boundaryObj));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("PoiRepository", "Failed to preload boundaries", e);
                errorLiveData.postValue("境界データの読み込みに失敗しました");
            } finally {
                boundaryLatch.countDown();
            }
        });
    }

    private static boolean hasBBox(JsonObject obj) {
        return obj != null
                && obj.has("minLat")
                && obj.has("minLon")
                && obj.has("maxLat")
                && obj.has("maxLon");
    }

    private static BBox readBBox(JsonObject obj) {
        return new BBox(
                obj.get("minLat").getAsDouble(),
                obj.get("minLon").getAsDouble(),
                obj.get("maxLat").getAsDouble(),
                obj.get("maxLon").getAsDouble()
        );
    }

    /* ---------- 取得系 ---------- */

    @Override
    public LiveData<List<OsmPoi>> getPoisLiveData() {
        return poisLiveData;
    }

    /**
     * 単一スレッドExecutor上でタスクを実行する。タスクが例外を投げても
     * Executorのワーカースレッドが死なないよう保護する。
     */
    private void runOnExecutor(String operation, Runnable task) {
        synchronized (pendingOperations) {
            if (pendingOperations.getAndIncrement() == 0) {
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    loadingLiveData.setValue(true);
                } else {
                    loadingLiveData.postValue(true);
                }
            }
        }
        loadingStatusLiveData.postValue(operation);
        executor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                Log.e("PoiRepository", "Error in " + operation, e);
                errorLiveData.postValue("処理中にエラーが発生しました");
            } finally {
                synchronized (pendingOperations) {
                    if (pendingOperations.decrementAndGet() == 0) {
                        loadingLiveData.postValue(false);
                        loadingStatusLiveData.postValue("");
                    }
                }
            }
        });
    }

    @Override
    public void loadPoisForArea(double[][] latLonPoints, boolean forceNotify) {
        loadPoisForArea(latLonPoints, forceNotify, null, null);
    }

    @Override
    public void loadPoisForArea(double[][] latLonPoints, boolean forceNotify, String hintPrefName, String hintSubName) {
        if (latLonPoints == null || latLonPoints.length == 0) { return; }

        this.currentPrefCodes.clear();

        // 表示・計算対象範囲の更新
        double latMin = Double.MAX_VALUE, latMax = -Double.MAX_VALUE;
        double lonMin = Double.MAX_VALUE, lonMax = -Double.MAX_VALUE;
        for (double[] p : latLonPoints) {
            latMin = Math.min(latMin, p[0]);
            latMax = Math.max(latMax, p[0]);
            lonMin = Math.min(lonMin, p[1]);
            lonMax = Math.max(lonMax, p[1]);
        }
        this.activeLatMin = latMin;
        this.activeLatMax = latMax;
        this.activeLonMin = lonMin;
        this.activeLonMax = lonMax;
        this.areaFilterEnabled = true;

        final double fLatMin = latMin;
        final double fLatMax = latMax;
        final double fLonMin = lonMin;
        final double fLonMax = lonMax;

        runOnExecutor("表示エリアを判定中", () -> {
            try {
                // 境界データの読み込み完了を待機（最大5秒）
                boundaryLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 表示範囲にかかるエリア（都道府県またはサブ領域）を判定する。
            Set<String> cachedAreaKeys;
            if (!prefBoundaryCache.isEmpty()) {
                // BBoxを用いたローカル判定（高速）
                cachedAreaKeys = findIntersectingAreas(fLatMin, fLatMax, fLonMin, fLonMax);
            } else {
                cachedAreaKeys = new HashSet<>();
            }

            if (hintPrefName != null) {
                if (hintSubName != null) {
                    cachedAreaKeys.add(hintPrefName + ":" + hintSubName);
                } else {
                    cachedAreaKeys.add(hintPrefName);
                }
            }

            if (cachedAreaKeys.isEmpty()) {
                postCombined();
                return;
            }

            // 新規フェッチが必要なエリアを特定
            List<String[]> neededAreas = new ArrayList<>();

            for (String key : cachedAreaKeys) {
                String prefName;
                String subName;

                if (key.contains(":")) {
                    String[] parts = key.split(":");
                    prefName = parts[0];
                    subName = parts[1];
                } else {
                    prefName = key;
                    subName = null;
                }

                Integer code = null;
                for(Map.Entry<Integer, String> e : prefCodeNameMap.entrySet()){
                    if(e.getValue().equals(prefName)){
                        code = e.getKey();
                        break;
                    }
                }
                if (code == null || code < 0) {
                    continue;
                }

                currentPrefCodes.add(code);

                // サブエリアの存在を常に確認し、ある場合はサブ単位での取得を優先する
                Map<Integer, String> subAll = prefSubCodeNameMap.get(code);
                if (subAll != null && !subAll.isEmpty()) {
                    if (subName != null) {
                        // 特定のサブエリアが判明している場合
                        if (local != null && !local.hasArea(code, subName)) {
                            neededAreas.add(new String[]{prefName, subName});
                        }
                    } else {
                        // サブエリアがあるはずだがキーが都道府県名のみの場合、交差するサブエリアを全て特定
                        for (String sub : subAll.values()) {
                            String subKey = prefName + ":" + sub;
                            BBox subBBox = prefBoundaryCache.get(subKey);
                            if (subBBox != null) {
                                if (fLatMin <= subBBox.getMaxLat() && fLatMax >= subBBox.getMinLat() &&
                                        fLonMin <= subBBox.getMaxLon() && fLonMax >= subBBox.getMinLon()) {
                                    if (local != null && !local.hasArea(code, sub)) {
                                        neededAreas.add(new String[]{prefName, sub});
                                    }
                                }
                            } else {
                                // BBoxがない場合は念のため全て追加
                                if (local != null && !local.hasArea(code, sub)) {
                                    neededAreas.add(new String[]{prefName, sub});
                                }
                            }
                        }
                    }
                } else {
                    // サブエリアがない都道府県の場合
                    if (local != null && !local.hasArea(code, null)) {
                        neededAreas.add(new String[]{prefName, null});
                    }
                }
            }

            if (neededAreas.isEmpty()) {
                postCombined();
                return;
            }

            // クールダウン判定（新規ネットワーク取得が発生する場合のみ適用）
            long currentTime = System.currentTimeMillis();
            if (!forceNotify && currentTime - lastFetchTime < MIN_INTERVAL_MS) {
                postCombined();
                return;
            }
            lastFetchTime = currentTime;
            startCooldownTimer();

            for (String[] area : neededAreas) {
                Integer code = null;
                for(Map.Entry<Integer, String> e : prefCodeNameMap.entrySet()){
                    if(e.getValue().equals(area[0])){
                        code = e.getKey();
                        break;
                    }
                }
                if (code != null) {
                    loadArea(code, area[0], area[1], false);
                }
            }

            // 新しくフェッチしたデータがあるため再反映
            postCombined();
        });
    }

    @Override
    public void refreshPrefecture(int prefCode, String prefName, String subName) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFetchTime < MIN_INTERVAL_MS) {
            errorLiveData.postValue("しばらく待ってから再度お試しください");
            return;
        }
        lastFetchTime = currentTime;
        startCooldownTimer();

        String label = (subName == null) ? prefName : prefName + " " + subName;
        runOnExecutor(label + "のデータを再取得中", () -> {
            loadArea(prefCode, prefName, subName, true); // 強制ネットワーク取得
            postCombined();
        });
    }

    @Override
    public void deletePrefectureCache(int prefCode, String subName) {
        runOnExecutor("キャッシュを削除中", () -> {
            if (local == null) { return; }
            if (subName == null) {
                local.deletePrefecture(prefCode);
                currentPrefCodes.remove(prefCode);
            } else {
                // TODO: local.deleteArea(prefCode, subName) を実装すべきだが、
                // 現状は PoiLocalDataSource.upsertArea で削除ロジックがあるのでそれを利用するか
                // とりあえず table_pref_meta と table_poi から消す
                local.deleteArea(prefCode, subName);
            }
            postCombined();
        });
    }

    @Override
    public List<PrefMeta> getSavedPrefectures() {
        return local.getAllPrefMeta();
    }

    @Override
    public List<OsmPoi> getAllCachedPois() {
        return local.getAllPois();
    }

    @Override
    public void fetchCityData() {
        runOnExecutor("地名データを読み込み中", () -> {
            try {
                // 境界データの読み込みを待機
                boundaryLatch.await(10, TimeUnit.SECONDS);

                String cityJson = JpPostalUtil.getRawCityJson().join();
                if (cityJson == null || cityJson.isEmpty()) {
                    return;
                }
                JSONArray array = new JSONArray(cityJson);
                List<PlaceInfo> places = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    
                    String isInStr = obj.optString("is_in", null);
                    if (isInStr == null) {
                        isInStr = String.valueOf(obj.optInt("is_in", obj.optInt("prefCode", -1)));
                    }
                    if ("-1".equals(isInStr) || isInStr.isEmpty()) continue;

                    int prefCode;
                    String subName = null;
                    if (isInStr.contains("_")) {
                        String[] parts = isInStr.split("_");
                        prefCode = Integer.parseInt(parts[0]);
                        int subCode = Integer.parseInt(parts[1]);
                        Map<Integer, String> subMap = prefSubCodeNameMap.get(prefCode);
                        if (subMap != null) {
                            subName = subMap.get(subCode);
                        }
                    } else {
                        prefCode = Integer.parseInt(isInStr);
                    }

                    String name = obj.has("city") ? obj.getString("city") : obj.getString("name");

                    Double lat = null;
                    Double lon = null;
                    if (obj.has("label")) {
                        JSONObject label = obj.getJSONObject("label");
                        lat = label.getDouble("lat");
                        lon = label.getDouble("lon");
                    } else if (obj.has("lat")) {
                        lat = obj.getDouble("lat");
                        lon = obj.getDouble("lon");
                    }

                    double minLat, maxLat, minLon, maxLon;
                    if (obj.has("bbox")) {
                        JSONObject bbox = obj.getJSONObject("bbox");
                        minLat = bbox.getDouble("minLat");
                        maxLat = bbox.getDouble("maxLat");
                        minLon = bbox.getDouble("minLon");
                        maxLon = bbox.getDouble("maxLon");
                    } else {
                        minLat = obj.getDouble("minLat");
                        maxLat = obj.getDouble("maxLat");
                        minLon = obj.getDouble("minLon");
                        maxLon = obj.getDouble("maxLon");
                    }

                    places.add(new PlaceInfo(
                            prefCode, subName, name, lat, lon, minLat, maxLat, minLon, maxLon
                    ));
                }
                if (local != null) {
                    local.upsertPlaces(places);
                    synchronized (placeCache) {
                        placeCache.clear();
                        placeCache.addAll(places);
                    }
                }
            } catch (Exception e) {
                Log.e("PoiRepository", "Failed to fetch city data", e);
            }
        });
    }

    @Override
    public void fetchOfficeData() {
        runOnExecutor("郵便局データを読み込み中", () -> {
            try {
                // 境界データの読み込みを待機
                boundaryLatch.await(10, TimeUnit.SECONDS);

                String officeJson = JpPostalUtil.getRawOfficeJson().join();
                if (officeJson == null || officeJson.isEmpty()) {
                    return;
                }
                JSONArray array = new JSONArray(officeJson);
                // Map<PrefCode, Map<SubNameKey, List<OsmPoi>>>
                Map<Integer, Map<String, List<OsmPoi>>> groupedPois = new HashMap<>();

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String name = obj.getString("name");

                    String isInStr = obj.optString("is_in", null);
                    if (isInStr == null) {
                        isInStr = String.valueOf(obj.optInt("is_in", -1));
                    }
                    if ("-1".equals(isInStr) || isInStr.isEmpty()) continue;

                    int prefCode;
                    String subName = null;

                    if (isInStr.contains("_")) {
                        String[] parts = isInStr.split("_");
                        prefCode = Integer.parseInt(parts[0]);
                        int subCode = Integer.parseInt(parts[1]);
                        Map<Integer, String> subMap = prefSubCodeNameMap.get(prefCode);
                        if (subMap != null) {
                            subName = subMap.get(subCode);
                        }
                    } else {
                        prefCode = Integer.parseInt(isInStr);
                    }

                    String poiType = obj.optString("poiType", "node");
                    long poiId = obj.getLong("poiId");

                    Map<String, String> tags = new HashMap<>();
                    tags.put("name", name);
                    tags.put("amenity", "post_office");

                    String prefName = prefCodeNameMap.get(prefCode);
                    if (prefName != null) {
                        tags.put("addr:prefecture", prefName);
                    }

                    // v0 data as requested. lat/lon might not be present in raw json.
                    double lat = obj.optDouble("lat", 0.0);
                    double lon = obj.optDouble("lon", 0.0);

                    // 座標がない場合は都道府県またはサブエリアの代表点をセットする
                    if (lat == 0.0 && lon == 0.0) {
                        String boundaryKey = (subName == null) ? prefName : prefName + ":" + subName;
                        if (boundaryKey != null) {
                            BBox bbox = prefBoundaryCache.get(boundaryKey);
                            if (bbox != null) {
                                lat = (bbox.getMinLat() + bbox.getMaxLat()) / 2.0;
                                lon = (bbox.getMinLon() + bbox.getMaxLon()) / 2.0;
                            }
                        }
                    }
                    OsmPoi poi = new OsmPoi(poiId, lat, lon, poiType, tags, 0);

                    if (!groupedPois.containsKey(prefCode)) {
                        groupedPois.put(prefCode, new HashMap<>());
                    }
                    Map<String, List<OsmPoi>> subMap = groupedPois.get(prefCode);
                    String subKey = (subName == null) ? "" : subName;
                    if (!subMap.containsKey(subKey)) {
                        subMap.put(subKey, new ArrayList<>());
                    }
                    subMap.get(subKey).add(poi);
                }

                if (local != null) {
                    for (Map.Entry<Integer, Map<String, List<OsmPoi>>> prefEntry : groupedPois.entrySet()) {
                        int prefCode = prefEntry.getKey();
                        for (Map.Entry<String, List<OsmPoi>> subEntry : prefEntry.getValue().entrySet()) {
                            String subName = subEntry.getKey();
                            if (subName.isEmpty()) subName = null;
                            local.insertPoisIfNotExist(prefCode, subName, subEntry.getValue());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("PoiRepository", "Failed to fetch office data", e);
            }
        });
    }

    @Override
    public List<PlaceInfo> searchPlaces(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String q = Normalizer.normalize(query.trim(), Normalizer.Form.NFKC).toLowerCase();
        List<PlaceInfo> results = new ArrayList<>();
        synchronized (placeCache) {
            for (PlaceInfo place : placeCache) {
                String normalizedName = Normalizer.normalize(place.getName(), Normalizer.Form.NFKC).toLowerCase();
                if (normalizedName.contains(q)) {
                    results.add(place);
                }
            }
        }
        // キャッシュが空の場合は念のためDBからも探す（初回起動時など）
        if (results.isEmpty() && local != null) {
            return local.searchPlaces(query);
        }
        return results;
    }

    /**
     * SQLiteに保存されている市区町村データをメモリキャッシュへ読み込む。
     */
    private void loadPlacesFromCache() {
        if (local == null) return;
        executor.execute(() -> {
            List<PlaceInfo> places = local.getAllPlaces();
            synchronized (placeCache) {
                placeCache.clear();
                placeCache.addAll(places);
            }
        });
    }

    @Override
    public String getPrefectureName(int prefCode) {
        String name = prefCodeNameMap.get(prefCode);
        return name != null ? name : "Unknown(" + prefCode + ")";
    }

    @Override
    public void fetchDataDate(DataDateCallback callback) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://yui-kitamura.github.io/OsmJpPostalMapDataSource/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        DataDateApi api = retrofit.create(DataDateApi.class);
        api.getDataDate().enqueue(new retrofit2.Callback<DataDateResponse>() {
            @Override
            public void onResponse(Call<DataDateResponse> call, Response<DataDateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("データ鮮度情報の取得に失敗しました");
                }
            }

            @Override
            public void onFailure(Call<DataDateResponse> call, Throwable t) {
                callback.onError("ネットワークエラーが発生しました: " + t.getMessage());
            }
        });
    }

    private List<OsmPoi> mergeAndVerify(List<OsmPoi> cached, List<OsmPoi> fetched) {
        if (cached == null || cached.isEmpty()) {
            return fetched;
        }
        Map<String, OsmPoi> fetchedMap = new HashMap<>();
        for (OsmPoi p : fetched) {
            fetchedMap.put(p.getType() + ":" + p.getId(), p);
        }

        List<OsmPoi> result = new ArrayList<>();
        List<OsmPoi> missing = new ArrayList<>();

        // 1. キャッシュされているデータを走査し、フェッチしたデータと比較
        for (OsmPoi c : cached) {
            String key = c.getType() + ":" + c.getId();
            if (fetchedMap.containsKey(key)) {
                OsmPoi f = fetchedMap.get(key);
                if (c.getVer() > f.getVer()) {
                    // キャッシュの方が新しい（ユーザーが編集済み）
                    result.add(c);
                } else {
                    // フェッチしたデータの方が新しいか同じ
                    result.add(f);
                }
                // 処理済みとしてフェッチマップから削除
                fetchedMap.remove(key);
            } else {
                // フェッチしたデータに含まれていない場合、削除された可能性がある
                missing.add(c);
            }
        }

        // 2. フェッチしたデータのうち、キャッシュになかったものを追加
        result.addAll(fetchedMap.values());

        if (missing.isEmpty()) {
            return result;
        }

        // 3. フェッチに含まれなかったキャッシュデータの存在確認（Overpass）
        List<OsmPoi> stillExists = verifyMissingPois(missing);
        result.addAll(stillExists);

        return result;
    }

    private List<OsmPoi> verifyMissingPois(List<OsmPoi> missing) {
        List<OsmPoi> stillExists = new ArrayList<>();
        List<OsmPoi> toCheck = new ArrayList<>();
        for (OsmPoi p : missing) {
            if (p.getId() >= 0) {
                toCheck.add(p);
            } else {
                stillExists.add(p); // 暫定IDは維持
            }
        }
        if (toCheck.isEmpty()) return stillExists;

        // Overpass Query
        StringBuilder sb = new StringBuilder();
        sb.append("[out:json];(");
        for (OsmPoi p : toCheck) {
            String type = p.getType();
            if ("node".equals(type) || "way".equals(type) || "relation".equals(type)) {
                sb.append(type).append("(").append(p.getId()).append(");");
            }
        }
        sb.append(");out center;");

        try {
            retrofit2.Response<String> response = getOverpassApi().query(sb.toString()).execute();
            if (response.isSuccessful() && response.body() != null) {
                JSONObject root = new JSONObject(response.body());
                JSONArray elements = root.optJSONArray("elements");
                if (elements != null) {
                    Map<String, JSONObject> elementMap = new HashMap<>();
                    for (int i = 0; i < elements.length(); i++) {
                        JSONObject el = elements.getJSONObject(i);
                        elementMap.put(el.getString("type") + ":" + el.getLong("id"), el);
                    }
                    for (OsmPoi p : toCheck) {
                        String key = p.getType() + ":" + p.getId();
                        if (elementMap.containsKey(key)) {
                            JSONObject el = elementMap.get(key);
                            double lat = el.optDouble("lat", p.getLat());
                            double lon = el.optDouble("lon", p.getLon());
                            // Way/Relation の場合は center があればそれを使う
                            if (el.has("center")) {
                                JSONObject center = el.getJSONObject("center");
                                lat = center.optDouble("lat", lat);
                                lon = center.optDouble("lon", lon);
                            }
                            long ver = el.optLong("version", p.getVer());
                            Map<String, String> tags = new HashMap<>();
                            JSONObject tagsJson = el.optJSONObject("tags");
                            if (tagsJson != null) {
                                for (java.util.Iterator<String> it = tagsJson.keys(); it.hasNext(); ) {
                                    String k = it.next();
                                    tags.put(k, tagsJson.optString(k));
                                }
                            } else {
                                tags = p.getTags();
                            }
                            stillExists.add(new OsmPoi(p.getId(), lat, lon, p.getType(), tags, ver));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("PoiRepository", "Overpass check failed", e);
            // エラー時は安全のため残す
            stillExists.addAll(toCheck);
        }
        return stillExists;
    }

    /**
     * 指定のエリアを読み込む。{@code forceNetwork} が false かつキャッシュ済みなら
     * SQLiteから読み、そうでなければネットワーク取得してSQLiteへ保存する。
     * 読み込んだエリアの県コードは {@link #currentPrefCodes} に追加される。
     */
    private void loadArea(int prefCode, String prefName, String subName, boolean forceNetwork) {
        currentPrefCodes.add(prefCode);
        boolean cached = local != null && local.hasArea(prefCode, subName);
        if (!forceNetwork && cached) {
            return; // SQLiteの内容をそのまま利用（postCombinedで読み出す）
        }
        try {
            Map<String, Integer> subAll = JpPostalUtil.getSubAreas(prefName).join();
            if (subName != null && subName.trim().isEmpty() == false) {
                String label = prefName + " " + subName;
                loadingStatusLiveData.postValue(label + "のデータを取得中");
                List<OsmPoi> fetched = JpPostalUtil.getPoiData(prefName, subName).join();
                if (local != null) {
                    loadingStatusLiveData.postValue(label + "のデータを処理中");
                    List<OsmPoi> cachedPois = local.getByArea(prefCode, subName);
                    List<OsmPoi> verified = mergeAndVerify(cachedPois, fetched);
                    local.upsertArea(prefCode, subName, prefName, verified, System.currentTimeMillis());
                }
            } else {
                // サブ領域指定がない場合、もしサブ領域が存在するならそれらを全て取得する
                Set<String> subs = (subAll != null) ? subAll.keySet() : null;
                if (subs != null && subs.isEmpty() == false) {
                    for (String sub : subs) {
                        loadingStatusLiveData.postValue(prefName + " " + sub + " のデータを取得中");
                        
                        List<OsmPoi> subData = JpPostalUtil.getPoiData(prefName, sub).join();
                        if (subData != null && local != null) {
                            List<OsmPoi> cachedPois = local.getByArea(prefCode, sub);
                            List<OsmPoi> verified = mergeAndVerify(cachedPois, subData);
                            local.upsertArea(prefCode, sub, prefName, verified, System.currentTimeMillis());
                        }
                    }
                } else {
                    loadingStatusLiveData.postValue(prefName + "のデータを取得中");
                    List<OsmPoi> fetched = JpPostalUtil.getPoiData(prefName).join();
                    if (local != null) {
                        loadingStatusLiveData.postValue(prefName + "のデータを処理中");
                        List<OsmPoi> cachedPois = local.getByArea(prefCode, null);
                        List<OsmPoi> verified = mergeAndVerify(cachedPois, fetched);
                        local.upsertArea(prefCode, null, prefName, verified, System.currentTimeMillis());
                    }
                }
            }
        } catch (Exception e) {
            Log.e("PoiRepository", "Failed to load area: " + prefName, e);
            errorLiveData.postValue("データの取得に失敗しました: " + prefName);
        }
    }

    /**
     * アプリ起動時、SQLiteに保存されているすべての都道府県を読み込む。
     */
    private void loadAllFromCache() {
        runOnExecutor("保存済みデータを読み込み中", () -> {
            if (local == null) return;
            List<PrefMeta> saved = local.getAllPrefMeta();
            if (saved.isEmpty()) return;

            for (PrefMeta meta : saved) {
                currentPrefCodes.add(meta.getPrefCode());
            }
        });
    }

    /**
     * SQLiteに保存されているグリッドキャッシュ（逆ジオコーディング結果）を読み込む。
     */
    private void loadGridCacheFromDb() {
        if (local == null) return;
        executor.execute(() -> {
            Map<Long, Set<String>> cached = local.getAllGridPrefs();
            gridPrefCache.putAll(cached);
        });
    }

    /**
     * {@link #currentPrefCodes} のPOIをSQLiteから結合し、上限判定のうえLiveDataへ反映する。
     * 処理速度向上のため、{@link #areaFilterEnabled} が有効な場合は表示範囲内のみに絞り込む。
     */
    private void postCombined() {
        if (local == null) { return; }
        if (!areaFilterEnabled) {
            // 範囲未指定時は全件ロードを避ける（起動時のブロッキング防止）
            return;
        }
        loadingStatusLiveData.postValue("表示データを抽出中");
        List<OsmPoi> all = local.getByBoundingBox(activeLatMin, activeLatMax, activeLonMin, activeLonMax);
        poisLiveData.postValue(all);
    }

    private Set<String> findIntersectingAreas(double latMin, double latMax, double lonMin, double lonMax) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map.Entry<String, BBox> entry : prefBoundaryCache.entrySet()) {
            BBox b = entry.getValue();
            if (latMin <= b.getMaxLat() && latMax >= b.getMinLat() &&
                lonMin <= b.getMaxLon() && lonMax >= b.getMinLon()) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    /**
     * 表示範囲にかかるエリアキーを返す。
     * BBoxキャッシュとグリッドキャッシュのみを使用する。
     */
    private Set<String> reverseGeocodeAreas(double[][] points) {
        Set<String> keys = new LinkedHashSet<>();

        // 1. BBoxキャッシュで判定を試みる。サブエリアがあればサブエリアのキー（pref:sub）が優先される。
        if (!prefBoundaryCache.isEmpty()) {
            for (double[] p : points) {
                for (Map.Entry<String, BBox> entry : prefBoundaryCache.entrySet()) {
                    BBox b = entry.getValue();
                    if (p[0] >= b.getMinLat() && p[0] <= b.getMaxLat() &&
                        p[1] >= b.getMinLon() && p[1] <= b.getMaxLon()) {
                        keys.add(entry.getKey());
                    }
                }
            }
        }

        // 2. グリッドキャッシュ（過去の判定結果）で補完する
        if (keys.isEmpty()) {
            double gridUnit = 1.0 / 60.0;
            for (double[] p : points) {
                long key = getGridKey(p, gridUnit);
                Set<String> cached = gridPrefCache.get(key);
                if (cached != null) {
                    keys.addAll(cached);
                }
            }
        }

        // 3. ヒットした結果をグリッドキャッシュにフィードバックする（経緯度グリッドでのエリア情報はサブの情報を持つ）
        if (!keys.isEmpty() && points.length > 0) {
            double gridUnit = 1.0 / 60.0;
            long key = getGridKey(points[0], gridUnit);
            if (!gridPrefCache.containsKey(key)) {
                gridPrefCache.put(key, keys);
                if (local != null) {
                    final String names = String.join(",", keys);
                    executor.execute(() -> local.upsertGridPref(key, names));
                }
            }
        }

        return keys;
    }

    private long getGridKey(double[] p, double gridUnit) {
        long latIdx = Math.round(p[0] / gridUnit);
        long lonIdx = Math.round(p[1] / gridUnit);
        return (latIdx << 32) | (lonIdx & 0xFFFFFFFFL);
    }

    /* ---------- 保存系（バックグラウンド実行 + 即時SQLite反映） ---------- */

    @Override
    public void savePoi(OsmPoi poi, String comment, PoiSaveCallback callback) {
        runOnExecutor("修正を送信中", () -> {
            // 1. Create Changeset
            postProgress(callback, "Changesetを作成中…");
            ChangeSetInfo csInfo = new ChangeSetInfo(0, comment, "OsmJPPostalMap Android v" + BuildConfig.VERSION_NAME, new HashMap<>());
            long csId;
            csId = JpPostalUtil.callOsmCreateChangeset(accessToken, csInfo).join();
            ChangeSetInfo csInfoActive = new ChangeSetInfo(csId, comment, "OsmJPPostalMap Android v" + BuildConfig.VERSION_NAME, new HashMap<>());
            try {
                // 編集処理
                postProgress(callback, "入力内容を送信中…");
                JpPostalUtil.callOsmCreateOrModifyElement(accessToken, csInfoActive, poi).join();
                // CS close
                postProgress(callback, "Changesetを確定中…");
                JpPostalUtil.callOsmCloseChangeset(accessToken, csInfoActive).join();
            } catch (Exception e) {
                Log.e("PoiRepository", "Failed to save POI", e);
                postError(callback, "入力内容の反映に失敗しました。リトライしてください");
                return;
            }
            postSuccess(callback);
            // 2. ローカルSQLiteへ即時反映（既存POIはidが判っているのでそのままupsert）
            executor.execute(() -> cacheEditedPoi(poi));
        });
    }

    @Override
    public void addPostBox(double lat, double lon, String shape, String branch, String postboxRef, String collectionTimes, String note, Map<String, String> addressTags, PoiSaveCallback callback) {
        runOnExecutor("新規ポストを送信中", () -> {
            Map<String, String> csTags = new HashMap<>();
            String comment = context != null ? context.getString(pro.eng.yui.android.osmjppostalmap.R.string.changeset_comment_add_postbox) : "郵便ポストの追加";
            ChangeSetInfo createInfo = new ChangeSetInfo(0L, comment,
                    "OsmJPPostalMap Android v" + BuildConfig.VERSION_NAME, csTags);
            try {
                postProgress(callback, "Changesetを作成中…");
                long csId = JpPostalUtil.callOsmCreateChangeset(accessToken, createInfo).join();
                ChangeSetInfo csIdInfo = new ChangeSetInfo(csId);

                Map<String, String> poiTags = new HashMap<>();
                if (addressTags != null) {
                    poiTags.putAll(addressTags);
                }
                poiTags.put("amenity", "post_box");
                poiTags.put("operator", "日本郵便");
                if ("柱上箱型".equals(shape)) {
                    poiTags.put("support", "pole");
                    poiTags.put("post_box:type", "lamp");
                } else if ("円柱".equals(shape)) {
                    poiTags.put("support", "ground");
                    poiTags.put("post_box:type", "pillar");
                }
                if (branch != null && !branch.isEmpty()) {
                    poiTags.put("operator:branch", branch);
                }
                if (postboxRef != null && !postboxRef.isEmpty()) {
                    poiTags.put("ref", postboxRef);
                }
                if (collectionTimes != null && !collectionTimes.isEmpty()) {
                    poiTags.put("collection_times", collectionTimes);
                }
                if (note != null && !note.isEmpty()) {
                    poiTags.put("note", note);
                }
                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                poiTags.put("check_date", today);

                OsmPoi newPoi = new OsmPoi(lat, lon, "node", poiTags);
                // OSM API call
                postProgress(callback, "入力内容を送信中…");
                String osmResult = JpPostalUtil.callOsmCreateOrModifyElement(accessToken, csIdInfo, newPoi).join();
                postProgress(callback, "Changesetを確定中…");
                JpPostalUtil.callOsmCloseChangeset(accessToken, csIdInfo).join();

                postSuccess(callback);

                // ローカルSQLiteへ即時反映。
                long finalId = -System.currentTimeMillis();
                String finalType = "node";
                if (osmResult != null && osmResult.contains("#")) {
                    try {
                        String[] parts = osmResult.split("#");
                        finalType = parts[0];
                        finalId = Long.parseLong(parts[1]);
                    } catch (Exception e) {
                        Log.e("PoiRepository", "Failed to parse OSM result: " + osmResult, e);
                    }
                }
                OsmPoi cachePoi = new OsmPoi(finalId, lat, lon, finalType, poiTags, 1);
                executor.execute(() -> cacheEditedPoi(cachePoi));
            } catch (Exception e) {
                Log.e("PoiRepository", "Failed to add post box", e);
                postError(callback, "通信エラー: " + e.getMessage());
            }
        });
    }

    /**
     * 編集/新規作成したPOIを、その座標が属する都道府県のキャッシュへ即時反映する。
     * 座標→県コードは単一点の逆ジオコーディングで解決する。
     */
    private void cacheEditedPoi(OsmPoi poi) {
        if (local == null) { return; }
        try {
            Set<String> keys = reverseGeocodeAreas(new double[][]{{poi.getLat(), poi.getLon()}});
            if (keys.isEmpty()) { return; }
            String key = keys.iterator().next();
            String prefName = key.contains(":") ? key.split(":")[0] : key;
            String subName = key.contains(":") ? key.split(":")[1] : null;

            Integer code = JpPostalUtil.getPrefectures().join().get(prefName);
            if (code == null || code < 0) { return; }
            local.upsertPoi(code, subName, poi);
            // 表示中であれば再構成
            if (currentPrefCodes.contains(code)) {
                postCombined();
            }
        } catch (RuntimeException ignore) { }
    }

    @Override
    public LiveData<Location> getLocationLiveData() {
        return locationLiveData;
    }

    @Override
    public void startLocationUpdates() {
        startLocationUpdates(5000, 10);
    }

    @Override
    public void startLocationUpdates(long minTimeMs, float minDistanceM) {
        if (locationManager == null || context == null) return;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMs, minDistanceM, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMs, minDistanceM, locationListener);
        Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (lastGps != null) {
            locationLiveData.postValue(lastGps);
            updateCurrentArea(lastGps);
        } else if (lastNetwork != null) {
            locationLiveData.postValue(lastNetwork);
            updateCurrentArea(lastNetwork);
        }
    }

    @Override
    public void stopLocationUpdates() {
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    @Override
    public LiveData<String> getCurrentPrefecture() {
        return currentPrefectureLiveData;
    }

    @Override
    public LiveData<String> getCurrentSubArea() {
        return currentSubAreaLiveData;
    }

    private void updateCurrentArea(Location location) {
        if (context == null) return;
        executor.execute(() -> {
            // 1. まず自前の境界データで判定を試みる（サブエリアまで判明する）
            try {
                // 境界データの読み込み完了を待機
                boundaryLatch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}

            Set<String> areas = reverseGeocodeAreas(new double[][]{{location.getLatitude(), location.getLongitude()}});
            if (!areas.isEmpty()) {
                String key = areas.iterator().next();
                if (key.contains(":")) {
                    String[] parts = key.split(":");
                    currentPrefectureLiveData.postValue(parts[0]);
                    currentSubAreaLiveData.postValue(parts[1]);
                } else {
                    currentPrefectureLiveData.postValue(key);
                    currentSubAreaLiveData.postValue(null);
                }
                return;
            }

            // 2. 判定できなければ Geocoder をフォールバックとして使う
            Geocoder geocoder = new Geocoder(context, Locale.JAPAN);
            try {
                List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    currentPrefectureLiveData.postValue(addresses.get(0).getAdminArea());
                    currentSubAreaLiveData.postValue(null);
                }
            } catch (Exception ignored) {}
        });
    }

    @Override
    public LiveData<String> getError() {
        return errorLiveData;
    }

    public void clearError() {
        errorLiveData.postValue(null);
    }

    @Override
    public LiveData<String> getSuccessMessage() {
        return successLiveData;
    }

    public void clearSuccessMessage() {
        successLiveData.postValue(null);
    }

    @Override
    public LiveData<Long> getCooldownRemaining() {
        return cooldownRemainingLiveData;
    }

    @Override
    public LiveData<Boolean> getLoading() {
        return loadingLiveData;
    }

    @Override
    public LiveData<String> getLoadingStatus() {
        return loadingStatusLiveData;
    }

    @Override
    public long getCooldownInterval() {
        return MIN_INTERVAL_MS;
    }

    private void startCooldownTimer() {
        handler.post(() -> {
            if (cooldownRunnable != null) {
                handler.removeCallbacks(cooldownRunnable);
            }
            cooldownRunnable = new Runnable() {
                @Override
                public void run() {
                    long remaining = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastFetchTime);
                    if (remaining > 0) {
                        cooldownRemainingLiveData.postValue(remaining);
                        handler.postDelayed(this, 100);
                    } else {
                        cooldownRemainingLiveData.postValue(0L);
                    }
                }
            };
            handler.post(cooldownRunnable);
        });
    }

    /* コールバックはメインスレッドへマーシャリングする */
    private void postSuccess(PoiSaveCallback callback) {
        handler.post(callback::onSuccess);
    }

    private void postError(PoiSaveCallback callback, String message) {
        handler.post(() -> callback.onError(message));
    }

    private void postProgress(PoiSaveCallback callback, String message) {
        loadingStatusLiveData.postValue(message);
        handler.post(() -> callback.onProgress(message));
    }

    @Override
    public void addNote(double lat, double lon, String text, PoiSaveCallback callback) {
        // 地図メモ (Note) の最終行に署名を追加
        String finalNote = text + "\ncreated by OSM JP Postal Map Android v" + BuildConfig.VERSION_NAME;
        /*
        // OSM Notes API (匿名投稿可能)
        // https://wiki.openstreetmap.org/wiki/API_v0.6#Map_Notes_API
        // POST /api/0.6/notes?lat=...&lon=...&text=...
        */
    }
}
