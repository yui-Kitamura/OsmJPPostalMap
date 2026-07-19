package pro.eng.yui.android.osmjppostalmap.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.eng.yui.android.osmjppostalmap.data.api.OsmApi;
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
    private final OsmApi osmApi;
    private final MutableLiveData<List<OsmPoi>> poisLiveData = new MutableLiveData<>(new ArrayList<>());
    private String accessToken;

    public PoiRepositoryImpl() {
        this(null);
    }

    public PoiRepositoryImpl(String accessToken) {
        this.accessToken = accessToken;
        Retrofit overpassRetrofit = new Retrofit.Builder()
                .baseUrl("https://overpass-api.de/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.overpassApi = overpassRetrofit.create(OverpassApi.class);

        Retrofit osmRetrofit = new Retrofit.Builder()
                .baseUrl("https://www.openstreetmap.org/api/0.6/")
                .build();
        this.osmApi = osmRetrofit.create(OsmApi.class);
    }

    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    @Override
    public LiveData<List<OsmPoi>> getPois(double minLat, double minLon, double maxLat, double maxLon) {
        String query = String.format(Locale.JAPAN,
        "[out:json][timeout:25];" +
                "area[\"boundary\"=\"administrative\"][\"admin_level\"=\"2\"][\"name\"=\"日本\"]->.a;" +
                "(" +
                "  node(area.a)[\"amenity\"=\"post_box\"](%f,%f,%f,%f);" +
                "  nw(area.a)[\"amenity\"=\"post_office\"][!\"operator\"](%f,%f,%f,%f);" +
                "  nw(area.a)[\"amenity\"=\"post_office\"][\"operator\"=\"日本郵便\"](%f,%f,%f,%f);" +
                ");" +
                "out body center;",
                minLat, minLon, maxLat, maxLon,
                minLat, minLon, maxLat, maxLon,
                minLat, minLon, maxLat, maxLon,
                minLat, minLon, maxLat, maxLon);

        overpassApi.query(query).enqueue(new Callback<OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<OsmPoi> currentPois = poisLiveData.getValue();
                    if (currentPois == null) currentPois = new ArrayList<>();
                    
                    for (OverpassResponse.Element element : response.body().elements) {
                        // 既存のIDチェック（重複排除）
                        boolean exists = false;
                        for (OsmPoi p : currentPois) {
                            if (p.getId() == element.id) {
                                exists = true;
                                break;
                            }
                        }
                        if (exists){ continue; }

                        currentPois.add(new OsmPoi(element.id, element.lat, element.lon, element.type, element.tags));
                    }
                    poisLiveData.postValue(new ArrayList<>(currentPois));
                } else {
                    // TODO: ViewModel経由でエラー通知
                }
            }

            @Override
            public void onFailure(Call<OverpassResponse> call, Throwable t) {
                // TODO: ViewModel経由でエラー通知
            }
        });

        return poisLiveData;
    }

    @Override
    public LiveData<OsmPoi> getPoi(long id, String type) {
        MutableLiveData<OsmPoi> poiLiveData = new MutableLiveData<>();
        String query = String.format(Locale.US, "[out:json][timeout:25]; %s(%d); out body center;", type, id);

        overpassApi.query(query).enqueue(new Callback<OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().elements.isEmpty()) {
                    OverpassResponse.Element element = response.body().elements.get(0);
                    poiLiveData.postValue(new OsmPoi(element.id, element.lat, element.lon, element.type, element.tags));
                } else {
                    poiLiveData.postValue(null);
                }
            }

            @Override
            public void onFailure(Call<OverpassResponse> call, Throwable t) {
                poiLiveData.postValue(null);
            }
        });

        return poiLiveData;
    }

    @Override
    public void savePoi(OsmPoi poi, String comment, PoiSaveCallback callback) {
        if (accessToken == null) {
            callback.onError("ログインが必要です");
            return;
        }
        // MVPの簡略化として、成功をシミュレート
        // 実際には changeset/create -> node/update -> changeset/close を行う
        callback.onSuccess();
    }

    @Override
    public void addPostBox(double lat, double lon, String shape, String branch, String collectionTimes, PoiSaveCallback callback) {
        if (accessToken == null) {
            callback.onError("ログインが必要です");
            return;
        }

        // XML生成 (OSM API v0.6 形式)
        StringBuilder xml = new StringBuilder();
        xml.append("<osm>");
        xml.append("<node lat=\"").append(lat).append("\" lon=\"").append(lon).append("\">");
        xml.append("<tag k=\"amenity\" v=\"post_box\"/>");
        xml.append("<tag k=\"operator\" v=\"日本郵便\"/>");
        if (branch != null && !branch.isEmpty()) {
            xml.append("<tag k=\"operator:branch\" v=\"").append(branch).append("\"/>");
        }
        if (collectionTimes != null && !collectionTimes.isEmpty()) {
            xml.append("<tag k=\"collection_times\" v=\"").append(collectionTimes).append("\"/>");
        }
        xml.append("</node>");
        xml.append("</osm>");

        // TODO: 実際の送信処理
        callback.onSuccess();
    }

    @Override
    public LiveData<List<OsmPoi>> searchPois(String query) {
        MutableLiveData<List<OsmPoi>> result = new MutableLiveData<>();
        
        // 郵便番号(7桁)かどうかの簡易判定
        String osmQuery;
        if (query.matches("\\d{3}-?\\d{4}")) {
            osmQuery = String.format(Locale.US, "node[\"addr:postcode\"=\"%s\"];", query);
        } else {
            // 名称または住所での検索
            osmQuery = String.format(Locale.US, 
                "node[\"name\"~\"%s\"];" +
                "node[\"addr:full\"~\"%s\"];" +
                "node[\"addr:street\"~\"%s\"];", 
                query, query, query);
        }

        String fullQuery = "[out:json][timeout:25];(" + osmQuery + ");out body center;";

        overpassApi.query(fullQuery).enqueue(new Callback<OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<OsmPoi> pois = new ArrayList<>();
                    for (OverpassResponse.Element element : response.body().elements) {
                        pois.add(new OsmPoi(element.id, element.lat, element.lon, element.type, element.tags));
                    }
                    result.postValue(pois);
                }
            }

            @Override
            public void onFailure(Call<OverpassResponse> call, Throwable t) {
                result.postValue(new ArrayList<>());
            }
        });
        
        return result;
    }

    @Override
    public void addNote(double lat, double lon, String text, PoiSaveCallback callback) {
        // 地図メモ (Note) の最終行に署名を追加
        String finalNote = text + "\ncreated by OSM JP Postal Map Android v1.0";
        
        // OSM Notes API (匿名投稿可能)
        // https://wiki.openstreetmap.org/wiki/API_v0.6#Map_Notes_API
        // POST /api/0.6/notes?lat=...&lon=...&text=...
        
        // MVPの簡略化として、成功をシミュレート
        callback.onSuccess();
    }
}
