package com.dlight.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.format.Formatter;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.ColorInt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;

public class Param {
    // 单例模式
    private static volatile Param instance = null;

    // 参数
    private String ipAddress = "0.0.0.0";  // IP地址

    // 私有构造函数，防止外部创建
    private Param() {
    }

    // 获取单例实例
    public static Param getInstance() {
        if (instance == null) {
            synchronized (Param.class) {  // 双重检查锁定
                if (instance == null) {
                    instance = new Param();
                }
            }
        }
        return instance;
    }

    // 初始化参数
    public void init(Context context) {
        // 异步获取 IP 地址
        new Thread(() -> {
            ipAddress = getIpAddress(context);
        }).start();
    }

    // 获取公网/局域网 IP
    private String getIpAddress(Context context) {
        // 优先尝试公网 IP
        String publicIp = getPublicIp();
        if (!publicIp.equals("0.0.0.0")) {
            return publicIp;
        }

        // 尝试局域网 IP
        String localIp = getLocalIpAddress(context);
        if (!localIp.equals("0.0.0.0")) {
            return localIp;
        }

        // 尝试移动数据网络 IP
        String mobileIp = getMobileIpAddress();
        if (!mobileIp.equals("0.0.0.0")) {
            return mobileIp;
        }

        // 无法获取 IP 时返回默认值
        return "0.0.0.0";
    }

    // 获取公网 IP（通过网络请求）
    private String getPublicIp() {
        try {
            URL url = new URL("https://checkip.amazonaws.com");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String ip = reader.readLine();
            reader.close();
            return ip != null && !ip.isEmpty() ? ip : "0.0.0.0";
        } catch (Exception e) {
            e.printStackTrace();
            return "0.0.0.0";
        }
    }

    // 获取局域网 IP（WiFi）
    private String getLocalIpAddress(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                return Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "0.0.0.0";
    }

    // 获取移动数据网络 IP
    private String getMobileIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress inetAddress = addresses.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.isSiteLocalAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "0.0.0.0";
    }

    public String getIpAddress() {
        return ipAddress;
    }
    public static long ipToLong(String ip) {
        if (ip == null || ip.isEmpty()) {
            return 0;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return 0;
        }
        try {
            return ((long) Integer.parseInt(parts[0]) << 24) & 0xFF000000L |
                ((long) Integer.parseInt(parts[1]) << 16) & 0x00FF0000L |
                ((long) Integer.parseInt(parts[2]) << 8)  & 0x0000FF00L |
                ((long) Integer.parseInt(parts[3]))       & 0x000000FFL;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return 0;
        }
    }


    public static void setStatusBarTransparent(Activity activity, boolean lightText, @ColorInt int statusBarColor) {
        Window window = activity.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        int visibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && lightText) {
            visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        window.getDecorView().setSystemUiVisibility(visibility);

        // 这里设置你传入的颜色
        window.setStatusBarColor(statusBarColor);
    }

}
