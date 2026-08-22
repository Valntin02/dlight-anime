# Stability Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish one environment configuration, separate API and image network policies, migrate all images to Glide, remove dependency conflicts, and eliminate the highest-risk TLS, signing, and permission problems without changing product behavior.

**Architecture:** Keep the currently used `RetrofitClient`/`ApiService` surface as a compatibility boundary while replacing its internals with `NetworkConfig` and `HttpClientFactory`. Glide receives an independent system-CA client through `MyAppGlideModule`; UI code calls a small `ImageLoader` instead of configuring requests. Security and dependency cleanup happen only after the new clients and image path are verified.

**Tech Stack:** Java, Android Gradle Plugin 8.6.1, Gradle 8.7, Retrofit 2.9.0, OkHttp 4.12.0, Glide 4.14.0, JUnit 4.13.2, Robolectric 4.13.2

---

## File structure

- Create `app/src/main/java/com/dlight/network/NetworkConfig.java`: validate and normalize the one API base URL.
- Create `app/src/main/java/com/dlight/network/HttpClientFactory.java`: build independent API and image clients.
- Modify `app/src/main/java/com/dlight/data/remote/RetrofitClient.java`: retain the legacy public API while using the new configuration/client.
- Modify `app/src/main/java/com/dlight/DlightApplication.java`: stop eagerly creating the unused second Retrofit stack.
- Delete `app/src/main/java/com/dlight/data/remote/NetworkHelper.java`: remove the fixed-IP trust store and verifier after callers migrate.
- Create `app/src/main/java/com/dlight/util/ImageUrlResolver.java`: resolve relative and loopback image URLs only at load time.
- Create `app/src/main/java/com/dlight/util/ImageLoader.java`: define cover, avatar, and player-thumbnail Glide policies.
- Modify `app/src/main/java/com/dlight/util/MyAppGlideModule.java`: use the image-specific client.
- Modify seven Picasso callers and three direct Glide callers to use `ImageLoader`.
- Modify Gradle dependency files to leave one version per library and add test support.
- Create Debug-only network security configuration; make the main configuration reject cleartext.
- Modify the custom media HTTP source to use platform TLS validation.
- Modify signing configuration and ignore rules so credentials are not embedded in source.

### Task 1: Add a tested single API environment configuration

**Files:**
- Modify: `gradle/base.gradle`
- Modify: `app/build.gradle`
- Create: `app/src/main/java/com/dlight/network/NetworkConfig.java`
- Create: `app/src/test/java/com/dlight/network/NetworkConfigTest.java`

- [ ] **Step 1: Enable BuildConfig and add the test dependencies**

In `gradle/base.gradle`, change the build feature and test block to:

```groovy
android {
    compileSdk 35

    defaultConfig {
        minSdk 26
        targetSdk 33
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures.buildConfig = true

    testOptions {
        unitTests.includeAndroidResources = true
    }
}

dependencies {
    implementation fileTree(dir: "libs", include: ["*.jar", "*.aar"])
    implementation androidDependencies.annotation

    testImplementation testDependencies.junit
    testImplementation "org.robolectric:robolectric:4.13.2"
    androidTestImplementation testDependencies.runner
    androidTestImplementation testDependencies.espressoCore
}
```

- [ ] **Step 2: Define build-type API values**

At the top of `app/build.gradle`, define escaped Gradle properties:

```groovy
def quoteBuildConfig = { String value ->
    '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'
}
def debugApiBaseUrl = providers.gradleProperty("DLIGHT_DEBUG_API_BASE_URL")
    .orElse("http://10.0.2.2:8000/")
    .get()
def releaseApiBaseUrl = providers.gradleProperty("DLIGHT_RELEASE_API_BASE_URL")
    .orElse("")
    .get()
```

Add the fields to the existing build types and fail only when a release task is requested without a URL:

```groovy
buildTypes {
    debug {
        minifyEnabled false
        buildConfigField "String", "API_BASE_URL", quoteBuildConfig(debugApiBaseUrl)
    }
    release {
        minifyEnabled true
        buildConfigField "String", "API_BASE_URL", quoteBuildConfig(releaseApiBaseUrl)
        proguardFiles getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (releaseApiBaseUrl.trim().isEmpty()) {
            throw new GradleException("Set -PDLIGHT_RELEASE_API_BASE_URL=https://host/ for release builds")
        }
    }
}
```

