package com.larfchan.giffgaffkeepalive;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String PREFS = "keepalive_settings";
    private static final String KEY_DNS_IP = "dns_ip";
    private static final String KEY_LAST_TIME = "last_time_v2";
    private static final String KEY_LAST_SUMMARY = "last_summary_v2";
    private static final String KEY_ATTEMPT_PENDING = "attempt_pending_v2";
    private static final String KEY_ATTEMPT_STARTED = "attempt_started_v2";
    private static final String DEFAULT_DNS_IP = "8.8.8.8";
    private static final int DNS_PORT = 53;

    private static final int BLACK = Color.rgb(17, 17, 17);
    private static final int INK = Color.rgb(34, 36, 40);
    private static final int MUTED = Color.rgb(98, 103, 113);
    private static final int PAGE = Color.rgb(244, 245, 247);
    private static final int WHITE = Color.WHITE;
    private static final int LIME = Color.rgb(217, 255, 67);
    private static final int BORDER = Color.rgb(218, 221, 226);
    private static final int GREEN = Color.rgb(17, 118, 70);
    private static final int GREEN_BG = Color.rgb(232, 247, 239);
    private static final int RED = Color.rgb(176, 38, 47);
    private static final int RED_BG = Color.rgb(255, 238, 239);
    private static final int BLUE = Color.rgb(34, 83, 155);
    private static final int BLUE_BG = Color.rgb(235, 242, 255);

    private SharedPreferences preferences;
    private CellularUdpSender sender;
    private EditText hostInput;
    private Button sendButton;
    private LinearLayout statusCard;
    private TextView statusTitle;
    private TextView statusBody;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        sender = new CellularUdpSender(this);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        setContentView(buildScreen());
        restoreLastStatus();
    }

    private View buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(PAGE);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setFitsSystemWindows(true);
        scrollView.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        page.addView(buildHeader());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        page.addView(content, matchWrap());

        content.addView(buildReminderCard());
        content.addView(buildTargetCard());
        content.addView(buildSendButton());
        content.addView(buildStatusCard());
        return scrollView;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(24), dp(22), dp(24));
        header.setBackgroundColor(BLACK);

        TextView badge = text("手动发送", 12, BLACK);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(rounded(LIME, 100));
        LinearLayout.LayoutParams badgeParams = wrapWrap();
        badgeParams.bottomMargin = dp(14);
        header.addView(badge, badgeParams);

        TextView title = text("giffgaff 保号", 30, WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, matchWrap());

        TextView subtitle = text(
                "点击一次，只提交一次极小 DNS 查询。",
                15,
                Color.rgb(213, 215, 219)
        );
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(8);
        header.addView(subtitle, subtitleParams);
        return header;
    }

    private View buildReminderCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("发送前请确认"));

        TextView reminders = body(
                "1、请在设置中确保漫游只给本 App 权限\n\n"
                        + "2、请关闭智能切卡\n\n"
                        + "3、请关闭无关 VPN 软件\n\n"
                        + "4、发送之后，请等待一段时间自行查看是否有余额变动，以免保号失败"
        );
        card.addView(reminders, topMargin(14));
        return card;
    }

    private View buildTargetCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("公网 IP"));

        hostInput = input(DEFAULT_DNS_IP);
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hostInput.setText(savedDnsIp());
        hostInput.setSelectAllOnFocus(true);
        card.addView(hostInput, topMargin(10));

        TextView help = text(
                "默认使用 Google Public DNS；UDP 53 端口已固定，无需填写。",
                13,
                MUTED
        );
        card.addView(help, topMargin(8));
        return card;
    }

    private View buildSendButton() {
        sendButton = new Button(this);
        sendButton.setText("发送一次");
        sendButton.setTextSize(16);
        sendButton.setTextColor(WHITE);
        sendButton.setTypeface(Typeface.DEFAULT_BOLD);
        sendButton.setAllCaps(false);
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setMinHeight(dp(56));
        sendButton.setPadding(dp(18), dp(12), dp(18), dp(12));
        sendButton.setBackground(rounded(BLACK, 14));
        sendButton.setOnClickListener(view -> startSend());

        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(14);
        return wrapWithParams(sendButton, params);
    }

    private View buildStatusCard() {
        statusCard = card();
        statusTitle = sectionTitle("尚未发送");
        statusCard.addView(statusTitle);
        statusBody = body("点击按钮后，应用会经非 VPN 蜂窝网络发送一次。");
        statusBody.setTextIsSelectable(true);
        statusCard.addView(statusBody, topMargin(8));
        return statusCard;
    }

    private void startSend() {
        hostInput.setError(null);

        final TargetSpec target;
        try {
            target = TargetSpec.parsePublicIp(hostInput.getText().toString(), DNS_PORT);
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage() == null ? "公网 IP 格式不正确" : exception.getMessage();
            hostInput.setError(message);
            showValidationError(message);
            return;
        }

        boolean attemptRecorded = preferences.edit()
                .putString(KEY_DNS_IP, target.host)
                .putBoolean(KEY_ATTEMPT_PENDING, true)
                .putLong(KEY_ATTEMPT_STARTED, System.currentTimeMillis())
                .commit();
        if (!attemptRecorded) {
            showValidationError("无法保存发送状态，因此未发送");
            return;
        }

        hideKeyboard();
        setBusy(true);
        showProgress("准备发送", "正在申请非 VPN 蜂窝网络…");

        sender.send(target, new CellularUdpSender.Callback() {
            @Override
            public void onProgress(String message) {
                showProgress("正在发送", message);
            }

            @Override
            public void onSuccess(CellularUdpSender.Result result) {
                setBusy(false);
                showSuccess(result);
            }

            @Override
            public void onError(String message, boolean outcomeUnknown) {
                setBusy(false);
                if (outcomeUnknown) {
                    showUnknownResult(message);
                } else {
                    preferences.edit().putBoolean(KEY_ATTEMPT_PENDING, false).apply();
                    showSendError(message);
                }
            }
        });
    }

    private void showSuccess(CellularUdpSender.Result result) {
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());

        String summary = "时间：" + timestamp
                + "\n目标：" + result.destination.getHostAddress() + ':' + result.port
                + "\n已提交：" + result.applicationBytes + " 字节 DNS 查询"
                + "\n理论 IP 层上行：" + result.theoreticalIpBytes + " 字节"
                + "\n\n请稍后自行查看余额或账户记录。";

        preferences.edit()
                .putLong(KEY_LAST_TIME, System.currentTimeMillis())
                .putString(KEY_LAST_SUMMARY, summary)
                .putBoolean(KEY_ATTEMPT_PENDING, false)
                .apply();

        setStatusColors(GREEN, GREEN_BG);
        statusTitle.setText("已发送");
        statusBody.setText(summary);
    }

    private void showProgress(String title, String message) {
        setStatusColors(BLUE, BLUE_BG);
        statusTitle.setText(title);
        statusBody.setText(message);
    }

    private void showValidationError(String message) {
        setStatusColors(RED, RED_BG);
        statusTitle.setText("未发送");
        statusBody.setText(message);
    }

    private void showSendError(String message) {
        setStatusColors(RED, RED_BG);
        statusTitle.setText("发送未确认");
        statusBody.setText((message == null ? "发生未知错误" : message)
                + "\n\n网络申请或系统探测仍可能产生少量流量，请先查看余额再决定是否重试。");
    }

    private void showUnknownResult(String message) {
        setStatusColors(RED, RED_BG);
        statusTitle.setText("发送结果未知");
        statusBody.setText((message == null ? "发送阶段发生未知错误" : message)
                + "\n\n数据可能已经提交。请先查看余额或账户记录，不要立即重试。");
    }

    private void restoreLastStatus() {
        if (preferences.getBoolean(KEY_ATTEMPT_PENDING, false)) {
            long started = preferences.getLong(KEY_ATTEMPT_STARTED, 0L);
            String when = started > 0L
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(started))
                    : "未知时间";
            setStatusColors(RED, RED_BG);
            statusTitle.setText("上次结果未知");
            statusBody.setText("上次操作始于 " + when
                    + "。请先查看余额或账户记录，不要立即重试。");
            return;
        }

        long lastTime = preferences.getLong(KEY_LAST_TIME, 0L);
        String lastSummary = preferences.getString(KEY_LAST_SUMMARY, "");
        if (lastTime > 0L && lastSummary != null && !lastSummary.isEmpty()) {
            setStatusColors(INK, WHITE);
            statusTitle.setText("上次发送");
            statusBody.setText(lastSummary);
        }
    }

    private String savedDnsIp() {
        String value = preferences.getString(KEY_DNS_IP, DEFAULT_DNS_IP);
        return value == null || value.trim().isEmpty() ? DEFAULT_DNS_IP : value;
    }

    private void setBusy(boolean busy) {
        hostInput.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        sendButton.setAlpha(busy ? 0.6f : 1f);
        sendButton.setText(busy ? "发送中…" : "发送一次");
    }

    private void setStatusColors(int foreground, int background) {
        statusCard.setBackground(roundedWithStroke(background, 16, foreground, 1));
        statusTitle.setTextColor(foreground);
        statusBody.setTextColor(foreground);
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return;
        }
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
        focused.clearFocus();
    }

    @Override
    protected void onDestroy() {
        sender.close();
        super.onDestroy();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundedWithStroke(WHITE, 16, BORDER, 1));
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(14);
        card.setLayoutParams(params);
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 20, INK);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView body(String value) {
        TextView view = text(value, 15, INK);
        view.setLineSpacing(0f, 1.3f);
        return view;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setTextSize(18);
        input.setTextColor(INK);
        input.setHintTextColor(Color.rgb(147, 151, 159));
        input.setHint(hint);
        input.setSingleLine(true);
        input.setPadding(0, dp(10), 0, dp(10));
        input.setBackgroundTintList(ColorStateList.valueOf(BLACK));
        return input;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundedWithStroke(
            int color,
            int radiusDp,
            int strokeColor,
            int strokeDp
    ) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams topMargin(int marginDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(marginDp);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private View wrapWithParams(View view, LinearLayout.LayoutParams params) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(view, matchWrap());
        wrapper.setLayoutParams(params);
        return wrapper;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
