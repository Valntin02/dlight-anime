package com.dlight.network;

/**
 * API配置类
 * 定义API基础URL等配置信息
 */
public class ApiConfig {
    // API基础URL
    public static final String BASE_URL = "https://113.45.243.38:10001";

    // API超时配置
    public static final int CONNECT_TIMEOUT = 15;
    public static final int READ_TIMEOUT = 30;
    public static final int WRITE_TIMEOUT = 15;
}
