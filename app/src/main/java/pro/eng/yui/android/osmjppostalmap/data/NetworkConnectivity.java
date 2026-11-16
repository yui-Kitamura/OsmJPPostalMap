package pro.eng.yui.android.osmjppostalmap.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

public final class NetworkConnectivity {

    private NetworkConnectivity() {
    }

    public static boolean hasValidatedInternet(Context context) {
        if (context == null) {
            return false;
        }

        try {
            Object service = context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (!(service instanceof ConnectivityManager)) {
                return false;
            }

            ConnectivityManager connectivityManager = (ConnectivityManager) service;
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) {
                return false;
            }

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            return isValidatedInternet(
                    true,
                    capabilities != null,
                    capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            );
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean isValidatedInternet(boolean hasActiveNetwork, boolean hasCapabilities,
                                        boolean hasInternetCapability, boolean hasValidatedCapability) {
        return hasActiveNetwork && hasCapabilities && hasInternetCapability && hasValidatedCapability;
    }
}
