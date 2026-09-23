<div align="center">
    <h1>我的电视 · 定制版</h1>
    <p>用 Android 原生 + Jetpack Compose 写的 IPTV 直播 / 点播播放器（个人定制分支）</p>

![GitHub Repo stars](https://img.shields.io/github/stars/nid12345/mytv-android)
![GitHub all releases](https://img.shields.io/github/downloads/nid12345/mytv-android/total)
[![Android Sdk Require](https://img.shields.io/badge/Android-5.0%2B-informational?logo=android)](https://apilevels.com/)
[![GitHub](https://img.shields.io/github/license/nid12345/mytv-android)](https://github.com/nid12345/mytv-android)

</div>

## 这是什么

本仓库是 [**yaoxieyoulei/mytv-android**](https://github.com/yaoxieyoulei/mytv-android) 的**个人定制分支**。

完整保留原项目的界面设计、排版与全部基础功能，只在此基础上做自用向的调整与修复。
界面与底层能力**全部来自原作者**，这里只是把它改成适合自己家里用的样子。

| 项 | 值 |
|---|---|
| 包名 | `top.yogiczy.mytv.tv.custom`（与原版**不同包名**，可与官方版共存） |
| 版本号 | `custom-vX.Y`（对应本仓库的 Release tag） |
| 最低系统 | Android 5.0（API 21） |
| 内置推送服务 | `http://<设备IP>:10481` |

下载：[本仓库 Releases](https://github.com/nid12345/mytv-android/releases) —— 装好后应用内也能自己检查更新。

---

## 功能

### 播放与选台

| 功能 | 说明 |
|---|---|
| 直播播放 | 基于 AndroidX Media3（ExoPlayer），支持 HLS / RTSP / 普通流 |
| 多线路 | 同一频道多条线路，左右键切换；播放失败自动跳下一条；**按实测播放效果排序**（不是只看响应速度） |
| 三段式选台 | 左侧分组 / 中间频道 / 右侧节目信息，遥控器上下左右即可操作 |
| 数字选台 | 直接按数字键跳频道 |
| 换台反转 | 可把上下键方向对调 |
| 自动连播 | **点播类源**（如潮汕节目回放）一个视频播完自动接下一个，同分组播完接下一分组 |
| 混合模式 | 直播源线路全不可用时，可回退到内置网页播放源 |

### 订阅源

| 功能 | 说明 |
|---|---|
| 多订阅源 | 支持 m3u / tvbox 格式，历史订阅源一键切换 |
| 快捷换源 | 选台界面分组栏顶部「换源」，直接列出全部订阅链接 |
| 内置订阅源 | 自建默认源、斗鱼直播、虎牙直播、YY轮播、潮汕节目回放 |
| 添加订阅 | 手动输入名称 + 链接，或用手机扫码推送 |
| 分组管理 | 分组显隐**按订阅源分别记忆**；「全部分组管理」可在一个界面里统一设置 |

### 节目单（EPG）

| 功能 | 说明 |
|---|---|
| 多节目单源 | 内置 4 个源，可在设置里切换 |
| 主源失效自动兜底 | 主源超时 / 返回的不是 XML / 解析不出节目，都会**自动改用能用的备用源**，并提示 + 记下来 |
| 跨源补齐 | 主源缺某个频道的节目时，自动从其它源取同名频道补上 |
| 当天节目单 | 选中频道按菜单键查看当前节目 |
| 节目预约 | 在节目单里预约某个节目，到点自动弹出提醒，确认后切到该频道 |

### 台标、投屏与推送

| 功能 | 说明 |
|---|---|
| 台标多级回退 | **内置离线台标**（常用频道瞬时显示）→ 自建镜像（CDN + 直连）→ 上游原站 → 上游国内镜像 |
| DLNA 投屏 | 快捷操作栏「投屏」，把当前频道推到局域网内的电视 / 盒子播放 |
| 扫码推送 | 手机访问 `http://<设备IP>:10481` 即可推送订阅源、节目单、配置项，以及 **APK 安装包** |
| 推送 APK | 收到安装包会**弹确认框**（文件名 / 大小 / 时间），点「立即安装」调起系统安装；未放行权限时给「去放行」入口 |
| 安装包管理 | 设置 → 推送 → 安装包管理：安装 / 删除已收到的包，也能卸载已装应用 |
| 系统应用入口 | 选台界面分组栏最底部「系统应用」，直接跳转其它 App（跳转前自动暂停直播） |

### 应用本身

| 功能 | 说明 |
|---|---|
| 应用内升级 | 检查本仓库 Release → 提示 → 下载 → 调起安装；**检查与下载都带镜像回退**（国内直连 GitHub 不稳） |
| 升级提醒开关 | 「升级提醒」控制是否自动检查；「更新强提醒」控制全屏提醒还是消息提示；「下载线路」可选自动 / 直连 / 镜像优先 |
| 崩溃记录 | 未捕获异常写入应用私有目录，下次启动显示在「设置 → 日志」——电视盒上没法连电脑看 logcat 时的唯一线索 |
| 开机自启 | 可设置开机自动运行 |
| 界面调整 | 密度缩放、字体缩放、显示比例（全局默认 + 单频道单独记忆）、时间显示模式、焦点优化 |
| 频道收藏 | 收藏跨订阅源保留（保存频道快照），换源后依然能播 |
| 记住上次频道 | 打开应用接着播上次的频道，可关闭 |

---

## 使用

### 遥控器操作

- 换台：上下方向键 / 数字键 / 屏幕上下滑动
- 选台界面：OK 键 / 单击屏幕
- 设置界面：菜单键、帮助键、长按 OK 键 / 双击、长按屏幕
- 切换线路：左右方向键 / 屏幕左右滑动
- 收藏频道：在选台界面长按 OK 键

### 触摸操作对应

| 遥控器 | 触摸 |
|---|---|
| 方向键 | 屏幕上/下/左/右滑动 |
| OK 键 | 点击屏幕 |
| 长按 OK 键 | 长按屏幕 |
| 菜单 / 帮助键 | 双击屏幕 |

### 自定义设置

浏览器打开 `http://<设备IP>:10481`，或在应用里进「设置」页。可配置订阅源、节目单源、缓存时间、界面、播放器、推送等。

### 订阅源与节目单格式

- 订阅源：m3u、tvbox 格式
- 节目单：`.xml`、`.xml.gz`（XMLTV 格式）

---

## 下载与安装

- 到 [Releases](https://github.com/nid12345/mytv-android/releases) 下载最新的 `MyTV-x.y.apk`
- 与官方版**不同包名**，可以同时装；本分支各版本之间**同一签名**，可直接覆盖升级
- 装好后应用内「设置 → 更新 → 检查更新」即可拿到后续版本

---

## 常见问题

**节目单一片空白 / 提示「所有节目单来源都不可用」**
节目单接口的可用性很不稳定（本分支内置的某个老牌源就曾经整体失效、跳转到一个停放页返回 HTML）。
设置 → 节目单里换一个源试试；错误消息里会写明每个源各自的失败原因。本分支已经做了「主源失效自动改用备用源」，
正常情况下不用手动干预。

**手机推送了 APK，但装不上**
先确认系统放行了「安装未知应用」（本应用在 Android 8+ 上需要这个权限）。
本分支的安装走的是应用商店同款路径（PackageInstaller 会话），并会把**每一级的失败原因**显示出来，
可以把那段原文反馈过来。

**默认直播源卡顿**
默认源是自建的第三方转发集合，卡不卡主要取决于源本身。设置 → 直播源 → 「线路测速排序」会按
实测下行速率把最好的线路排到前面；也可以直接在「换源」里切到斗鱼 / 虎牙这类大厂 CDN 的源。

**下载更新很慢或失败**
国内直连 GitHub 不稳。设置 → 更新 → 「下载线路」切成**镜像优先**即可。

---

## 版本与更新日志

当前版本 **1.8**（`custom-v1.8`）。各版本要点：

| 版本 | 要点 |
|---|---|
| 1.8 | 节目单主源失效自动切备用源；重做 APK 推送安装（会话安装 + 收到安装包弹确认框） |
| 1.7 | 修复选台菜单快速下滑闪退；新增点播自动连播；打通定制版升级通道；新增崩溃记录 |
| 1.6 | 线路测速改两阶段（读完整清单 + 实测下行速率），解决默认源打开就卡 |
| 1.5 | 修安装调起失败；订阅源改完立即生效；显示比例改「全局默认 + 单频道记忆」 |
| 1.4 | 安装包管理；显示比例按源记忆；记住上次频道；分组显隐按源记忆；系统应用入口；潮汕节目回放 |
| 1.3 | 修打开后反而播不了（测速抢带宽）；内置斗鱼/虎牙/YY；跨源收藏；默认三段式与 16:9 |
| 1.2 | 修节目表加载不出来；多线路按播放效果排序；新增「换源」入口 |
| 1.1 | 修节目单匹配与台标不显示；新增 DLNA 投屏 |

完整说明（含**动因、根因分析与验证情况**）见 [CHANGELOG-CUSTOM.md](./CHANGELOG-CUSTOM.md)。

上游原项目的更新日志见 [CHANGELOG.md](./CHANGELOG.md)。

---

## 声明

- 本项目是个人为了兴趣而做的定制分支，**仅供个人学习与测试**，请勿用于任何商业用途。
- 所用的直播源、节目单地址、台标等资源**均来自公开网络**，不提供任何破解内容，也不对它们的可用性、合法性作任何保证。
- 默认直播源为**自建转发**，随时可能变更或下线。
- 请自行确认所使用的内容来源合法合规。

---

## 致谢

**这个项目能存在，全靠下面这些人和项目。**

### 上游项目与本分支的基础

- [**yaoxieyoulei/mytv-android**](https://github.com/yaoxieyoulei/mytv-android) —— **本项目的基础**。
  界面设计、排版、架构与全部基础功能都来自原作者 [@yaoxieyoulei](https://github.com/yaoxieyoulei)，
  本仓库只是在其之上做自用调整。
- [yaoxieyoulei/my_tv](https://github.com/yaoxieyoulei/my_tv) —— 上游作者更早的 Flutter 版本
- [lizongying/my-tv](https://github.com/lizongying/my-tv) —— 上游的灵感来源
- [my-tv 参考设计稿](https://github.com/lizongying/my-tv/issues/594)

### 数据与资源

| 用途 | 来源 |
|---|---|
| 台标 | [fanmingming/live](https://github.com/fanmingming/live) 的 `tv/` 目录；国内镜像 [sujivin/live](https://gitee.com/sujivin/live)；本项目自建镜像 [nid12345/mytv-logos](https://github.com/nid12345/mytv-logos) |
| 节目单 | [老张的 EPG](http://epg.51zmt.top:8000/)（长期作为默认源，2026-09 已失效）、[112114](https://epg.112114.xyz/) 及其 CDN 转发、[Fanmingming](https://live.fanmingming.com/)、[EPG.pw](https://epg.pw/) |
| IPV6 订阅源 | [zhumeng11/IPTV](https://github.com/zhumeng11/IPTV) |
| 斗鱼 / 虎牙 / YY 订阅 | [sub.ottiptv.cc](https://sub.ottiptv.cc/) |
| 潮汕节目回放 | [gitee nid123/chaoshan-tv](https://gitee.com/nid123/chaoshan-tv)（自建整理） |
| 默认直播源 | 自建（`tvlive.nide.qzz.io`），聚合自公开网络 |

### 开源库（技术栈）

- [AndroidX](https://developer.android.com/jetpack/androidx) / [Jetpack Compose](https://developer.android.com/jetpack/compose) —— UI
- [androidx.tv:tv-material](https://developer.android.com/jetpack/androidx/releases/tv) —— TV 端 Material Design（三段式选台等界面全基于它）
- [AndroidX Media3 (ExoPlayer)](https://github.com/androidx/media) —— 播放内核
- [OkHttp](https://github.com/square/okhttp) —— 网络请求
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) —— 配置与缓存序列化
- [Coil](https://github.com/coil-kt/coil) —— 台标图片加载
- [Qrose](https://github.com/alexzhirkevich/qrose) —— 扫码推送的二维码
- [AndroidAsync](https://github.com/koush/AndroidAsync) —— 内置的推送 HTTP 服务
- [kotlinx-collections-immutable](https://github.com/Kotlin/kotlinx.collections.immutable)

### 其它

- 上游原项目的 Telegram 交流群：<https://t.me/mytv_android>

> 若上述任一来源的作者认为本仓库的引用方式不妥，请提 Issue，我会立刻调整或移除。
