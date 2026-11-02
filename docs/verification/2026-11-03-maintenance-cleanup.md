# 2026-11-03 项目维护清理

## 范围与恢复

恢复基线 `b903cf3`（干净工作区），恢复登记提交 `5f36ef4`；维护分支 `maintenance/cleanup-20261103`。本轮只清理项目结构、无调用代码／资源和维护文档，补足现有主线程约束与验证检查；不新增产品功能，不改变 Java 栈、已确认 UI、依赖、版本、包名、签名或 Vault／SharedPreferences 格式。不发布、推送、部署或重启生产任务。

## 检查覆盖

以 Git 跟踪清单、模块登记、生产引用、方法／字段／import 候选、资源 Lint、文件内容摘要和文档链接交叉检查项目。清理后保留 106 个主 Java 源文件、93 个 Java 测试／辅助源文件及三个远端 Python 资产；跟踪文件由 439 个降为 431 个。32 条需求行为与基线逐条比较一致，没有删除既有测试。

完全相同但仍必需的内容包括随 APK 分发的 CONTRIBUTING 副本和由同一图标规格生成的品牌／通知矢量，均保留。开发 venv、Gradle 依赖和构建目录属于仍有用途的忽略产物，未删除；未读取或提交个人环境设置。

## 清理前基线

使用既有 Python 3.12 venv、JDK 17 和 Android SDK 执行完整入口：

```sh
.venv/bin/python tools/verify.py --offline
```

本机通过环境变量限制 Gradle 堆为 1024 MiB、metaspace 为 512 MiB、worker 为 1；未写入项目配置。Android 582 项，失败／错误／跳过均为 0；Python 54 项，结构、35 个图标、数学字形、合成 SSH/SFTP、APK 和 Lint 通过；Lint 30 Warning、0 Error/Fatal。

## 文档与历史边界

- 删除重复英文主页、四个已被当前 docs 替代的根目录跳转文档、重复产品简介，以及两个无产品引用的历史截图；README 保留中英文各一份，并直接链接稳定版使用说明。
- 删除针对 1.0.55／1.0.56 和固定历史版本说明的一次性 GitHub 写入工作流；保留通用 Verify 和显式发布器。未操作远端 Release 或标签。
- 架构按职责、状态和跨模块约束组织，验收按需求与风险组织，去除逐日补丁叙述和重复数值。R-01～R-32 保留；明确标题搜索与不承诺消息正文全文搜索的区别。
- 旧恢复表移入 history，保留恢复证据。历史文档只修复指向已删除跳转文件的链接，不把旧建议提升为当前功能；1.1.1 build 74 说明明确标为历史构建。
- 保留所有行为回归测试、兼容旧数据／旧协议路径、许可证、第三方声明和有来源的验证记录。

文档阶段提交 `d9a72b0`；本地链接／需求／结构检查及差异检查通过。

## 代码与资源

- 全仓引用检查后移除 RemoteApp 的 remoteHasWork、refreshReadOnly、无调用的可写历史分支及未使用的 keepConversation 参数；实际浏览和写入冲突回退仍只读。移除 InteractionHistory 未调用的回答写入包装、TableContentView 未调用的偏移设置入口、三处无用 import 和仅供测试引用的重复警告常量；警告文本仍在测试中独立核对。
- ConnectionFailure.reason 复用已有 unwrap，消除同一异常拆包逻辑的重复。
- 删除 Lint 确认无调用的六个字符串键及其全部十语言副本（共 60 条），不改仍使用的文案。
- ConversationStore 的 get／contains／values／isScoped 原先未执行主线程检查，补齐与写入入口相同的保护；新增真实 worker 调用回归，四种读取均拒绝。没有新增会话字段或修改快照格式。
- 七组定向回归（会话容器、多会话、接续、问答恢复、权限 UI、诊断及表格）共 53 项通过，失败／错误／跳过均为 0。代码阶段提交 `0b0a5e0`。

## 防止再次积累的检查

结构检查扩大到 Java 子目录、重复模块名、陈旧预算、未登记 Python 资产、中英文主页／需求／验收的版本一致性，以及历史文档和贡献模板链接；补上 HTML 图片链接检查。验收不能引用不存在的需求编号。

图标检查拒绝规格外的多余生成文件，生成器同步删除自己拥有的旧 ic_ui_*.xml，不动其他 drawable。完整验证的子进程禁止写 Python 字节码，移除 assets 中既有的三份本地 pyc 缓存；清理前 APK 的实际 ZIP 列表未含这些缓存，不能据此声称修复了线上打包故障。

工具阶段提交 `24542b5`。新增 8 项 Python 回归，以隔离的微型仓库和真实子进程验证上述缺口。Python-only 入口 62 项通过，结构、图标和数学字形通过；这是工具阶段局部结果，不替代最终完整验收。

## 最终完整验收

在代码／工具阶段 `24542b5` 上运行同一完整入口，耗时约 3 分 16 秒：

| 项目 | 结果 |
| --- | --- |
| Android | 583 项，0 失败、0 错误、0 跳过（比基线增加 1 项 worker 读取回归） |
| Python | 62 项全部通过（比基线增加 8 项仓库／生成器／字节码回归） |
| 结构与文档 | 全部通过，32 条需求行为与基线一致 |
| 生成资产 | 35 个图标及数学字形检查通过 |
| SSH/SFTP | 完整入口自行管理的回环夹具通过并退出 |
| APK | assembleDebug 成功；ZIP 核对无 pyc／__pycache__，远端脚本和必需许可／贡献文件与源码一致 |
| Lint | 24 Warning，0 Error/Fatal；减少的全部为 6 项 UnusedResources，无新增类别或数量 |
| 差异 | git diff --check 通过，产物和个人环境未纳入提交 |

Lint 剩余类别：ExifInterface 1、InlinedApi 2、OldTargetApi 1、PluralsCandidate 5、ObsoleteSdkInt 1、MonochromeLauncherIcon 1、ViewConstructor 8、SetTextI18n 3、RtlHardcoded 2。AGP／compileSdk 兼容提示和既有编译弃用／unchecked 提示仍在；未修改级别或屏蔽告警。工具链升级、显示语义调整和协调层进一步拆分仍按 MAINTENANCE 单独管理，本轮不声称已消除全部技术债。

APK 仍为 app.remotessh、1.1.1 / versionCode 88、minSdk 26；签名校验通过，证书 SHA-256 与既有构建一致：`43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a`。该 APK 仅为验证产物，没有作为新版本交付或分发。最终摘要、测试和 Lint 报告位于开发手册列出的 app/build 路径；完整验收后只完善此文档。

## 证据限制

全部自动化使用合成数据和回环 SSH；未调用真实模型、读取真实 Codex 凭据、连接生产 SSH 或变更生产任务。没有新增目标手机、OEM 通知／后台、输入法／触摸动画、Tailscale SSH、真实服务或覆盖安装验收证据。本地执行不代表远端 GitHub CI 已运行。
