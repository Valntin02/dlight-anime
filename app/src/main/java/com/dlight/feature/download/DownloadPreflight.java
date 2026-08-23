package com.dlight.feature.download;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.StatFs;

import java.io.File;

public final class DownloadPreflight {
    public static final long MINIMUM_FREE_BYTES = 256L * 1024L * 1024L;

    public enum Result {
        READY,
        CONFIRM_CELLULAR,
        OFFLINE,
        LOW_STORAGE,
        ERROR
    }

    public enum Transport {
        WIFI,
        CELLULAR,
        ETHERNET,
        VPN,
        OTHER
    }

    public interface StorageProvider {
        long availableBytes(File directory);
    }

    public static final class Snapshot {
        private final boolean activeNetwork;
        private final boolean internet;
        private final boolean validated;
        private final Transport transport;

        private Snapshot(boolean activeNetwork, boolean internet, boolean validated,
                Transport transport) {
            this.activeNetwork = activeNetwork;
            this.internet = internet;
            this.validated = validated;
            this.transport = transport;
        }

        public static Snapshot disconnected() {
            return new Snapshot(false, false, false, null);
        }

        public static Snapshot connected(boolean internet, boolean validated,
                Transport transport) {
            return new Snapshot(true, internet, validated, transport);
        }
    }

    private DownloadPreflight() {
    }

    public static Result check(Context context) {
        try {
            Context appContext = context.getApplicationContext();
            if (appContext == null) {
                appContext = context;
            }
            File filesDirectory = appContext.getFilesDir();
            if (filesDirectory == null) {
                return Result.ERROR;
            }
            File videoDirectory = new File(filesDirectory, "video");
            File storageDirectory = videoDirectory.exists() ? videoDirectory : filesDirectory;
            return evaluate(captureNetwork(appContext), storageDirectory,
                    directory -> new StatFs(directory.getAbsolutePath()).getAvailableBytes());
        } catch (RuntimeException exception) {
            return Result.ERROR;
        }
    }

    public static Result evaluate(Snapshot snapshot, File storageDirectory,
            StorageProvider storageProvider) {
        if (snapshot == null || !snapshot.activeNetwork
                || !snapshot.internet || !snapshot.validated) {
            return Result.OFFLINE;
        }
        try {
            if (storageDirectory == null
                    || storageProvider.availableBytes(storageDirectory) < MINIMUM_FREE_BYTES) {
                return storageDirectory == null ? Result.ERROR : Result.LOW_STORAGE;
            }
        } catch (RuntimeException exception) {
            return Result.ERROR;
        }
        return snapshot.transport == Transport.CELLULAR
                ? Result.CONFIRM_CELLULAR : Result.READY;
    }

    private static Snapshot captureNetwork(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return Snapshot.disconnected();
        }
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return Snapshot.disconnected();
        }
        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return null;
        }
        return Snapshot.connected(
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                transportOf(capabilities));
    }

    private static Transport transportOf(NetworkCapabilities capabilities) {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return Transport.WIFI;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return Transport.ETHERNET;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return Transport.VPN;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return Transport.CELLULAR;
        }
        return Transport.OTHER;
    }
}
