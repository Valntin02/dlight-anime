package com.dlight.data.remote;

import android.util.Log;

import com.dlight.R;
import com.dlight.DlightApplication;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import javax.net.ssl.X509TrustManager;

//用来处理网络请求的类
public class NetworkHelper {

    private static final String TAG = "NetworkHelper";

    // 配置 Retrofit 和 OkHttpClient
    public static Retrofit getRetrofitInstance(String baseUrl) {
        try {
            // 加载自定义证书
            InputStream caInput = DlightApplication.getAppContext().getResources().openRawResource(R.raw.mycet); // 证书文件

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate caCert = (X509Certificate) certificateFactory.generateCertificate(caInput);

            // 创建 KeyStore 并加载证书
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", caCert);

            // 创建 TrustManagerFactory 并初始化
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            // 创建 SSLContext 并初始化
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new java.security.SecureRandom());

            // 创建 OkHttpClient，配置自定义证书的 SSLSocketFactory
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagerFactory.getTrustManagers()[0])
                .hostnameVerifier((hostname, session) -> hostname.equals("113.45.243.38"))  // 使用 IP 地址验证
                .build();

            // 创建 Retrofit 实例，使用 OkHttpClient
            return new Retrofit.Builder()
                .baseUrl(baseUrl)  // 替换为你的 API 基本 URL
                .addConverterFactory(GsonConverterFactory.create())  // 使用 Gson 来解析返回的 JSON
                .client(okHttpClient)  // 使用自定义的 OkHttpClient
                .build();

        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }

        return null;
    }

    // 配置 OkHttpClient
    public static OkHttpClient getOkHttpClient() {
        try {
            // 加载自定义证书
            InputStream caInput = DlightApplication.getAppContext().getResources().openRawResource(R.raw.mycet); // 证书文件

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate caCert = (X509Certificate) certificateFactory.generateCertificate(caInput);

            // 创建 KeyStore 并加载证书
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", caCert);

            // 创建 TrustManagerFactory 并初始化
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            // 创建 SSLContext 并初始化
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new java.security.SecureRandom());

            // 创建 OkHttpClient，配置自定义证书的 SSLSocketFactory
            return new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustManagerFactory.getTrustManagers()[0])
                .hostnameVerifier((hostname, session) -> hostname.equals("113.45.243.38"))  // 使用 IP 地址验证
                .build();

        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }

        return null;
    }

}
