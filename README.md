# RemoteGPT for Android

通过 SSH 操作远端 Codex 的 Android 原生客户端。支持项目与会话管理、任务执行、权限审批、结构化提问及附件传输。模型运行和账户登录均由远端 Codex 处理。

当前版本：**1.0.54 / versionCode 55**。Android 8+，包名 `app.remotessh`，APK 约 3.8 MB。

[下载 APK](https://github.com/qzh775226913-ai/RemoteGPT/releases/download/v1.0.54/RemoteGPT-1.0.54-Android-universal-debug.apk) · [发布说明与校验文件](https://github.com/qzh775226913-ai/RemoteGPT/releases/latest)

## 功能

- 多连接与多会话管理，支持独立草稿、附件、权限和任务状态。
- 远端持久会话，支持断线重连、事件补发和待审批恢复。
- 命令及文件审批、结构化问题表单、会话通知和气泡。
- 图片预览与保存、普通文件上传和下载。
- 远端模型、推理强度及速度选择；连接期间每 30 秒同步模型信息。
- SSH 主机指纹校验、连接环境诊断和公开账户额度显示。

## 环境要求

| 位置 | 要求 |
| --- | --- |
| 手机 | Android 8 或以上，可访问服务器的 SSH 地址 |
| 服务器 | Unix 环境、SSH/SFTP；连接用户已安装并配置支持 `app-server` 的 Codex CLI |
| 持久模式 | Python 3.8+，标准库包含 sqlite3、fcntl、Unix socket 支持，家目录可写 |

在应用中填写 SSH 连接信息，启动命令默认为 `codex app-server`。首次连接需核对主机指纹。配置步骤见 [使用说明](docs/USAGE.md)。

## 支持范围

持久会话依赖远端进程存活。通知、后台连接和图片解码受 Android 系统及设备能力影响。账户权限、模型可用性和服务费用由远端配置决定。验证结果及设备、服务测试范围见 [验收标准](docs/ACCEPTANCE.md)。

## 构建与验证

技术栈：Java 17、Android 原生 View、JSch SSH/SFTP、stdio JSON-RPC；远端托管使用 Python 标准库。

开发环境：JDK 17、Android SDK 36、Build Tools 35.0.0、Python 3.12。配置 `JAVA_HOME` 和 `ANDROID_HOME` 后执行：

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements-dev.txt
.venv/bin/python tools/verify.py
```

完整入口运行 Python 与 Android 测试、结构和图标检查、APK 构建及 Lint。SSH 测试使用独立回环夹具。详细配置见 [开发手册](docs/DEVELOPMENT.md)，版本结果见 [1.0.54 验证](docs/verification/2026-10-16-1.0.54.md)。

## 文档与许可

[使用说明](docs/USAGE.md) · [需求](docs/REQUIREMENTS.md) · [架构](docs/ARCHITECTURE.md) · [维护规则](docs/MAINTENANCE.md) · [回退说明](docs/ROLLBACK.md) · [问题反馈](https://github.com/qzh775226913-ai/RemoteGPT/issues)

采用 [项目自定义许可](LICENSE)。正常使用免费；商业再利用及修改版发布的授权条件以 LICENSE 为准。Codex 服务费用另计。

## 界面示例

截图展示特定设备及远端配置下的界面。

| 任务状态通知 | 模型与推理设置 |
| --- | --- |
| <img src="https://raw.githubusercontent.com/qzh775226913-ai/RemoteGPT/main/docs/screenshots/live-status.jpg" alt="任务状态通知" width="340"> | <img src="https://raw.githubusercontent.com/qzh775226913-ai/RemoteGPT/main/docs/screenshots/model-settings.jpg" alt="模型与推理设置" width="300"> |
