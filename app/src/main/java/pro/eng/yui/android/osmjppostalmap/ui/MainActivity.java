package pro.eng.yui.android.osmjppostalmap.ui;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import pro.eng.yui.android.osmjppostalmap.core.PoiDetailsDialog;
import pro.eng.yui.android.osmjppostalmap.core.PoiMarker;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.CollectionTimes;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OpeningHours;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.R;

import android.view.View;
import android.content.Intent;
import android.Manifest;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.location.Location;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;
import pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.TextValue;

public class MainActivity extends AppCompatActivity {

    private MapView map;
    private org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay locationOverlay;
    private MainViewModel viewModel;
    private AuthRepository authRepository;
    private Location lastLocation;
    private static final int PERMISSION_REQUEST_LOCATION = 100;
    private static final double GPS_MIN_ZOOM = 18.0;
    private static final double GPS_MAX_ZOOM = 20.0;
    private static final double MIN_ZOOM = 7.0;
    // 日本の領域（離島を含む）を収める表示可能範囲。
    private static final BoundingBox JAPAN_BOUNDS =
            new BoundingBox(45.60, 154.00, 20.20, 122.70);
    public static final GeoPoint TOKYO_CENTRAL_POST_OFFICE =
            new GeoPoint(35.6801350, 139.7646546);

