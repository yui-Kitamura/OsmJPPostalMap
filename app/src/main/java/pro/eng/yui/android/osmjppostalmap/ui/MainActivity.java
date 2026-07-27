package pro.eng.yui.android.osmjppostalmap.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import pro.eng.yui.android.osmjppostalmap.core.PoiDetailsDialog;
import pro.eng.yui.android.osmjppostalmap.core.PoiMarker;
import pro.eng.yui.android.osmjppostalmap.core.PrefRefreshDialog;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.CollectionTimes;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OpeningHours;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.R;

import android.view.View;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.widget.TextView;
import android.widget.Toast;
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
    private LocationManager locationManager;
    private Location lastLocation;
    private static final int PERMISSION_REQUEST_LOCATION = 100;
    private static final double GPS_MIN_ZOOM = 15.0;
    private static final double GPS_MAX_ZOOM = 19.0;
    private static final double MIN_ZOOM = 5.0;
    private static final int GPS_MAX_VISIBLE_POIS = 30;
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
    private org.osmdroid.util.BoundingBox gpsZoomMinBounds;
    private double gpsZoomBase = GPS_MIN_ZOOM;
    private final ExecutorService markerStateExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger markerRenderGeneration = new AtomicInteger();
    /** 一度生成・解析したマーカーを、地図移動後の再通知でも再利用する。 */
    private final Map<String, MarkerEntry> markerCache = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // OSM JP Tile Server
        map.setTileSource(new XYTileSource("OSMJP", (int) MIN_ZOOM, 19, 256, ".png",
                new String[] { "https://tile.openstreetmap.jp/" }));
        map.setMultiTouchControls(true);
        map.setScrollableAreaLimitDouble(JAPAN_BOUNDS);
        map.setMinZoomLevel(MIN_ZOOM);

        GeoPoint startPoint = TOKYO_CENTRAL_POST_OFFICE;
        map.getController().setZoom(17.0);
        map.getController().setCenter(startPoint);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.updateAccessToken(authRepository.getAccessToken());
        viewModel.setFilterOpenOnly(false); // 初期化トリガー
        viewModel.fetchDataDate(); // データ鮮度情報の取得を開始
        
        // 初回表示トリガー：レイアウト完了後に一度 updatePois を実行する
        map.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                map.removeOnLayoutChangeListener(this);
                // 位置がレイアウトより先に確定した場合、または位置情報が不許可と確定した場合にロードする。
                if (canLoadPois()) {
                    updatePois();
                }
            }
        });

        requestLocationPermissions();
        
        // Observe Filtered POIs
        viewModel.getFilteredPois().observe(this, pois -> {
            int renderGeneration = markerRenderGeneration.incrementAndGet();
            adjustGpsZoomForPoiCount(pois);
            map.getOverlays().removeIf(overlay -> overlay instanceof PoiMarker);
            viewModel.updateAccessToken(authRepository.getAccessToken());

            List<OsmPoi> allPois = viewModel.getPois().getValue();
            if (allPois != null) {
                Set<String> liveKeys = new HashSet<>();
                for (OsmPoi poi : allPois) liveKeys.add(markerKey(poi));
                markerCache.keySet().retainAll(liveKeys);
            }

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
                    MarkerEntry clickEntry = entry;
                    entry.marker.setOnMarkerClickListener((m, mapView) -> {
                        PoiDetailsDialog.show(this, clickEntry.poi, ((PoiMarker) m).getSchedule());
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

        // Menu Button
        View menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        // Refresh Button → 更新ダイアログを開く
        CooldownRefreshButton refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> {
            if (!initialLocationSet) {
                initialLocationSet = true;
            }
            PrefRefreshDialog.show(this, viewModel, this::updatePois);
        });

        viewModel.getCooldownRemaining().observe(this, remaining -> {
            refreshButton.setCooldown(remaining, viewModel.getCooldownInterval());
            boolean loading = Boolean.TRUE.equals(viewModel.getLoading().getValue());
            refreshButton.setEnabled(remaining <= 0 && !loading);
            if (remaining > 0) {
                refreshButton.setAlpha(0.5f);
            } else {
                refreshButton.setAlpha(1.0f);
            }
        });

        viewModel.getLoading().observe(this, loadingValue -> {
            boolean loading = Boolean.TRUE.equals(loadingValue);
            refreshButton.setLoading(loading);
            Long remaining = viewModel.getCooldownRemaining().getValue();
            refreshButton.setEnabled(!loading && (remaining == null || remaining <= 0));
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
                gpsZoomCenter = mapTargetFor(lastLocation);
                // 押下時より広域にはしない。15未満の場合だけ最小値の15まで拡大する。
                gpsZoomBase = Math.min(GPS_MAX_ZOOM,
                        Math.max(GPS_MIN_ZOOM, map.getZoomLevelDouble()));
                map.getController().setZoom(gpsZoomBase);
                map.getController().animateTo(gpsZoomCenter);

                // 移動後の基準ズーム表示範囲を取得してからPOIをロードする。
                // ロード完了時に、実際に画面内へ描画されるPOI数から最終ズームを決める。
                map.postDelayed(() -> {
                    gpsZoomMinBounds = map.getBoundingBox();
                    gpsZoomAdjustmentPending = true;
                    initialLocationSet = true;
                    updatePois();
                }, 500);
            } else {
                Toast.makeText(this, "現在地を取得中です...", Toast.LENGTH_SHORT).show();
            }
        });

        // Add PostBox Button
        findViewById(R.id.add_postbox_button).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddPostBoxActivity.class);
            org.osmdroid.api.IGeoPoint center = map.getMapCenter();
            intent.putExtra("LATITUDE", center.getLatitude());
            intent.putExtra("LONGITUDE", center.getLongitude());
            intent.putExtra("ZOOM_LEVEL", map.getZoomLevelDouble());
            startActivity(intent);
        });

        // Error Bar
        TextView statusBar = findViewById(R.id.error_bar);
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


        map.addMapListener(new org.osmdroid.events.MapListener() {
            @Override
            public boolean onScroll(org.osmdroid.events.ScrollEvent event) {
                if (map.isLayoutOccurred()) {
                    scheduleUpdatePois();
                }
                return true;
            }

            @Override
            public boolean onZoom(org.osmdroid.events.ZoomEvent event) {
                if (!map.isLayoutOccurred()) {
                    return true;
                }
                // 表示中のPOIがない場合は再取得を試みる
                List<OsmPoi> currentPois = viewModel.getFilteredPois().getValue();
                if (currentPois == null || currentPois.isEmpty()) {
                    scheduleUpdatePois();
                }
                return true;
            }
        });
    }

    private void scheduleUpdatePois() {
        if (debounceRunnable != null) {
            debounceHandler.removeCallbacks(debounceRunnable);
        }
        debounceRunnable = this::updatePois;
        debounceHandler.postDelayed(debounceRunnable, 1500);
    }

    /**
     * GPS移動先のPOI密度に応じて、描画数が過密にならない最小ズームを選ぶ。
     * Zoomが1上がるごとに表示範囲の縦横が半分になる前提で、各候補の
     * 画面内POI数を実座標から数える。
     */
    private void adjustGpsZoomForPoiCount(List<OsmPoi> pois) {
        if (!gpsZoomAdjustmentPending || gpsZoomCenter == null || gpsZoomMinBounds == null) {
            return;
        }
        gpsZoomAdjustmentPending = false;

        double targetZoom = GPS_MAX_ZOOM;
        double candidateZoom = gpsZoomBase;
        while (candidateZoom <= GPS_MAX_ZOOM) {
            double scale = Math.pow(2.0, candidateZoom - gpsZoomBase);
            double halfLatitudeSpan = gpsZoomMinBounds.getLatitudeSpan() / (2.0 * scale);
            double halfLongitudeSpan = gpsZoomMinBounds.getLongitudeSpan() / (2.0 * scale);
            int visibleCount = 0;

            for (OsmPoi poi : pois) {
                if (Math.abs(poi.getLat() - gpsZoomCenter.getLatitude()) <= halfLatitudeSpan
                        && longitudeDistance(poi.getLon(), gpsZoomCenter.getLongitude()) <= halfLongitudeSpan) {
                    visibleCount++;
                    if (visibleCount > GPS_MAX_VISIBLE_POIS) {
                        break;
                    }
                }
            }
            if (visibleCount <= GPS_MAX_VISIBLE_POIS) {
                targetZoom = candidateZoom;
                break;
            }
            if (candidateZoom >= GPS_MAX_ZOOM) {
                break;
            }
            // 最初の候補には押下時の小数ズームも使い、以降は整数ズームで評価する。
            candidateZoom = Math.min(GPS_MAX_ZOOM, Math.floor(candidateZoom) + 1.0);
        }
        map.getController().setZoom(targetZoom);
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

    private void updatePois() {
        if (!canLoadPois() || map == null || !map.isLayoutOccurred()) {
            return;
        }
        // 表示範囲の4隅＋中心を渡し、範囲にかかる都道府県すべてをキャッシュ優先で取得する
        org.osmdroid.util.BoundingBox bb = map.getBoundingBox();
        double[][] points = new double[][]{
                {map.getMapCenter().getLatitude(), map.getMapCenter().getLongitude()},
                {bb.getLatNorth(), bb.getLonWest()},
                {bb.getLatNorth(), bb.getLonEast()},
                {bb.getLatSouth(), bb.getLonWest()},
                {bb.getLatSouth(), bb.getLonEast()},
        };
        viewModel.fetchPoisForArea(points);
    }

    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionResolved = false;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);
        } else {
            locationPermissionResolved = true;
            startLocationUpdates();
        }
    }

    private boolean canLoadPois() {
        return initialLocationSet
                || (locationPermissionResolved
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED);
    }

    private void startLocationUpdates() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
                Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc != null) {
                    updateCurrentLocation(loc);
                    // 初期表示を現在地に
                    map.getController().setCenter(mapTargetFor(loc));
                    if (!initialLocationSet) {
                        initialLocationSet = true;
                        updatePois();
                    }
                }
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, locationListener);
                Location loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (loc != null && lastLocation == null) {
                    updateCurrentLocation(loc);
                    // 初期表示を現在地に
                    map.getController().setCenter(mapTargetFor(loc));
                    if (!initialLocationSet) {
                        initialLocationSet = true;
                        updatePois();
                    }
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            updateCurrentLocation(location);
            if (!initialLocationSet) {
                initialLocationSet = true;
                map.getController().setCenter(mapTargetFor(location));
                updatePois();
            }
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(@NonNull String provider) {}
        @Override public void onProviderDisabled(@NonNull String provider) {}
    };

    private void updateCurrentLocation(Location location) {
        lastLocation = location;
        if (locationOverlay != null && location != null) {
            // MyLocationNewOverlay handles its own location updates if provider is active,
            // but we can ensure it has the latest data.
            map.invalidate();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            locationPermissionResolved = true;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                // 許可が得られなかった場合は、デフォルト位置（東京）でロードを開始する
                updatePois();
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
        if (authRepository != null && viewModel != null) {
            viewModel.updateAccessToken(authRepository.getAccessToken());
            viewModel.forceRefresh(); // リングの状態などを最新時刻で再評価
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

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
        if (locationOverlay != null) {
            locationOverlay.disableMyLocation();
        }
        updateHandler.removeCallbacks(updateRunnable);
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
                ArrayList<ScheduleResult> results = new ArrayList<>(end - start);
                for (int i = start; i < end; i++) {
                    OsmPoi poi = pois.get(i);
                    boolean postOffice = "post_office".equals(poi.getTag("amenity"));
                    TextValue tagValue = postOffice
                            ? new OpeningHours(poi.getTag("opening_hours"))
                            : new CollectionTimes(poi.getTag("collection_times"));
                    ScheduleParser.TimeType timeType = postOffice
                            ? ScheduleParser.TimeType.OPENING_HOURS
                            : ScheduleParser.TimeType.COLLECTION_TIMES;
                    results.add(new SimpleScheduleParser().parse(
                            tagValue, System.currentTimeMillis(), timeType));
                }

                int batchStart = start;
                runOnUiThread(() -> {
                    if (markerRenderGeneration.get() != renderGeneration) return;
                    for (int i = 0; i < results.size(); i++) {
                        int resultIndex = batchStart + i;
                        OsmPoi poi = pois.get(resultIndex);
                        PoiMarker marker = markers.get(resultIndex);
                        marker.setSchedule(results.get(i));
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
        int priority = Integer.compare(
                getPriorityForSorting(a.getSchedule()), getPriorityForSorting(b.getSchedule()));
        if (priority != 0) return priority;
        if (a.getSchedule() != null && b.getSchedule() != null
                && a.getSchedule().getNextEvent() != null && b.getSchedule().getNextEvent() != null) {
            return b.getSchedule().getNextEvent().getTimestamp()
                    .compareTo(a.getSchedule().getNextEvent().getTimestamp());
        }
        return 0;
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
