package pro.eng.yui.android.osmjppostalmap.data.remote;

import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import retrofit2.Call;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OsmOAuthApiTest {

    @Test
    public void publicClientPostsEncodedFormToOsmOAuthHost() throws Exception {
        Call<?> call = OsmOAuthApi.create().getAccessTokenPublic(
                "client-id", "code+with&reserved=value/", "authorization_code", "osmjppostalmap://oauth"
        );

        Request request = call.request();
        assertEquals("https://www.openstreetmap.org/oauth2/token", request.url().toString());
        assertEquals("POST", request.method());
        assertEquals(
                "client_id=client-id&code=code%2Bwith%26reserved%3Dvalue%2F&grant_type=authorization_code&redirect_uri=osmjppostalmap%3A%2F%2Foauth",
                bodyAsString(request)
        );
    }

    @Test
    public void confidentialClientPostsAllCredentialsToOsmOAuthHost() throws Exception {
        Call<?> call = OsmOAuthApi.create().getAccessToken(
                "client-id", "secret+value&", "auth-code", "authorization_code", "osmjppostalmap://oauth"
        );

        Request request = call.request();
        assertEquals("https://www.openstreetmap.org/oauth2/token", request.url().toString());
        assertEquals("POST", request.method());
        assertEquals(
                "client_id=client-id&client_secret=secret%2Bvalue%26&code=auth-code&grant_type=authorization_code&redirect_uri=osmjppostalmap%3A%2F%2Foauth",
                bodyAsString(request)
        );
    }

    private String bodyAsString(Request request) throws Exception {
        Buffer buffer = new Buffer();
        Objects.requireNonNull(request.body()).writeTo(buffer);
        return buffer.readUtf8();
    }
}