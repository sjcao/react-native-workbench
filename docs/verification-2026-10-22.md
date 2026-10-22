# React Native Workbench 验证记录（2026-10-22）

## Marketplace 1.0.3：无法解压

- 原更新：[1171904](https://plugins.jetbrains.com/plugin/34209-react-native-workbench/update/1171904#verification-results)。
- 验证用文件：从 `https://plugins.jetbrains.com/files/34209/1171904/react-native-workbench-1.0.3.zip` 下载的真实公开安装包，大小 89,544 字节。
- SHA-256：`75136195855e9adb5cd5b50e2d9002256041bdc208c8f5e7641538b49248f3c8`。
- 标准 DEFLATE ZIP，UTF-8 文件名，一个根目录，主 JAR 位于 `react-native-workbench/lib/`。外层 ZIP 和内层 JAR 均完成全量 CRC 检查，无重复项。
- 官方 Plugin Verifier `1.411`：Android Studio `AI-261.26222.65.2614.16379836` 返回 `Compatible`。
- 同一 ZIP、同一验证器，对邮件指定的 IDEA `IU-263.6800.24` 返回 `Compatible`。
- IDEA 从 JetBrains 官方下载，下载文件 SHA-256 已与官方公布值核对：`246e36808bd04477f33d9bd15f24e472641da0543524997df0230b2322895878`。

本机报告位于：

```text
build/reports/marketplace-1.0.3-verifier/AI-261.26222.65.2614.16379836/
build/reports/marketplace-263-verifier/IU-263.6800.24/
```

可复验命令（使用本机下载的官方 verifier JAR）：

```text
java -jar verifier-cli-1.411-all.jar check-plugin build/archive-investigation/marketplace-1.0.3.zip build/archive-investigation/idea-263.6800.24 -verification-reports-dir build/reports/marketplace-263-verifier
```

结论：原安装包在本地可完整解压，并通过目标 IDE 验证，未发现可以通过插件源码修复的压缩包损坏。更可能是 Marketplace 验证服务的临时故障、缓存或环境问题；无法仅凭通用报错确定其内部原因。应重新运行原更新的服务端验证，必要时向 JetBrains 提交上述证据。

JetBrains 对相同错误曾确认过验证服务故障：

- [官方社区说明：The plugin verifier cannot extract plugin achieve](https://platform.jetbrains.com/t/the-plugin-verifier-cannot-extract-plugin-achieve/4116)。
- [官方错误说明：UnableToExtractZip](https://plugins.jetbrains.com/docs/marketplace/list-of-common-errors-and-warnings.html)。

## 指定设备的 adb 流式安装

在本机 SDK Platform Tools `36.0.0-13206524` 与华为 LIO-AN00 上，项目 CLI 的直接 `adb install -r -d` 出现 `Performing Streamed Install` 后返回空错误，部分重试还伴随设备离线或 adb 查询无响应。同一个约 224 MB 的 APK 使用 `adb install --no-streaming -r -d` 可以成功。此现象定位到了安装传输路径，不能仅凭这些日志归因到特定驱动或手机系统组件。

修复：插件使用自己的 Node 桥接程序调用项目原有 CLI，仅给指定设备的 adb install 添加 `--no-streaming`，保留 serial、已有安装参数、构建、Metro 和 Activity 启动。桥接仅在本次 CLI 进程内生效，不改项目配置、SDK 文件或全局环境。启动前检查选中设备的可用状态，不把设备缺失误报为成功。

已通过插件真实执行链验证：明确指定华为设备后，日志显示 `Performing Push Install`、`Success`，前台 Activity 对应包名为 `com.guoen.robotapp.dev`。

随后切换到 OPPO PHB110，明确指定该设备再次运行完整测试；在手机上允许系统安装确认后，安装和启动均成功，前台同样为 `com.guoen.robotapp.dev`。当前完整测试结果为 Kotlin/JUnit 50 项通过、1 项符号链接测试跳过，Node 6 项通过，0 失败。

## 发布构建检查

新增 `verifyPluginArchive`，自动接入构建、验证与发布。检查 ZIP/JAR CRC、路径、目录结构、插件 ID 和版本，并记录 SHA-256。人为损坏压缩数据和错误目录结构两个负向样本均被拒绝。构建使用固定文件顺序、时间戳、UTF-8 元数据和普通 ZIP 格式。

`test` 同时执行 Kotlin/JUnit 测试和 Node 安装传输测试。真机测试需显式设置项目和序列号；安装确认、USB 调试授权仍由手机系统处理。

当前 1.0.4 构建的 SHA-256 为 `823807f05c14b70114ca20a28701bdc1ecc5e77d290efe65e5f914601c7be81c`。详细完整性报告：`build/reports/pluginArchive/integrity.txt`。

同一 1.0.4 安装包已通过 Gradle `verifyPlugin` 对 IDEA `IU-263.6800.24` 的验证，结果 `Compatible`，任务 `BUILD SUCCESSFUL`。报告位于 `build/reports/pluginVerifier/IU-263.6800.24/`。服务端旧告警尚需发布者在 Marketplace 重新运行验证；本地通过结果不会自动覆盖服务端记录。
