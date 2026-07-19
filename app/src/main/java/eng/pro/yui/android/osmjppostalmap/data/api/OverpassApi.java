package eng.pro.yui.android.osmjppostalmap.data.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OverpassApi {
    @GET("interpreter")
    Call<OverpassResponse> query(@Query("data") String data);
}
