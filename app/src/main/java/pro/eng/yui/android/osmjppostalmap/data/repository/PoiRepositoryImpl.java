package pro.eng.yui.android.osmjppostalmap.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import pro.eng.yui.android.osmjppostalmap.BuildConfig;
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
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Long> cooldownRemainingLiveData = new MutableLiveData<>(0L);
    private String accessToken;
    private static long lastFetchTime = 0;
    /** APIコールの最小間隔ms */
    private static final long MIN_INTERVAL_MS = 3500;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable cooldownRunnable;

    public PoiRepositoryImpl() {
        this(null);
    }

    public PoiRepositoryImpl(String accessToken) {
        this.accessToken = accessToken;

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request().newBuilder()
                            .header("User-Agent", "OsmJPPostalMap/" + BuildConfig.VERSION_NAME)
                            .header("Accept", "application/json")
                            .build();
                    return chain.proceed(request);
                })
                .build();

        Retrofit overpassRetrofit = new Retrofit.Builder()
                .baseUrl("https://overpass-api.de/api/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.overpassApi = overpassRetrofit.create(OverpassApi.class);

        Retrofit osmRetrofit = new Retrofit.Builder()
                .baseUrl("https://www.openstreetmap.org/api/0.6/")
                .client(client)
                .build();
        this.osmApi = osmRetrofit.create(OsmApi.class);
    }

    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    @Override
    public LiveData<List<OsmPoi>> getPois(double minLat, double minLon, double maxLat, double maxLon) {
        String query = String.format(Locale.US,
                "[out:json][timeout:25];" +
                "(" +
                "  node[\"amenity\"=\"post_box\"](%f,%f,%f,%f);" +
                "  nw[\"amenity\"=\"post_office\"][!\"operator\"](%f,%f,%f,%f);" +
                "  nw[\"amenity\"=\"post_office\"][\"operator\"=\"日本郵便\"](%f,%f,%f,%f);" +
                ");" +
                "out body center qt;",
                minLat, minLon, maxLat, maxLon,
                minLat, minLon, maxLat, maxLon,
                minLat, minLon, maxLat, maxLon);

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFetchTime < MIN_INTERVAL_MS) {
            return poisLiveData;
        }
        lastFetchTime = currentTime;
        startCooldownTimer();

        overpassApi.query(query).enqueue(new Callback<OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().elements.size() > 500) {
                        errorLiveData.postValue("ズームインしてください");
                        return;
                    }

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

                        double lat = element.lat;
                        double lon = element.lon;
                        if ("way".equals(element.type) && element.center != null) {
                            lat = element.center.lat;
                            lon = element.center.lon;
                        }

                        currentPois.add(new OsmPoi(element.id, lat, lon, element.type, element.tags));
                    }
                    poisLiveData.postValue(new ArrayList<>(currentPois));
                } else {
                    errorLiveData.postValue("データの取得に失敗しました: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<OverpassResponse> call, Throwable t) {
                errorLiveData.postValue("通信エラーが発生しました: " + t.getMessage());
            }
        });

        return poisLiveData;
    }

    @Override
    public LiveData<OsmPoi> getPoi(long id, String type) {
        MutableLiveData<OsmPoi> poiLiveData = new MutableLiveData<>();
        String query = String.format(Locale.US, "[out:json][timeout:25]; %s(%d); out body center qt;", type, id);

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFetchTime < MIN_INTERVAL_MS) {
            return poiLiveData;
        }
        lastFetchTime = currentTime;
        startCooldownTimer();

        overpassApi.query(query).enqueue(new Callback<OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().elements.isEmpty()) {
                    OverpassResponse.Element element = response.body().elements.get(0);
                    double lat = element.lat;
                    double lon = element.lon;
                    if ("way".equals(element.type) && element.center != null) {
                        lat = element.center.lat;
                        lon = element.center.lon;
                    }
                    poiLiveData.postValue(new OsmPoi(element.id, lat, lon, element.type, element.tags));
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

        String auth = "Bearer " + accessToken;

        // 1. Create Changeset
        String changesetXml = "<osm><changeset>" +
                "<tag k=\"created_by\" v=\"OsmJPPostalMap Android v" + BuildConfig.VERSION_NAME + "\"/>" +
                "<tag k=\"comment\" v=\"" + comment + "\"/>" +
                "</changeset></osm>";

        osmApi.createChangeset(auth, changesetXml).enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.isSuccessful() && response.body() != null) {
                    long changesetId = Long.parseLong(response.body().trim());
                    updatePoiInternal(auth, changesetId, poi, callback);
                } else {
                    callback.onError("Changesetの作成に失敗しました: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                callback.onError("通信エラー: " + t.getMessage());
            }
        });
    }

    private void updatePoiInternal(String auth, long changesetId, OsmPoi poi, PoiSaveCallback callback) {
        // XML生成 (OSM API v0.6 形式)
        StringBuilder xml = new StringBuilder();
        xml.append("<osm>");
        xml.append("<").append(poi.getType()).append(" id=\"").append(poi.getId()).append("\" ");
        if ("node".equals(poi.getType())) {
            xml.append("lat=\"").append(poi.getLat()).append("\" lon=\"").append(poi.getLon()).append("\" ");
        }
        // バージョン情報が必要だが、OsmPoiには含まれていない。
        // 本来は事前に要素を取得してバージョンを確認すべきだが、簡易実装として
        // 取得した直後の値を保持している想定で進める（ただしOsmPoiにversionがないのでAPIから再取得が必要）
        // getElementを呼んで最新のXMLを取得し、tagsを書き換えるのが安全。

        osmApi.getElement(poi.getType(), poi.getId()).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String originalXml = response.body().string();
                        // 簡易的なXML置換。本来はXMLパーサーを使うべき。
                        // version="..." を抽出
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("version=\"(\\d+)\"").matcher(originalXml);
                        if (m.find()) {
                            String version = m.group(1);
                            
                            StringBuilder updateXml = new StringBuilder();
                            updateXml.append("<osm>");
                            updateXml.append("<").append(poi.getType()).append(" ")
                                    .append("id=\"").append(poi.getId()).append("\" ")
                                    .append("version=\"").append(version).append("\" ")
                                    .append("changeset=\"").append(changesetId).append("\" ");
                            
                            if ("node".equals(poi.getType())) {
                                updateXml.append("lat=\"").append(poi.getLat()).append("\" lon=\"").append(poi.getLon()).append("\" ");
                            }
                            updateXml.append(">");

                            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                            Map<String, String> tags = poi.getTags();
                            tags.put("check_date", today);

                            for (Map.Entry<String, String> entry : tags.entrySet()) {
                                updateXml.append("<tag k=\"").append(entry.getKey()).append("\" v=\"").append(entry.getValue()).append("\"/>");
                            }
                            updateXml.append("</").append(poi.getType()).append(">");
                            updateXml.append("</osm>");

                            osmApi.updateElement(auth, poi.getType(), poi.getId(), updateXml.toString()).enqueue(new Callback<String>() {
                                @Override
                                public void onResponse(Call<String> call, Response<String> response) {
                                    if (response.isSuccessful()) {
                                        closeChangeset(auth, changesetId, callback);
                                    } else {
                                        callback.onError("データの更新に失敗しました: " + response.code());
                                    }
                                }

                                @Override
                                public void onFailure(Call<String> call, Throwable t) {
                                    callback.onError("通信エラー: " + t.getMessage());
                                }
                            });
                        } else {
                            callback.onError("バージョン情報の取得に失敗しました");
                        }
                    } else {
                        callback.onError("要素の取得に失敗しました");
                    }
                } catch (Exception e) {
                    callback.onError("処理エラー: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                callback.onError("通信エラー: " + t.getMessage());
            }
        });
    }

    private void closeChangeset(String auth, long changesetId, PoiSaveCallback callback) {
        osmApi.closeChangeset(auth, changesetId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                callback.onSuccess();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // クローズ失敗でもデータは保存されていることが多いが、一応成功扱いにするか迷うところ
                callback.onSuccess();
            }
        });
    }

    @Override
    public void addPostBox(double lat, double lon, String shape, String branch, String collectionTimes, String note, PoiSaveCallback callback) {
        if (accessToken == null) {
            callback.onError("ログインが必要です");
            return;
        }

        String auth = "Bearer " + accessToken;

        // 1. Create Changeset
        String changesetXml = "<osm><changeset>" +
                "<tag k=\"created_by\" v=\"OsmJPPostalMap Android v" + BuildConfig.VERSION_NAME + "\"/>" +
                "<tag k=\"comment\" v=\"郵便ポストの追加\"/>" +
                "</changeset></osm>";

        osmApi.createChangeset(auth, changesetXml).enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.isSuccessful() && response.body() != null) {
                    long changesetId = Long.parseLong(response.body().trim());
                    createPostBoxInternal(auth, changesetId, lat, lon, shape, branch, collectionTimes, note, callback);
                } else {
                    callback.onError("Changesetの作成に失敗しました: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                callback.onError("通信エラー: " + t.getMessage());
            }
        });
    }

    private void createPostBoxInternal(String auth, long changesetId, double lat, double lon, String shape, String branch, String collectionTimes, String note, PoiSaveCallback callback) {
        // XML生成 (OSM API v0.6 形式)
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<osm version=\"0.6\" generator=\"OsmJPPostalMap\">\n");
        xml.append("  <node changeset=\"").append(changesetId).append("\" lat=\"").append(lat).append("\" lon=\"").append(lon).append("\">\n");
        xml.append("    <tag k=\"amenity\" v=\"post_box\"/>\n");
        xml.append("    <tag k=\"operator\" v=\"日本郵便\"/>\n");
        if ("柱上箱型".equals(shape)) {
            xml.append("    <tag k=\"support\" v=\"pole\"/>\n");
        } else if ("円柱".equals(shape)) {
            xml.append("    <tag k=\"support\" v=\"ground\"/>\n");
        }
        if (branch != null && !branch.isEmpty()) {
            xml.append("    <tag k=\"operator:branch\" v=\"").append(branch).append("\"/>\n");
        }
        if (collectionTimes != null && !collectionTimes.isEmpty()) {
            xml.append("    <tag k=\"collection_times\" v=\"").append(collectionTimes).append("\"/>\n");
        }
        if (note != null && !note.isEmpty()) {
            xml.append("    <tag k=\"note\" v=\"").append(note).append("\"/>\n");
        }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        xml.append("    <tag k=\"check_date\" v=\"").append(today).append("\"/>\n");
        xml.append("  </node>\n");
        xml.append("</osm>");

        osmApi.createElement(auth, "node", xml.toString()).enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.isSuccessful()) {
                    closeChangeset(auth, changesetId, callback);
                } else {
                    callback.onError("ポストの作成に失敗しました: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                callback.onError("通信エラー: " + t.getMessage());
            }
        });
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

        String fullQuery = "[out:json][timeout:25];(" + osmQuery + ");out body center qt;";

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFetchTime < MIN_INTERVAL_MS) {
            result.postValue(new ArrayList<>());
            return result;
        }
        lastFetchTime = currentTime;

        overpassApi.query(fullQuery).enqueue(new Callback<OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<OsmPoi> pois = new ArrayList<>();
                    for (OverpassResponse.Element element : response.body().elements) {
                        double lat = element.lat;
                        double lon = element.lon;
                        if ("way".equals(element.type) && element.center != null) {
                            lat = element.center.lat;
                            lon = element.center.lon;
                        }
                        pois.add(new OsmPoi(element.id, lat, lon, element.type, element.tags));
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
    public LiveData<String> getError() {
        return errorLiveData;
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
    }

    @Override
    public void addNote(double lat, double lon, String text, PoiSaveCallback callback) {
        // 地図メモ (Note) の最終行に署名を追加
        String finalNote = text + "\ncreated by OSM JP Postal Map Android v" + BuildConfig.VERSION_NAME;
        
        // OSM Notes API (匿名投稿可能)
        // https://wiki.openstreetmap.org/wiki/API_v0.6#Map_Notes_API
        // POST /api/0.6/notes?lat=...&lon=...&text=...
        
        osmApi.createNote(lat, lon, finalNote).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Noteの作成に失敗しました: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                callback.onError("通信エラーが発生しました: " + t.getMessage());
            }
        });
    }
}
