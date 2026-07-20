package pro.eng.yui.android.osmjppostalmap.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import pro.eng.yui.android.osmjppostalmap.BuildConfig;
import pro.eng.yui.android.osmjppostalmap.data.api.OsmApi;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AuthRepository {
    private static final String PREF_NAME = "osm_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_USER_NAME = "user_name";

    private final SharedPreferences prefs;
    private final OsmApi authApi;

    public AuthRepository(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request().newBuilder()
                            .header("User-Agent", "OsmJPPostalMap/" + BuildConfig.VERSION_NAME)
                            .header("Accept", "application/json")
                            .build();
                    return chain.proceed(request);
                })
                .build();

        this.authApi = new Retrofit.Builder()
                .baseUrl("https://www.openstreetmap.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OsmApi.class);
    }

    public OsmApi getAuthApi() {
        return authApi;
    }

    public void saveAccessToken(String token) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public void saveUserName(String name) {
        prefs.edit().putString(KEY_USER_NAME, name).apply();
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, null);
    }

    public boolean isLoggedIn() {
        return getAccessToken() != null;
    }

    public void logout() {
        prefs.edit().clear().apply();
    }
}
