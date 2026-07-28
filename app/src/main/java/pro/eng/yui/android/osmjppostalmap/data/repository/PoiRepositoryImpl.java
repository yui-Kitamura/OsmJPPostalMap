package pro.eng.yui.android.osmjppostalmap.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.api.osm.ChangeSetInfo;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.BBox;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CountDownLatch boundaryLatch = new CountDownLatch(1);
    private Runnable cooldownRunnable;
    private final AtomicInteger pendingOperations = new AtomicInteger();

    /** ローカルキャッシュ（{@link #init(Context)} で初期化） */
    private PoiLocalDataSource local;
    /** 現在描画対象としている都道府県コード（再描画の再構成に使用） */
    private final Set<Integer> currentPrefCodes = new LinkedHashSet<>();
    /** 現在表示・計算対象としている地理的範囲 */
    private double activeLatMin = -90, activeLatMax = 90, activeLonMin = -180, activeLonMax = 180;
    private boolean areaFilterEnabled = false;

    /** 逆ジオコーディング結果のキャッシュ。固定座標系（1海里）へのアライメントによりヒット率を高める。 */
    private final Map<Long, Set<String>> gridPrefCache = new java.util.concurrent.ConcurrentHashMap<>();
    /** 都道府県の境界ボックス（BBox）キャッシュ。 */
    private final Map<String, BBox> prefBoundaryCache = new java.util.concurrent.ConcurrentHashMap<>();

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
        if (repo.local == null) {
            repo.local = new PoiLocalDataSource(context.getApplicationContext());
            repo.preloadBoundaries();
            repo.loadAllFromCache();
            repo.loadGridCacheFromDb();
        }
    }

    private PoiRepositoryImpl() {
        this(null);
    }

    private PoiRepositoryImpl(String accessToken) {
        this.accessToken = accessToken;
    }

    private void preloadBoundaries() {
        JpPostalUtil.getRawPrefecturesJson().thenAccept(json -> {
            try {
                if (json == null || json.isEmpty()) return;
                Gson gson = new Gson();
                java.lang.reflect.Type type = new TypeToken<Map<String, RawPrefData>>(){}.getType();
                Map<String, RawPrefData> prefs = gson.fromJson(json, type);

                if (prefs == null) return;

                for (Map.Entry<String, RawPrefData> entry : prefs.entrySet()) {
                    String prefName = entry.getKey();
                    RawPrefData pref = entry.getValue();

                    if (pref.subAreas != null && !pref.subAreas.isEmpty()) {
                        for (Map.Entry<String, RawSubAreaData> subEntry : pref.subAreas.entrySet()) {
                            String subName = subEntry.getKey();
                            RawSubAreaData sub = subEntry.getValue();
                            if (sub.bbox != null) {
                                prefBoundaryCache.put(prefName + ":" + subName, sub.bbox);
                            }
                        }
                    } else if (pref.bbox != null) {
                        prefBoundaryCache.put(prefName, pref.bbox);
                    }
                }
            } catch (Exception e) {
                Log.e("PoiRepository", "Failed to preload boundaries", e);
                errorLiveData.postValue("境界データの読み込みに失敗しました");
            } finally {
                boundaryLatch.countDown();
            }
        }).exceptionally(ex -> {
            Log.e("PoiRepository", "Failed to preload boundaries future", ex);
            errorLiveData.postValue("境界データの取得に失敗しました");
            boundaryLatch.countDown();
            return null;
        });
    }

    private static class RawPrefData {
        BBox bbox;
        Map<String, RawSubAreaData> subAreas;
    }

    private static class RawSubAreaData {
        BBox bbox;
    }

    /* ---------- 取得系 ---------- */

    @Override
    public LiveData<List<OsmPoi>> getPoisLiveData() {
        return poisLiveData;
    }

    @Override
    public LiveData<List<OsmPoi>> getPois(String prefName) {
        // 互換用。バックグラウンドでキャッシュ優先の単一県読み込みを行う。
        runOnExecutor(prefName + "のデータを準備中", () -> {
            Map<String, Integer> prefs = JpPostalUtil.getPrefectures().join();
            Integer code = prefs.get(prefName);
            if (code == null || code < 0) { return; }
            currentPrefCodes.clear();
            loadPref(code, prefName, false);
            postCombined();
        });
        return poisLiveData;
    }

    /**
     * 単一スレッドExecutor上でタスクを実行する。タスクが例外を投げても
     * Executorのワーカースレッドが死なないよう保護する。
     */
    private void runOnExecutor(String operation, Runnable task) {
        if (pendingOperations.getAndIncrement() == 0) {
            loadingLiveData.postValue(true);
        }
        loadingStatusLiveData.postValue(operation);
        executor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                errorLiveData.postValue("処理中にエラーが発生しました");
            } finally {
                if (pendingOperations.decrementAndGet() == 0) {
                    loadingLiveData.postValue(false);
                    loadingStatusLiveData.postValue("");
                }
            }
        });
    }

    @Override
    public void loadPoisForArea(double[][] latLonPoints, boolean forceNotify) {
        if (latLonPoints == null || latLonPoints.length == 0) { return; }

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
                // 境界データの読み込み完了を待機（最大10秒）
                boundaryLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 表示範囲にかかるエリア（都道府県またはサブ領域）を判定する。
            Set<String> areaKeys;
            if (!prefBoundaryCache.isEmpty()) {
                // BBoxを用いたローカル判定（高速）
                areaKeys = findIntersectingAreas(fLatMin, fLatMax, fLonMin, fLonMax);
            } else {
                // まだBBoxが読み込まれていない場合は従来の逆ジオコーディング
                areaKeys = reverseGeocodeAreas(latLonPoints);
            }

            if (areaKeys.isEmpty()) {
                if (forceNotify) postCombined();
                return;
            }

            // 新規フェッチが必要なエリアを特定
            Map<String, Integer> prefs = JpPostalUtil.getPrefectures().join();
            List<String[]> neededAreas = new ArrayList<>(); // [prefName, subName]
            for (String key : areaKeys) {
                String prefName = key.contains(":") ? key.split(":")[0] : key;
                String subName = key.contains(":") ? key.split(":")[1] : null;

                Integer code = prefs.get(prefName);
                if (code == null || code < 0) { continue; }
                currentPrefCodes.add(code);
                if (local != null && !local.hasArea(code, subName)) {
                    neededAreas.add(new String[]{prefName, subName});
                }
            }

            if (neededAreas.isEmpty()) {
                if (forceNotify) postCombined();
                return;
            }

            // クールダウン判定（新規ネットワーク取得が発生する場合のみ適用）
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFetchTime < MIN_INTERVAL_MS) {
                return;
            }
            lastFetchTime = currentTime;
            startCooldownTimer();

            for (String[] area : neededAreas) {
                Integer code = prefs.get(area[0]);
                loadArea(code, area[0], area[1], false);
            }

            // 新しくフェッチしたデータがあるため再反映
            postCombined();
        });
    }

    @Override
    public void refreshPrefecture(int prefCode, String prefName) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFetchTime < MIN_INTERVAL_MS) {
            errorLiveData.postValue("しばらく待ってから再度お試しください");
            return;
        }
        lastFetchTime = currentTime;
        startCooldownTimer();

        runOnExecutor(prefName + "のデータを再取得中", () -> {
            loadArea(prefCode, prefName, null, true); // 強制ネットワーク取得
            postCombined();
        });
    }

    @Override
    public void deletePrefectureCache(int prefCode) {
        runOnExecutor("キャッシュを削除中", () -> {
            if (local == null) { return; }
            local.deletePrefecture(prefCode);
            currentPrefCodes.remove(prefCode);
            postCombined();
        });
    }

    @Override
    public List<PrefMeta> getSavedPrefectures() {
        if (local == null) { return new ArrayList<>(); }
        return local.getAllPrefMeta();
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
            if (subName != null) {
                String label = prefName + " " + subName;
                loadingStatusLiveData.postValue(label + "のデータを取得中");
                List<OsmPoi> fetched = JpPostalUtil.getPoiData(prefName, subName).join();
                if (local != null) {
                    loadingStatusLiveData.postValue(label + "のデータを処理中");
                    local.upsertArea(prefCode, subName, prefName, fetched, System.currentTimeMillis());
                }
            } else {
                // サブ領域指定がない場合、もしサブ領域が存在するならそれらを全て取得する
                Map<String, Integer> subAll = JpPostalUtil.getSubAreas(prefName).join();
                Set<String> subs = subAll.keySet();
                if (subs != null && !subs.isEmpty()) {
                    for (String sub : subs) {
                        loadingStatusLiveData.postValue(prefName + " " + sub + " のデータを取得中");
                        List<OsmPoi> subData = JpPostalUtil.getPoiData(prefName, sub).join();
                        if (subData != null && local != null) {
                            local.upsertArea(prefCode, sub, prefName, subData, System.currentTimeMillis());
                        }
                    }
                } else {
                    loadingStatusLiveData.postValue(prefName + "のデータを取得中");
                    List<OsmPoi> fetched = JpPostalUtil.getPoiData(prefName, null).join();
                    if (local != null) {
                        loadingStatusLiveData.postValue(prefName + "のデータを処理中");
                        local.upsertArea(prefCode, null, prefName, fetched, System.currentTimeMillis());
                    }
                }
            }
        } catch (RuntimeException e) {
            errorLiveData.postValue("データの取得に失敗しました: " + prefName);
        }
    }

    /** 互換用。 */
    private void loadPref(int prefCode, String prefName, boolean forceNetwork) {
        loadArea(prefCode, prefName, null, forceNetwork);
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

        // 1. BBoxキャッシュで判定を試みる
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
        double gridUnit = 1.0 / 60.0;
        for (double[] p : points) {
            long key = getGridKey(p, gridUnit);
            Set<String> cached = gridPrefCache.get(key);
            if (cached != null) {
                keys.addAll(cached);
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
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
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
            ChangeSetInfo createInfo = new ChangeSetInfo(0L, "郵便ポストの追加",
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
                JpPostalUtil.callOsmCreateOrModifyElement(accessToken, csIdInfo, newPoi).join();
                postProgress(callback, "Changesetを確定中…");
                JpPostalUtil.callOsmCloseChangeset(accessToken, csIdInfo).join();

                postSuccess(callback);

                // ローカルSQLiteへ即時反映。
                // 注: callOsmCreateOrModifyElement が採番IDを返さないため、
                //     暫定的に一意な負のidで保存する（次回の県フル更新で正規データに置換される）。
                long tempId = -System.currentTimeMillis();
                OsmPoi cachePoi = new OsmPoi(tempId, lat, lon, "node", poiTags, 0);
                executor.execute(() -> cacheEditedPoi(cachePoi));
            } catch (RuntimeException e) {
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
