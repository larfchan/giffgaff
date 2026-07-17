# giffgaff 保号（非官方）

一个手动触发的极简 Android 工具。点击一次后，应用会申请系统首选的**非 VPN 蜂窝网络**，向默认目标 `8.8.8.8:53` 发送一个 **17 字节的合法 DNS 查询**。

没有广告、分析 SDK、后台服务、定时任务、自启动、自动重试或开发者运营的服务器。

## 发送内容

- 默认公网 IP：Google Public DNS `8.8.8.8`
- 协议与端口：UDP 53，端口固定且不在界面显示
- 应用负载：17 字节的 DNS 根域 A 记录查询
- 次数：每次点击只调用一次 `send()`
- 网络：只申请带互联网能力的非 VPN 蜂窝网络
- 接收：应用不调用 `receive()`，但 DNS 服务器仍可能回复，系统也可能产生少量网络探测
- 失败：不回退到 Wi‑Fi，不做应用层重试

UDP 协议必须有端口。选择 53 是因为 `8.8.8.8` 是 Google 公布的公共 DNS 地址，传统 DNS 使用 UDP/TCP 53。发送合法 DNS 查询比向公共服务发送畸形的单字节包更合适。

数字 IPv4 的理论 IP 层上行是 `20B IPv4 + 8B UDP + 17B = 45B`；IPv6 是 `40B + 8B + 17B = 65B`。这些数字不包含蜂窝承载、隧道、系统探测、服务器回复和运营商计费取整，因此不能承诺整次联网低于 1KB，也不能据此推导实际费用。

这是直接发往目标 IP 的明文 DNS，不使用 Android Private DNS，也不会经过 VPN。默认目标 Google Public DNS 可以看到运营商出口 IP、查询内容和时间；本应用查询的是根域，不包含用户域名、账号或设备标识。

参考：

- [Google Public DNS 使用说明](https://developers.google.com/speed/public-dns/docs/using)
- [Google Public DNS 概览](https://developers.google.com/speed/public-dns/)
- [Google Public DNS 隐私说明](https://developers.google.com/speed/public-dns/privacy)

## 手机上只保留四条提醒

1. 请在设置中确保漫游只给本 App 权限
2. 请关闭智能切卡
3. 请关闭无关 VPN 软件
4. 发送之后，请等待一段时间自行查看是否有余额变动，以免保号失败

界面没有确认勾选框。公网 IP 可以修改，但只接受数字公网 IPv4/IPv6；由于端口和内容固定为 DNS/UDP 53，通常保持默认的 `8.8.8.8` 即可。

Stock Android 没有统一的“只允许某个 App 数据漫游”权限。第一条提醒依赖手机厂商是否提供按应用联网/漫游控制；应用本身不能绕过系统的数据漫游开关，也不能可靠识别当前实际使用的是哪张 SIM。因此仍应把 giffgaff 设为默认移动数据 SIM，并关闭智能切卡。

## 保号与费用边界

giffgaff 当前帮助页说明，号码至少每六个月需要进行一次合格活动，使用移动数据连接互联网属于合格活动之一：

- [giffgaff：号码为什么会被停用](https://help.giffgaff.com/en/articles/242797-understanding-why-your-number-has-been-deactivated)

应用显示“已发送”只表示 DNS 查询已交给绑定在蜂窝网络上的 UDP socket，不表示：

- Google 一定回复；
- giffgaff 一定产生可见扣费；
- giffgaff 已经确认本次活动；
- 整张 SIM 的本次流量低于 1KB。

首次使用后请等待账户记录更新并核对余额。在确认这种方式对自己的 SIM 有效前，保留短信等其他合格活动作为兜底；不要等到六个月期限当天才操作。

截至 2026-07-17，[giffgaff 中国漫游价格页](https://www.giffgaff.com/roaming/china)显示数据为 20p/MB、发送短信为 30p/条。公开页面没有说明最小计费单位、最低扣款或取整方式，所以不能保证一次点击的实际扣款一定低于一条短信。

## 通过 GitHub Actions 获取 APK

电脑不需要 Android Studio。

1. 打开仓库的 **Actions** 页面。
2. 进入最新一次成功的 **Build APK**。
3. 在 **Artifacts** 下载 `giffgaff-keepalive-debug-<commit>`。
4. 解压并安装 `app-debug.apk`；同目录的 `.sha256` 文件可用于校验下载完整性。

每次推送到 `main`、每个 Pull Request，或手动触发 workflow 时，CI 会执行 Android Lint、JVM 单元测试、Debug APK 构建、SHA-256 生成和 artifact 上传。

Debug APK 可直接侧载，但不同 GitHub Actions 运行生成的 debug 签名可能不同，因此新版不一定能覆盖安装旧版。长期使用应配置固定的 release keystore，且私钥不能提交到仓库。

## 本地构建

如果电脑已有 JDK 17 和 Android SDK（不要求 Android Studio）：

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

项目使用 Gradle Wrapper 8.13、Android Gradle Plugin 8.13.2、`compileSdk 36`，最低支持 Android 6.0（API 23）。

## 权限与隐私

Manifest 只有三个普通权限：

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `CHANGE_NETWORK_STATE`

应用只在私有沙盒中保存目标 IP、发送时间和本机结果，Manifest 已禁用系统备份。应用不读取手机号、SIM 标识、设备 ID、联系人、短信或 giffgaff 账号。应用启动时不联网，只有用户点击发送按钮后才申请蜂窝网络。
