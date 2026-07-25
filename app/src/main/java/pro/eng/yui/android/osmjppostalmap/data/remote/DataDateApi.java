package pro.eng.yui.android.osmjppostalmap.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;

public interface DataDateApi {
    @GET("data/date.json")
    Call<DataDateResponse> getDataDate();
}
