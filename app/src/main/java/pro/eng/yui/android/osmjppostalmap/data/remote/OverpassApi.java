package pro.eng.yui.android.osmjppostalmap.data.remote;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

/**
 * Overpass API client interface.
 */
public interface OverpassApi {
    /**
     * Executes an Overpass QL query.
     * @param data The Overpass QL query string.
     * @return The response as a JSON string (use converter-scalars or manual parsing).
     */
    @FormUrlEncoded
    @POST("interpreter")
    Call<String> query(@Field("data") String data);
}
