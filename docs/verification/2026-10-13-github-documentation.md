# GitHub 项目说明与上传前验证

日期：2026-10-13。版本：1.0.51 / versionCode 52。

## 范围与恢复

- 变更前恢复提交：`f487d89`，与已交付源码 `e532f91` 一致。
- 精简 README，新增 [使用说明](../USAGE.md)，说明服务器要求、手机配置、权限行为和常见故障。
- 移除首页的本机局域网／tailnet 下载地址，避免将临时个人下载入口作为公开安装渠道。
- 仅修改文档；应用代码、需求契约、版本、依赖、包名、签名与存储格式未变。未操作真实 Codex、手机或远端托管服务。

## 实际本地验证

完整入口：

```sh
python3 tools/verify.py --offline
```

使用已配置 JDK 17、Android SDK 与开发 Python 依赖环境，结果：

| 检查 | 结果 |
| --- | --- |
| 文档链接、需求映射、模块与文件预算 | 通过 |
| 生成图标一致性 | 通过 |
| Python 测试 | 44，通过 |
| Android 测试 | 356，0 失败、0 错误、0 跳过 |
| APK 构建 | 通过 |
| Lint | 0 Error/Fatal；17 项既有 Warning |
| 完整验收摘要 | `full_acceptance: true` |
| 差异空白检查 | `git diff --check` 通过 |

Gradle 完整执行耗时 1 分 31 秒。SSH 检查使用入口拥有的合成回环夹具，未使用真实账户或调用模型。

## 上传准备与边界

交付目标为 `qzh775226913-ai/RemoteGPT` 的 `main` 分支。使用该仓库专用 SSH deploy key，私钥存于仓库外；远端 URL 与 SSH 配置仅保存在本地 Git 配置中，不纳入提交。不强推，不改写原提交历史，也不改变本地原始 `main` 恢复分支。

上传前扫描 HEAD 可达历史的 509 个 blob，未发现私钥块、常见 GitHub token 或 OpenAI 项目密钥格式；历史路径检查未发现被跟踪的 `.env`、`local.properties`、私钥文件或签名密钥。该结果限于所列格式与路径检查，不等于任意敏感信息的穷尽检测。

GitHub Actions 以远端实际运行结果为准，本地通过不表示 CI 已通过。未验证真实 Codex 服务、macOS 主机、手机安装、厂商通知／后台表现或跨设备 Tailscale SSH；当前 [验收边界](../ACCEPTANCE.md) 保持有效。APK 分发与签名沿用已有 [1.0.51 交付](2026-10-13-1.0.51.md)，本阶段不发布新版本。

## 实际上传核对

首次推送提交 `709a06b4df639609e1388165e5145a0663637000` 已写入公开仓库的 `main`。通过 HTTPS 在独立临时目录实际克隆，核对远端 HEAD、全部可达 Git 对象与本地一致：43 个提交、212 个当前文件，`git fsck --full` 通过。原提交历史与恢复提交均保留，私钥和个人 Git 配置未上传。

首次 [GitHub Actions 运行](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38349503145) 在 SDK 初始化阶段失败：`setup-android@v3` 默认请求旧 `tools` 包，服务器返回 `Failed to find package 'tools'`，完整测试尚未执行。后续 CI 修复单独提交，不与应用功能或依赖升级混合。
