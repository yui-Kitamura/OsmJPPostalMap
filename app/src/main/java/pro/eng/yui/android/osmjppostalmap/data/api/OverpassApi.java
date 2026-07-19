package pro.eng.yui.android.osmjppostalmap.data.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;

public interface OverpassApi {
    @Headers("Accept: application/json")
    @GET("interpreter")
    Call<OverpassResponse> query(@Query("data") String data);
}
