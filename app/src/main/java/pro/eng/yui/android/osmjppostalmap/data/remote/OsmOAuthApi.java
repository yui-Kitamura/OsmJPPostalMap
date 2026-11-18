package pro.eng.yui.android.osmjppostalmap.data.remote;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

public interface OsmOAuthApi {
    String BASE_URL = "https://www.openstreetmap.org/";

    @FormUrlEncoded
    @POST("oauth2/token")
    Call<ResponseBody> getAccessToken(
            @Field("client_id") String clientId,
            @Field("client_secret") String clientSecret,
            @Field("code") String code,
            @Field("grant_type") String grantType,
            @Field("redirect_uri") String redirectUri
    );

    @FormUrlEncoded
    @POST("oauth2/token")
    Call<ResponseBody> getAccessTokenPublic(
            @Field("client_id") String clientId,
            @Field("code") String code,
            @Field("grant_type") String grantType,
            @Field("redirect_uri") String redirectUri
    );

    static OsmOAuthApi create() {
        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .build()
                .create(OsmOAuthApi.class);
    }
}