    private final android.os.Handler debounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable debounceRunnable = null;
    private boolean initialLocationSet = false;
    private boolean locationPermissionResolved = false;
    private boolean gpsZoomAdjustmentPending = false;
    private GeoPoint gpsZoomCenter;
    private double gpsZoomBase = GPS_MIN_ZOOM;
    private double gpsZoomLimit = MIN_ZOOM;
    private ProgressBar gpsProgress;
    private final ExecutorService markerStateExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingDeque<Runnable>() {
                @Override
                public boolean offer(Runnable runnable) {
                    return offerFirst(runnable);
                }
            }
    );
    private final AtomicInteger markerRenderGeneration = new AtomicInteger();
    private ActivityResultLauncher<Intent> editPoiLauncher;
    private PoiDetailsDialog currentPoiDetailsDialog;

    private enum UpdateMode {
        NORMAL,           // 11海里制限あり
        GPS_OR_INITIAL,   // 50km制限
        FULL_SCREEN       // 制限なし（表示範囲全域）
    }

    private static class PoiInfo {
        final double dLat;
        final double dLon;
        final boolean isPostOffice;
        final boolean isPostBox;
        PoiInfo(double dLat, double dLon, boolean isPostOffice, boolean isPostBox) {
            this.dLat = dLat;
            this.dLon = dLon;
            this.isPostOffice = isPostOffice;
            this.isPostBox = isPostBox;
        }
    }

    /** 一度生成・解析したマーカーを、地図移動後の再通知でも再利用する。 */
    private final Map<String, MarkerEntry> markerCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static final String PREF_MAP_STATE = "map_state";
    private static final String KEY_LAST_LAT = "last_lat";
    private static final String KEY_LAST_LON = "last_lon";
    private static final String KEY_LAST_ZOOM = "last_zoom";
    private static final String KEY_HAS_SAVED_STATE = "has_saved_state";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        
        authRepository = new AuthRepository(this);
        // ローカルキャッシュ(SQLite)を初期化してからViewModel/リポジトリを利用する
        PoiRepositoryImpl.init(getApplicationContext());
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.updateAccessToken(authRepository.getAccessToken());
        
        // osmdroid configuration
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        
        setContentView(R.layout.activity_main);

        ClockFilterButton filterButton = findViewById(R.id.filter_button);
        if (filterButton != null) {
            filterButton.invalidate();
        }
        View postOfficeFilterButtonForHolidays = findViewById(R.id.post_office_filter_button);
        if (postOfficeFilterButtonForHolidays != null) {
            postOfficeFilterButtonForHolidays.invalidate();
        }

        // Edge-to-Edge adjustment
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        map = findViewById(R.id.map);
        gpsProgress = findViewById(R.id.gps_progress);
        ScaleBarView scaleBarView = findViewById(R.id.scale_bar);
        if (scaleBarView != null) {
            scaleBarView.setMapView(map);
        }
        locationOverlay = new org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(
                new org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(this), map);
        
        // Use a simple blue circle for current location instead of the default person icon
        android.graphics.Bitmap personBitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(personBitmap);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x330000FF); // Semi-transparent blue for the outer ring
        canvas.drawCircle(24, 24, 24, paint);
        paint.setColor(0xFFFFFFFF); // White border
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawCircle(24, 24, 10, paint);
        paint.setColor(0xFF4285F4); // Google Maps blue
        paint.setStyle(android.graphics.Paint.Style.FILL);
        canvas.drawCircle(24, 24, 10, paint);
        
        locationOverlay.setPersonIcon(personBitmap);
        locationOverlay.setDirectionIcon(personBitmap);
        locationOverlay.setPersonHotspot(24, 24);

        locationOverlay.enableMyLocation();
        map.getOverlays().add(locationOverlay);

        // OSM JP Tile Server
        map.setTileSource(new XYTileSource("OSMJP", (int) MIN_ZOOM, 20, 256, ".png",
                new String[] { "https://tile.openstreetmap.jp/" }));
        map.setMultiTouchControls(true);
        map.setScrollableAreaLimitDouble(JAPAN_BOUNDS);
        map.setMinZoomLevel(MIN_ZOOM);

        android.content.SharedPreferences mapPrefs = getSharedPreferences(PREF_MAP_STATE, MODE_PRIVATE);
        if (mapPrefs.getBoolean(KEY_HAS_SAVED_STATE, false)) {
            double lat = Double.longBitsToDouble(mapPrefs.getLong(KEY_LAST_LAT, Double.doubleToRawLongBits(TOKYO_CENTRAL_POST_OFFICE.getLatitude())));
            double lon = Double.longBitsToDouble(mapPrefs.getLong(KEY_LAST_LON, Double.doubleToRawLongBits(TOKYO_CENTRAL_POST_OFFICE.getLongitude())));
            double zoom = (double) mapPrefs.getFloat(KEY_LAST_ZOOM, 18.0f);
            map.getController().setZoom(zoom);
            map.getController().setCenter(new GeoPoint(lat, lon));
            // 保存された位置から開始する場合は、GPS確定を待たずにロードを開始できるようにする
            initialLocationSet = true;
            // ただしGPSパーミッションがある場合は、GPS確定時の即時移動を許可するために lastLocation は null のままにする
        } else {
            map.getController().setZoom(18.0);
            map.getController().setCenter(TOKYO_CENTRAL_POST_OFFICE);
        }

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.updateAccessToken(authRepository.getAccessToken());
        viewModel.setFilterOpenOnly(false); // 初期化トリガー
        viewModel.fetchDataDate(); // データ鮮度情報の取得を開始
        
        editPoiLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        boolean isNote = result.getData().getBooleanExtra("is_note", false);
                        if (isNote) {
                            viewModel.setSuccessMessage(getString(R.string.msg_note_success));
                        } else {
                            boolean isNew = result.getData().getBooleanExtra("is_new", false);
                            viewModel.setSuccessMessage(getString(isNew ? R.string.msg_add_success : R.string.msg_save_success));
                        }
                    }
                }
        );

        // 初回表示トリガー：レイアウト完了後に位置情報が確定していれば updatePois を実行する
        map.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                map.removeOnLayoutChangeListener(this);
                // 位置情報パーミッションが解決済み（許可・拒否問わず）で、かつGPSを待機していない場合のみ実行
                if (canLoadPois() && (initialLocationSet || locationPermissionResolved)) {
                    // もしパーミッションが許可されていて、まだ位置が確定していないならGPSを待つ
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && !initialLocationSet) {
                        return;
                    }
                    updatePois(true, UpdateMode.GPS_OR_INITIAL);
                }
            }
        });

        map.addMapListener(new org.osmdroid.events.MapListener() {
            @Override
            public boolean onScroll(org.osmdroid.events.ScrollEvent event) {
                initialLocationSet = true;
                scheduleUpdatePois();
                return true;
            }

            @Override
            public boolean onZoom(org.osmdroid.events.ZoomEvent event) {
                initialLocationSet = true;
                scheduleUpdatePois();
                return true;
            }
        });

        requestLocationPermissions();
        
        // Observe Filtered POIs
        viewModel.getFilteredPois().observe(this, pois -> {
            if (pois == null) return;
            final int renderGeneration = markerRenderGeneration.incrementAndGet();
            
            // UIスレッドで必要な情報を取得
            final int mapWidth = map.getWidth();
            final int mapHeight = map.getHeight();
            final boolean zoomAdjustmentPending = gpsZoomAdjustmentPending;
            final GeoPoint zoomCenter = gpsZoomCenter;
            final double zoomBase = gpsZoomBase;
            final double zoomLimit = gpsZoomLimit;
            final boolean postOfficeOnly = Boolean.TRUE.equals(viewModel.getFilterPostOfficeOnly().getValue());
            final List<OsmPoi> allPoisSnapshot = viewModel.getPois().getValue();

            markerStateExecutor.execute(() -> {
                if (markerRenderGeneration.get() != renderGeneration) return;

                // 1. GPSズームの計算 (Background)
                Double targetZoom = null;
                boolean satisfied = false;
                if (zoomAdjustmentPending && zoomCenter != null && mapWidth > 0 && mapHeight > 0) {
                    // 計算部分は重複を避けるため helper に切り出すか、ここでインライン化
                    targetZoom = calculateTargetZoomInBackground(pois, zoomCenter, zoomBase, mapWidth, mapHeight, postOfficeOnly, zoomLimit);
                    if (targetZoom != null) satisfied = true;
                }
                final Double fTargetZoom = targetZoom;
                final boolean fSatisfied = satisfied;

                // 2. マーカーキャッシュのクリーンアップ (Background)
                if (allPoisSnapshot != null) {
                    Set<String> liveKeys = new HashSet<>();
                    for (OsmPoi poi : allPoisSnapshot) liveKeys.add(markerKey(poi));
                    markerCache.keySet().retainAll(liveKeys);
                }

                // 3. UIスレッドで描画反映
                runOnUiThread(() -> {
                    if (markerRenderGeneration.get() != renderGeneration) return;

                    if (fTargetZoom != null) {
                        map.getController().setZoom(fTargetZoom);
                        if (fSatisfied) {
                            gpsZoomAdjustmentPending = false;
                            if (gpsProgress != null) gpsProgress.setVisibility(View.GONE);
                        } else {
                            Boolean loading = viewModel.getLoading().getValue();
                            if (!Boolean.TRUE.equals(loading)) {
                                gpsZoomAdjustmentPending = false;
                                if (gpsProgress != null) gpsProgress.setVisibility(View.GONE);
                            }
                        }
                    }

                    map.getOverlays().removeIf(overlay -> overlay instanceof PoiMarker);
                    viewModel.updateAccessToken(authRepository.getAccessToken());

                    ArrayList<PoiMarker> markers = new ArrayList<>();
                    ArrayList<OsmPoi> poisToParse = new ArrayList<>();
                    ArrayList<PoiMarker> markersToParse = new ArrayList<>();
                    
                    for (OsmPoi poi : pois) {
                        boolean postOffice = "post_office".equals(poi.getTag("amenity"));
                        String key = markerKey(poi);
                        String stateSource = markerStateSource(poi);
                        MarkerEntry entry = markerCache.get(key);

                        if (entry == null || entry.postOffice != postOffice) {
                            PoiMarker.PoiType type = postOffice
                                    ? PoiMarker.PoiType.POST_OFFICE : PoiMarker.PoiType.POST_BOX;
                            entry = new MarkerEntry(new PoiMarker(map, type), postOffice);
                            final MarkerEntry clickEntry = entry;
                            entry.marker.setOnMarkerClickListener((m, mapView) -> {
                                PoiMarker pm = (PoiMarker) m;
                                currentPoiDetailsDialog = PoiDetailsDialog.show(this, clickEntry.poi, pm.getSchedule(), pm.getLimitedServiceSchedule(), lastLocation);
                                return true;
                            });
                            markerCache.put(key, entry);
                        }

                        entry.poi = poi;
                        PoiMarker marker = entry.marker;
                        marker.setPosition(new GeoPoint(poi.getLat(), poi.getLon()));
                        markers.add(marker);

                        if (!stateSource.equals(entry.stateSource)) {
                            marker.setSchedule(null);
                            poisToParse.add(poi);
                            markersToParse.add(marker);
                        }
                    }

                    markers.sort(this::compareMarkerPriority);
                    map.getOverlays().addAll(markers);
                    map.invalidate();
                    if (!poisToParse.isEmpty()) {
                        updateMarkerStatesAsync(poisToParse, markersToParse, markers, renderGeneration);
                    }
                });
            });
        });

        // Menu Button
        View menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        viewModel.getLoading().observe(this, loadingValue -> {
            boolean isLoading = Boolean.TRUE.equals(loadingValue);
            if (!isLoading && gpsZoomAdjustmentPending) {
                // ロードが完了した時点でまだズーム調整が保留されている場合、
                // 最新のPOIリストを用いて最終的なズーム調整を実行する。
                adjustGpsZoomForPoiCount(viewModel.getFilteredPois().getValue());
            }
        });


        // Filter Button
        filterButton.setOnClickListener(v -> {
            boolean currentFilter = viewModel.getFilterOpenOnly().getValue() != null && viewModel.getFilterOpenOnly().getValue();
            viewModel.setFilterOpenOnly(!currentFilter);
            filterButton.setFilterActive(!currentFilter);
            Toast.makeText(this, currentFilter ? "フィルタ解除" : "営業中・収集残りありのみ表示", Toast.LENGTH_SHORT).show();
        });

        viewModel.getFilterOpenOnly().observe(this, active -> {
            filterButton.setFilterActive(active != null && active);
        });

        // Post Office Filter Button
        PostOfficeFilterButton postOfficeFilterButton = findViewById(R.id.post_office_filter_button);
        postOfficeFilterButton.setOnClickListener(v -> {
            boolean currentFilter = viewModel.getFilterPostOfficeOnly().getValue() != null && viewModel.getFilterPostOfficeOnly().getValue();
            viewModel.setFilterPostOfficeOnly(!currentFilter);
            postOfficeFilterButton.setFilterActive(!currentFilter);
            Toast.makeText(this, currentFilter ? "フィルタ解除" : "郵便局のみ表示", Toast.LENGTH_SHORT).show();
        });

        viewModel.getFilterPostOfficeOnly().observe(this, active -> {
            postOfficeFilterButton.setFilterActive(active != null && active);
        });

        // GPS Button
        findViewById(R.id.gps_button).setOnClickListener(v -> {
            if (lastLocation != null) {
                gpsZoomLimit = map.getZoomLevelDouble();
                performInitialGpsZoom(lastLocation);
            } else {
                Toast.makeText(this, "現在地を取得中です...", Toast.LENGTH_SHORT).show();
            }
        });

        // Add PostBox Button
        findViewById(R.id.add_postbox_button).setOnClickListener(v -> {
            Intent intent = new Intent(this, EditPoiActivity.class);
            org.osmdroid.api.IGeoPoint center = map.getMapCenter();
            intent.putExtra("POI_ID", 0L);
            intent.putExtra("POI_LAT", center.getLatitude());
            intent.putExtra("POI_LON", center.getLongitude());
            intent.putExtra("ZOOM_LEVEL", map.getZoomLevelDouble());
            launchEditPoi(intent);
        });

        viewModel.getLocation().observe(this, this::updateCurrentLocation);
        final TextView statusBar = findViewById(R.id.error_bar);
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg == null || msg.isEmpty()) {
                statusBar.setVisibility(View.GONE);
            } else {
                statusBar.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.jp_post_red)); // Red
                statusBar.setText(msg);
                statusBar.setVisibility(View.VISIBLE);
                statusBar.postDelayed(() -> {
                    statusBar.setVisibility(View.GONE);
                    viewModel.clearError();
                }, 5000);
            }
        });

        viewModel.getSuccessMessage().observe(this, msg -> {
            if (msg == null || msg.isEmpty()) {
                statusBar.setVisibility(View.GONE);
            } else {
                statusBar.setBackgroundColor(0xFF4CAF50); // Green
                statusBar.setText(msg);
                statusBar.setVisibility(View.VISIBLE);
                statusBar.postDelayed(() -> {
                    statusBar.setVisibility(View.GONE);
                    viewModel.clearSuccessMessage();
                }, 5000);
            }
        });


    }

    public void launchEditPoi(Intent intent) {
        if (editPoiLauncher != null) {
            editPoiLauncher.launch(intent);
        } else {
            startActivity(intent);
        }
    }

    private void scheduleUpdatePois() {
        if (debounceRunnable != null) {
            debounceHandler.removeCallbacks(debounceRunnable);
        }
        debounceRunnable = this::updatePois;
        debounceHandler.postDelayed(debounceRunnable, 1000);
    }

    /**
     * GPS移動先のPOI密度に応じて、適切なズームレベルを計算する（バックグラウンド実行用）。
     */
    private Double calculateTargetZoomInBackground(List<OsmPoi> pois, GeoPoint center, double zoomBase, int width, int height, boolean postOfficeOnly, double zoomLimit) {
        if (pois == null || center == null) return null;

        int shortSide = Math.min(width, height);

        // gpsZoomBaseにおける1ピクセルあたりの緯度経度幅を計算で求める
        double dLonPerPixel = 360.0 / (Math.pow(2.0, zoomBase) * 256.0);
        double dLatPerPixel = dLonPerPixel / Math.cos(Math.toRadians(center.getLatitude()));

        // gpsZoomBaseにおける短辺相当の地理的範囲（半径）
        double halfLonBase = (shortSide * dLonPerPixel) / 2.0;
        double halfLatBase = (shortSide * dLatPerPixel) / 2.0;

        List<PoiInfo> poiInfos = new ArrayList<>(pois.size());
        for (OsmPoi poi : pois) {
            double dLat = Math.abs(poi.getLat() - center.getLatitude());
            double dLon = longitudeDistance(poi.getLon(), center.getLongitude());
            String amenity = poi.getTag("amenity");
            poiInfos.add(new PoiInfo(dLat, dLon, "post_office".equals(amenity), "post_box".equals(amenity)));
        }

        for (double candidateZoom = GPS_MAX_ZOOM; candidateZoom >= MIN_ZOOM; candidateZoom -= 1.0) {
            double scale = Math.pow(2.0, candidateZoom - zoomBase);
            double halfLon = halfLonBase / scale;
            double halfLat = halfLatBase / scale;

            int visibleCount = 0;
            boolean hasPostOffice = false;
            boolean hasPostBox = false;

            for (PoiInfo pi : poiInfos) {
                if (pi.dLat <= halfLat && pi.dLon <= halfLon) {
                    visibleCount++;
                    if (pi.isPostOffice) hasPostOffice = true;
                    if (pi.isPostBox) hasPostBox = true;
                }
            }

            if (visibleCount >= 5 && hasPostOffice && (postOfficeOnly || hasPostBox)) {
                return candidateZoom;
            }
        }
        return Math.max(MIN_ZOOM, zoomLimit);
    }

    /** 互換用。 */
    private void adjustGpsZoomForPoiCount(List<OsmPoi> pois) {
        // Obsolete, moved to background logic
    }

    private void performInitialGpsZoom(Location location) {
        if (location == null) return;
        if (gpsProgress != null) gpsProgress.setVisibility(View.VISIBLE);
        gpsZoomCenter = mapTargetFor(location);
        gpsZoomBase = GPS_MIN_ZOOM;

        // 計算式による範囲算出を導入したため、移動完了を待つ必要がない。
        gpsZoomAdjustmentPending = true;
        initialLocationSet = true;

        map.getController().setZoom(gpsZoomBase);
        map.getController().setCenter(gpsZoomCenter);

        if (canLoadPois()) {
            updatePois(true, UpdateMode.GPS_OR_INITIAL);
        }
    }

    private static double longitudeDistance(double longitude1, double longitude2) {
        double distance = Math.abs(longitude1 - longitude2);
        return Math.min(distance, 360.0 - distance);
    }

    private static GeoPoint mapTargetFor(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        if (latitude >= JAPAN_BOUNDS.getLatSouth()
                && latitude <= JAPAN_BOUNDS.getLatNorth()
                && longitude >= JAPAN_BOUNDS.getLonWest()
                && longitude <= JAPAN_BOUNDS.getLonEast()) {
            return new GeoPoint(latitude, longitude);
        }
        return TOKYO_CENTRAL_POST_OFFICE;
    }

    private void updatePois(boolean forceNotify, UpdateMode mode) {
        if (!canLoadPois() || map == null || !map.isLayoutOccurred()) {
            return;
        }

        org.osmdroid.util.BoundingBox bb = map.getBoundingBox();
        org.osmdroid.api.IGeoPoint center = map.getMapCenter();
        double centerLat = center.getLatitude();
        double centerLon = center.getLongitude();

        double latMin, latMax, lonMin, lonMax;
        if (mode == UpdateMode.GPS_OR_INITIAL) {
            // GPSボタン押下時と初期表示は、GPS座標と周辺50km以内
            double rangeDeg = 50.0 / 111.0;
            latMin = centerLat - rangeDeg;
            latMax = centerLat + rangeDeg;
            double cosLat = Math.cos(Math.toRadians(centerLat));
            double rangeLon = (cosLat > 0.01) ? rangeDeg / cosLat : rangeDeg;
            lonMin = centerLon - rangeLon;
            lonMax = centerLon + rangeLon;
        } else {
            // 通常表示・全画面取得: 画面内に映っている範囲全て
            latMin = bb.getLatSouth();
            latMax = bb.getLatNorth();
            lonMin = bb.getLonWest();
            lonMax = bb.getLonEast();
        }

        // 固定グリッド（1海里＝1/60度）へのアライメント
        double gridUnit = 1.0 / 60.0;
        double gridLatMin = Math.floor(latMin / gridUnit) * gridUnit;
        double gridLatMax = Math.ceil(latMax / gridUnit) * gridUnit;
        double gridLonMin = Math.floor(lonMin / gridUnit) * gridUnit;
        double gridLonMax = Math.ceil(lonMax / gridUnit) * gridUnit;

        // 逆ジオコーディング地点の密度を下げ、処理負荷を減らす
        // 最小ステップを0.1度とし、かつグリッド単位（1海里）の整数倍にする
        double rawStep = Math.max(0.1, Math.max(latMax - latMin, lonMax - lonMin) / 5.0);
        double step = Math.ceil(rawStep / gridUnit) * gridUnit;

        List<double[]> pointsList = new ArrayList<>();
        // 表示範囲の4隅を確実に入れる（RepositoryのactiveLatMin/Maxを正確にするため）
        pointsList.add(new double[]{latMin, lonMin});
        pointsList.add(new double[]{latMin, lonMax});
        pointsList.add(new double[]{latMax, lonMin});
        pointsList.add(new double[]{latMax, lonMax});

        for (double lat = gridLatMin; lat <= gridLatMax + (gridUnit / 2.0); lat += step) {
            for (double lon = gridLonMin; lon <= gridLonMax + (gridUnit / 2.0); lon += step) {
                pointsList.add(new double[]{lat, lon});
            }
        }

        // GPS座標そのものも判定対象に含める
        if (lastLocation != null) {
            pointsList.add(new double[]{lastLocation.getLatitude(), lastLocation.getLongitude()});
        }

        viewModel.fetchPoisForArea(pointsList.toArray(new double[0][0]), forceNotify);
    }

    private void updatePois() {
        updatePois(false, UpdateMode.NORMAL);
    }

    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionResolved = false;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);
        } else {
            locationPermissionResolved = true;
            viewModel.startLocationUpdates();
        }
    }

    private boolean canLoadPois() {
        // レイアウト未完了ならロードしない
        if (map == null || !map.isLayoutOccurred()) return false;
        
        // 位置確定済み、または権限判定済み（拒否含む）であればロード可能
        return initialLocationSet || locationPermissionResolved;
    }


    private void updateCurrentLocation(Location location) {
        if (location == null) return;
        boolean firstLocation = lastLocation == null;
        lastLocation = location;
        if (locationOverlay != null) {
            map.invalidate();
        }
        if (firstLocation && !initialLocationSet) {
            // ここで即座に initialLocationSet = true にするとレイアウト完了時のロードが走る可能性があるが、
            // ズームアニメーションを優先するため、performInitialGpsZoom 内で適切に管理する。
            gpsZoomLimit = map.getZoomLevelDouble();
            performInitialGpsZoom(location);
        } else if (firstLocation && initialLocationSet) {
            // すでに前回の位置が復旧されている場合、アニメーションさせずにGPS位置へ即時移動してPOIを更新する
            gpsZoomCenter = mapTargetFor(location);
            map.getController().setCenter(gpsZoomCenter);
            updatePois(true, UpdateMode.GPS_OR_INITIAL);
        }
        if (gpsProgress != null) gpsProgress.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            locationPermissionResolved = true;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                viewModel.startLocationUpdates();
            } else {
                if (gpsProgress != null) gpsProgress.setVisibility(View.GONE);
                // 許可が得られなかった場合は、デフォルト位置（東京）を初期位置として確定させる
                initialLocationSet = true;
                if (canLoadPois()) {
                    updatePois(true, UpdateMode.GPS_OR_INITIAL);
                }
            }
        }
    }

    private final android.os.Handler updateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (map != null) {
                updatePois(); // POI情報も1分ごとに更新（状態変更のため）
                map.invalidate(); // 再描画をトリガーしてリングを更新
            }
            View filterButton = findViewById(R.id.filter_button);
            if (filterButton != null) {
                filterButton.invalidate();
            }
            updateHandler.postDelayed(this, 60000); // 1分ごとに実行
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
        if (locationOverlay != null) {
            locationOverlay.enableMyLocation();
        }
        if (viewModel != null) {
            viewModel.startLocationUpdates();
        }
        if (authRepository != null && viewModel != null) {
            viewModel.updateAccessToken(authRepository.getAccessToken());

            // マーカーの状態を強制的に再解析させる
            for (MarkerEntry entry : markerCache.values()) {
                entry.stateSource = null;
            }
            viewModel.forceRefresh(); // リングの状態などを最新時刻で再評価
        }

        // ダイアログが表示中なら、スケジュールを再計算して更新する
        if (currentPoiDetailsDialog != null && currentPoiDetailsDialog.isShowing()) {
            OsmPoi poi = currentPoiDetailsDialog.getPoi();
            boolean postOffice = "post_office".equals(poi.getTag("amenity"));
            TextValue tagValue = postOffice
                    ? new OpeningHours(poi.getTag("opening_hours"))
                    : new CollectionTimes(poi.getTag("collection_times"));
            ScheduleParser.TimeType timeType = postOffice
                    ? ScheduleParser.TimeType.OPENING_HOURS
                    : ScheduleParser.TimeType.COLLECTION_TIMES;

            ScheduleResult main = new SimpleScheduleParser().parse(
                    tagValue, System.currentTimeMillis(), timeType);

            ScheduleResult ls = null;
            if (postOffice) {
                String lsTag = poi.getTag("opening_hours:limited_service");
                if (lsTag != null && !lsTag.isEmpty()) {
                    ls = new SimpleScheduleParser().parse(
                            new OpeningHours(lsTag), System.currentTimeMillis(), ScheduleParser.TimeType.OPENING_HOURS);
                }
            }
            currentPoiDetailsDialog.update(main, ls, lastLocation);
        }

        // 時計ボタンなどの描画を更新
        View filterButton = findViewById(R.id.filter_button);
        if (filterButton != null) {
            filterButton.invalidate();
        }
        View postOfficeFilterButton = findViewById(R.id.post_office_filter_button);
        if (postOfficeFilterButton != null) {
            postOfficeFilterButton.invalidate();
        }

        // 初期ロードは onCreate/位置確定時に実行済み。ここで即時実行すると同じ範囲の
        // ロードが直列に二重投入され、更新表示が長時間消えないため、次回周期から開始する。
        updateHandler.postDelayed(updateRunnable, 60000);
    }

    private void saveMapState() {
        if (map == null) return;
        org.osmdroid.api.IGeoPoint center = map.getMapCenter();
        double zoom = map.getZoomLevelDouble();
        getSharedPreferences(PREF_MAP_STATE, MODE_PRIVATE).edit()
                .putLong(KEY_LAST_LAT, Double.doubleToRawLongBits(center.getLatitude()))
                .putLong(KEY_LAST_LON, Double.doubleToRawLongBits(center.getLongitude()))
                .putFloat(KEY_LAST_ZOOM, (float) zoom)
                .putBoolean(KEY_HAS_SAVED_STATE, true)
                .apply();
    }

    @Override
    public void onPause() {
        super.onPause();
        saveMapState();
        map.onPause();
        if (locationOverlay != null) {
            locationOverlay.disableMyLocation();
        }
        updateHandler.removeCallbacks(updateRunnable);
        viewModel.stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        markerRenderGeneration.incrementAndGet();
        markerStateExecutor.shutdownNow();
        super.onDestroy();
    }

    /**
     * マーカー本体を先に表示し、時間の掛かるスケジュール解析結果を小分けに反映する。
     */
    private void updateMarkerStatesAsync(List<OsmPoi> pois, ArrayList<PoiMarker> markers,
                                         ArrayList<PoiMarker> visibleMarkers, int renderGeneration) {
        markerStateExecutor.execute(() -> {
            final int batchSize = 25;
            for (int start = 0; start < pois.size(); start += batchSize) {
                if (markerRenderGeneration.get() != renderGeneration) return;

                int end = Math.min(start + batchSize, pois.size());
                ArrayList<ScheduleResult[]> results = new ArrayList<>(end - start);
                for (int i = start; i < end; i++) {
                    OsmPoi poi = pois.get(i);
                    boolean postOffice = "post_office".equals(poi.getTag("amenity"));
                    TextValue tagValue = postOffice
                            ? new OpeningHours(poi.getTag("opening_hours"))
                            : new CollectionTimes(poi.getTag("collection_times"));
                    ScheduleParser.TimeType timeType = postOffice
                            ? ScheduleParser.TimeType.OPENING_HOURS
                            : ScheduleParser.TimeType.COLLECTION_TIMES;
                    
                    ScheduleResult main = new SimpleScheduleParser().parse(
                            tagValue, System.currentTimeMillis(), timeType);
                    
                    ScheduleResult ls = null;
                    if (postOffice) {
                        String lsTag = poi.getTag("opening_hours:limited_service");
                        if (lsTag != null && !lsTag.isEmpty()) {
                            ls = new SimpleScheduleParser().parse(
                                    new OpeningHours(lsTag), System.currentTimeMillis(), ScheduleParser.TimeType.OPENING_HOURS);
                        }
                    }
                    results.add(new ScheduleResult[]{main, ls});
                }

                int batchStart = start;
                runOnUiThread(() -> {
                    if (markerRenderGeneration.get() != renderGeneration) return;
                    for (int i = 0; i < results.size(); i++) {
                        int resultIndex = batchStart + i;
                        OsmPoi poi = pois.get(resultIndex);
                        PoiMarker marker = markers.get(resultIndex);
                        marker.setSchedule(results.get(i)[0]);
                        marker.setLimitedServiceSchedule(results.get(i)[1]);
                        MarkerEntry entry = markerCache.get(markerKey(poi));
                        if (entry != null && entry.marker == marker) {
                            entry.stateSource = markerStateSource(poi);
                        }
                    }
                    map.invalidate();
                });
            }

            runOnUiThread(() -> {
                if (markerRenderGeneration.get() != renderGeneration) return;
                visibleMarkers.sort(this::compareMarkerPriority);
                map.getOverlays().removeAll(visibleMarkers);
                map.getOverlays().addAll(visibleMarkers);
                map.invalidate();
            });
        });
    }

    private String markerKey(OsmPoi poi) {
        String type = poi.getType() == null ? "" : poi.getType();
        if (poi.getId() != 0) {
            return type + ':' + poi.getId();
        }
        return type + ":@" + poi.getLat() + ',' + poi.getLon();
    }

    /** 状態表示に関係する値が変わった場合だけ再解析する。 */
    private String markerStateSource(OsmPoi poi) {
        return String.valueOf(poi.getTag("amenity")) + '\u0000'
                + String.valueOf(poi.getTag("opening_hours")) + '\u0000'
                + String.valueOf(poi.getTag("opening_hours:limited_service")) + '\u0000'
                + String.valueOf(poi.getTag("collection_times"));
    }

    private static final class MarkerEntry {
        final PoiMarker marker;
        final boolean postOffice;
        OsmPoi poi;
        String stateSource;

        MarkerEntry(PoiMarker marker, boolean postOffice) {
            this.marker = marker;
            this.postOffice = postOffice;
        }
    }

    private int compareMarkerPriority(PoiMarker a, PoiMarker b) {
        ScheduleResult sa = getEffectiveSchedule(a);
        ScheduleResult sb = getEffectiveSchedule(b);
        int priority = Integer.compare(
                getPriorityForSorting(sa), getPriorityForSorting(sb));
        if (priority != 0) return priority;

        ScheduleResult.Event ea = sa != null ? sa.getNextEvent() : null;
        ScheduleResult.Event eb = sb != null ? sb.getNextEvent() : null;

        if (ea != null && eb != null) {
            return eb.getTimestamp().compareTo(ea.getTimestamp());
        } else if (ea != null) {
            return 1;
        } else if (eb != null) {
            return -1;
        }
        return 0;
    }

    private ScheduleResult getEffectiveSchedule(PoiMarker marker) {
        ScheduleResult main = marker.getSchedule();
        ScheduleResult ls = marker.getLimitedServiceSchedule();
        if (ls != null && (ls.getCurrentState() == ScheduleResult.CurrentState.OPENING || 
                          ls.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON)) {
            return ls;
        }
        return main;
    }

    private int getPriorityForSorting(ScheduleResult schedule) {
        if (schedule == null) return 0;
        switch (schedule.getCurrentState()) {
            case OPENING_BUT_EVENT_SOON: return 100;
            case OPENING: return 80;
            case CLOSING_BUT_OPEN_SOON: return 60;
            case CLOSED: return 40;
            case TODAY_FINISHED: return 20;
            case UNKNOWN:
            default: return 0;
        }
    }
}
