package pro.eng.yui.android.osmjppostalmap.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.eng.yui.android.osmjppostalmap.data.api.OverpassApi;
import pro.eng.yui.android.osmjppostalmap.data.api.OverpassResponse;
import pro.eng.yui.android.osmjppostalmap.domain.model.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PoiRepositoryImpl implements PoiRepository {

    private final OverpassApi overpassApi;
    private final MutableLiveData<List<OsmPoi>> poisLiveData = new MutableLiveData<>(new ArrayList<>());

    public PoiRepositoryImpl() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://overpass-api.de/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.overpassApi = retrofit.create(OverpassApi.class);
    }

    @Override
    public LiveData<List<OsmPoi>> getPois(double minLat, double minLon, double maxLat, double maxLon) {
        String query = String.format(Locale.US,
                "[out:json][timeout:25];" +
                "(" +
                "  node[\"amenity\"=\"post_box\"](%f,%f,%f,%f);" +
                "  node[\"amenity\"=\"post_office\"](%f,%f,%f,%f);" +
                "  way[\"amenity\"=\"post_office\"](%f,%f,%f,%f);" +
                ");" +
                "out body center;",
                minLat, minLon, maxLat, maxLon,
                minLat, minLon, maxLat, maxLon,
                minLat, minLon, maxLat, maxLon);

        overpassApi.query(query).enqueue(new Callback<OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<OsmPoi> pois = new ArrayList<>();
                    for (OverpassResponse.Element element : response.body().elements) {
                        // 郵便局のフィルタリング (operator=未設定 OR "日本郵便")
                        if ("post_office".equals(element.tags.get("amenity"))) {
                            String operator = element.tags.get("operator");
                            if (operator != null && !operator.contains("日本郵便") && !operator.equals("Japan Post")) {
                                continue;
                            }
                        }
                        pois.add(new OsmPoi(element.id, element.lat, element.lon, element.type, element.tags));
                    }
                    poisLiveData.postValue(pois);
                }
            }

            @Override
            public void onFailure(Call<OverpassResponse> call, Throwable t) {
                // TODO: エラー通知
            }
        });

        return poisLiveData;
    }

    @Override
    public LiveData<OsmPoi> getPoi(long id, String type) {
        // TODO: 実装
        return new MutableLiveData<>();
    }

    @Override
    public void savePoi(OsmPoi poi, String comment, PoiSaveCallback callback) {
        // TODO: OSM API実装
    }

    @Override
    public void addPostBox(double lat, double lon, String shape, String branch, String collectionTimes, PoiSaveCallback callback) {
        // TODO: OSM API実装
    }

    @Override
    public void addNote(double lat, double lon, String text, PoiSaveCallback callback) {
        // TODO: OSM API実装
    }
}
