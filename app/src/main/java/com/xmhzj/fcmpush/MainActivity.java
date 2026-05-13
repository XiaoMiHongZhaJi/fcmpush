package com.xmhzj.fcmpush;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvToken;
    private Button btnRegister, btnToggleOptions;
    private LinearLayout layoutMoreOptions;
    private EditText etExtraInfo1, etExtraInfo2, etExtraInfo3;
    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private List<MessageModel> messageList = new ArrayList<>();
    private SharedPreferences sp;
    private static final String TAG = "MAIN_DEBUG";
    // 在 MainActivity 类顶部添加声明
    private EditText etSearch;
    private TextView etDate;
    private TextView tvCalendarDay;
    private FrameLayout flCalendar;
    private ImageView ivCalendar;
    private ImageButton btnMenu;
    private List<MessageModel> filteredList = new ArrayList<>(); // 用于存储过滤后的数据

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        sp = getSharedPreferences(AppConfig.preferencesName, MODE_PRIVATE);
        initViews(this);
        checkPermission();

        boolean migrated = sp.getBoolean("old_data_migrated", false);
        if (!migrated) {
            new Thread(() -> {
                migrateOldDataIfNeeded(this, sp);
            }).start();

            Toast.makeText(this, "数据迁移完成，请退出并重新打开", Toast.LENGTH_SHORT).show();
        }

        String localToken = sp.getString(AppConfig.preferencesToken, null);
        if (localToken != null) {
            checkToken();
            if (migrated) {
                loadMessageList();
            }
            showNoticeUrl(localToken);
        }

        // 注册广播监听新消息
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(messageReceiver, new IntentFilter("com.xmhzj.NEW_MESSAGE"),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? RECEIVER_EXPORTED : 0);
        }
    }

    public void migrateOldDataIfNeeded(Context context, SharedPreferences sp) {

        // 读取旧 JSON 数据
        String json = sp.getString(AppConfig.preferencesMessages, "[]");
        Gson gson = new Gson();

        List<MessageModel> oldList = gson.fromJson(json, new TypeToken<List<MessageModel>>(){}.getType());
        if (oldList == null || oldList.isEmpty()) {
            sp.edit().putBoolean("old_data_migrated", true).apply();
            return;
        }

        AppDatabase db = AppDatabase.getInstance(context);
        MessageDao dao = db.messageDao();

        for (MessageModel old : oldList) {

            long sendTimestamp = 0;
            long receivedTimestamp = 0;

            // 如果旧版本没有时间戳，就尝试解析时间字符串
            try {
                sendTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .parse(old.sendTime).getTime();
            } catch (Exception ignored){}

            if (old.receivedTime != null) {
                try {
                    receivedTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .parse(old.receivedTime).getTime();
                } catch (Exception ignored){}
            }

            MessageModel newModel = new MessageModel(
                    old.title,
                    old.body,
                    old.sendTime,
                    old.receivedTime,
                    sendTimestamp,
                    receivedTimestamp,
                    old.priority,
                    old.group,
                    ""
            );

            dao.insert(newModel);
        }

        // 标记迁移完成
        sp.edit().putBoolean("old_data_migrated", true).apply();
    }

    // 这是 Activity 里的方法
    private void handleDelete(Context context, int position) {
        if (position == RecyclerView.NO_POSITION) return;

        // 1. 备份数据用于撤销
        final MessageModel deletedMessage = filteredList.get(position);
        final int deletedPositionFilteredList = filteredList.indexOf(deletedMessage);
        final int deletedPositionMessageList = messageList.indexOf(deletedMessage);

        // 2. 从集合移除并通知适配器刷新
        filteredList.remove(deletedPositionFilteredList);
        messageList.remove(deletedPositionMessageList);
        adapter.notifyItemRemoved(position);

        // 3. 弹出撤销提示
        Snackbar snackbar = Snackbar.make(rvMessages, String.format("已删除消息[%s]: %s", deletedMessage.id, deletedMessage.title), Snackbar.LENGTH_LONG);
        snackbar.setAction("撤销", v -> {
            // 点击撤销：恢复数据
            filteredList.add(deletedPositionFilteredList, deletedMessage);
            messageList.add(deletedPositionMessageList, deletedMessage);
            adapter.notifyItemInserted(position);
        });

        // 4. 监听消失：真正存盘
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (event != DISMISS_EVENT_ACTION) {
                    // 根据id删除消息
                    new Thread(() -> {
                        AppDatabase db = AppDatabase.getInstance(context);
                        MessageDao dao = db.messageDao();
                        dao.delete(deletedMessage); // ⭐推荐按ID删
                    }).start();
                }
            }
        });
        snackbar.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews(Context context) {
        tvStatus = findViewById(R.id.tvStatus);
        tvToken = findViewById(R.id.tvToken);
        btnRegister = findViewById(R.id.btnRegister);
        btnToggleOptions = findViewById(R.id.btnToggleOptions);
        layoutMoreOptions = findViewById(R.id.layoutMoreOptions);

        // 在 initViews() 方法中添加以下代码 (建议放在 loadMessageList() 调用之前)
        etSearch = findViewById(R.id.etSearch);
        etDate = findViewById(R.id.etDate);
        tvCalendarDay = findViewById(R.id.tvCalendarDay);
        flCalendar = findViewById(R.id.flCalendar);
        ivCalendar = findViewById(R.id.ivCalendar);
        btnMenu = findViewById(R.id.btnMenu);

        Drawable clear = ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_clear);
        if (clear != null) {
            clear.setAlpha(0);
            etSearch.setCompoundDrawablesWithIntrinsicBounds(null, null, clear, null);
        }

        rvMessages = findViewById(R.id.rvMessages);

        ImageButton btnCopyAction1 = findViewById(R.id.btnCopyAction1);
        ImageButton btnPlayAction1 = findViewById(R.id.btnPlayAction1);
        etExtraInfo1 = findViewById(R.id.etExtraInfo1);

        ImageButton btnCopyAction2 = findViewById(R.id.btnCopyAction2);
        ImageButton btnPlayAction2 = findViewById(R.id.btnPlayAction2);
        etExtraInfo2 = findViewById(R.id.etExtraInfo2);

        ImageButton btnCopyAction3 = findViewById(R.id.btnCopyAction3);
        ImageButton btnPlayAction3 = findViewById(R.id.btnPlayAction3);
        etExtraInfo3 = findViewById(R.id.etExtraInfo3);

        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter();
        rvMessages.setAdapter(adapter);

        // 日历图标点击事件
        flCalendar.setOnClickListener(v -> {

            flCalendar.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                    .withEndAction(() -> flCalendar.animate().scaleX(1f).scaleY(1f).setDuration(80))
                    .start();

            // 获取当前已选择的日期
            String currentDate = etDate.getText().toString();
            int year = Calendar.getInstance().get(Calendar.YEAR);
            int month = Calendar.getInstance().get(Calendar.MONTH);
            int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

            // 解析已选择的日期
            if (!currentDate.isEmpty()) {
                String[] parts = currentDate.split("-");
                year = Integer.parseInt(parts[0]);
                month = Integer.parseInt(parts[1]) - 1;
                day = Integer.parseInt(parts[2]);
            }

            // 弹出 DatePickerDialog
            DatePickerDialog dialog = new DatePickerDialog(this, (view, year1, month1, dayOfMonth) -> {
                // 格式化为 yyyy-MM-dd
                String selectedDate = String.format("%d-%02d-%02d", year1, month1 + 1, dayOfMonth);
                etDate.setText(selectedDate);
                tvCalendarDay.setText(String.valueOf(dayOfMonth));
                ivCalendar.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_calendar));
                performSearch(); // 选择日期后立即搜索
            }, year, month, day);

            // 点击“取消”按钮 或返回键触发
            dialog.setOnCancelListener(dialogInterface -> {
                // 用户取消选择
                String date = etDate.getText().toString();
                if (!date.isEmpty()) {
                    etDate.setText(null);
                    tvCalendarDay.setText(null);
                    ivCalendar.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_calendar_default));
                    performSearch(); // 清除日期后立即搜索
                    // 这里写你的逻辑
                    Toast.makeText(this, "已清除选择的日期", Toast.LENGTH_SHORT).show();
                }
            });

            dialog.show();
        });

        // --- 2. 搜索逻辑 (内容 + 日期) ---
        // 添加 TextWatcher 到 etSearch
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                performSearch();

                Drawable clear = ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_clear);

                if (clear != null) {
                    if (s.length() == 0) {
                        clear.setAlpha(0);
                    }
                }
                etSearch.setCompoundDrawablesWithIntrinsicBounds(null, null, clear, null);
            }
        });

        etSearch.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (event.getAction() == MotionEvent.ACTION_UP) {

                    Drawable clear = etSearch.getCompoundDrawables()[2];
                    if (clear == null) return false;

                    int touchX = (int) event.getX();
                    int width = etSearch.getWidth();

                    int drawableWidth = clear.getBounds().width();

                    // 判断是否点击在右侧 icon 区域
                    if (touchX >= (width - etSearch.getPaddingRight() - drawableWidth)) {

                        etSearch.setText(null);

                        // 🔥 点击反馈（轻微缩放效果）
                        etSearch.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80)
                                .withEndAction(() -> etSearch.animate().scaleX(1f).scaleY(1f).setDuration(80))
                                .start();

                        return true;
                    }
                }
                return false;
            }
        });

        // --- 3. 菜单逻辑 (3个点) ---
        btnMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(MainActivity.this, v);
            popup.getMenu().add("清空消息列表");
            popup.getMenu().add("服务器设置");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("清空消息列表")) {
                    // 复用原有的清空逻辑
                    new AlertDialog.Builder(this)
                            .setTitle("清空消息")
                            .setMessage("确定删除所有历史消息吗？")
                            .setPositiveButton("确定", (dialog, which) -> {
                                new Thread(() -> {
                                    AppDatabase db = AppDatabase.getInstance(this);
                                    MessageDao dao = db.messageDao();
                                    dao.clear();
                                    runOnUiThread(() -> {
                                        messageList.clear();
                                        filteredList.clear();
                                        adapter.notifyDataSetChanged();
                                        Toast.makeText(this, "已清空消息列表", Toast.LENGTH_SHORT).show();
                                    });
                                }).start();
                            }).setNegativeButton("取消", null).show();
                } else if (item.getTitle().equals("服务器设置")) {
                    showServerSettingsDialog();
                }
                return true;
            });
            popup.show();
        });

        // --- 在这里添加滑动删除绑定 ---
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                return false; // 不处理上下拖动
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 滑动成功后调用刚才定义的删除方法
                int position = viewHolder.getBindingAdapterPosition();
                handleDelete(context, position);
            }
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    View itemView = viewHolder.itemView;

                    // 1. 计算滑动比例 (0.0 到 1.0)
                    // 使用滑动距离除以条目宽度。dX 是负数，所以加负号
                    float swipeProgress = Math.min(1f, -dX / itemView.getWidth());

                    // 2. 绘制渐变红色背景
                    Paint paint = new Paint();
                    int redColor = Color.RED;
                    // 计算透明度：0 (全透明) 到 255 (不透明)
                    // 乘以 1.5 可以让颜色变红得快一点，增强视觉反馈
                    int alpha = (int) (Math.min(1f, swipeProgress * 1.5f) * 255);
                    paint.setColor(redColor);
                    paint.setAlpha(alpha);

                    // 绘制矩形背景（只覆盖滑动露出来的部分）
                    c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(),
                            (float) itemView.getRight(), (float) itemView.getBottom(), paint);

                    // 3. 绘制删除图标
                    Drawable icon = ContextCompat.getDrawable(recyclerView.getContext(), android.R.drawable.ic_menu_delete);
                    if (icon != null) {
                        // 设置图标颜色（可选，比如强行设为白色）
                        icon.setTint(Color.WHITE);
                        // 图标也随滑动逐渐显现
                        icon.setAlpha(alpha);

                        int itemHeight = itemView.getBottom() - itemView.getTop();
                        int intrinsicWidth = icon.getIntrinsicWidth();
                        int intrinsicHeight = icon.getIntrinsicHeight();

                        // 计算图标位置：居中垂直，并距离右侧有一定间距
                        int iconMargin = (itemHeight - intrinsicHeight) / 2;
                        int iconTop = itemView.getTop() + (itemHeight - intrinsicHeight) / 2;
                        int iconBottom = iconTop + intrinsicHeight;

                        // 图标固定在右侧，或者随滑动移动（这里采用固定在右侧露出部分）
                        int iconRight = itemView.getRight() - iconMargin;
                        int iconLeft = itemView.getRight() - iconMargin - intrinsicWidth;

                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        icon.draw(c);
                    }
                }

                // 必须调用 super 才能保持正常的滑动动画
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        });

        // 由 ItemTouchHelper 对象调用，传入 RecyclerView
        itemTouchHelper.attachToRecyclerView(rvMessages);

        // 注册/重新注册按钮
        btnRegister.setOnClickListener(v -> {
            // 加载已保存的 Token
            String savedToken = sp.getString(AppConfig.preferencesToken, "");
            if (savedToken.isEmpty()) {
                doRegister(); // 无Token，执行注册逻辑
            } else {
                // 弹出确认对话框
                new AlertDialog.Builder(this)
                        .setTitle("刷新 Token")
                        .setMessage("确定要刷新 Token 吗？旧的 Token 将失效。")
                        .setPositiveButton("确定", (dialog, which) -> {
                            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener(t -> {
                                doRegister(); // 用户点击确定，执行注册逻辑
                            });
                        })
                        .setNegativeButton("取消", null) // 点击取消不做任何操作
                        .show();
            }
        });

        // 点击展开/收起按钮的逻辑
        btnToggleOptions.setOnClickListener(v -> {
            if (layoutMoreOptions.getVisibility() == View.GONE) {
                // 展开
                layoutMoreOptions.setVisibility(View.VISIBLE);
                btnRegister.setVisibility(View.VISIBLE);
                btnToggleOptions.setText("点击收起");
            } else {
                // 收起
                layoutMoreOptions.setVisibility(View.GONE);
                btnRegister.setVisibility(View.GONE);
                btnToggleOptions.setText("展开更多选项");
            }
        });

        // 标题栏右侧的【复制】按钮
        btnCopyAction1.setOnClickListener(v -> {
            Object token = etExtraInfo1.getTag();
            Object text = etExtraInfo1.getText();
            if (text != null && token != null) {
                String url = text.toString().replace("{token}", token.toString());
                copyToClipboard(url);
                Toast.makeText(this, "完整内容已复制", Toast.LENGTH_SHORT).show();
            }
        });
        // 标题栏右侧的【播放】按钮：跳转浏览器发送请求
        btnPlayAction1.setOnClickListener(v -> {
            Object token = etExtraInfo1.getTag();
            Object text = etExtraInfo1.getText();
            if (text != null && token != null) {
                try {
                    String url = text.toString().replace("{token}", token.toString());
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开浏览器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "请先获取 Token", Toast.LENGTH_SHORT).show();
            }
        });

        // 标题栏右侧的【复制】按钮
        btnCopyAction2.setOnClickListener(v -> {
            Object token = etExtraInfo2.getTag();
            Object text = etExtraInfo2.getText();
            if (text != null && token != null) {
                String url = text.toString().replace("{token}", token.toString());
                copyToClipboard(url);
                Toast.makeText(this, "完整内容已复制", Toast.LENGTH_SHORT).show();
            }
        });
        // 标题栏右侧的【播放】按钮：跳转浏览器发送请求
        btnPlayAction2.setOnClickListener(v -> {
            Object token = etExtraInfo2.getTag();
            Object text = etExtraInfo2.getText();
            if (text != null && token != null) {
                try {
                    String url = text.toString().replace("{token}", token.toString());
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开浏览器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "请先获取 Token", Toast.LENGTH_SHORT).show();
            }
        });

        // 标题栏右侧的【复制】按钮
        btnCopyAction3.setOnClickListener(v -> {
            Object token = etExtraInfo3.getTag();
            Object text = etExtraInfo3.getText();
            if (text != null && token != null) {
                String url = text.toString().replace("{token}", token.toString());
                copyToClipboard(url);
                Toast.makeText(this, "完整内容已复制", Toast.LENGTH_SHORT).show();
            }
        });
        // 标题栏右侧的【播放】按钮：跳转浏览器发送请求
        btnPlayAction3.setOnClickListener(v -> {
            Object token = etExtraInfo3.getTag();
            Object text = etExtraInfo3.getText();
            if (text != null && token != null) {
                try {
                    String url = text.toString().replace("{token}", token.toString());
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开浏览器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "请先获取 Token", Toast.LENGTH_SHORT).show();
            }
        });

        // 复制 Token
        tvToken.setOnClickListener(v -> {
            String token = tvToken.getTag().toString();
            copyToClipboard(token);
            Toast.makeText(this, "Token 已复制", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 执行搜索：根据内容和日期过滤列表
     */
    private void performSearch() {
        String keyword = etSearch.getText().toString().toLowerCase();
        String date = etDate.getText().toString();

        // 清空过滤列表
        filteredList.clear();

        // 遍历原始数据
        for (MessageModel model : messageList) {
            // 检查内容
            if (!keyword.isEmpty()) {
                if (model.title != null && model.title.toLowerCase().contains(keyword)) {
                    // match = true
                } else if (model.body != null && model.body.toLowerCase().contains(keyword)) {
                    // match = true
                } else {
                    continue;
                }
            }

            // 检查日期 (格式: yyyy-MM-dd)
            if (!date.isEmpty()) {
                String sendTime = model.sendTime;
                if (sendTime == null) {
                    continue;
                }
                if (sendTime.length() < 10) {
                    continue;
                }
                if (!date.equals(sendTime.substring(0, 10))) {
                    continue;
                }
            }

            filteredList.add(model);
        }

        adapter.notifyDataSetChanged();
    }

    /**
     * 显示服务器设置对话框
     */
    private void showServerSettingsDialog() {
        // 使用自定义布局或简单的单选列表
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("服务器设置");

        // 服务器列表
        String[] servers = AppConfig.fcmApiUrls;

        // 默认选中当前 URL
        int checkedItem = 0;
        String currentUrl = sp.getString(AppConfig.preferencesApiUrl, servers[0]);
        if (currentUrl.equals(servers[1])) {
            checkedItem = 1;
        } else {
            checkedItem = 2;
        }

        // 使用单选按钮
        builder.setSingleChoiceItems(servers, checkedItem, null);

        // 设置确定按钮
        builder.setPositiveButton("确定", (choiceDialog, which) -> {
            // 获取选中的项
            int selectedPosition = ((AlertDialog) choiceDialog).getListView().getCheckedItemPosition();
            String server = servers[selectedPosition];

            if (server.contains("自定义")) {
                // 弹出输入框
                AlertDialog.Builder inputBuilder = new AlertDialog.Builder(this);
                inputBuilder.setTitle("请输入自定义服务器地址");

                EditText input = new EditText(this);
                input.setHint("https://your_domain.com");
                input.setText(currentUrl); // 显示当前地址
                // 只能单行
                input.setSingleLine(true);
                // 回车键显示“完成”
                input.setImeOptions(EditorInfo.IME_ACTION_DONE);
                inputBuilder.setView(input);
                inputBuilder.setPositiveButton("保存", null); // 先传 null，后面自己处理点击事件
                inputBuilder.setNegativeButton("取消", null);

                AlertDialog dialog = inputBuilder.create();

                input.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        extracted(input);
                        dialog.dismiss();
                        return true;
                    }
                    return false;
                });

                dialog.setOnShowListener(dialogInterface -> {
                    Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

                    btn.setOnClickListener(v -> {
                        extracted(input);
                        dialog.dismiss();
                    });
                });
                dialog.show();
            } else {
                sp.edit().putString(AppConfig.preferencesApiUrl, server).apply();
                refreshNoticeUrl(server);

                if (server.contains("cyf.lol")) { // 选择了 cyf.lol
                    Toast.makeText(this, "提示：该域名将于2027年2月失效", Toast.LENGTH_LONG).show();
                } else if (server.contains("workers.dev")) { // 选择了 workers.dev
                    Toast.makeText(this, "提示：该服务器需要代理", Toast.LENGTH_LONG).show();
                }
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void extracted(EditText input) {
        String customUrl = input.getText().toString().trim();
        // 判空
        if (customUrl.isEmpty()) {
            input.setError("服务器地址不能为空");
            return;
        }
        // 自动补全 http://
        if (!customUrl.startsWith("http://") && !customUrl.startsWith("https://")) {
            customUrl = "http://" + customUrl;
        }
        // 去掉末尾的 /
        if (customUrl.endsWith("/")) {
            customUrl = customUrl.substring(0, customUrl.length() - 1);
        }
        // URL 格式校验
        boolean isValid = android.util.Patterns.WEB_URL.matcher(customUrl).matches();
        if (!isValid) {
            input.setError("请输入正确的服务器地址");
            return;
        }
        // 保存
        sp.edit().putString(AppConfig.preferencesApiUrl, customUrl).apply();
        Toast.makeText(this, "已设置自定义服务器地址", Toast.LENGTH_SHORT).show();
        refreshNoticeUrl(customUrl);
    }

    // 辅助方法：复制文本到剪贴板
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }

    private void loadMessageList() {

        new Thread(() -> {  // ⭐ 在后台线程查询数据库
            AppDatabase db = AppDatabase.getInstance(this);
            List<MessageModel> list = db.messageDao().getAll(); // SELECT * FROM ...

            runOnUiThread(() -> {   // ⭐ 查询完成后回到主线程更新 UI
                messageList.clear();
                messageList.addAll(list);
                // 不直接调用 adapter.notifyDataSetChanged()
                // 而是调用 performSearch()，这样可以保留当前的搜索和日期过滤状态
                performSearch();
            });

        }).start();
    }

    private void showNoticeUrl(String token) {

        String apiUrl = sp.getString(AppConfig.preferencesApiUrl, AppConfig.fcmApiUrls[0]);

        tvStatus.setText("状态: 已注册    Token: (点击复制)");
        tvToken.setText(getHideToken(token));
        tvToken.setTag(token);
        btnRegister.setText("重新注册 / 刷新 Token");
        btnRegister.setTextColor(Color.parseColor("#FF6666"));
        btnRegister.setVisibility(View.GONE);      // 隐藏注册按钮
        btnToggleOptions.setVisibility(View.VISIBLE); // 显示展开更多按钮

        // 1. 显示缩略后的文字
        etExtraInfo1.setText(apiUrl + "/{token}/消息内容");
        etExtraInfo2.setText(apiUrl + "/{token}/消息标题/消息内容");
        etExtraInfo3.setText(apiUrl + "/{token}/消息标题/消息内容?group=test&priority=high");

        // 2. 将完整的文字保存在 Tag 中（Tag 可以存储任何对象，非常适合存这种隐藏数据）
        etExtraInfo1.setTag(token);
        etExtraInfo2.setTag(token);
        etExtraInfo3.setTag(token);
    }

    private void refreshNoticeUrl(String apiUrl) {
        etExtraInfo1.setText(apiUrl + "/{token}/消息内容");
        etExtraInfo2.setText(apiUrl + "/{token}/消息标题/消息内容");
        etExtraInfo3.setText(apiUrl + "/{token}/消息标题/消息内容?group=test&priority=high");
    }

    private String getHideToken(String token) {
        if (token != null && token.length() > 15) {
            return String.format("%s******%s (总长度: %s)", token.substring(0, 5), token.substring(token.length() - 5), token.length());
        }
        return token;
    }

    private void doRegister() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "注册成功", Toast.LENGTH_SHORT).show();
                String token = task.getResult();
                sp.edit().putString(AppConfig.preferencesToken, token).apply();
                showNoticeUrl(token);
            } else {
                Toast.makeText(this, task.getException().getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    // 广播接收器：当 Service 收到消息时通知 Activity 刷新
    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadMessageList(); // 重新加载数据并刷新列表
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        AppConfig.isForeground = true; // 标记在前台
        loadMessageList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppConfig.isForeground = false; // 标记已离开前台
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(messageReceiver);
    }

    private void checkToken() {
        String localToken = sp.getString(AppConfig.preferencesToken, null);
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String latestToken = task.getResult();

                if (localToken == null || !localToken.equals(latestToken)) {
                    // token 已变化（旧的失效）
                    sp.edit().putString("token", latestToken).apply();
                    Toast.makeText(this, "Token 已刷新", Toast.LENGTH_SHORT).show();
                    showNoticeUrl(latestToken);
                }
            }
        });
    }

    // --- 适配器内部类 ---
    class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_message, p, false);
            return new VH(v);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            MessageModel m = filteredList.get(position);
            String packageName = m.packageName;
            holder.title.setText(m.title);
            holder.body.setText(m.body);

            Drawable drawable;
            if ("com.tencent.mm".equals(packageName)) {
                drawable = ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.ic_notification_mm);
                // ✅ 设置颜色 #2AAE67
                DrawableCompat.setTint(drawable, Color.parseColor("#2AAE67"));
            } else {
                drawable = ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.ic_notification);
                DrawableCompat.setTint(drawable, Color.parseColor("#666666"));
            }

            holder.icon.setImageDrawable(drawable);

            Log.i(TAG, m.body + " Send: " + m.sendTimestamp + ", Received: " + m.receivedTimestamp);
            if (m.receivedTimestamp - m.sendTimestamp < AppConfig.showReceivedTimeDelayMs) {
                holder.time.setText(m.sendTime);
            } else {
                holder.time.setText(m.sendTime + " → " + m.receivedTime);
            }

            // 点击整个条目显示详情
            holder.itemView.setOnClickListener(v -> {
                showDetailDialog(m);
            });
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView title, body, time;
            ImageView icon;

            public VH(View v) {
                super(v);
                title = v.findViewById(R.id.itemTitle);
                body = v.findViewById(R.id.itemBody);
                time = v.findViewById(R.id.itemTime);
                icon = v.findViewById(R.id.itemIcon);
            }
        }
    }

    private void showDetailDialog(MessageModel m) {
        // 构建详情内容字符串
        StringBuilder sb = new StringBuilder();
        sb.append("【内容】").append(m.body).append("\n");
        sb.append("【发送时间】").append(m.sendTime).append("\n");
        sb.append("【接收时间】").append(m.receivedTime);
        if (m.packageName != null && !m.packageName.equals("")) {
            sb.append("\n");
            sb.append("【包名】").append(m.packageName);
        }
        if (m.group != null && !m.group.equals("default")) {
            sb.append("\n");
            sb.append("【分组】").append(m.group);
        }
        if (m.priority != null && !m.priority.equals("default")) {
            sb.append("\n");
            sb.append("【优先级】").append(m.group);
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(m.title)
                .setMessage(sb.toString())
                .setPositiveButton("确定", null)
                .setNeutralButton("复制内容", (dialog, which) -> {
                    copyToClipboard(m.body);
                    Toast.makeText(this, "内容已复制", Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}