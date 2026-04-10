package com.xmhzj.fcmpush;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class MessageModel {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String body;

    // 原有格式化后的字段
    public String sendTime;
    public String receivedTime;

    // 新增：原始时间戳
    public long sendTimestamp;
    public long receivedTimestamp;

    public String priority; // 优先级
    public String group;    // 分组

    public MessageModel(String title, String body, String sendTime, String receivedTime,
                        long sendTimestamp, long receivedTimestamp,
                        String priority, String group) {
        this.title = title;
        this.body = body;
        this.sendTime = sendTime;
        this.receivedTime = receivedTime;
        this.sendTimestamp = sendTimestamp;
        this.receivedTimestamp = receivedTimestamp;
        this.priority = priority;
        this.group = group;
    }
}