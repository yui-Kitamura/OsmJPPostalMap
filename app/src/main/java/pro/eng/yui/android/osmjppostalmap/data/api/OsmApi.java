package pro.eng.yui.android.osmjppostalmap.data.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface OsmApi {
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

    @GET("api/0.6/user/details.json")
    Call<ResponseBody> getUserDetailsJson(@Header("Authorization") String auth);

    @PUT("api/0.6/changeset/create")
    Call<String> createChangeset(@Header("Authorization") String auth, @Body String xml);

    @PUT("api/0.6/changeset/{id}/close")
    Call<Void> closeChangeset(@Header("Authorization") String auth, @Path("id") long id);

    @GET("api/0.6/{type}/{id}")
    Call<ResponseBody> getElement(@Path("type") String type, @Path("id") long id);

    @PUT("api/0.6/{type}/create")
    Call<String> createElement(@Header("Authorization") String auth, @Path("type") String type, @Body String xml);

    @PUT("api/0.6/{type}/{id}")
    Call<String> updateElement(@Header("Authorization") String auth, @Path("type") String type, @Path("id") long id, @Body String xml);

    @POST("api/0.6/notes")
    Call<ResponseBody> createNote(@retrofit2.http.Query("lat") double lat, @retrofit2.http.Query("lon") double lon, @retrofit2.http.Query("text") String text);
}
