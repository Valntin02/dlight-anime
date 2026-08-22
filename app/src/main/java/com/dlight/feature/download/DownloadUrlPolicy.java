package com.dlight.feature.download;

import com.dlight.BuildConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DownloadUrlPolicy {
    private DownloadUrlPolicy() {
    }

    public static void validate(URI uri) throws IOException {
        validate(uri, BuildConfig.DEBUG);
    }

    static void validate(URI uri, boolean allowPrivate) throws IOException {
        validateSyntax(uri);
        if (!allowPrivate) {
            resolveAllowedAddresses(uri, false);
        }
    }

    public static List<InetAddress> resolveAllowedAddresses(URI uri) throws IOException {
        return resolveAllowedAddresses(uri, BuildConfig.DEBUG);
    }

    static List<InetAddress> resolveAllowedAddresses(URI uri, boolean allowPrivate)
            throws IOException {
        validateSyntax(uri);

        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException error) {
            throw new IOException("下载地址主机解析失败", error);
        }
        if (!allowPrivate) {
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()
                        || isAdditionalPrivateRange(address)) {
                    throw new IOException("下载地址指向私有或本地地址");
                }
            }
        }
        List<InetAddress> copy = new ArrayList<>();
        Collections.addAll(copy, addresses);
        return Collections.unmodifiableList(copy);
    }

    private static void validateSyntax(URI uri) throws IOException {
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
    }

    private static boolean isAdditionalPrivateRange(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            return (bytes[0] & 0xfe) == 0xfc;
        }
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 100 && second >= 64 && second <= 127;
        }
        return false;
    }
}
