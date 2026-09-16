# senjucao 发布指南

新版名称：React Native Workbench
版本：1.0.3
作者：senjucao
插件 ID：dev.senjucao.reactnativeworkbench

采用新 ID，作为独立插件上架；不会自动覆盖原 React Native Companion。不要同时运行两个插件的 Metro 会话。

## 本次验证（2026-09-10）

2026-09-11 图标工具栏更新：27 项测试通过、0 失败，1 项符号链接测试因 Windows 权限限制跳过。包含实际 Swing 组件的窄窗口折叠、更多按钮边界、图标无障碍名称和扫描期间禁用测试。新版 buildPlugin 和本地 Android Studio Plugin Verifier 均通过。未取得真实 IDE 截图，浅色/深色外观、菜单键盘交互和设备端到端运行仍待人工验收。

- JDK 21、本地 Android Studio AI-261.26222.65.2613.16025427 编译和 buildPlugin 成功。
- 17 项单元测试通过，0 失败。
- Plugin Verifier 1.410 对上述 Android Studio 返回 Compatible，无实验性 API 告警。
- 已核对安装包中的 ID、作者、版本，以及 META-INF/LICENSE、META-INF/NOTICE。
- 未进行真实 IDE 界面操作和 React Native Android/iOS 端到端运行；截图仍待更新。
- 项目配置检查提示 since-build=243 低于本地编译平台 261。发布前必须验证旧版兼容性，或根据验证结果收紧版本范围。

本地校验命令：`./gradlew test buildPlugin verifyPlugin -PlocalIdePath="D:/Program Files/Android/Android Studio" --offline`。不带 localIdePath 时使用下载的默认平台，并验证推荐 IDE。

## 发布前

1. 执行 test、buildPlugin 与 verifyPlugin；兼容性校验必须覆盖声明的最低版本 2024.3 和目标 IDE。仅使用本地新版 Android Studio 编译不代表已验证全部版本。
2. 在 IDE 中从磁盘安装 ZIP，验证浅色/深色、小窗口、无设备、错误目录、停止/重启、关闭项目、Metro 与 Android 同时运行。
3. 在 macOS 真机验证 iOS 模拟器、实体设备及 release 构建。
4. 用新版真实 IDE 界面截图替换原有 docs/screenshots/screenshot_1.png；原截图属于旧界面，不用于新版 listing。
5. 在个人账号创建/选择 senjucao 发布者，填写真实支持邮箱、仓库地址和隐私政策链接。这些地址尚未提供，代码未虚构。
6. 首次在 Marketplace 手动上传 build/distributions/react-native-workbench-1.0.3.zip，完成分类、说明和审核。此工作区未执行发布。

后续自动发布使用 JETBRAINS_MARKETPLACE_TOKEN；签名使用 JETBRAINS_CERTIFICATE_CHAIN、JETBRAINS_PRIVATE_KEY、JETBRAINS_PRIVATE_KEY_PASSWORD 环境变量。不要提交凭据。

LICENSE 和 NOTICE 随插件包保留；新版署名为 senjucao，原代码来源不删除。

官方说明：https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html
