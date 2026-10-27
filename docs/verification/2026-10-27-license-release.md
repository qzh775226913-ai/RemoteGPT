# 2026-10-27 许可澄清与 1.0.64 公开发布

恢复基线：`0f8cef3`。保留包名、原签名、数据格式与依赖。

## 变更

- 自定义许可 1.1 明确源码可用定位、免费修复／补丁／PR／修改版分发及商业边界；旧许可不追溯替换。
- 新贡献须明确确认额外再许可授权，保留贡献者版权；不追溯授权历史贡献。
- 从固定版本 JAR 提取 JSch／JZlib／jBCrypt 与 Bouncy Castle 许可，补充传递依赖 AndroidX Annotation 的 Apache-2.0 声明，随源码、APK 与 ZIP 分发。
- 统一中英文版本入口，补充更新记录、下载说明与表格合成界面及用户提供的实机通知图；其他附件含个人主机／路径，未公开。

许可阶段 `288f63f`，分发校验阶段 `af0006c`，独立提交。

## 验证

最终源码与材料运行 `.venv/bin/python tools/verify.py --offline`：

- Android 429 项，0 失败／错误／跳过；Python 50 项通过，含分发材料回归 6 项。
- 文档／模块／预算、生成图标与公式字形检查、APK 构建通过；full_acceptance=true。
- Lint 0 Error/Fatal，20 项既有 Warning，无新增；沿用既有 compileSdk 提示。Gradle 3 分 11 秒。
- aapt：app.remotessh，1.0.64／66；apksigner 通过，与 1.0.63 同证书。
- 签名证书 SHA-256：`43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a`。
- APK 4,390,215 字节，SHA-256：`1d7f02f99d10215ab6bc0916f7f76e96923a61afae0d94fe85b16650a41b0818`。
- ZIP 6,937,433 字节，SHA-256：`8ce74b28c57fc778c8460c037c6d07ddd4785366fcc5c999eaa4a99d6d94d91f`。
- APK／ZIP 的项目许可、贡献规则、组件与字体声明逐字比对通过；ZIP 内 APK 与独立 APK 一致。
- 公式库完整源码沿用已验证的 0.2.0 归档；源码 JAR 与本轮 Maven Central 下载摘要一致。
- `git diff --check`、项目检查通过。首轮完整验收后补齐传递依赖声明，以上数据来自最终复验。

## GitHub 发布

源码提交 `1a7dda0`；[完整 CI](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/40434210917) 与 [发布任务](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/40434210899) 均成功。
初始 main／版本标签两次 CI 在依赖解析阶段遇到阿里云 Maven HTTP 502，未执行 Android 测试；同一源码的发布触发 CI 完整通过，未放宽门禁。

[v1.0.64](https://github.com/qzh775226913-ai/RemoteGPT/releases/tag/v1.0.64) 已公开并设为 latest，五项附件的服务端大小与摘要和本地一致。
从 GitHub 完整下载 APK／ZIP，SHA-256 与上述值一致；重新检查随包材料通过。
临时诊断只读取合成验证日志，未读取生产凭据或调用模型；诊断／发布触发标签在交付后清理，临时制品中转按服务期限自动失效。

工程检查不构成法律效力认定。真机覆盖安装、OEM 通知、实际网络性能及真实模型未验收。
