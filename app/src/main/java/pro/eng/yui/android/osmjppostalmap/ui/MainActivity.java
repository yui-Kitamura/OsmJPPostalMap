package pro.eng.yui.android.osmjppostalmap.ui;

import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import pro.eng.yui.android.osmjppostalmap.core.PoiDetailsDialog;
import pro.eng.yui.android.osmjppostalmap.core.PoiMarker;
import pro.eng.yui.android.osmjppostalmap.domain.model.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.R;

public class MainActivity extends AppCompatActivity {

    private MapView map;
    private MainViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // osmdroid configuration
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        
        setContentView(R.layout.activity_main);

        map = findViewById(R.id.map);
        // OSM JP Tile Server
        map.setTileSource(new XYTileSource("OSMJP", 0, 18, 256, ".png", 
                new String[] { "https://tile.openstreetmap.jp/" }));
        map.setMultiTouchControls(true);

        GeoPoint startPoint = new GeoPoint(35.68238, 139.76556); // 東京駅前郵便局
        map.getController().setZoom(17.0);
        map.getController().setCenter(startPoint);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        
        // Observe POIs
        viewModel.getPois().observe(this, pois -> {
            map.getOverlays().clear();
            for (OsmPoi poi : pois) {
                PoiMarker.PoiType type = "post_office".equals(poi.getTag("amenity")) ? 
                        PoiMarker.PoiType.POST_OFFICE : PoiMarker.PoiType.POST_BOX;
                PoiMarker marker = new PoiMarker(map, type);
                marker.setPosition(new GeoPoint(poi.getLat(), poi.getLon()));
                
                // 解析 (本来はViewModelで行うべき)
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

        // 初期表示領域のPOI取得
        map.addMapListener(new org.osmdroid.events.MapListener() {
            @Override
            public boolean onScroll(org.osmdroid.events.ScrollEvent event) {
                updatePois();
                return true;
            }

            @Override
            public boolean onZoom(org.osmdroid.events.ZoomEvent event) {
                updatePois();
                return true;
            }
        });
    }

    private void updatePois() {
        org.osmdroid.util.BoundingBox box = map.getBoundingBox();
        viewModel.fetchPois(box.getLatSouth(), box.getLonWest(), box.getLatNorth(), box.getLonEast());
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
    }
}
