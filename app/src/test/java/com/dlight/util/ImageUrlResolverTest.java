package com.dlight.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ImageUrlResolverTest {
    private static final String BASE_URL = "https://api.example.com:8443/api/";

    @Test
    public void resolve_returnsNullForNullAndBlankValues() {
        assertNull(ImageUrlResolver.resolve(null, BASE_URL));
        assertNull(ImageUrlResolver.resolve("   ", BASE_URL));
    }

    @Test
    public void resolve_preservesThirdPartyHttpsUrl() {
        String url = "https://cdn.example.net/images/a.jpg?size=large#preview";

        assertEquals(url, ImageUrlResolver.resolve(url, BASE_URL));
    }

    @Test
    public void resolve_resolvesRootRelativeUrlAgainstOrigin() {
        assertEquals(
            "https://api.example.com:8443/upload/a.jpg",
            ImageUrlResolver.resolve("/upload/a.jpg", BASE_URL)
        );
    }

    @Test
    public void resolve_resolvesOrdinaryRelativeUrlAgainstBasePath() {
        assertEquals(
            "https://api.example.com:8443/api/upload/a.jpg",
            ImageUrlResolver.resolve("upload/a.jpg", BASE_URL)
        );
    }

    @Test
    public void resolve_rewrites127LoopbackUsingBaseAuthority() {
        assertEquals(
            "https://api.example.com:8443/upload/a.jpg?size=large#preview",
            ImageUrlResolver.resolve(
                "http://127.0.0.1:8000/upload/a.jpg?size=large#preview",
                BASE_URL
            )
        );
    }

    @Test
    public void resolve_rewritesLocalhostLoopbackUsingBaseAuthority() {
        assertEquals(
            "https://api.example.com:8443/upload/a.jpg",
            ImageUrlResolver.resolve("http://localhost/upload/a.jpg", BASE_URL)
        );
    }

    @Test
    public void resolve_rewritesUnspecifiedLoopbackUsingBaseAuthority() {
        assertEquals(
            "https://api.example.com:8443/upload/a.jpg",
            ImageUrlResolver.resolve("http://0.0.0.0:9000/upload/a.jpg", BASE_URL)
        );
    }

    @Test
    public void resolve_rejectsBaseUserInfoWhenRewritingLoopback() {
        assertNull(
            ImageUrlResolver.resolve(
                "http://localhost:8000/image%20one.jpg?x=1#part",
                "https://user:password@[2001:db8::1]:9443/api/"
            )
        );
    }

    @Test
    public void resolve_rejectsUserInfoInThirdPartyHttpUrl() {
        assertNull(
            ImageUrlResolver.resolve("https://user:secret@cdn.example.net/a.jpg", BASE_URL)
        );
    }

    @Test
    public void resolve_normalizesSupportedSchemesToLowercaseWithoutReencoding() {
        assertEquals(
            "https://cdn.example.net/image%20one.jpg?x=%2F#part%20one",
            ImageUrlResolver.resolve(
                "HTTPS://cdn.example.net/image%20one.jpg?x=%2F#part%20one",
                BASE_URL
            )
        );
        assertEquals(
            "content://media/images/image%20one.jpg",
            ImageUrlResolver.resolve("CONTENT://media/images/image%20one.jpg", BASE_URL)
        );
        assertEquals(
            "file:///storage/image%20one.jpg",
            ImageUrlResolver.resolve("FILE:///storage/image%20one.jpg", BASE_URL)
        );
        assertEquals(
            "android.resource://com.dlight/drawable/placeholder",
            ImageUrlResolver.resolve(
                "ANDROID.RESOURCE://com.dlight/drawable/placeholder",
                BASE_URL
            )
        );
    }

    @Test
    public void resolve_appliesHttpPolicyAfterResolvingNetworkPathReference() {
        assertEquals(
            "https://api.example.com:8443/a.jpg",
            ImageUrlResolver.resolve("//localhost:9000/a.jpg", BASE_URL)
        );
        assertEquals(
            "https://cdn.example.net/a.jpg",
            ImageUrlResolver.resolve("//cdn.example.net/a.jpg", BASE_URL)
        );
    }

    @Test
    public void resolve_rejectsOpaqueLoaderUris() {
        assertNull(ImageUrlResolver.resolve("content:opaque", BASE_URL));
        assertNull(ImageUrlResolver.resolve("file:opaque", BASE_URL));
    }

    @Test
    public void resolve_preservesSupportedNonNetworkUris() {
        assertEquals(
            "content://media/external/images/media/1",
            ImageUrlResolver.resolve("content://media/external/images/media/1", BASE_URL)
        );
        assertEquals(
            "content://localhost/images/1",
            ImageUrlResolver.resolve("content://localhost/images/1", BASE_URL)
        );
        assertEquals(
            "file:///storage/emulated/0/Pictures/a.jpg",
            ImageUrlResolver.resolve("file:///storage/emulated/0/Pictures/a.jpg", BASE_URL)
        );
        assertEquals(
            "android.resource://com.dlight/drawable/placeholder",
            ImageUrlResolver.resolve(
                "android.resource://com.dlight/drawable/placeholder",
                BASE_URL
            )
        );
    }

    @Test
    public void resolve_returnsNullForMalformedUri() {
        assertNull(ImageUrlResolver.resolve("https://example.com/bad path.jpg", BASE_URL));
        assertNull(ImageUrlResolver.resolve("http://[invalid", BASE_URL));
        assertNull(ImageUrlResolver.resolve("https://example.com:99999/a.jpg", BASE_URL));
    }

    @Test
    public void resolve_returnsNullForUnknownAbsoluteScheme() {
        assertNull(ImageUrlResolver.resolve("ftp://example.com/a.jpg", BASE_URL));
    }

    @Test
    public void resolve_doesNotRewriteOrdinaryThirdPartyHost() {
        assertEquals(
            "http://images.example.org:8000/upload/a.jpg",
            ImageUrlResolver.resolve("http://images.example.org:8000/upload/a.jpg", BASE_URL)
        );
    }

    @Test
    public void resolve_returnsNullWhenBaseUrlIsMalformed() {
        assertNull(ImageUrlResolver.resolve("upload/a.jpg", "https://bad host/api/"));
        assertNull(ImageUrlResolver.resolve("http://localhost/a.jpg", "not a base"));
        assertNull(ImageUrlResolver.resolve("upload/a.jpg", "https://example.com:99999/api/"));
    }
}
