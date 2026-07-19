package pro.eng.yui.android.osmjppostalmap.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import pro.eng.yui.android.osmjppostalmap.core.PoiDetailsDialog;
import pro.eng.yui.android.osmjppostalmap.core.PoiMarker;
import pro.eng.yui.android.osmjppostalmap.domain.model.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.R;

import android.view.View;
import android.content.Intent;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity {

    private MapView map;
    private MainViewModel viewModel;
    private RecyclerView searchResultsList;
    private pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository authRepository;

    private double lastZoomLevel = 17.0;
    private final android.os.Handler debounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable debounceRunnable = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        authRepository = new pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository(this);
        // osmdroid configuration
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        
        setContentView(R.layout.activity_main);

        // Edge-to-Edge adjustment
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        map = findViewById(R.id.map);
        searchResultsList = findViewById(R.id.search_results);
        searchResultsList.setLayoutManager(new LinearLayoutManager(this));

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
        
        // Observe Filtered POIs
        viewModel.getFilteredPois().observe(this, pois -> {
            map.getOverlays().clear();
            viewModel.updateAccessToken(authRepository.getAccessToken());
            for (OsmPoi poi : pois) {
                PoiMarker.PoiType type = "post_office".equals(poi.getTag("amenity")) ? 
                        PoiMarker.PoiType.POST_OFFICE : PoiMarker.PoiType.POST_BOX;
                PoiMarker marker = new PoiMarker(map, type);
                marker.setPosition(new GeoPoint(poi.getLat(), poi.getLon()));
                
                String tag = type == PoiMarker.PoiType.POST_OFFICE ? "opening_hours" : "collection_times";
                marker.setSchedule(new pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser()
                        .parse(poi.getTag(tag), System.currentTimeMillis()));
                
                marker.setOnMarkerClickListener((m, mapView) -> {
                    PoiDetailsDialog.show(this, poi, ((PoiMarker)m).getSchedule());
                    return true;
                });
                map.getOverlays().add(marker);
            }
            map.invalidate();
        });

        // Menu Button
        View menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        // Refresh Button
        findViewById(R.id.refresh_button).setOnClickListener(v -> {
            updatePois();
            Toast.makeText(this, "再取得しています...", Toast.LENGTH_SHORT).show();
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

        // Add PostBox Button
        findViewById(R.id.add_postbox_button).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddPostBoxActivity.class);
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
                    // ズームイン時は処理しない
                    lastZoomLevel = currentZoom;
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
        if (map == null || !map.isLayoutOccurred()) {
            return;
        }
        org.osmdroid.util.BoundingBox box = map.getBoundingBox();
        viewModel.fetchPois(box.getLatSouth(), box.getLonWest(), box.getLatNorth(), box.getLonEast());
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
        updateHandler.post(updateRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
        updateHandler.removeCallbacks(updateRunnable);
    }
}
