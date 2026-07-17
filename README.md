# giffgaff 活跃测试（非官方）

一个非 giffgaff 官方的极简 Android 工具：用户手动点击后，经系统首选的**非 VPN 蜂窝网络**提交一个**含 1 字节应用负载的 UDP 数据报**。没有广告、分析 SDK、后台服务、定时/自启动任务或开发者运营的服务器。

> 这不是“严格只产生 1KB 流量”或“费用一定低于短信”的承诺。APK 能控制的是自己的 1 字节应用负载；Android 拉起/验证蜂窝网络、DNS、蜂窝承载开销和运营商计费取整不受应用控制。

## 为什么使用 UDP，而不是访问 URL

giffgaff 当前条款要求 SIM 每六个月至少完成一次合格活动，其中包括连接互联网；官方帮助页进一步写明可使用移动数据连接互联网：

- [giffgaff 条款第 13.5 条](https://www.giffgaff.com/terms)
- [停用与保号帮助页](https://help.giffgaff.com/en/articles/242797-understanding-why-your-number-has-been-deactivated)

普通 URL 请求不适合“尽可能少且可控”的目标：

- HTTP 需要 TCP 握手、确认和服务器响应；
- HTTPS 还需要 TLS 握手与证书，冷连接通常是数 KB；
- 即使使用 `HEAD`，服务器仍会返回响应头；
- TCP 目标无响应时可能重传，反而更不可控。

本应用因此只发送 UDP：

- 固定 1 字节应用负载（字节值 `0x47`）；
- 单个 `send()`；
- 应用不调用 `receive()`、不等待回复，但远端仍可能返回 UDP 或 ICMP 数据；
- 不做应用层重试；
- 失败时不回退到 Wi‑Fi 或其他默认网络。

数字公网 IPv4 的理论 IP 层上行是 `20B IPv4 + 8B UDP + 1B = 29B`；IPv6 是 `40B + 8B + 1B = 49B`。这些数字**不含**蜂窝承载、隧道、系统探测、DNS 和计费取整。

## 使用前准备

1. 在 Android 设置中，把 giffgaff 设为**默认移动数据 SIM**，关闭另一张 SIM 的自动数据切换，并在首次测试时关闭 VPN。
2. 确保 giffgaff 账户有可用于漫游数据的余额或产品。
3. 开启系统数据漫游；如果手机厂商提供按应用联网/漫游控制，可只允许本应用。
4. 准备一个你控制或获准使用的公网目标：
   - 最省流量：NAS 的数字公网 IPv4；
   - 地址会变化：NAS 的 DDNS 主机名，但会额外产生 DNS；
   - NAS 必须有公网 IPv4、全局 IPv6，或完成 UDP 端口转发；
   - `192.168.x.x`、`10.x.x.x` 等局域网地址不能用于蜂窝互联网保号。
5. 在 NAS 上配置一个专用 UDP 端口，收到后静默丢弃或记录；也可由防火墙 `DROP`。若端口直接关闭，主机可能返回 ICMP Port Unreachable，增加少量下行。

应用输入框只接受主机名/IP 和独立端口，**不要填写** `https://`、URL 路径、账号或管理端点。

## Android 与双卡边界

Stock Android 没有标准的“只给某个 APK 数据漫游权限”。本应用申请的是一个带互联网能力的非 VPN 蜂窝网络；系统的数据漫游开关和厂商的按应用联网策略仍然有效，应用不会绕过它们。系统网络探测或其他获准应用仍可能联网。

Android 公共 API 也不能让普通应用可靠地选中任意非默认 SIM。因此：

- 必须先将 giffgaff 设为默认移动数据 SIM；应用仍无法读取或证明实际 SIM 身份；
- 默认启用“仅在系统报告正在漫游时发送”；
- 如果设备无法准确报告漫游状态，可在自行确认后关闭这一检查；
- 应用不会读取手机号、SIM 标识、联系人或短信。

## 结果应该怎样理解

成功状态只是一条**本机提交记录**，表示：

> 1 字节负载已交给绑定在蜂窝网络上的 UDP socket。

它**不表示**：

- NAS 一定收到；
- giffgaff 一定计费；
- giffgaff 的保号系统一定已经记录；
- 整张 SIM 本次联网总流量低于 1KB。

如果应用在操作中被系统结束，重开后会把结果标为“未知”；负载可能已提交，也可能没有，此时不要立即重试。首次使用后请查看 giffgaff 的使用记录、余额变化或 NAS 日志进行核验。在确认这种方式对自己的 SIM 有效之前，保留一次合格短信作为兜底；也建议约每 5 个月操作一次，不要等到六个月期限当天。

## 中国漫游成本

截至 2026-07-17，[giffgaff 中国漫游价格页](https://www.giffgaff.com/roaming/china)显示：

- 数据：20p/MB；
- 发送短信：30p/条。

官方公开页面没有说明漫游数据的最小计费单位、会话最低扣款或具体取整方式，所以不能用“1 字节负载”推导出精确账单金额，也不能保证实际扣款低于 30p 短信。普通套餐在中国等 EU 区域以外通常不包含漫游数据，需要 Airtime Credit；价格也可能变化，使用前请重新查看官方页面。

## 通过 GitHub Actions 获取 APK

电脑不需要 Android Studio。

1. 打开仓库的 **Actions** 页面。
2. 进入最新一次 **Build APK** 成功运行。
3. 在 **Artifacts** 下载 `giffgaff-keepalive-debug-<commit>`。
4. 解压并安装 `app-debug.apk`；同目录的 `.sha256` 文件可用于校验。

每次推送到 `main`、每个 Pull Request，或手动触发 workflow 时，CI 会执行：

- Android Lint；
- JVM 单元测试；
- Debug APK 构建；
- SHA-256 生成；
- APK artifact 上传（保留 30 天）。

Debug APK 可直接侧载，但不同 GitHub Actions 运行生成的 debug 签名可能不同，因此新版不一定能覆盖安装旧版。长期使用应配置由 GitHub Secrets 提供的固定 release keystore；私钥不要提交到仓库。同一构建生成的 `.sha256` 只能校验下载完整性，不能独立证明发布者身份。

## 本地构建

如果电脑已有 JDK 17 和 Android SDK（不要求 Android Studio）：

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

输出文件：

```text
app/build/outputs/apk/debug/app-debug.apk
```

项目使用 Gradle Wrapper 8.13、Android Gradle Plugin 8.13.2、`compileSdk 36`，最低支持 Android 6.0（API 23）。

## 权限与隐私

Manifest 只有三个普通权限：

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `CHANGE_NETWORK_STATE`

目标、端口、操作时间和解析后的 IP 以未加密 `SharedPreferences` 保存在应用私有沙盒中，Manifest 已禁用系统备份。可用应用内“清除本地设置与记录”按钮，或在 Android 设置中清除应用数据。

使用主机名时，域名会暴露给系统配置的 DNS/Private DNS 解析器；目标主机可能看到运营商出口 IP。应用不读取或发送手机号、SIM 标识、设备 ID、联系人、短信或 giffgaff 账号。应用启动时不联网，只有用户点击按钮并通过确认项后才开始本次任务。
