# 2026-10-29 推理档位与模型／通知语言显示修复

## 范围与提交

用户要求推理强度档位使用官方英文，并反馈模型同步提示、下拉通知仍残留中文／繁体中文。涉及 R-11、R-17、R-23。

- 恢复提交：`bc8542f`。
- 修复与回归提交：`4593c05`。
- 保持原版本 1.0.65 / versionCode 67，仍为开发验证 APK；未进行 GitHub 发布或生产部署。

## 原因与改动

1. ModelOptions 的档位名称此前进入翻译资源。将 None、Minimal、Low、Medium、High、Extra High、Max、Ultra 集中为英文基础资源并设置 translatable=false，移除九种翻译副本。只显示服务器支持的选项，原顺序和请求 ID 不变；未知档位保留原始 ID，应用自己的“默认”入口仍随界面语言显示。
2. ModelPicker 首次绘制会本地化 modelsStatus，状态监听回调却直接 setText(app.modelsStatus)。同一目录对象下只有状态变化时没有再次完整重绘，导致中文覆盖。两个入口现在复用同一 renderCatalogStatus；刷新失败的远端错误后缀保持原文。
3. ConnectionService 只首次发布系统缓存的会话快捷入口名称。语言变化后重绘通知仍引用旧缓存名称。现在缓存最近发布的名称，变化时用同一个 shortcut ID 更新名称，并保留原有延迟移除桌面动态入口的机制。这个问题可影响系统通知所展示的会话名称；厂商实际渲染效果需手机验证。

核对了[官方协议 ReasoningEffortOption](https://github.com/openai/codex/blob/main/codex-rs/app-server-protocol/src/protocol/v2/model.rs)：目录提供 reasoningEffort 和 description，没有独立的档位名称字段。因此保持现有标准英文名称映射，不把 description 当作名称，也不增加未由远端提供的选项。

## 实际验证

```sh
.venv/bin/python tools/verify.py --offline
```

- Android：**448 tests，0 failures，0 errors，0 skipped**。
- Python：**54 tests，通过**。
- 结构、模块预算、文档链接、图标与公式字形检查通过。
- APK 构建成功；Lint **0 Error/Fatal，16 Warning**，与修复前相同。
- git diff --check 通过。
- APK 签名验证通过，证书与此前交付的开发 APK 一致。

[ModelDisplayTest](../../app/src/test/java/app/remotessh/ModelDisplayTest.java) 覆盖十语言下固定英文档位、未知 ID 原样保留、面板内同一目录快照的同步／刷新／失败／空状态更新，以及目录替换；原始模型数据和远端错误后缀不变。[NotificationLanguageTest](../../app/src/test/java/app/remotessh/NotificationLanguageTest.java) 覆盖活动任务先以繁体中文发布，再切换英文后，系统缓存名称、任务通知、MessagingStyle 会话标题、前台通知及操作按钮同步更新，保持快捷入口 ID 和原始会话／任务状态，并按原计划清理桌面动态入口。既有 BubbleTest 及全量回归通过。

## 测试包交付与边界

已更新专用临时下载目录中的测试包，并提供新文件名 `RemoteGPT-model-notification-fix-debug.apk` 避免复用旧下载缓存。通过本机访问 Tailscale 地址返回 HTTP 200，下载原始字节与构建 APK 的 SHA-256 一致，文件 4,730,259 字节。摘要：`116d0a034625bd9dbf3d2b8bfba00e473ab3cc6b7c2af65da42be883ff2e164a`。临时服务不包含项目源码或凭据。

本轮验证是合成 SSH/SFTP、Robolectric 与本机构建／下载证据，不是目标手机验收。需用户安装修正版后核对实际下拉通知／厂商 SystemUI 缓存效果；系统自己生成的界面文字仍由手机系统语言控制。本轮没有调用真实模型、使用真实凭据或重启远端任务。GitHub 文件、远端脚本、会话存储、传输／协议代码无变化。
