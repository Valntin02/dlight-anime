package com.dlight.feature.download;

import com.dlight.BuildConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public final class DownloadUrlPolicy {
    private DownloadUrlPolicy() {
    }

    public static void validate(URI uri) throws IOException {
        validate(uri, BuildConfig.DEBUG);
    }

    static void validate(URI uri, boolean allowPrivate) throws IOException {
        if (uri == null
                || !uri.isAbsolute()
                || uri.isOpaque()
                || uri.getScheme() == null
                || !("http".equalsIgnoreCase(uri.getScheme())
                        || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getHost().isEmpty()
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || uri.getPort() == 0
                || uri.getPort() < -1
                || uri.getPort() > 65535) {
            throw new IOException("下载地址无效");
        }
        if (allowPrivate) {
            return;
        }

        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException error) {
            throw new IOException("下载地址主机解析失败", error);
        }
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IOException("下载地址指向私有或本地地址");
            }
        }
    }
}