- [ ] **Step 3: Write the failing URL normalization tests**

Create `NetworkConfigTest.java`:

```java
package com.dlight.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class NetworkConfigTest {
    @Test
    public void normalize_addsTrailingSlashAndTrimsWhitespace() {
        assertEquals("http://10.0.2.2:8000/",
            NetworkConfig.normalizeBaseUrl("  http://10.0.2.2:8000  "));
    }

    @Test
    public void normalize_preservesBasePath() {
        assertEquals("https://example.com/api/",
            NetworkConfig.normalizeBaseUrl("https://example.com/api"));
    }

    @Test
    public void normalize_rejectsBlankUnsupportedOrHostlessValues() {
        assertThrows(IllegalArgumentException.class,
            () -> NetworkConfig.normalizeBaseUrl(" "));
        assertThrows(IllegalArgumentException.class,
            () -> NetworkConfig.normalizeBaseUrl("ftp://example.com"));
        assertThrows(IllegalArgumentException.class,
            () -> NetworkConfig.normalizeBaseUrl("https:///missing-host"));
    }
}
```

- [ ] **Step 4: Run the test and verify the red state**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :app:testDebugUnitTest --tests 'com.dlight.network.NetworkConfigTest' --console=plain
```

Expected: compilation fails because `NetworkConfig` does not exist.

- [ ] **Step 5: Implement NetworkConfig**

Create `NetworkConfig.java`:

```java
package com.dlight.network;

import com.dlight.BuildConfig;
import java.net.URI;

public final class NetworkConfig {
    private NetworkConfig() {
    }

    public static String apiBaseUrl() {
        return normalizeBaseUrl(BuildConfig.API_BASE_URL);
    }

    static String normalizeBaseUrl(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw new IllegalArgumentException("API base URL is empty");
        }
        String value = rawValue.trim();
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("API base URL is invalid: " + value, error);
        }
        String scheme = uri.getScheme();
        if (scheme == null || uri.getHost() == null
            || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("API base URL must use http or https and include a host");
        }
        return value.endsWith("/") ? value : value + "/";
    }
}
```

- [ ] **Step 6: Run the focused test and Debug build**

Run the focused test from Step 4, then:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :app:assembleDebug --console=plain
```

Expected: tests pass and `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit Task 1**

```bash
git add gradle/base.gradle app/build.gradle \
  app/src/main/java/com/dlight/network/NetworkConfig.java \
  app/src/test/java/com/dlight/network/NetworkConfigTest.java
git commit -m "refactor: centralize API environment config"
```

### Task 2: Separate API and image clients while preserving RetrofitClient callers

**Files:**
- Create: `app/src/main/java/com/dlight/network/HttpClientFactory.java`
- Modify: `app/src/main/java/com/dlight/data/remote/RetrofitClient.java`
- Modify: `app/src/main/java/com/dlight/DlightApplication.java`
- Modify: `app/src/main/java/com/dlight/util/MyAppGlideModule.java`
- Modify: `app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java`
- Delete: `app/src/main/java/com/dlight/data/remote/NetworkHelper.java`
- Test: `app/src/test/java/com/dlight/network/HttpClientFactoryTest.java`

- [ ] **Step 1: Write failing client policy tests**

Create `HttpClientFactoryTest.java`:

```java
package com.dlight.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.net.ProxySelector;
import org.junit.Test;

