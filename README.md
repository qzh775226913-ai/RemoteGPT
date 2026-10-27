# RemoteGPT

[English](README.en.md) · 简体中文

[![Verify](https://github.com/qzh775226913-ai/RemoteGPT/actions/workflows/verify.yml/badge.svg)](https://github.com/qzh775226913-ai/RemoteGPT/actions/workflows/verify.yml)
[![Release](https://img.shields.io/github/v/release/qzh775226913-ai/RemoteGPT)](https://github.com/qzh775226913-ai/RemoteGPT/releases/latest)
![Android 8+](https://img.shields.io/badge/Android-8%2B-green)

通过原生 Android 界面操作远端 Codex：项目与历史会话、审批与问题表单、图片及文件传输。
Codex 运行和登录保留在服务器，通信走 SSH，无需额外开放模型 HTTP 端口。独立客户端，与 OpenAI 无隶属关系。

**[下载 APK · 1.0.65](https://github.com/qzh775226913-ai/RemoteGPT/releases/download/v1.0.65/RemoteGPT-1.0.65-Android-universal-debug.apk)** · [首次连接](docs/USAGE.md) · [更新记录](CHANGELOG.md) · [协议覆盖](docs/CODEX_DISPLAY_COVERAGE.md) · [反馈](https://github.com/qzh775226913-ai/RemoteGPT/issues)

当前源码与公开稳定版：**1.0.65 / versionCode 67**。Android 8+，包名 `app.remotessh`，APK 约 4.4 MB。
1.0.58–1.0.63 为历史 Tailscale 交付版本，其改动已汇入本版。

## 界面

<img src="docs/screenshots/table-conversation.png" alt="合成测试：对话、公式与原生表格" width="250"> <img src="docs/screenshots/device-multi-task.jpg" alt="用户提供的实机多任务通知示例" width="250">

左：当前源码的合成测试截图。右：用户提供的实机多任务通知；版本及机型未独立核验，显示形式受 Android、权限和厂商系统影响。

## 功能

- 项目与历史浏览；多会话独立草稿、附件、审批及状态。
- 执行中补充输入或排队；结构化提问与权限审批。
- 从远端同步模型、推理强度及速度；会话权限选择。
- 文件上传下载；图片缩放、连续翻页、有限缓存及相邻预加载。
- 本地公式与原生 Markdown 表格；宽内容横向滚动，复制保留原文。
- 默认持久模式保留断线后的远端任务，重连恢复输出和待审批。

## 使用

1. 远端 Unix 主机提供 SSH/SFTP，连接用户已配置支持 `app-server` 的 Codex CLI；默认持久模式需 Python 3.8+。
2. 安装 APK，填写可达的 SSH 地址及认证信息，核对主机指纹。
3. 点选项目，新建或接续会话，手动发送消息。

[连接字段与排错](docs/USAGE.md) · [环境与验收边界](docs/ACCEPTANCE.md)

## 下载说明

- `.apk`：直接安装，沿用现有项目签名的 debug 构建。
- `with-license.zip`：同一 APK、许可声明、公式库对应源码。
- `.sha256`：制品校验文件；`LICENSE.txt`：本版项目许可。

同包名且同签名的安装包可用于覆盖升级；目标手机安装结果需实测。主机／托管进程退出后的任务续跑不作保证。
模型及账户费用由远端配置决定；图片预加载速度取决于网络、图片和缓存预算。

## 构建与验证

Java 17、原生 Android View、JSch SSH/SFTP、stdio JSON-RPC、Python 标准库托管。
开发环境需 JDK 17、Android SDK 36／Build Tools 35.0.0、Python 3.12。

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements-dev.txt
.venv/bin/python tools/verify.py
```

完整入口检查 Python／Android 测试、结构、生成资产、APK 与 Lint，使用合成回环 SSH 夹具。
[开发手册](docs/DEVELOPMENT.md) · [本版验证](docs/verification/2026-10-27-1.0.65.md)

## 许可与贡献

[源码可用许可 1.1](LICENSE)，不属于 OSI 开源许可：免费使用、公开协作及符合条件的免费修改版分发；商业再利用、收费功能访问及闭源交付另需授权。普通工作与独立开发成果不收费、不分成。

[贡献规则](CONTRIBUTING.md) · [第三方许可](docs/THIRD_PARTY.md) · [需求](docs/REQUIREMENTS.md) · [架构](docs/ARCHITECTURE.md) · [回退](docs/ROLLBACK.md)
