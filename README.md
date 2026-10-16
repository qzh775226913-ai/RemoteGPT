# RemoteGPT for Android

通过 SSH 在 Android 手机上使用远端 Codex 的原生客户端。以图形界面管理项目、对话、权限审批和附件，适合已经能通过 SSH 使用 Codex、希望在手机上继续工作的用户。

这是独立项目。模型推理与 Codex 登录发生在远端；手机负责交互和文件传输。应用通过 SSH 启动 `codex app-server`，无需另行手动部署 Web 服务。

**小而美的手机端工作入口**：APK 约 **3.8 MB（不到 4 MB）**，原生界面、SSH 直连，轻量便捷。

当前版本：**1.0.54 / versionCode 55**。最低 Android 8（API 26），包名 `app.remotessh`。

**[下载 APK](https://github.com/qzh775226913-ai/RemoteGPT/releases/download/v1.0.54/RemoteGPT-1.0.54-Android-universal-debug.apk)** · [发布说明与附件](https://github.com/qzh775226913-ai/RemoteGPT/releases/latest)

## 使用许可

本项目源码公开，采用 **[免费使用与商业再利用许可](LICENSE)**。个人及组织可免费正常使用（包括工作和商业项目），并修改、编译供自己或组织内部使用；可免费分享保留版权、来源和许可的未修改版本。

**换皮或修改版发布、闭源产品交付，以及利用 App 或其代码提供收费、订阅、广告等商业产品或服务，须事先取得书面授权。** 分成或费用另行约定；权利人保留依法追究的权利。仅使用 App 完成自己的工作，不要求对独立工作成果支付分成。

站内查看／fork 按 GitHub 条款处理；第三方材料遵守各自许可证。完整条件以 [LICENSE](LICENSE) 为准。

## 主要能力

- **轻量原生**：不内嵌模型，安装包小；统一图标、深色界面、状态卡片和原生表单，适合手机操作。安装后的占用会随系统展开与缓存变化。
- **直连便捷**：SSH 直接连接自己的服务器，无需额外 Web 中转或部署一套管理后台；配合断线恢复机制继续工作，实际速度与稳定性取决于网络和远端环境。

- **流体云／实时通知与会话气泡**：显示思考、执行、待审批和完成状态；通知可处理权限请求或返回对应会话。流体云是否展示由系统与厂商决定，需相应通知授权。
- **交互式审批和提问**：命令／文件权限弹窗、方案选择与补充信息表单；频繁权限审批时可明确确认临时“强制允许”，普通提问仍手动回答。
- **断线后远端继续运行**：默认 Python 持久托管，SSH 断线后任务可继续；依赖远端主机与托管进程存活。
- **自动重连与活动会话恢复**：连接恢复后补回可恢复会话的输出和待处理请求；仅浏览历史不会自动接续或发送消息。
- **多会话并行**：管理多个主机和项目，同一连接中运行多个会话，各自保留草稿、附件与权限选择。
- **图片、文件与模型设置**：上传和预览附件，按远端能力选择模型、推理强度和速度，查看执行过程与账户额度。
- **首次连接诊断**：区分 SSH 认证、Python、Codex、SFTP 和登录问题，提供对应处理说明。

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

1.0.54 已通过[本地完整验收](docs/verification/2026-10-16-1.0.54.md)。前一安装包交付见 [1.0.53 tailnet 验证](docs/verification/2026-10-15-1.0.53.md)。1.0.51 的本地完整验收和 [GitHub CI 验收](docs/verification/2026-10-13-github-ci.md)均已通过；安装包与未验证范围见 [交付记录](docs/verification/2026-10-13-1.0.51.md)。GitHub Actions 使用同一验证入口，后续提交的结果以仓库 Actions 页面为准。

## 项目文档

- [项目介绍与试用反馈](docs/PROMOTION.md)：可分享的介绍、准备条件和反馈入口。
- [使用说明](docs/USAGE.md)：服务器准备、手机配置、权限与故障排查。
- [开发手册](docs/DEVELOPMENT.md)：工具链、测试入口、CI 和构建产物。
- [需求](docs/REQUIREMENTS.md)、[架构](docs/ARCHITECTURE.md)、[验收边界](docs/ACCEPTANCE.md)：当前行为和证据范围。
- [维护规则](AGENTS.md)、[技术债](docs/MAINTENANCE.md)、[回退说明](docs/ROLLBACK.md)：贡献与维护约束。

协议依据：[Codex App Server](https://learn.chatgpt.com/docs/app-server)。历史归档仅作追溯，当前需求文档优先。

## 真机展示

以下为用户提供的真机截图；流体云样式与模型目录取决于系统和远端配置。为减少个人环境信息展示，未展示带有主机、目录或任务内容的截图。截图不代表所有设备或功能均已完成真机验收。

| 流体云状态 | 模型与推理设置 |
| --- | --- |
| <img src="https://raw.githubusercontent.com/qzh775226913-ai/RemoteGPT/main/docs/screenshots/live-status.jpg" alt="流体云显示执行中" width="340"> | <img src="https://raw.githubusercontent.com/qzh775226913-ai/RemoteGPT/main/docs/screenshots/model-settings.jpg" alt="远端模型与推理设置" width="300"> |