public class HttpClientFactoryTest {
    @Test
    public void apiAndImageClientsAreIndependentAndUsePlatformTls() {
        assertNotSame(HttpClientFactory.apiClient(), HttpClientFactory.imageClient());
        assertSame(ProxySelector.getDefault(), HttpClientFactory.apiClient().proxySelector());
        assertSame(ProxySelector.getDefault(), HttpClientFactory.imageClient().proxySelector());
        assertFalse(HttpClientFactory.imageClient().interceptors().stream()
            .anyMatch(interceptor -> interceptor.getClass().getName().contains("HttpLoggingInterceptor")));
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :app:testDebugUnitTest --tests 'com.dlight.network.HttpClientFactoryTest' --console=plain
```

Expected: compilation fails because `HttpClientFactory` does not exist.

- [ ] **Step 3: Implement independent clients**

Create `HttpClientFactory.java`:

```java
package com.dlight.network;

import com.dlight.BuildConfig;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public final class HttpClientFactory {
    private static final OkHttpClient API_CLIENT = buildApiClient();
    private static final OkHttpClient IMAGE_CLIENT = buildBaseClient().build();

    private HttpClientFactory() {
    }

    public static OkHttpClient apiClient() {
        return API_CLIENT;
    }

    public static OkHttpClient imageClient() {
        return IMAGE_CLIENT;
    }

    private static OkHttpClient buildApiClient() {
        OkHttpClient.Builder builder = buildBaseClient();
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.redactHeader("Authorization");
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            builder.addInterceptor(logging);
        }
        return builder.build();
    }

    private static OkHttpClient.Builder buildBaseClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true);
    }
}
```

- [ ] **Step 4: Rewire RetrofitClient and fail fast**

Replace `RetrofitClient` internals with:

```java
private static Retrofit createRetrofit() {
    return new Retrofit.Builder()
        .baseUrl(NetworkConfig.apiBaseUrl())
        .client(HttpClientFactory.apiClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build();
}

public static Retrofit getRetrofitInstance() {
    if (retrofit == null) {
        synchronized (RetrofitClient.class) {
            if (retrofit == null) {
                retrofit = createRetrofit();
            }
        }
    }
    return retrofit;
}
```

Import `NetworkConfig` and `HttpClientFactory`; remove `Param` and `NetworkHelper` imports. Do not catch initialization exceptions or return `null`.

- [ ] **Step 5: Rewire Glide and OkHttpUtils callers**

In `MyAppGlideModule.registerComponents`, use:

```java
registry.replace(
    GlideUrl.class,
    InputStream.class,
    new OkHttpUrlLoader.Factory(HttpClientFactory.imageClient())
);
```

In `DanmkuVideoActivity`, replace `NetworkHelper.getOkHttpClient()` with `HttpClientFactory.apiClient()` and update the import.

- [ ] **Step 6: Remove eager unused NetworkManager initialization**

In `DlightApplication`, remove the `NetworkManager` import, `initNetworkManager()` call, and the private `initNetworkManager()` method. Log the normalized value using `NetworkConfig.apiBaseUrl()`.

- [ ] **Step 7: Delete NetworkHelper and prove no references remain**

Delete `NetworkHelper.java`, then run:

```bash
rg -n 'NetworkHelper|hostnameVerifier|sslSocketFactory' app/src/main/java/com/dlight
```

Expected: no `NetworkHelper` references. Remaining TLS matches must be limited to the custom media source addressed in Task 5.

- [ ] **Step 8: Run tests and build**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit Task 2**

```bash
git add app/src/main/java/com/dlight/network/HttpClientFactory.java \
  app/src/main/java/com/dlight/data/remote/RetrofitClient.java \
  app/src/main/java/com/dlight/DlightApplication.java \
  app/src/main/java/com/dlight/util/MyAppGlideModule.java \
  app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java \
  app/src/test/java/com/dlight/network/HttpClientFactoryTest.java
git add -u app/src/main/java/com/dlight/data/remote/NetworkHelper.java
git commit -m "refactor: separate API and image network clients"
```

### Task 3: Add tested image URL resolution and a shared Glide loader

**Files:**
- Create: `app/src/main/java/com/dlight/util/ImageUrlResolver.java`
- Create: `app/src/main/java/com/dlight/util/ImageLoader.java`
- Create: `app/src/test/java/com/dlight/util/ImageUrlResolverTest.java`
- Modify: `app/src/main/res/drawable/placeholder.xml`
- Modify: `app/src/main/res/drawable/error_placeholder.xml`

- [ ] **Step 1: Write the failing resolver tests**

Create a parameterized-style JUnit class that includes these assertions:

```java
assertNull(ImageUrlResolver.resolve(null, "http://10.0.2.2:8000/"));
assertNull(ImageUrlResolver.resolve("  ", "http://10.0.2.2:8000/"));
assertEquals("https://cdn.example.com/a.jpg",
    ImageUrlResolver.resolve("https://cdn.example.com/a.jpg", base));
assertEquals("http://10.0.2.2:8000/upload/a.jpg",
    ImageUrlResolver.resolve("/upload/a.jpg", base));
assertEquals("http://10.0.2.2:8000/upload/a.jpg?x=1#cover",
    ImageUrlResolver.resolve("http://127.0.0.1:9000/upload/a.jpg?x=1#cover", base));
assertEquals("content://media/external/images/1",
    ImageUrlResolver.resolve("content://media/external/images/1", base));
```

- [ ] **Step 2: Run the resolver test and verify it fails**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :app:testDebugUnitTest --tests 'com.dlight.util.ImageUrlResolverTest' --console=plain
```

Expected: compilation fails because `ImageUrlResolver` does not exist.

- [ ] **Step 3: Implement ImageUrlResolver**

Use `java.net.URI`; preserve `file`, `content`, and `android.resource` values, leave ordinary absolute HTTP(S) hosts unchanged, replace only `127.0.0.1`, `localhost`, and `0.0.0.0` with the base scheme/authority, and resolve relative paths against `NetworkConfig.apiBaseUrl()`. Invalid values return `null` instead of throwing into RecyclerView binding.

The public surface is exactly:

```java
public final class ImageUrlResolver {
    public static String resolve(String rawUrl) {
        return resolve(rawUrl, NetworkConfig.apiBaseUrl());
    }

    static String resolve(String rawUrl, String baseUrl) {
        // URI normalization described above
    }
}
```

- [ ] **Step 4: Implement the shared ImageLoader**

Create three explicit entry points:

```java
public static void loadCover(ImageView view, String rawUrl) {
    Glide.with(view)
        .load(ImageUrlResolver.resolve(rawUrl))
        .placeholder(R.drawable.placeholder)
        .error(R.drawable.error_placeholder)
        .centerCrop()
        .into(view);
}

public static void loadAvatar(ImageView view, String rawUrl) {
    Glide.with(view)
        .load(ImageUrlResolver.resolve(rawUrl))
        .placeholder(R.drawable.placeholder)
        .error(R.drawable.error_placeholder)
        .circleCrop()
        .into(view);
}

public static void loadPlayerThumb(ImageView view, String rawUrl) {
    loadCover(view, rawUrl);
}
```

Do not disable memory or disk caching. Keep avatar cache invalidation as a separate upload-success concern.

- [ ] **Step 5: Run the resolver test**

Run the command from Step 2. Expected: all resolver tests pass.

- [ ] **Step 6: Commit Task 3**

```bash
git add app/src/main/java/com/dlight/util/ImageUrlResolver.java \
  app/src/main/java/com/dlight/util/ImageLoader.java \
  app/src/test/java/com/dlight/util/ImageUrlResolverTest.java \
  app/src/main/res/drawable/placeholder.xml \
  app/src/main/res/drawable/error_placeholder.xml
git commit -m "feat: add shared image loading policy"
```

### Task 4: Migrate every image caller from Picasso/direct Glide

**Files:**
- Modify: `app/src/main/java/com/dlight/ui/home/VideoAdapter.java`
- Modify: `app/src/main/java/com/dlight/feature/search/SearchResultAdapter.java`
- Modify: `app/src/main/java/com/dlight/ui/player/IntroFragment.java`
- Modify: `app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java`
- Modify: `app/src/main/java/com/dlight/data/local/PlayRecordAdapter.java`
- Modify: `app/src/main/java/com/dlight/feature/download/AdapterDownVideo.java`
- Modify: `app/src/main/java/com/dlight/ui/adapter/VodAdapter.java`
- Modify: `app/src/main/java/com/dlight/feature/user/UserFragment.java`
- Modify: `app/src/main/java/com/dlight/feature/comment/CommentAdapter.java`
- Modify: `app/src/main/java/com/dlight/feature/comment/CommentReplyAdapter.java`

- [ ] **Step 1: Migrate cover call sites**

Replace every Picasso cover request with:

```java
ImageLoader.loadCover(targetImageView, rawCoverUrl);
```

Use `ImageLoader.loadPlayerThumb` for `DanmkuVideoActivity`. Call the loader even when URL is null/empty so recycled holders cannot retain an old image.

- [ ] **Step 2: Migrate avatar call sites**

Replace the direct Glide chains in user, comment, and reply views with:

```java
ImageLoader.loadAvatar(targetImageView, rawAvatarUrl);
```

Remove `skipMemoryCache` and `DiskCacheStrategy` overrides. In `UserFragment`, delete the local `rewriteLoopbackHost` method because resolution now belongs to `ImageUrlResolver`.

- [ ] **Step 3: Prove Picasso and direct UI Glide calls are gone**

```bash
rg -n 'Picasso|Glide\.with|rewriteLoopbackHost' app/src/main/java
```

Expected: only `ImageLoader` and `MyAppGlideModule` contain Glide API calls; no Picasso or loopback helper remains.

- [ ] **Step 4: Build and run the image-focused unit tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :app:testDebugUnitTest --tests 'com.dlight.util.ImageUrlResolverTest' \
  :app:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Perform emulator image smoke checks**

With the backend on port 8000 and the emulator proxy configured, launch the app and verify:

- homepage third-party covers load;
- loopback user avatar resolves through `10.0.2.2`;
- search, detail, history, and download covers do not retain stale images after fast scrolling;
- a 404 image shows `error_placeholder` without hiding page content.

- [ ] **Step 6: Commit Task 4**

```bash
git add app/src/main/java/com/dlight/ui/home/VideoAdapter.java \
  app/src/main/java/com/dlight/feature/search/SearchResultAdapter.java \
  app/src/main/java/com/dlight/ui/player/IntroFragment.java \
  app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java \
  app/src/main/java/com/dlight/data/local/PlayRecordAdapter.java \
  app/src/main/java/com/dlight/feature/download/AdapterDownVideo.java \
  app/src/main/java/com/dlight/ui/adapter/VodAdapter.java \
  app/src/main/java/com/dlight/feature/user/UserFragment.java \
  app/src/main/java/com/dlight/feature/comment/CommentAdapter.java \
  app/src/main/java/com/dlight/feature/comment/CommentReplyAdapter.java
git commit -m "refactor: migrate images to shared Glide loader"
```

### Task 5: Remove dependency conflicts and harden TLS, signing, and permissions

**Files:**
- Modify: `gradle/dependencies.gradle`
- Modify: `app/build.gradle`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/xml/network_security_config.xml`
- Create: `app/src/debug/res/xml/network_security_config.xml`
- Modify: `app/src/main/java/com/dlight/network/exosource/DlightDefaultHttpDataSource.java`
- Modify: `.gitignore`
- Delete: `release.jks`

- [ ] **Step 1: Capture the current resolved dependency versions**

Run `dependencyInsight` for `okhttp`, `glide`, `material`, `recyclerview`, and `appcompat` on `debugRuntimeClasspath`; save the terminal results in the task log, not in the repository.

- [ ] **Step 2: Leave one dependency source per library**

Set these existing map versions in `gradle/dependencies.gradle`:

```groovy
glideVersion = "4.14.0"

androidDependencies = [
    recyclerView: "androidx.recyclerview:recyclerview:1.4.0",
    appCompat   : "androidx.appcompat:appcompat:1.6.0",
    design      : "com.google.android.material:material:1.12.0",
    viewpager2  : "androidx.viewpager2:viewpager2:1.0.0",
    core        : "androidx.core:core:1.7.0",
    transition  : "androidx.transition:transition:1.4.1",
    annotation  : "androidx.annotation:annotation:1.3.0",
    multidex    : "androidx.multidex:multidex:2.0.0"
]
```

Set `dataDependencies.okhttp` to `4.12.0`. In `app/build.gradle`, delete duplicate direct RecyclerView, legacy support AppCompat, direct OkHttp, Picasso, duplicate Material, and duplicate Glide core declarations. Keep one logging-interceptor `4.12.0`, Glide compiler `4.14.0`, Retrofit, Gson converter, Picasso removed, Room, and map-based dependencies.

- [ ] **Step 3: Replace custom media trust-all TLS with platform validation**

In `DlightDefaultHttpDataSource`, remove the custom `HostnameVerifier`, empty `X509TrustManager`, trust-all `SSLContext`, and every `setHostnameVerifier((hostname, session) -> true)` call. For HTTPS connections, use the `HttpsURLConnection` returned by `URL.openConnection()` without replacing its socket factory or hostname verifier. Preserve headers, redirects, Range handling, timeouts, and response parsing.

Verify the source scan:

```bash
rg -n 'trustAll|checkServerTrusted|hostnameVerifier|setHostnameVerifier|SSLContext.getInstance\("SSL"' \
  app/src/main/java/com/dlight
```

Expected: no trust-all or always-true verification paths.

- [ ] **Step 4: Split Debug and main cleartext policy**

Set the main config to:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

Create the Debug override:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

Remove `android:usesCleartextTraffic="true"` from the main Manifest. Debug remains compatible with the local backend; Release rejects cleartext.

- [ ] **Step 5: Remove unused high-risk permissions**

Delete `WRITE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, `SYSTEM_ALERT_WINDOW`, and `WRITE_SETTINGS`. Preserve `ACCESS_NETWORK_STATE`, `INTERNET`, `POST_NOTIFICATIONS`, and `FOREGROUND_SERVICE`.

Run:

```bash
rg -n 'WRITE_EXTERNAL_STORAGE|READ_EXTERNAL_STORAGE|SYSTEM_ALERT_WINDOW|WRITE_SETTINGS' \
  app/src/main app/src/debug
```

Expected: no matches.

- [ ] **Step 6: Remove embedded signing credentials**

Delete the hard-coded signing config and do not sign Debug with the release key. Read optional Release signing values from environment variables only when all are present:

```groovy
def releaseStorePath = System.getenv("DLIGHT_RELEASE_STORE_FILE")
def releaseStorePassword = System.getenv("DLIGHT_RELEASE_STORE_PASSWORD")
def releaseKeyAlias = System.getenv("DLIGHT_RELEASE_KEY_ALIAS")
def releaseKeyPassword = System.getenv("DLIGHT_RELEASE_KEY_PASSWORD")
def hasReleaseSigning = [releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword]
    .every { it != null && !it.trim().isEmpty() }

signingConfigs {
    if (hasReleaseSigning) {
        release {
            storeFile file(releaseStorePath)
            storePassword releaseStorePassword
            keyAlias releaseKeyAlias
            keyPassword releaseKeyPassword
        }
    }
}
```

In the Release build type, assign `signingConfig signingConfigs.release` only when `hasReleaseSigning`. Add `*.jks` and `*.keystore` to `.gitignore`, then remove tracked `release.jks`. The deletion remains recoverable from Git history; do not rewrite history in this plan.

- [ ] **Step 7: Prove dependency and secret cleanup**

```bash
rg -n 'Picasso|com\.android\.support|123456|storePassword "|keyPassword "' \
  app gradle --glob '*.gradle' --glob '*.java'
git ls-files '*.jks' '*.keystore'
```

Expected: no matches and no tracked key stores.

- [ ] **Step 8: Run the full foundation verification**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

Expected: tests pass, lint completes, and `BUILD SUCCESSFUL`.

Run the five `dependencyInsight` commands again. Expected: one direct version policy for each audited library, with Glide core/integration/compiler aligned to 4.14.0 and OkHttp/logging aligned to 4.12.0.

- [ ] **Step 9: Run emulator smoke verification**

Verify App launch, homepage API data, third-party covers, search, detail, one HTTPS playback source, user avatar, and download list. Inspect Logcat for `SSLHandshakeException`, `UnknownHostException`, `Cleartext`, `Bad Gateway`, Glide failures, and crashes. Debug local HTTP is permitted; third-party HTTPS must pass platform validation.

- [ ] **Step 10: Commit Task 5**

```bash
git add gradle/dependencies.gradle app/build.gradle .gitignore \
  app/src/main/AndroidManifest.xml \
  app/src/main/res/xml/network_security_config.xml \
  app/src/debug/res/xml/network_security_config.xml \
  app/src/main/java/com/dlight/network/exosource/DlightDefaultHttpDataSource.java
git add -u release.jks
git commit -m "chore: harden Android network and release config"
```

## Foundation completion gate

Before starting the business-stability plan, verify all of the following from current evidence:

- `NetworkConfigTest`, `HttpClientFactoryTest`, and `ImageUrlResolverTest` execute and pass; the test task is not `NO-SOURCE`.
- `assembleDebug` and `lintDebug` complete successfully.
- No Picasso import/call or dependency remains.
- No fixed-IP hostname verifier, trust-all manager, or always-true media verifier remains.
- No signing password or tracked key store remains.
- Debug emulator can access `10.0.2.2:8000` and external HTTPS images simultaneously.
- Homepage, search, detail, avatar, and download cover smoke checks pass.

Only after this gate passes should the next plan address playback selection, m3u8 parsing, download state reconciliation, Room compatibility, and page error states.
