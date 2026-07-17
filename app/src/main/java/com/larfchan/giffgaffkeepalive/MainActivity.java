package com.larfchan.giffgaffkeepalive;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String PREFS = "keepalive_settings";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_ONLY_ROAMING = "only_roaming";
    private static final String KEY_LAST_TIME = "last_time";
    private static final String KEY_LAST_SUMMARY = "last_summary";
    private static final String KEY_ATTEMPT_PENDING = "attempt_pending";
    private static final String KEY_ATTEMPT_STARTED = "attempt_started";

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
    private EditText portInput;
    private TextView targetModeHint;
    private CheckBox onlyRoamingCheck;
    private CheckBox defaultSimCheck;
    private CheckBox authorizedTargetCheck;
    private Button sendButton;
    private Button clearButton;
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

        content.addView(buildRuleCard());
        content.addView(buildTargetCard());
        content.addView(buildSafetyCard());
        content.addView(buildSendButton());
        content.addView(buildStatusCard());
        content.addView(buildClearButton());
        content.addView(buildFooter());

        return scrollView;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(24), dp(22), dp(24));
        header.setBackgroundColor(BLACK);

        TextView badge = text("非官方 · 手动 · 无定时任务", 12, BLACK);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(rounded(LIME, 100));
        LinearLayout.LayoutParams badgeParams = wrapWrap();
        badgeParams.bottomMargin = dp(14);
        header.addView(badge, badgeParams);

        TextView title = text("giffgaff 活跃测试", 30, WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, matchWrap());

        TextView subtitle = text(
                "经系统首选蜂窝网络提交含 1 字节应用负载的 UDP 数据报。",
                15,
                Color.rgb(213, 215, 219)
        );
        subtitle.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(8);
        header.addView(subtitle, subtitleParams);

        return header;
    }

    private View buildRuleCard() {
        LinearLayout card = card();

        TextView eyebrow = eyebrow("为什么这样做");
        card.addView(eyebrow);

        TextView title = sectionTitle("官方规则认可移动数据连接");
        card.addView(title, topMargin(6));

        TextView copy = body(
                "截至 2026-07-17，giffgaff 规则要求每六个月至少进行一次合格活动，其中包括“使用移动数据连接互联网”。规则可能变化，请以官方最新页面为准。建议约每 5 个月操作一次。"
        );
        card.addView(copy, topMargin(8));

        TextView warning = callout(
                "本应用只能控制自身的 1 字节应用负载。蜂窝建立、DNS、系统探测和运营商计费取整不受 APK 控制；不能承诺整次 SIM 流量低于 1KB、费用低于短信，或一定被记为保号活动。",
                BLUE,
                BLUE_BG
        );
        card.addView(warning, topMargin(14));
        return card;
    }

    private View buildTargetCard() {
        LinearLayout card = card();
        card.addView(eyebrow("发送目标"));
        card.addView(sectionTitle("你控制的公网地址"), topMargin(6));
        card.addView(body(
                "强烈推荐 NAS 的数字公网 IPv4。DDNS 主机名属于高级选项，会额外产生 DNS/Private DNS 流量。不要填写 http://、路径或局域网地址。"
        ), topMargin(8));

        TextView hostLabel = fieldLabel("公网 IP / 主机名");
        card.addView(hostLabel, topMargin(18));

        hostInput = input("例如：你的公网 IP 或 nas.example.com");
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        hostInput.setText(preferences.getString(KEY_HOST, ""));
        card.addView(hostInput, topMargin(5));

        targetModeHint = text("", 13, MUTED);
        targetModeHint.setLineSpacing(0f, 1.2f);
        card.addView(targetModeHint, topMargin(7));
        hostInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence sequence, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence sequence, int start, int before, int count) {
                updateTargetModeHint(sequence == null ? "" : sequence.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        updateTargetModeHint(hostInput.getText().toString());

        TextView portLabel = fieldLabel("UDP 端口");
        card.addView(portLabel, topMargin(16));

        portInput = input("1–65535");
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText(preferences.getString(KEY_PORT, "9"));
        card.addView(portInput, topMargin(5));

        TextView portHelp = text(
                "端口 9 是传统 Discard 端口。更推荐在自己的 NAS 上配置一个专用 UDP 端口，收到后静默丢弃或由防火墙 DROP。",
                13,
                MUTED
        );
        portHelp.setLineSpacing(0f, 1.2f);
        card.addView(portHelp, topMargin(7));
        return card;
    }

    private View buildSafetyCard() {
        LinearLayout card = card();
        card.addView(eyebrow("发送条件"));
        card.addView(sectionTitle("先确认默认数据卡"), topMargin(6));
        card.addView(body(
                "Android 公共 API 不能证明实际使用了哪张 SIM。本应用只申请系统首选的非 VPN 蜂窝网络；请把 giffgaff 设为默认数据卡，并关闭另一张 SIM 的自动数据切换和 VPN。"
        ), topMargin(8));

        onlyRoamingCheck = checkbox("仅在系统报告“正在漫游”时发送");
        onlyRoamingCheck.setChecked(preferences.getBoolean(KEY_ONLY_ROAMING, true));
        card.addView(onlyRoamingCheck, topMargin(14));

        defaultSimCheck = checkbox("我已将 giffgaff 设为默认移动数据 SIM");
        card.addView(defaultSimCheck, topMargin(6));

        authorizedTargetCheck = checkbox("该目标由我控制，或我已获准向它发送");
        card.addView(authorizedTargetCheck, topMargin(6));

        TextView platformNote = callout(
                "Stock Android 没有标准的“只允许此 APK 漫游”权限。厂商按应用开关可以配合，但系统探测或其他获准应用仍可能联网。本应用没有定时、自启动或自动重试。",
                MUTED,
                Color.rgb(241, 242, 244)
        );
        card.addView(platformNote, topMargin(14));
        return card;
    }

    private View buildSendButton() {
        sendButton = new Button(this);
        sendButton.setText("提交 1 字节 UDP 负载");
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
        statusBody = body("填写目标并确认发送条件后，点击上方按钮。");
        statusBody.setTextIsSelectable(true);
        statusCard.addView(statusBody, topMargin(8));
        return statusCard;
    }

    private View buildClearButton() {
        clearButton = new Button(this);
        clearButton.setText("清除本地设置与记录");
        clearButton.setTextSize(13);
        clearButton.setTextColor(MUTED);
        clearButton.setAllCaps(false);
        clearButton.setBackground(roundedWithStroke(PAGE, 12, BORDER, 1));
        clearButton.setOnClickListener(view -> clearLocalData());
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(14);
        return wrapWithParams(clearButton, params);
    }

    private View buildFooter() {
        TextView footer = text(
                "无分析 SDK · 无广告 · 无定时/自启动 · 不连接开发者服务器\n应用不读取 SIM 身份；目标仍可能回复 UDP/ICMP。提交成功不代表 NAS 收到、运营商计费或已确认保号。",
                12,
                MUTED
        );
        footer.setGravity(Gravity.CENTER);
        footer.setLineSpacing(0f, 1.25f);
        footer.setPadding(dp(8), dp(4), dp(8), dp(10));
        return footer;
    }

    private void updateTargetModeHint(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.contains("://") || value.contains("/")) {
            targetModeHint.setText("请只填主机名或公网 IP，不要填 URL 或路径。");
            targetModeHint.setTextColor(RED);
        } else if (AddressPolicy.looksNumericAddress(stripIpv6Brackets(value))) {
            targetModeHint.setText("数字 IP：不会产生 DNS 查询，流量最可控。");
            targetModeHint.setTextColor(GREEN);
        } else {
            targetModeHint.setText("主机名：发送前会通过同一蜂窝网络进行 DNS 解析，实际流量会增加。");
            targetModeHint.setTextColor(MUTED);
        }
    }

    private void startSend() {
        clearFieldErrors();

        if (!defaultSimCheck.isChecked()) {
            showValidationError("请先确认 giffgaff 已设为默认移动数据 SIM。");
            return;
        }
        if (!authorizedTargetCheck.isChecked()) {
            showValidationError("请先确认你有权向该目标发送数据。");
            return;
        }

        final TargetSpec target;
        try {
            target = TargetSpec.parse(
                    hostInput.getText().toString(),
                    portInput.getText().toString()
            );
        } catch (IllegalArgumentException exception) {
            showValidationError(exception.getMessage());
            if (exception.getMessage() != null && exception.getMessage().contains("端口")) {
                portInput.setError(exception.getMessage());
            } else {
                hostInput.setError(exception.getMessage());
            }
            return;
        }

        preferences.edit()
                .putString(KEY_HOST, target.host)
                .putString(KEY_PORT, Integer.toString(target.port))
                .putBoolean(KEY_ONLY_ROAMING, onlyRoamingCheck.isChecked())
                .putBoolean(KEY_ATTEMPT_PENDING, true)
                .putLong(KEY_ATTEMPT_STARTED, System.currentTimeMillis())
                .apply();

        hideKeyboard();
        setBusy(true);
        showProgress("准备发送", "不会使用 Wi‑Fi，也不会在失败时回退到其他网络。");

        sender.send(target, onlyRoamingCheck.isChecked(), new CellularUdpSender.Callback() {
            @Override
            public void onProgress(String message) {
                showProgress("正在发送", message);
            }

            @Override
            public void onSuccess(CellularUdpSender.Result result) {
                setBusy(false);
                showSuccess(target, result);
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                preferences.edit().putBoolean(KEY_ATTEMPT_PENDING, false).apply();
                showSendError(message);
            }
        });
    }

    private void showSuccess(TargetSpec target, CellularUdpSender.Result result) {
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());

        StringBuilder summary = new StringBuilder();
        summary.append("时间：").append(timestamp)
                .append("\n目标：").append(target.host)
                .append(" → ").append(result.destination.getHostAddress())
                .append(':').append(result.port)
                .append("\n动作：含 1 字节应用负载的 UDP 数据报已交给蜂窝 socket")
                .append("\n理论 IP 层上行：").append(result.theoreticalIpBytes)
                .append(" B（不含蜂窝承载、系统探测和计费取整）")
                .append("\nDNS：").append(result.usedDns ? "使用了主机名解析" : "未使用")
                .append("\n蜂窝网络：经系统网络请求取得（已排除 VPN）")
                .append("\n漫游状态：")
                .append(result.roaming ? "系统报告正在漫游" : "系统报告非漫游或未要求漫游")
                .append("\n本应用 UID 近似计数：↑ ")
                .append(formatCounter(result.uidTxDelta))
                .append(" / ↓ ").append(formatCounter(result.uidRxDelta))
                .append("\n\n这是本机提交记录，不是保号确认。请首次在 giffgaff 使用记录中核验；在确认可靠前，仍保留短信作为兜底。");

        String text = summary.toString();
        preferences.edit()
                .putLong(KEY_LAST_TIME, System.currentTimeMillis())
                .putString(KEY_LAST_SUMMARY, text)
                .putBoolean(KEY_ATTEMPT_PENDING, false)
                .apply();

        setStatusColors(GREEN, GREEN_BG);
        statusTitle.setText("UDP 负载已提交 · 未验证保号");
        statusBody.setText(text);
    }

    private void showProgress(String title, String message) {
        setStatusColors(BLUE, BLUE_BG);
        statusTitle.setText(title);
        statusBody.setText(message);
    }

    private void showValidationError(String message) {
        setStatusColors(RED, RED_BG);
        statusTitle.setText("未开始发送");
        statusBody.setText(message == null ? "发生未知错误" : message);
    }

    private void showSendError(String message) {
        setStatusColors(RED, RED_BG);
        statusTitle.setText("1 字节负载未确认提交");
        statusBody.setText((message == null ? "发生未知错误" : message)
                + "\n\n注意：失败前的网络申请、DNS 或 Android 系统探测仍可能已经产生流量或费用。");
    }

    private void restoreLastStatus() {
        if (preferences.getBoolean(KEY_ATTEMPT_PENDING, false)) {
            long started = preferences.getLong(KEY_ATTEMPT_STARTED, 0L);
            String when = started > 0L
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(started))
                    : "未知时间";
            setStatusColors(RED, RED_BG);
            statusTitle.setText("上次操作结果未知 · 请勿立即重试");
            statusBody.setText("上次操作始于 " + when
                    + "，但应用未能保存最终结果。负载可能已经提交，也可能没有；网络申请、DNS 或系统探测也可能产生费用。请先核对 giffgaff/NAS 记录。");
            return;
        }
        long lastTime = preferences.getLong(KEY_LAST_TIME, 0L);
        String lastSummary = preferences.getString(KEY_LAST_SUMMARY, "");
        if (lastTime > 0L && lastSummary != null && !lastSummary.isEmpty()) {
            setStatusColors(INK, WHITE);
            statusTitle.setText("本机上次提交记录 · 非保号确认");
            statusBody.setText(lastSummary);
        }
    }

    private void setBusy(boolean busy) {
        hostInput.setEnabled(!busy);
        portInput.setEnabled(!busy);
        onlyRoamingCheck.setEnabled(!busy);
        defaultSimCheck.setEnabled(!busy);
        authorizedTargetCheck.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        clearButton.setEnabled(!busy);
        sendButton.setAlpha(busy ? 0.6f : 1f);
        clearButton.setAlpha(busy ? 0.6f : 1f);
        sendButton.setText(busy ? "提交中…" : "提交 1 字节 UDP 负载");
    }

    private void clearLocalData() {
        preferences.edit().clear().apply();
        hostInput.setText("");
        portInput.setText("9");
        onlyRoamingCheck.setChecked(true);
        defaultSimCheck.setChecked(false);
        authorizedTargetCheck.setChecked(false);
        setStatusColors(INK, WHITE);
        statusTitle.setText("本地设置与记录已清除");
        statusBody.setText("应用沙盒内保存的目标、端口和提交记录已删除。");
    }

    private void setStatusColors(int accent, int background) {
        statusCard.setBackground(roundedWithStroke(background, 18, accent, 1));
        statusTitle.setTextColor(accent);
        statusBody.setTextColor(INK);
    }

    private void clearFieldErrors() {
        hostInput.setError(null);
        portInput.setError(null);
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
        card.setBackground(roundedWithStroke(WHITE, 18, BORDER, 1));
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(14);
        card.setLayoutParams(params);
        return card;
    }

    private TextView eyebrow(String value) {
        TextView view = text(value.toUpperCase(Locale.ROOT), 11, MUTED);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setLetterSpacing(0.08f);
        return view;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 20, INK);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView body(String value) {
        TextView view = text(value, 14, MUTED);
        view.setLineSpacing(0f, 1.35f);
        return view;
    }

    private TextView fieldLabel(String value) {
        TextView view = text(value, 13, INK);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setTextSize(16);
        input.setTextColor(INK);
        input.setHintTextColor(Color.rgb(147, 151, 159));
        input.setHint(hint);
        input.setSingleLine(true);
        input.setPadding(0, dp(10), 0, dp(10));
        input.setBackgroundTintList(ColorStateList.valueOf(BLACK));
        return input;
    }

    private CheckBox checkbox(String value) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(value);
        checkBox.setTextSize(14);
        checkBox.setTextColor(INK);
        checkBox.setGravity(Gravity.TOP);
        checkBox.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{BLACK, MUTED}
        ));
        return checkBox;
    }

    private TextView callout(String value, int color, int background) {
        TextView view = text(value, 13, color);
        view.setLineSpacing(0f, 1.3f);
        view.setPadding(dp(13), dp(11), dp(13), dp(11));
        view.setBackground(rounded(background, 12));
        return view;
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

    private static String stripIpv6Brackets(String value) {
        if (value.startsWith("[") && value.endsWith("]") && value.length() > 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String formatCounter(long bytes) {
        return bytes < 0L ? "暂不可用" : bytes + " B";
    }
}
