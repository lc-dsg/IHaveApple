# IHaveApple for Android

原生 Android 复刻版：Kotlin + Jetpack Compose + libmpv，界面与交互对照 `prototype/qml` 的桌面版实现。
内容来自红果短剧匿名接口（经由 `openhongguo` SDK），仅供个人学习使用。

## 构建

- JDK 21、Android SDK 36（compileSdk / targetSdk 36，minSdk 28）
- 只产出 `arm64-v8a` 与 `x86_64`（模拟器）两个 ABI

```bash
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 目录

- `app/src/main/java/com/ihaveapple/app/data`：内容仓库（信息流 / 搜索 / 分集 / 播放模型 / 弹幕）、本地偏好与进度存储
- `app/src/main/java/com/ihaveapple/app/player`：`MpvController`（TextureView + libmpv）、`PlayerViewModel`（播放状态机）
- `app/src/main/java/com/ihaveapple/app/ui`：播放页、选集面板、资料库（搜索 / 历史 / 收藏）、设置页

## 关于 app/libs/openhongguo-patched.aar

Maven 上 `io.github.beilunyang:openhongguo:0.4.0`（2026-11-18）的 Go core 走的是旧的播放接口：
x-medusa 仍可解析时服务端返回正常结果。2026-11-23 起上游只允许「不可解析」的 x-medusa，
旧接口返回 200 空响应，表现为 `anonymous response was empty`、无法起播。

因此这里按 SDK 源码重建了 Go core（gomobile + Go 1.26 + NDK r28c，使用新的
`/novel/player/multi_video_model/v1/` 路径与降级 x-medusa），再把新 core 合进 0.4.0 的
Kotlin wrapper，得到 `app/libs/openhongguo-patched.aar`。`app/build.gradle.kts` 通过
`implementation(files("libs/openhongguo-patched.aar"))` 引用它。

上游发布修复版后，可以删除该 AAR、改回 Maven 依赖：

```kotlin
implementation("io.github.beilunyang:openhongguo:<new-version>")
```