# 首次公开 APK Release

版本：1.0.51 / versionCode 52。恢复提交：`7f857f7`。

## 范围与制品

首次将既有 1.0.51 APK 分发到 GitHub Release；这是新增分发渠道，不是新应用构建，版本、应用代码、依赖、包名、存储格式及签名均不变。README 和 [发布说明](../releases/1.0.51.md) 补充轻量原生、SSH 直连、流体云／实时通知、交互审批、远端断线续跑与活动会话恢复。

公开 APK：`RemoteGPT-1.0.51-Android-universal-debug.apk`，3,812,491 字节，约 3.8 MB；不将安装包大小当成安装后的总占用。

- SHA-256：`d53f0b7b40c15a56f48521c3b866c5e64626c27aafeb0af2369b7d123ca7b80e`。
- `apksigner verify --print-certs` 通过，签名 SHA-256：`43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a`，与此前分发一致。
- 安装 ZIP 附同一 APK、LICENSE 与安装说明；另附 APK／ZIP 校验文件及 LICENSE.txt。

## 验证

本阶段执行完整 `python3 tools/verify.py --offline`：文档／结构／预算及 28 个图标检查通过；44 项 Python 测试通过；356 项 Android 测试，0 失败、0 错误、0 跳过；APK 构建通过，Lint 0 Error/Fatal、17 项既有 Warning，`full_acceptance: true`，Gradle 1 分 24 秒。

随后补充用户截图与优势说明，再次通过文档／结构检查、`git diff --check` 和发布器语法检查。发布器要求正式标签、源码版本、制品摘要、ZIP 内 APK／许可均一致，并等待同一源码提交完整 GitHub Verify 成功；草稿附件全部上传和核对后才公开，不覆盖已有公开 Release。

四张用户提供的真机 JPEG 解码与文件检查通过，清除 EXIF 等文件元数据，逐像素核对画面不变；原附件保留。截图分别展示流体云状态、多会话通知、模型设置及聊天首页，仅证明拍摄环境的展示效果，不证明所有 OEM 或断线恢复已完成真机验收。

## 发布方式与边界

现有连接没有 Release 写入接口，SSH deploy key 可推送代码／标签。新增显式发布工作流以临时注释标签接收制品 URL 与摘要，仅发布任务使用 GitHub 自有临时 token 的仓库写权限；正常 Verify 保持只读。签名私钥不上传，也不从新 runner 重建不同签名的 APK。

新增 Funnel 端口因本机权限不足未开启，原有配置和下载服务不变。改用限时临时制品托管，公开制品上传设置 2 小时自动删除；上传 URL 不进入源码文件，临时触发标签完成后删除，永久下载由 GitHub Release 承担。传输制品均已实际完整下载并核对摘要。

公开 Release 成功与附件下载结果须以实际远端执行核对；本记录不以流程配置代替已经发布。流体云受系统授权与厂商决定，SSH 速度／稳定性受网络影响，持久模式依赖远端进程存活。未运行真实模型、安装手机、部署或重启生产 Codex；[验收边界](../ACCEPTANCE.md) 继续有效。

发布前两次源码 Verify（[主分支](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38354682540)、[版本标签](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38354688678)）均成功。临时文件服务的下载按钮提供带时效的正式下载 URL；制品实际使用该链接完整下载并校验。Python 默认下载请求返回 403，发布器改用服务文档支持的 curl 客户端，并限制 HTTPS、下载大小和超时；未绕过验证码、账户授权或访问权限。此修复单独提交，尚未公开 Release 的版本标签使用明确旧值的 lease 更新至修复提交，源应用与 APK 不变。
