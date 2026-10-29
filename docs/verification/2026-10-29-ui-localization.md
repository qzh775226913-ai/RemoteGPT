# 2026-10-29 界面多语言验证

## 范围与恢复

- 用户批准：仅应用 UI 本地化；GitHub 主页和公开说明不调整；服务器消息、报错、提问／选项、模型名称／描述、日志和命令保持原文；默认跟随系统。
- 需求：R-23；保持 R-01 至 R-22 的业务行为。
- 分支：`feature/ui-localization`。
- 开发前恢复提交：`f7c6b6e`；包含此前已有的英文首页。此前 HEAD 为 `0c7b8ee`。
- 功能与配套测试／维护文档提交：`d80fb9a`。
- 本次没有发布、推送、安装手机或部署远端；版本仍为 1.0.65 / versionCode 67，公开 APK 不因此获得新功能。

## 实现

- 451 个 UI 字符串、6 组复数资源，覆盖英文、简体中文、繁体中文、日文、韩文、西班牙语、巴西葡萄牙语、德语、法语和俄语。
- 新增独立 AppLanguage、UiText、UiPresentation、LanguagePicker 模块；模块规模预算未提高。
- 首次／无偏好时跟随系统首选语言，不支持时回退英文；中文区分 Hans／Hant，葡萄牙语使用巴西版本。手动选择使用语言自称并保存；可恢复跟随系统。
- 语言刷新不调用业务 changed 流程；不触发会话保存、审批调度或远端重启。原有业务状态字符串、会话字段与协议不变。界面重建复用原有草稿和问答恢复机制。
- 资源访问仅持有应用来源的 Resources，不持有 Activity／Context。日期和百分比使用所选 Locale，数量使用完整复数分支。
- 通知、频道名称、气泡及固定无障碍文字本地化；频道 ID 和用户设置保持。完成通知中的真实回复保持原文，无回复的提示在显示时解析。
- 连接表单本地校验在调用 Profile.validate 的明确边界转换为 UI 提示；Profile 的原始诊断异常不变。远端错误不进入文案转换器。
- App Bundle 语言拆分关闭，保障离线安装包含全部资源；未引入依赖或运行时翻译／下载服务。
- 只对长翻译需要的权限选项行改用最小高度与自适应高度，保留既有外观、布局关系及点击入口。

## 验证结果

开发环境按 docs/DEVELOPMENT.md 设置 JDK 17 与 Android SDK，通过开发 Python 环境执行：

```sh
.venv/bin/python tools/verify.py --offline
```

| 项目 | 开发前基线 | 最终结果 |
| --- | --- | --- |
| Android 测试 | 434；0 失败／错误／跳过 | **445；0 失败／错误／跳过** |
| Python 测试 | 由基线完整入口执行通过 | **54 通过**，含 4 项本地化资源／边界检查 |
| 结构、文档链接、模块预算 | 通过 | 通过 |
| 图标和公式字形一致性 | 通过 | 通过 |
| Debug APK | 构建成功 | 构建成功 |
| Lint Error／Fatal | 0 | **0** |
| Lint Warning | 22 | **16** |
| SSH/SFTP | 独立合成回环夹具 | 独立合成回环夹具，0 跳过 |
| 差异空白检查 | 工作区干净 | `git diff --check` 通过 |

最终完整入口摘要位于本地 `app/build/verification/summary.json`，测试和 Lint 报告位于 `app/build`，APK 为 `app/build/outputs/apk/debug/app-debug.apk`。构建目录不提交。

16 项非阻塞警告：ExifInterface 1、InlinedApi 2、OldTargetApi 1、ObsoleteSdkInt 1、MonochromeLauncherIcon 1、ViewConstructor 4、SetTextI18n 4、RtlHardcoded 2。未降低规则或添加 Lint baseline；新增的复数分支、资源持有方式、语言拆分和省略号告警已处理。

开发中曾有两类未通过结果：旧测试期待中文界面显示英文推理档位，已改成对应中文 UI 断言且协议 ID 断言保持；一次完整入口的 444 个 Android 测试通过，但 Lint 检出 7 项复数分支错误，不能视为验收通过。补齐语言复数类别、增加本地校验提示测试后重新运行完整入口，以上最终结果替代这些中间结果。

## 专项证据

- [LocalizationTest](../../app/src/test/java/app/remotessh/LocalizationTest.java)：默认跟随系统、中文文字体系与不支持语言回退、选择持久化／恢复跟随、十种语言独立资源、自称列表、服务器原文／模型名称／提问内容不变、本地表单校验、审批 token 与 A/B 会话草稿隔离、频道名称、Activity 重建后的草稿／附件／问答恢复、Android 8 字号与语言组合。
- [LocalizationLayoutTest](../../app/src/test/java/app/remotessh/LocalizationLayoutTest.java)：十种语言、320dp 窄屏、浅色和深色较大字号，生成 **40 张原生合成截图**，权限文字的行高与省略检查通过。截图位于本地 `app/build/screenshots/localization/`；抽查语言自称列表及俄语长文案权限面板。
- [test_localization.py](../../tools/test_localization.py)：十语言键集合与格式参数一致、非空资源、复数 other 分支、翻译残留标记、核心协议／数据模块不依赖本地化、UI 文案硬编码检查。
- 既有 UI 测试显式使用简体中文 fixture，继续验证原行为；新增测试分别选择各语言，不将产品默认语言固定为中文。
- 与恢复提交比较：README 系列、`.github`、远端 assets，以及 Profile、Vault、ConversationStore、ConversationState、SshTransport、RpcClient、ChatItem、InteractionHistory、ConversationJournal、TurnController 均无变更。包名、签名配置及原偏好键保持。

## 证据限制与回退

- 翻译草稿有自动辅助，关键权限警告和常用操作进行了人工语义复核。未完成十语言母语者审校，不宣称所有措辞均已通过当地用户验收。
- 原生合成截图和 Robolectric 不代替真机字体／TalkBack、系统选择器、OEM 通知／气泡、后台网络、Tailscale 或真实模型服务验收。本轮没有调用真实模型或生产凭据，没有重启生产任务。
- 系统自身界面与外部消息不强制改为应用所选语言。外部中文错误在英文界面中仍按原文显示，这是批准的内容边界。
- 版本号未递增，本地 APK 仅为开发验证产物；正式分发仍需独立版本、签名及覆盖安装验收。
- 回退可从 `f7c6b6e` 建立恢复分支，或按逆序 revert 本次提交，遵循 docs/ROLLBACK.md。新增 `language/tag` 偏好可由旧版忽略，不迁移原凭据、对话或远端存储。
