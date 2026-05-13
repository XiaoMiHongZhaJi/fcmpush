package com.xmhzj.fcmpush;

public class AppConfig {
    // fcm api url list
    public static String[] fcmApiUrls = {
            "https://fcm.cyf.lol",
            "https://fcm.xmhzj.workers.dev",
            "自定义"
    };
    // 系统配置名称
    public static String preferencesName = "FCM_CONFIG";
    // 系统配置名称
    public static String preferencesToken = "token";
    // 系统配置名称
    public static String preferencesMessages = "messages";
    // 系统配置名称
    public static String preferencesApiUrl = "apiUrl";
    // 消息延迟时显示发送时间和接收时间
    public static long showReceivedTimeDelayMs = 5 * 1000;
    // 后台接收消息后退出延时
    public static long exitDelayMs = 20 * 1000;
    // 记录 App 是否正在前台显示
    public static boolean isForeground = false;
}
