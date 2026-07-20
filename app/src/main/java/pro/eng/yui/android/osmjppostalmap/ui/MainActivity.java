package pro.eng.yui.android.osmjppostalmap.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.List;

import pro.eng.yui.android.osmjppostalmap.core.PoiDetailsDialog;
import pro.eng.yui.android.osmjppostalmap.core.PoiMarker;
import pro.eng.yui.android.osmjppostalmap.domain.model.OsmPoi;
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
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;
import pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser;

public class MainActivity extends AppCompatActivity {

    private MapView map;
    private org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay locationOverlay;
    private MainViewModel viewModel;
    private RecyclerView searchResultsList;
    private AuthRepository authRepository;
    private LocationManager locationManager;
    private Location lastLocation;
    private static final int PERMISSION_REQUEST_LOCATION = 100;

    private double lastZoomLevel = 17.0;
    private final android.os.Handler debounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable debounceRunnable = null;
    private boolean initialLocationSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        authRepository = new AuthRepository(this);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.updateAccessToken(authRepository.getAccessToken());
        
        // osmdroid configuration
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        
        setContentView(R.layout.activity_main);
        SimpleScheduleParser.setOnHolidaysLoadedListener(() -> {
            runOnUiThread(() -> {
                View filterButton = findViewById(R.id.filter_button);
                if (filterButton != null) {
                    filterButton.invalidate();
                }
            });
        });
        new Thread(SimpleScheduleParser::initializeHolidays).start();

        // Edge-to-Edge adjustment
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        map = findViewById(R.id.map);
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

