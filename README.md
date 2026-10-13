# RemoteGPT for Android

通过 SSH 在 Android 手机上使用远端 Codex 的原生客户端。以图形界面管理项目、对话、权限审批和附件，适合已经能通过 SSH 使用 Codex、希望在手机上继续工作的用户。

这是独立项目。模型推理与 Codex 登录发生在远端；手机负责交互和文件传输。应用通过 SSH 启动 `codex app-server`，无需另行手动部署 Web 服务。

当前版本：**1.0.51 / versionCode 52**。最低 Android 8（API 26），包名 `app.remotessh`。

## 主要能力

- 管理多个 SSH 连接，浏览项目与历史对话；同一连接中可运行多个会话。
- 展示执行过程、权限审批和结构化提问，按远端能力选择模型与推理设置。
- 发送图片与普通文件，预览图片、复制消息和保存附件。
- 默认启用远端持久会话，SSH 断线后托管进程可继续运行，重连恢复输出与待处理请求。
- 首次连接检查远端环境，区分认证、Python、Codex、SFTP 和登录问题。

## 开始使用

| 位置 | 要求 |
| --- | --- |
| 手机 | Android 8 或以上；能访问服务器的 SSH 地址 |
| 服务器 | Unix 环境、SSH 与 SFTP；同一 SSH 用户已安装并配置可用的 Codex CLI，支持 `app-server` |
| 默认持久模式 | 非交互 SSH 环境能运行 `python3`，版本 ≥ 3.8，具备 `sqlite3`、`fcntl` 和 Unix socket；家目录可写 |

1. 在远端用将要连接的 SSH 用户安装、登录并确认 Codex 可用。
2. 安装 APK，在应用中填写主机、端口、用户名及 SSH 认证信息。
3. 启动命令填写 `codex app-server`；找不到程序时改为 Codex 的绝对路径加 `app-server`。
4. 核对首次连接的 SSH 主机指纹，选择远端项目目录，发送消息。

完整步骤、配置字段和常见问题见 **[使用说明](docs/USAGE.md)**。Codex 安装与账户配置见 [官方 CLI 文档](https://learn.chatgpt.com/docs/codex/cli)。

## 使用边界

- 应用沿用远端 Codex 的账户、模型服务和计费规则；连接诊断通过不代表模型服务网络或额度一定可用。
- 没有 Python 时，可明确关闭持久模式后连接；断线续跑不保证，版本维护和部分问答恢复不可用。
- 持久模式不能保证主机重启、托管进程崩溃或手机系统强杀后的无损恢复。
- 当前提供 Android 客户端。macOS 远端、真实 Codex 服务、Tailscale SSH 和厂商后台／通知表现仍需按实际环境验收。

## 构建与验证

技术栈：原生 Android Java 17、JSch SSH/SFTP、stdio JSON-RPC；远端持久托管使用 Python 标准库。

开发需要 JDK 17、Android SDK 36／Build Tools 35.0.0、Python 3.12。配置 `JAVA_HOME` 和 `ANDROID_HOME` 后：

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements-dev.txt
.venv/bin/python tools/verify.py
```

完整验证运行合成 SSH 夹具、Python／Android 测试、结构与图标检查、APK 构建和 Lint，不连接生产 SSH 或调用真实模型。APK 位于 `app/build/outputs/apk/debug/app-debug.apk`；自行构建的签名可能与作者提供的安装包不同，覆盖安装前应核对。仅需构建时运行 `./gradlew :app:assembleDebug`。

1.0.51 的本地完整验收通过；结果与未验证范围见 [交付记录](docs/verification/2026-10-13-1.0.51.md)。GitHub Actions 使用同一验证入口，远端运行结果以仓库 Actions 页面为准。

## 项目文档

- [使用说明](docs/USAGE.md)：服务器准备、手机配置、权限与故障排查。
- [开发手册](docs/DEVELOPMENT.md)：工具链、测试入口、CI 和构建产物。
- [需求](docs/REQUIREMENTS.md)、[架构](docs/ARCHITECTURE.md)、[验收边界](docs/ACCEPTANCE.md)：当前行为和证据范围。
- [维护规则](AGENTS.md)、[技术债](docs/MAINTENANCE.md)、[回退说明](docs/ROLLBACK.md)：贡献与维护约束。

协议依据：[Codex App Server](https://learn.chatgpt.com/docs/app-server)。历史归档仅作追溯，当前需求文档优先。
