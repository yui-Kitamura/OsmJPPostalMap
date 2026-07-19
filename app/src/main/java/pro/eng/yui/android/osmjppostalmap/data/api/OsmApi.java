package pro.eng.yui.android.osmjppostalmap.data.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface OsmApi {
    @GET("user/details")
    Call<ResponseBody> getUserDetails(@Header("Authorization") String auth);

    @PUT("changeset/create")
    Call<String> createChangeset(@Header("Authorization") String auth, @Body String xml);

    @POST("changeset/{id}/close")
    Call<Void> closeChangeset(@Header("Authorization") String auth, @Path("id") long id);

    @GET("{type}/{id}")
    Call<ResponseBody> getElement(@Path("type") String type, @Path("id") long id);

    @POST("{type}/create")
    Call<String> createElement(@Header("Authorization") String auth, @Path("type") String type, @Body String xml);

    @PUT("{type}/{id}")
    Call<String> updateElement(@Header("Authorization") String auth, @Path("type") String type, @Path("id") long id, @Body String xml);

    @POST("notes")
    Call<ResponseBody> createNote(@retrofit2.http.Query("lat") double lat, @retrofit2.http.Query("lon") double lon, @retrofit2.http.Query("text") String text);
}
