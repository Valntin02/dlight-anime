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

    public interface NetworkProvider {
        Snapshot capture();
    }

    public static final class Snapshot {
        private final boolean activeNetwork;
        private final boolean internet;
        private final boolean validated;
        private final Transport transport;
        private final boolean metered;

        private Snapshot(boolean activeNetwork, boolean internet, boolean validated,
                Transport transport, boolean metered) {
            this.activeNetwork = activeNetwork;
            this.internet = internet;
            this.validated = validated;
            this.transport = transport;
            this.metered = metered;
        }

        public static Snapshot disconnected() {
            return new Snapshot(false, false, false, null, false);
        }

        public static Snapshot connected(boolean internet, boolean validated,
                Transport transport, boolean metered) {
            return new Snapshot(true, internet, validated, transport, metered);
        }
    }

    private DownloadPreflight() {
    }

    public static Result check(Context context) {
        return check(context, null, false);
    }

    public static Result check(Context context, File videoDirectory, boolean meteredConfirmed) {
        try {
            Context appContext = context.getApplicationContext();
            if (appContext == null) {
                appContext = context;
            }
            File filesDirectory = appContext.getFilesDir();
            if (filesDirectory == null) {
                return Result.ERROR;
            }
            File requestedDirectory = videoDirectory == null
                    ? new File(filesDirectory, "video") : videoDirectory;
            File storageDirectory = existingDirectory(requestedDirectory, filesDirectory);
            Context capturedContext = appContext;
            return check(() -> captureNetwork(capturedContext), storageDirectory,
                    directory -> new StatFs(directory.getAbsolutePath()).getAvailableBytes(),
                    meteredConfirmed);
        } catch (RuntimeException exception) {
            return Result.ERROR;
        }
    }

    public static Result check(NetworkProvider networkProvider, File storageDirectory,
            StorageProvider storageProvider, boolean meteredConfirmed) {
        try {
            return evaluate(networkProvider.capture(), storageDirectory, storageProvider,
                    meteredConfirmed);
        } catch (RuntimeException exception) {
            return Result.ERROR;
        }
    }

    public static Result evaluate(Snapshot snapshot, File storageDirectory,
            StorageProvider storageProvider, boolean meteredConfirmed) {
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
        return snapshot.metered && !meteredConfirmed
                ? Result.CONFIRM_CELLULAR : Result.READY;
    }

    private static File existingDirectory(File requestedDirectory, File filesDirectory) {
        File candidate = requestedDirectory;
        while (candidate != null) {
            if (candidate.exists()) {
                return candidate;
            }
            candidate = candidate.getParentFile();
        }
        return filesDirectory;
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
        boolean internet =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        boolean validated =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean metered = internet && validated && connectivityManager.isActiveNetworkMetered();
        return Snapshot.connected(internet, validated, transportOf(capabilities), metered);
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
