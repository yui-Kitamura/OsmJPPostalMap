package pro.eng.yui.android.osmjppostalmap.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.api.osm.ChangeSetInfo;
import pro.eng.yui.oss.osm.lib.jppostalcore.api.overpass.OverpassQuery;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.eng.yui.android.osmjppostalmap.BuildConfig;
import pro.eng.yui.android.osmjppostalmap.data.local.PoiLocalDataSource;
import pro.eng.yui.android.osmjppostalmap.domain.model.PrefMeta;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;

public class PoiRepositoryImpl implements PoiRepository {

    private final MutableLiveData<List<OsmPoi>> poisLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> successLiveData = new MutableLiveData<>();
    private final MutableLiveData<Long> cooldownRemainingLiveData = new MutableLiveData<>(0L);
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
    private Runnable cooldownRunnable;

    /** ローカルキャッシュ（{@link #init(Context)} で初期化） */
    private PoiLocalDataSource local;
    /** 現在描画対象としている都道府県コード（再描画の再構成に使用） */
    private final Set<Integer> currentPrefCodes = new LinkedHashSet<>();

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
            repo.loadAllFromCache();
        }
    }

    private PoiRepositoryImpl() {
        this(null);
    }

    private PoiRepositoryImpl(String accessToken) {
        this.accessToken = accessToken;
    }

    /* ---------- 取得系 ---------- */

    @Override
    public LiveData<List<OsmPoi>> getPoisLiveData() {
        return poisLiveData;
    }

    @Override
    public LiveData<List<OsmPoi>> getPois(String prefName) {
        // 互換用。バックグラウンドでキャッシュ優先の単一県読み込みを行う。
        runOnExecutor(() -> {
            Map<String, Integer> prefs = JpPostalUtil.getPrefectures();
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
    private void runOnExecutor(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                errorLiveData.postValue("処理中にエラーが発生しました");
            }
        });
    }

    @Override
    public void loadPoisForArea(double[][] latLonPoints) {
        if (latLonPoints == null || latLonPoints.length == 0) { return; }

        // 1. 座標範囲を特定
        double latMin = Double.MAX_VALUE;
        double latMax = -Double.MAX_VALUE;
        double lonMin = Double.MAX_VALUE;
        double lonMax = -Double.MAX_VALUE;
        for (double[] p : latLonPoints) {
            latMin = Math.min(latMin, p[0]);
            latMax = Math.max(latMax, p[0]);
            lonMin = Math.min(lonMin, p[1]);
            lonMax = Math.max(lonMax, p[1]);
        }
        final double fLatMin = latMin, fLatMax = latMax, fLonMin = lonMin, fLonMax = lonMax;

        runOnExecutor(() -> {
            // 2. キャッシュから座標範囲で即座に読み出す
            if (local != null) {
                List<OsmPoi> cached = local.getByBoundingBox(fLatMin, fLatMax, fLonMin, fLonMax);
                if (!cached.isEmpty()) {
                    poisLiveData.postValue(cached);
                }
            }

            // 4. 表示範囲にかかる都道府県名を逆ジオコーディングで特定
            Set<String> prefNames = reverseGeocodePrefectures(latLonPoints);
            if (prefNames.isEmpty()) { return; }

            // 5. 新規フェッチが必要な県を特定
            Map<String, Integer> prefs = JpPostalUtil.getPrefectures();
            List<String> neededPrefNames = new ArrayList<>();
            for (String name : prefNames) {
                Integer code = prefs.get(name);
                if (code == null || code < 0) { continue; }
                currentPrefCodes.add(code);
                if (local != null && !local.hasPrefecture(code)) {
                    neededPrefNames.add(name);
                }
            }

            if (neededPrefNames.isEmpty()) {
                return; // すべてキャッシュ済みなのでスキップ
            }

            // 3. クールダウン判定（新規ネットワーク取得が発生する場合のみ適用）
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFetchTime < MIN_INTERVAL_MS) {
                return;
            }
            lastFetchTime = currentTime;
            startCooldownTimer();

            for (String name : neededPrefNames) {
                Integer code = prefs.get(name);
                loadPref(code, name, false);
            }

            // 新しくフェッチしたデータがあるため、再度座標範囲で抽出して反映
            if (local != null) {
                poisLiveData.postValue(local.getByBoundingBox(fLatMin, fLatMax, fLonMin, fLonMax));
            }
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

        runOnExecutor(() -> {
            loadPref(prefCode, prefName, true); // 強制ネットワーク取得
            postCombined();
        });
    }

    @Override
    public List<PrefMeta> getSavedPrefectures() {
        if (local == null) { return new ArrayList<>(); }
        return local.getAllPrefMeta();
    }

    /**
     * 1都道府県を読み込む。{@code forceNetwork} が false かつキャッシュ済みなら
     * SQLiteから読み、そうでなければネットワーク取得してSQLiteへ保存する。
     * 読み込んだ県は {@link #currentPrefCodes} に追加される。
     */
    private void loadPref(int prefCode, String prefName, boolean forceNetwork) {
        currentPrefCodes.add(prefCode);
        boolean cached = local != null && local.hasPrefecture(prefCode);
        if (!forceNetwork && cached) {
            return; // SQLiteの内容をそのまま利用（postCombinedで読み出す）
        }
        try {
            List<OsmPoi> fetched = JpPostalUtil.getPoiData(prefName);
            if (fetched.isEmpty()) {
                // データソースに無い場合はOverpassフォールバック
                try {
                    fetched = JpPostalUtil.callOverpass(
                            OverpassQuery.getPostSearchQuery(prefName), 3, 5);
                } catch (IOException | IllegalStateException ignore) { }
            }
            if (local != null) {
                local.upsertPrefecture(prefCode, prefName, fetched, System.currentTimeMillis());
            }
        } catch (RuntimeException e) {
            errorLiveData.postValue("データの取得に失敗しました: " + prefName);
        }
    }

    /**
     * アプリ起動時、SQLiteに保存されているすべての都道府県を読み込む。
     */
    private void loadAllFromCache() {
        runOnExecutor(() -> {
            if (local == null) return;
            List<PrefMeta> saved = local.getAllPrefMeta();
            if (saved.isEmpty()) return;

            for (PrefMeta meta : saved) {
                currentPrefCodes.add(meta.getPrefCode());
            }
            postCombined();
        });
    }

    /**
     * {@link #currentPrefCodes} のPOIをSQLiteから結合し、上限判定のうえLiveDataへ反映する。
     */
    private void postCombined() {
        if (local == null) { return; }
        List<OsmPoi> all = new ArrayList<>();
        for (int code : currentPrefCodes) {
            all.addAll(local.getByPrefCode(code));
        }
        poisLiveData.postValue(all);
    }

    /**
     * 複数座標を一度のOverpass呼び出しで逆ジオコーディングし、
     * admin_level=4（都道府県）の name 集合を返す。
     */
    private Set<String> reverseGeocodePrefectures(double[][] points) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < points.length; i++) {
            body.append(String.format(Locale.US,
                    "is_in(%.7f,%.7f)->.a%d;", points[i][0], points[i][1], i));
        }
        body.append("(");
        for (int i = 0; i < points.length; i++) {
            body.append(String.format(Locale.US,
                    "rel(pivot.a%d)[\"boundary\"=\"administrative\"][\"admin_level\"=\"4\"];", i));
        }
        body.append(");");

        Set<String> names = new LinkedHashSet<>();
        try {
            for (OsmPoi rel : JpPostalUtil.callOverpass(body.toString(), 3, 3)) {
                String name = rel.getTag("name");
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        } catch (IOException | RuntimeException ignore) {
            // IllegalStateException(429/リトライ超過), IllegalArgumentException(400) 等を含めて無視
        }
        return names;
    }

    @Override
    public LiveData<OsmPoi> getPoi(long id, String type) {
        MutableLiveData<OsmPoi> poiLiveData = new MutableLiveData<>();
        String query = String.format(Locale.US, "%s(%d);", type, id);

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFetchTime < MIN_INTERVAL_MS) {
            return poiLiveData;
        }
        lastFetchTime = currentTime;
        startCooldownTimer();

        runOnExecutor(() -> {
            try {
                List<OsmPoi> poiList = JpPostalUtil.callOverpass(query, 3, 5);
                poiLiveData.postValue(poiList.get(0));
            } catch (IOException | RuntimeException e) {
                poiLiveData.postValue(null);
            }
        });
        return poiLiveData;
    }

    /* ---------- 保存系（バックグラウンド実行 + 即時SQLite反映） ---------- */

    @Override
    public void savePoi(OsmPoi poi, String comment, PoiSaveCallback callback) {
        if (accessToken == null) {
            postError(callback, "ログインが必要です");
            return;
        }
        runOnExecutor(() -> {
            // 1. Create Changeset
            ChangeSetInfo csInfo = new ChangeSetInfo(0, comment, "OsmJPPostalMap Android v" + BuildConfig.VERSION_NAME, new HashMap<>());
            long csId;
            try {
                csId = JpPostalUtil.callOsmCreateChangeset(accessToken, csInfo);
            } catch (IOException ioe) {
                ioe.printStackTrace();
                postError(callback, "ChangeSetの登録開始処理に失敗しました。リトライしてください");
                return;
            }
            ChangeSetInfo csInfoActive = new ChangeSetInfo(csId, comment, "OsmJPPostalMap Android v" + BuildConfig.VERSION_NAME, new HashMap<>());
            try {
                // 編集処理
                JpPostalUtil.callOsmCreateOrModifyElement(accessToken, csInfoActive, poi);
                // CS close
                JpPostalUtil.callOsmCloseChangeset(accessToken, csInfoActive);
            } catch (IOException ioe) {
                ioe.printStackTrace();
                postError(callback, "入力内容の反映に失敗しました。リトライしてください");
                return;
            }
            // 2. ローカルSQLiteへ即時反映（既存POIはidが判っているのでそのままupsert）
            cacheEditedPoi(poi);
            postSuccess(callback);
        });
    }

    @Override
    public void addPostBox(double lat, double lon, String shape, String branch, String postboxRef, String collectionTimes, String note, PoiSaveCallback callback) {
        if (accessToken == null) {
            postError(callback, "ログインが必要です");
            return;
        }
        runOnExecutor(() -> {
            Map<String, String> csTags = new HashMap<>();
            ChangeSetInfo createInfo = new ChangeSetInfo(0L, "郵便ポストの追加",
                    "OsmJPPostalMap Android v" + BuildConfig.VERSION_NAME, csTags);
            try {
                long csId = JpPostalUtil.callOsmCreateChangeset(accessToken, createInfo);
                ChangeSetInfo csIdInfo = new ChangeSetInfo(csId);

                Map<String, String> poiTags = new HashMap<>();
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
                JpPostalUtil.callOsmCreateOrModifyElement(accessToken, csIdInfo, newPoi);
                JpPostalUtil.callOsmCloseChangeset(accessToken, csIdInfo);

                // ローカルSQLiteへ即時反映。
                // 注: callOsmCreateOrModifyElement が採番IDを返さないため、
                //     暫定的に一意な負のidで保存する（次回の県フル更新で正規データに置換される）。
                long tempId = -System.currentTimeMillis();
                OsmPoi cachePoi = new OsmPoi(tempId, lat, lon, "node", poiTags, 0);
                cacheEditedPoi(cachePoi);
                postSuccess(callback);
            } catch (IOException e) {
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
            Set<String> names = reverseGeocodePrefectures(new double[][]{{poi.getLat(), poi.getLon()}});
            if (names.isEmpty()) { return; }
            String name = names.iterator().next();
            Integer code = JpPostalUtil.getPrefectures().get(name);
            if (code == null || code < 0) { return; }
            local.upsertPoi(code, poi);
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
