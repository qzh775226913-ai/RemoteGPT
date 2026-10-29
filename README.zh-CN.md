# RemoteGPT

[English](README.md) · 简体中文

在 Android 手机上通过图形界面操作远端 Codex。Codex 运行和账户登录保留在服务器，手机通过 SSH 连接。

**[下载 APK · 1.0.65](https://github.com/qzh775226913-ai/RemoteGPT/releases/download/v1.0.65/RemoteGPT-1.0.65-Android-universal-debug.apk)** · [更新记录](CHANGELOG.md) · [问题反馈](https://github.com/qzh775226913-ai/RemoteGPT/issues)

当前版本：**1.0.65 / versionCode 67**。支持 Android 8+。

## 界面

<img src="docs/screenshots/table-conversation.png" alt="公式与表格合成示例" width="250"> <img src="docs/screenshots/device-multi-task.jpg" alt="实机多任务通知示例" width="250">

左：公式与表格合成示例。右：实机多任务通知，展示形式随设备而异。

## 功能

- 浏览项目与历史，多会话保留独立草稿、附件和状态。
- 执行中补充输入或排队，处理审批与结构化提问。
- 选择远端模型、推理强度、速度和权限。
- 上传下载文件，图片缩放、连续翻页及相邻预加载。
- 显示公式与 Markdown 表格，复制保留原文。
- 持久模式支持断线后的远端任务保留与重连恢复。

## 使用

1. 远端主机准备 SSH/SFTP 和可用的 Codex CLI；持久模式需要 Python 3.8+。
2. 安装 APK，添加 SSH 连接。
3. 选择项目，新建或接续对话。

## 许可

[源码可用许可](LICENSE)：允许使用、修改与公开协作；非商业分发须保留来源和许可，商业再利用需书面授权。独立客户端，与 OpenAI 无隶属关系。

[贡献](CONTRIBUTING.md) · [第三方许可](docs/THIRD_PARTY.md)
