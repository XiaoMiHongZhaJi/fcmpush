# 📲 FCM Push (自建推送到安卓设备工具) 

一个基于 Google FCM (Firebase Cloud Messaging) 能力, 实现**向自己安卓设备推送消息**的轻量级应用

该项目允许用户在无需第三方推送平台的情况下, 通过自己的 Firebase 项目实现稳定的设备消息推送能力

---

# 🚀 功能介绍

本应用基于 Firebase Cloud Messaging (FCM) 服务, 实现以下功能: 

## 1️⃣ 自动注册 FCM 服务

* 打开应用后, 会自动请求通知权限
* 自动尝试注册 FCM 推送服务
* 若未成功注册, 可手动点击: 

  > 「获取注册 TOKEN」

⚠️ 注意: 

* 获取 TOKEN 过程需要网络代理 (🪜‌) 
* TOKEN 是设备唯一标识, 用于后续推送消息
* TOKEN 可通过重新注册来刷新重置, 重置后旧的 TOKEN 自动失效

---

## 2️⃣ 推送地址生成与使用

注册成功后, 点击: 

> 「展开更多选项」

将显示当前设备对应的: 

* 📡 推送 API URL
* 📱 可区分不同的分组和优先级

功能说明: 

* 点击右侧 **「复制」按钮**

    * 将 URL 复制到剪贴板

* 点击右侧 **「播放」按钮**

    * 使用浏览器自动打开 URL
    * 可测试验证推送效果

* 若收到消息只在通知中心展示,  **没有浮动通知** 或 **没有提示音**

    * 下拉通知中心, 长按通知, 点击 **更多设置**
    * 点击对应的 **通知类别** , 打开  **浮动通知**  **提示音** 等开关即可

---

## 3️⃣ 后台保活建议 (重要) 

为了确保消息及时到达, 建议开启此 APP 的以下系统权限: 

* ✅ 后台无限制运行
* ✅ 允许自启动

### ⚠️ 说明

* 本应用仅在收到 FCM 消息时短暂启动
* 通常运行时间 ≤ 30 秒后便自动退出
* **不会常驻后台**
* 对电量与内存几乎无影响

---

## 4️⃣ Google Play 服务依赖

本项目 **强依赖 Google Play Services 的 FCM 能力**

---

## 5️⃣ 国内设备兼容建议

### 📱 小米 / HyperOS / MIUI

推荐方式: 

* 使用 Gboard 输入法作为系统默认输入法
* 或将 NFC 刷卡应用 设置为 Google Play  (经验方案) 

---

### 🔓 已解锁 Bootloader 用户 (高级) 

推荐模块 (LSPosed): 

[https://github.com/Howard20181/HyperOS_FCM_Live](https://github.com/Howard20181/HyperOS_FCM_Live)

功能: 

* 增强 FCM 后台保活能力
* 解决系统后台限制导致的消息延迟问题

---

### 🔍 关键词参考 (酷安 或者 别的论坛 搜索)

* gcm 保活
* google play 服务 保活
* fcm 收不到消息
* 安卓 后台推送延迟

---

# 🏗️ 自建服务与部署说明 (如果你想自建服务 + 自己编译APP) 

---

## 🧭 Step 1: 创建 Google Cloud 项目

访问: 
[https://console.cloud.google.com/](https://console.cloud.google.com/)

操作步骤: 

1. 登录 Google 账号
2. 点击「选择项目」
3. 新建项目
4. 记录 PROJECT_ID

---

## 🧭 Step 2: 启用 Firebase 服务

进入 Firebase 控制台: 
[https://console.firebase.google.com/](https://console.firebase.google.com/)

1. 添加项目 (选择刚刚创建的 Google Cloud 项目) 
2. 进入项目设置
3. 添加 Android 应用 (package name 必须一致) 
4. 下载配置文件: 

### 📦 google-services.json

放置到 APP 路径: 

```
/app/google-services.json
```

---

## 🧭 Step 3: 创建 Service Account (关键) 

进入 Google Cloud 左侧菜单: 

IAM 与管理 → 服务账号

1. 创建服务账号
2. 分配权限: 

    * Firebase Admin SDK Admin
    * 或 Project Editor

---

## 🧭 Step 4: 生成 JSON 密钥

在服务账号页面: 

* Keys → Add Key → JSON

下载后得到: 

```json
{
  "type": "service_account",
  "project_id": "your-project-id",
  "private_key_id": "xxx",
  "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
  "client_email": "xxx@xxx.iam.gserviceaccount.com",
  "client_id": "xxx"
}
```

---

## 🔑 字段说明

| 字段             | 说明        |
| -------------- | -----------  |
| project_id     | 项目 ID       |
| private_key    | 服务端签名密钥  |
| client_email   | 服务账号邮箱   |
| private_key_id | 密钥 ID       |

---

# 📎 构建服务端 (基于 CloudFlare Workers) 请参考:

[https://github.com/XiaoMiHongZhaJi/fcmpush_server_cf](https://github.com/XiaoMiHongZhaJi/fcmpush_server_cf)

---

## ⚠️ 安全提醒

* private_key 禁止泄露
* 不要提交到公开仓库
* 推荐使用环境变量管理

---

# 📌 项目特点总结

* ✔ 自建 FCM 推送系统
* ✔ 无需第三方平台
* ✔ 直接推送到个人设备
* ✔ 支持 API 测试
* ✔ 极低资源占用
