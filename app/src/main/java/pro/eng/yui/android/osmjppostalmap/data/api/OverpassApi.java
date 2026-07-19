package pro.eng.yui.android.osmjppostalmap.data.api;

import retrofit2.Call;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface OverpassApi {
    @POST("interpreter")
    Call<OverpassResponse> query(@Query("data") String data);
}