        searchResultsList = findViewById(R.id.search_results);
        searchResultsList.setLayoutManager(new LinearLayoutManager(this));

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        SearchView searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                viewModel.search(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    searchResultsList.setVisibility(View.GONE);
                }
                return false;
            }
        });

        // OSM JP Tile Server
        map.setTileSource(new XYTileSource("OSMJP", 0, 18, 256, ".png", 
                new String[] { "https://tile.openstreetmap.jp/" }));
        map.setMultiTouchControls(true);

        GeoPoint startPoint = new GeoPoint(35.68238, 139.76556); // 東京駅前郵便局
        map.getController().setZoom(17.0);
        map.getController().setCenter(startPoint);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.updateAccessToken(authRepository.getAccessToken());
        viewModel.setFilterOpenOnly(false); // 初期化トリガー
        
        requestLocationPermissions();
        
        // Observe Filtered POIs
        viewModel.getFilteredPois().observe(this, pois -> {
            map.getOverlays().removeIf(overlay -> overlay instanceof PoiMarker);
            viewModel.updateAccessToken(authRepository.getAccessToken());
            
            java.util.ArrayList<PoiMarker> markers = new java.util.ArrayList<>();
            for (OsmPoi poi : pois) {
                ScheduleParser.Amenity amenity = 
                        "post_office".equals(poi.getTag("amenity")) ? 
                        ScheduleParser.Amenity.POST_OFFICE : 
                        ScheduleParser.Amenity.POST_BOX;
                
                PoiMarker.PoiType type = (amenity == ScheduleParser.Amenity.POST_OFFICE) ? 
                        PoiMarker.PoiType.POST_OFFICE : PoiMarker.PoiType.POST_BOX;
                PoiMarker marker = new PoiMarker(map, type);
                marker.setPosition(new GeoPoint(poi.getLat(), poi.getLon()));
                
                String tag = (amenity == ScheduleParser.Amenity.POST_OFFICE) ? 
                        "opening_hours" : "collection_times";
                marker.setSchedule(new SimpleScheduleParser()
                        .parse(poi.getTag(tag), System.currentTimeMillis(), amenity));
                
                marker.setOnMarkerClickListener((m, mapView) -> {
                    PoiDetailsDialog.show(this, poi, ((PoiMarker)m).getSchedule());
                    return true;
                });
                markers.add(marker);
            }

            // 優先度順にソート（重なり順を制御）
            // 優先度の低いものから順に追加することで、優先度の高いものが上に描画される
            markers.sort((a, b) -> {
                int pA = getPriorityForSorting(a.getSchedule());
                int pB = getPriorityForSorting(b.getSchedule());
                if (pA != pB) {
                    return Integer.compare(pA, pB); // 優先度昇順
                }
                // 同一ステータスの場合はイベント時刻が近い方を優先（後に描画）
                if (a.getSchedule().getNextEvent() != null && b.getSchedule().getNextEvent() != null) {
                    return Long.compare(b.getSchedule().getNextEvent().getTimestamp(), a.getSchedule().getNextEvent().getTimestamp());
                }
                return 0;
            });

            map.getOverlays().addAll(markers);
            map.invalidate();
        });

        // Menu Button
        View menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        // Refresh Button
        CooldownRefreshButton refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> {
            if (!initialLocationSet) {
                initialLocationSet = true;
            }
            updatePois();
            Toast.makeText(this, "再取得しています...", Toast.LENGTH_SHORT).show();
        });

        viewModel.getCooldownRemaining().observe(this, remaining -> {
            refreshButton.setCooldown(remaining, viewModel.getCooldownInterval());
            refreshButton.setEnabled(remaining <= 0);
            if (remaining > 0) {
                refreshButton.setAlpha(0.5f);
            } else {
                refreshButton.setAlpha(1.0f);
            }
        });

        // Search Button
        View searchCard = findViewById(R.id.search_card);
        View searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener(v -> {
            if (searchCard.getVisibility() == View.VISIBLE) {
                searchCard.setVisibility(View.GONE);
                searchResultsList.setVisibility(View.GONE);
                searchView.clearFocus();
            } else {
                searchCard.setVisibility(View.VISIBLE);
                searchView.requestFocus();
            }
        });

        // Filter Button
        ClockFilterButton filterButton = findViewById(R.id.filter_button);
        filterButton.setOnClickListener(v -> {
            boolean currentFilter = viewModel.getFilterOpenOnly().getValue() != null && viewModel.getFilterOpenOnly().getValue();
            viewModel.setFilterOpenOnly(!currentFilter);
            filterButton.setFilterActive(!currentFilter);
            Toast.makeText(this, currentFilter ? "フィルタ解除" : "営業中・収集残りありのみ表示", Toast.LENGTH_SHORT).show();
        });

        viewModel.getFilterOpenOnly().observe(this, active -> {
            filterButton.setFilterActive(active != null && active);
        });

        // GPS Button
        findViewById(R.id.gps_button).setOnClickListener(v -> {
            if (lastLocation != null) {
                map.getController().animateTo(new GeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude()));
                map.getController().setZoom(15.0); // 2km四方くらい (Zoom 15 is roughly 2.4km span at latitude 35)
                if (!initialLocationSet) {
                    initialLocationSet = true;
                    updatePois();
                }
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
            startActivity(intent);
        });

        // Error Bar
        TextView errorBar = findViewById(R.id.error_bar);
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg == null || msg.isEmpty()) {
                errorBar.setVisibility(View.GONE);
            } else {
                errorBar.setText(msg);
                errorBar.setVisibility(View.VISIBLE);
                errorBar.postDelayed(() -> errorBar.setVisibility(View.GONE), 5000);
            }
        });

        // Observe Search Results
        viewModel.getSearchResults().observe(this, results -> {
            if (results == null || results.isEmpty()) {
                searchResultsList.setVisibility(View.GONE);
                return;
            }
            searchResultsList.setVisibility(View.VISIBLE);
            searchResultsList.setAdapter(new SearchAdapter(results, poi -> {
                if (!initialLocationSet) {
                    initialLocationSet = true;
                }
                map.getController().animateTo(new GeoPoint(poi.getLat(), poi.getLon()));
                map.getController().setZoom(18.0);
                searchResultsList.setVisibility(View.GONE);
                searchCard.setVisibility(View.GONE);
                searchView.clearFocus();
            }));
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
                double currentZoom = event.getZoomLevel();
                if (currentZoom > lastZoomLevel) {
                    // ズームイン時
                    lastZoomLevel = currentZoom;
                    // 表示中のPOIがない場合は再取得を試みる
                    List<OsmPoi> currentPois = viewModel.getFilteredPois().getValue();
                    if (currentPois == null || currentPois.isEmpty()) {
                        scheduleUpdatePois();
                    }
                    return true;
                }
                lastZoomLevel = currentZoom;
                scheduleUpdatePois();
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

    private static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private final java.util.List<OsmPoi> items;
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(OsmPoi poi);
        }

        SearchAdapter(java.util.List<OsmPoi> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setPadding(32, 32, 32, 32);
            tv.setTextSize(16f);
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            OsmPoi poi = items.get(position);
            String name = poi.getTag("name");
            if (name == null) name = poi.getTag("addr:full");
            if (name == null) name = "位置: " + poi.getLat() + ", " + poi.getLon();
            holder.textView.setText(name);
            holder.itemView.setOnClickListener(v -> listener.onItemClick(poi));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(TextView v) { super(v); textView = v; }
        }
    }

    private void updatePois() {
        if (map == null || !map.isLayoutOccurred() || !initialLocationSet) {
            return;
        }
        org.osmdroid.util.BoundingBox box = map.getBoundingBox();
        viewModel.fetchPois(box.getLatSouth(), box.getLonWest(), box.getLatNorth(), box.getLonEast());
    }

    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
                Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc != null) {
                    updateCurrentLocation(loc);
                    // 初期表示を現在地に
                    map.getController().setCenter(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
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
                    map.getController().setCenter(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
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
                map.getController().setCenter(new GeoPoint(location.getLatitude(), location.getLongitude()));
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
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
        }
        updateHandler.post(updateRunnable);
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
