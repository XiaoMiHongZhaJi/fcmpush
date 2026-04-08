package com.xmhzj.fcmpush;

public class AppConfig {
    // fcm api url
    public static String fcmApiUrl = "https://fcm.cyf.lol";
    // 系统配置名称
    public static String preferencesName = "FCM_CONFIG";
    // 系统配置名称
    public static String preferencesToken = "token";
    // 系统配置名称
    public static String preferencesMessages = "messages";
    // 后台接收消息后退出延时
    public static long exitDelayMs = 20 * 1000;
    // 记录 App 是否正在前台显示
    public static boolean isForeground = false;
}
