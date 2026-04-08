package com.xmhzj.fcmpush;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MyFCMService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "fcm_default_channel";

    // 使用静态变量，确保多次消息触发时操作的是同一个计时器
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static Runnable sExitRunnable = null;

    private static final String TAG = "FCM_DEBUG";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // 1. 解析数据
        String title = remoteMessage.getData().getOrDefault("title", "新消息");
        String body = remoteMessage.getData().getOrDefault("body", "");
        String sendTime = remoteMessage.getData().getOrDefault("sendTime", "");
        String priority = remoteMessage.getData().getOrDefault("priority", "default");
        String groupKey = remoteMessage.getData().getOrDefault("group", "default");

        // 2. 永久保存到本地
        saveMessage(title, body, sendTime, priority, groupKey);

        // 2. 手动弹出通知（确保后台也能看到）
        showNotification(title, body, priority, groupKey);

        // 3. 发广播通知界面刷新
        sendBroadcast(new Intent("com.xmhzj.NEW_MESSAGE"));

        // 2. 退出逻辑处理
        handleAutoExit();
    }

    private void handleAutoExit() {
        // 如果之前已经有一个计时的任务，先取消它（实现“重新计时”）
        if (sExitRunnable != null) {
            sHandler.removeCallbacks(sExitRunnable);
            Log.d(TAG, "检测到新消息，重置倒计时");
        }

        // 创建新的退出任务
        sExitRunnable = () -> {
            // 在执行退出前，最后检查一次 App 是否切到了前台
            if (!AppConfig.isForeground) {
                Log.d(TAG, "5秒到期且处于后台，正在退出释放内存...");
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            } else {
                Log.d(TAG, "5秒到期，但检测到用户正在使用 App，取消退出任务。");
            }
        };

        // 开启倒计时
        sHandler.postDelayed(sExitRunnable, AppConfig.exitDelayMs);
    }

    private void showNotification(String title, String body, String priority, String groupKey) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // A. 映射优先级到系统常量
        int importance;
        int systemPriority;
        String channelId = groupKey.hashCode() + "";

        switch (priority.toLowerCase()) {
            case "high":
                importance = NotificationManager.IMPORTANCE_HIGH; // 弹窗+声音
                systemPriority = NotificationCompat.PRIORITY_HIGH;
                break;
            case "low":
                importance = NotificationManager.IMPORTANCE_LOW;  // 静默，不打扰
                systemPriority = NotificationCompat.PRIORITY_LOW;
                break;
            default:
                importance = NotificationManager.IMPORTANCE_DEFAULT; // 有声音，不弹窗
                systemPriority = NotificationCompat.PRIORITY_DEFAULT;
                break;
        }

        // B. 创建对应的通知渠道 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, groupKey, importance);
            channel.enableLights(true);
            channel.setLightColor(Color.GREEN);
            nm.createNotificationChannel(channel);
        }

        // C. 构建通知
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification) // 这里必须使用那个纯色透明图标
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(systemPriority)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setGroup(groupKey); // 【核心：设置系统分组】

        // D. 发送通知 (使用随机 ID 避免覆盖，除非你想让同组只显示一条)
        int notificationId = (int) System.currentTimeMillis();
        nm.notify(notificationId, builder.build());

        // E. 【进阶】发送摘要通知（Summary）
        // 这一步是为了让 Android 系统更好地把该组消息聚合在一起
        NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setGroup(groupKey)
                .setGroupSummary(true) // 标记为该组的“摘要”
                .setAutoCancel(true)
                .setContentIntent(pi);
        nm.notify(groupKey.hashCode(), summaryBuilder.build());
    }

    private void saveMessage(String title, String body, String sendTime, String priority, String group) {
        SharedPreferences sp = getSharedPreferences(AppConfig.preferencesName, MODE_PRIVATE);
        String json = sp.getString(AppConfig.preferencesMessages, "[]");
        Gson gson = new Gson();
        List<MessageModel> list = gson.fromJson(json, new TypeToken<List<MessageModel>>() {
        }.getType());

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String receivedTime = simpleDateFormat.format(new Date());
        sendTime = simpleDateFormat.format(new Date(Long.parseLong(sendTime)));

        // 创建包含新字段的模型
        MessageModel model = new MessageModel(title, body, sendTime, receivedTime, priority, group);
        list.add(0, model);

        sp.edit().putString(AppConfig.preferencesMessages, gson.toJson(list)).apply();
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);

        Log.d(TAG, "New token: " + token);

        // 取出旧 token
        SharedPreferences sp = getSharedPreferences(AppConfig.preferencesName, MODE_PRIVATE);
        String oldToken = sp.getString(AppConfig.preferencesToken, null);

        // 保存新 token
        sp.edit().putString(AppConfig.preferencesToken, token).apply();

        // 如果旧 token 存在 且 不一样 → 说明失效了
        if (oldToken != null && !oldToken.equals(token)) {
            sendTokenExpiredNotification();
        }
    }

    private void sendTokenExpiredNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        String channelName = "重要通知";
        String channelId = channelName.hashCode() + "";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        // 点击通知跳转到 MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("from_notification", true);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Token 已失效")
                .setContentText("点击刷新 Token")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        manager.notify(channelName.hashCode(), notification);
    }
}