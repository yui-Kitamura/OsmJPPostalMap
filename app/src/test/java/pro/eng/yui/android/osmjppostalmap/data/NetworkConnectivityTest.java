package pro.eng.yui.android.osmjppostalmap.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NetworkConnectivityTest {

    @Test
    public void rejectsUnavailableNetworkServiceOrCapabilities() {
        assertFalse(NetworkConnectivity.hasValidatedInternet(null));
        assertFalse(NetworkConnectivity.isValidatedInternet(false, false, false, false));
        assertFalse(NetworkConnectivity.isValidatedInternet(true, false, false, false));
    }

    @Test
    public void requiresInternetAndValidatedCapabilities() {
        assertFalse(NetworkConnectivity.isValidatedInternet(true, true, true, false));
        assertFalse(NetworkConnectivity.isValidatedInternet(true, true, false, true));
        assertTrue(NetworkConnectivity.isValidatedInternet(true, true, true, true));
    }
}
