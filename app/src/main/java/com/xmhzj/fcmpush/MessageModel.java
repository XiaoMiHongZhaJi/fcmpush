package com.xmhzj.fcmpush;

public class MessageModel {
    public String title;
    public String body;
    public String sendTime;
    public String receivedTime;
    public String priority; // 优先级
    public String group;    // 分组
    public String rawData;  // 备用：存储所有原始 JSON 数据

    public MessageModel(String title, String body, String sendTime, String receivedTime, String priority, String group) {
        this.title = title;
        this.body = body;
        this.sendTime = sendTime;
        this.receivedTime = receivedTime;
        this.priority = priority;
        this.group = group;
    }
}