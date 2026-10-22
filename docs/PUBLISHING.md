# senjucao 发布指南

新版名称：React Native Workbench
版本：1.0.4
作者：senjucao
插件 ID：dev.senjucao.reactnativeworkbench

采用新 ID，作为独立插件上架；不会自动覆盖原 React Native Companion。不要同时运行两个插件的 Metro 会话。

## 当前构建与完整性校验

使用 JDK 21 或更新版本，并安装 Node.js。执行 `./gradlew test buildPlugin verifyPlugin`。`test` 会同时执行 Node 安装传输回归测试；`buildPlugin` 会自动执行 `verifyPluginArchive`，验证 ZIP 和内层 JAR 的 CRC、路径、目录结构、插件 ID 与版本，生成 `build/reports/pluginArchive/integrity.txt` 和 SHA-256。校验失败会阻止后续验证和发布。

本地编译平台使用 `-PlocalIdePath="D:/Program Files/Android/Android Studio"`。要保留编译平台并复验另一版 IDE，另加 `-PverificationIdePath="<解压后的 IDE 目录>"`。例如本次复验使用 IDEA `263.6800.24`。

指定设备安装已改用 `adb install --no-streaming`，构建、Metro 与 Activity 启动仍由项目本地 CLI 处理。设备已断开或未授权时会提前失败，避免 CLI 返回 0 导致误报成功。真机测试通过 `WORKBENCH_ANDROID_SMOKE_PROJECT` 和 `WORKBENCH_ANDROID_SMOKE_DEVICE` 显式启用。

## 1.0.3 的 Marketplace 解压告警（2026-10-22）

已下载更新 `1171904` 在 Marketplace 实际提供的 ZIP，外层 ZIP 和内层 JAR 的 CRC 均正常。Plugin Verifier `1.411` 对同一个 ZIP 验证本机 Android Studio 和邮件所指的 IDEA `IU-263.6800.24`，均返回 `Compatible`。详见 [验证记录](verification-2026-10-22.md)。

这是验证端误报的有力证据，不应通过改包名、去除 263 兼容范围或重压缩已签名的旧包处理。发布者可在原更新的验证页面重新运行兼容性验证；若仍报相同错误，将验证记录与 SHA-256 提交给 JetBrains Marketplace 支持。本工作区未上传插件、撤下旧版本或修改服务端验证状态。

## 历史验证（2026-09-10）

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
6. 在 Marketplace 手动上传 build/distributions/react-native-workbench-1.0.4.zip，完成分类、说明和审核。此工作区未执行发布。

后续自动发布使用 JETBRAINS_MARKETPLACE_TOKEN；签名使用 JETBRAINS_CERTIFICATE_CHAIN、JETBRAINS_PRIVATE_KEY、JETBRAINS_PRIVATE_KEY_PASSWORD 环境变量。不要提交凭据。

LICENSE 和 NOTICE 随插件包保留；新版署名为 senjucao，原代码来源不删除。

官方说明：https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html
