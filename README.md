# RemoteGPT

[English](README.en.md) · 简体中文

在 Android 手机上通过图形界面操作远端 Codex。Codex 运行和账户登录保留在服务器，手机通过 SSH 连接。

**[下载 APK · 1.0.65](https://github.com/qzh775226913-ai/RemoteGPT/releases/download/v1.0.65/RemoteGPT-1.0.65-Android-universal-debug.apk)** · [更新记录](CHANGELOG.md) · [问题反馈](https://github.com/qzh775226913-ai/RemoteGPT/issues)

当前版本：**1.0.65 / versionCode 67**。手机需要 Android 8+。

## 界面

<img src="docs/screenshots/table-conversation.png" alt="公式与表格合成示例" width="250"> <img src="docs/screenshots/device-multi-task.jpg" alt="实机多任务通知示例" width="250">

左：公式与表格合成示例。右：实机多任务通知，展示形式随设备而异。

## 功能

- 浏览项目与历史，多会话保留独立草稿、附件和状态。
- 执行中补充输入或排队，处理审批与结构化提问。
- 选择远端模型、推理强度、速度和权限。
- 上传下载文件，预览图片并连续翻页。
- 显示公式与 Markdown 表格，复制保留原文。
- 持久模式支持断线后的远端任务保留与重连恢复。
- 前台服务维持连接，并用独立通知跟踪各活跃会话；具体表现由手机系统决定。

## 适用环境

| 设备 | 支持情况 |
| --- | --- |
| Android 手机 | Android 8 或以上 |
| Linux 服务器 | 主要支持和推荐的远端环境 |
| macOS 主机 | 满足下列 Unix 环境要求时可用；尚未做真实 macOS 主机自动化验证 |
| 原生 Windows 主机 | 暂不支持；可改用能由手机通过 SSH/SFTP 访问的 WSL 或 Linux 虚拟机 |

远端需要 SSH 命令执行、SFTP、POSIX `sh`、可写家目录和项目目录，以及支持 `app-server` 的 Codex CLI。同一 SSH 用户须已完成 Codex 登录。默认持久模式还需要 Python 3.8+，并支持 SQLite、`fcntl` 和 Unix socket；不能安装 Python 时可关闭持久模式，但不保证断线续跑和完整恢复。

手机只需能访问服务器的 SSH 端口，可使用局域网、公网或 Tailscale；不需要开放额外的模型服务端口。

## 使用

1. 用准备登录的 SSH 用户安装并登录 Codex，确认 `codex --version` 和 `codex app-server --help` 可运行。
2. 确认该用户可通过 SSH 执行命令、使用 SFTP，并能读写家目录和项目目录。
3. 安装 APK，添加 SSH 连接并通过可信渠道核对主机指纹。
4. 选择项目，新建或接续对话，然后发送消息。

若非交互 SSH 找不到 Codex，在连接配置中把启动命令改为 Codex 的绝对路径。完整的环境检查、字段说明和排错见[使用说明](docs/USAGE.md)。

## 许可

[源码可用许可](LICENSE)：允许使用、修改与公开协作；非商业分发须保留来源和许可，商业再利用需书面授权。独立客户端，与 OpenAI 无隶属关系。

[贡献](CONTRIBUTING.md) · [第三方许可](docs/THIRD_PARTY.md)
