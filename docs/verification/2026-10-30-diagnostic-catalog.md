# 2026-10-30 客户端诊断与目录显示开发验证

恢复基线 `b06cd1c`；功能提交 `440986d`。开发版本 **1.0.67 / versionCode 69**；GitHub 稳定版仍为 1.0.66。

## 已完成的前序发布

1.0.66 的提交、GitHub 推送、latest APK 发布、多语言主页说明与用户三张英文实机截图已完成。[发布记录](2026-10-29-1.0.66.md) 包含成功 CI、公开附件完整下载、签名及图片回读结果。此次后续修复未改动公开 Release 或版本标签。

## 根因与改动

- 截图中的中文不是服务器原始错误的翻译，而是 ConnectionFailure 客户端自己拼接的标题、字段名和网络排错指引。此前将整段诊断作为原文保留，遗漏了这部分 UI。本轮增加 DiagnosticText 来源对象，在明确的连接／操作错误生成处关联原始字符串；显示和复制时只重显客户端说明，原始异常、服务器 data 和 stderr 不翻译。
- 内部原始诊断格式与分类保持，用于 Python 环境判断等既有逻辑。仅同一个来源字符串引用与诊断对象匹配时才本地化；普通远端字符串即使内容相同也不套翻译。显示所需 threadId／path 以及 RPC data 做快照，不额外持有完整请求或消息正文。
- 当前错误的来源字段通过 ConversationState.capture/apply 与会话一起隔离，沿用 ConversationStore 主线程和异常恢复；全局最近错误的来源跟随原 lastError。新模块登记于 localization，RemoteApp 1779 行，没有提高预算。
- 速度元数据存在真实偏差：显式 default 被丢弃、本地普通档重复补入，默认档描述被固定说明覆盖。现保留远端 default 的位置、名称、描述；有名称时保持原文及空白，没有名称时使用原 ID，不再猜测／翻译 Fast 名称。仅旧目录缺少普通档时补协议隐含 Standard 控制项，功能门控和 fast/priority 精确 ID／旧别名兼容保持。
- 模型顺序本来就由 LinkedHashMap 保留分页首次出现顺序，没有本地按字母或默认标记排序。本轮核对并增加分页与界面回归，保持该行为。应用“沿用远端默认”的控制行位于服务器模型／速度列表之外，不冒充服务器目录条目。

官方目录转换参考：[server models.rs](https://github.com/openai/codex/blob/main/codex-rs/app-server/src/models.rs)。核对依据是协议及合成目录，不是使用生产凭据读取目标服务器或调用真实模型。

## 实际验证

最终执行：

```sh
.venv/bin/python tools/verify.py --offline
```

- Android **456 项，0 失败／错误／跳过**。
- Python **54 项通过**；结构／模块预算／文档链接、图标与公式字形检查通过。
- APK 构建成功；Lint **0 Error/Fatal，16 项既有 Warning**。
- full_acceptance=true，Gradle **3 分 16 秒**；git diff --check 通过。
- 初轮新增 Android 8 复制测试未等待 AlertDialog 按钮消息回调，夹具读到空剪贴板。补齐主线程等待并从 Activity 读取剪贴板后，定向和最终完整回归通过；未修改应用复制逻辑来迎合测试。

[DiagnosticLanguageTest](../../app/src/test/java/app/remotessh/DiagnosticLanguageTest.java)：本地指引／原始错误边界、十语言切换、普通服务器字符串同内容不误翻译、参数／RPC data 快照、A/B 隔离与重放状态、嵌套异常恢复、来源不匹配回退，以及 Android 8 的实际诊断窗口和复制路径。

[CatalogPresentationTest](../../app/src/test/java/app/remotessh/CatalogPresentationTest.java)：远端 normal/default 档位置／名称／描述、未知与旧 ID、能力门控、分页顺序和默认标记不重排，实际模型／速度面板行顺序及点击后的默认档远端说明。既有 ServiceTierStateTest、ConnectionFailureTest、ConversationStoreTest 和全量回归通过。

## 制品与下载

- 包名 app.remotessh，1.0.67／69，minSdk 26。签名验证通过，与稳定版和此前测试包同证书：`43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a`。
- 测试文件 `RemoteGPT-1.0.67-diagnostic-catalog-debug.apk`：**4,776,255 字节**，SHA-256 `366b5b56a814268b6224b2132a59acfa708a9142c7bc86a183afc5cd0378e13f`。
- 已放入独立临时下载目录。局域网接口和 Tailscale 接口分别监听；本机通过两个地址完整下载均返回 HTTP 200，原始字节／摘要与构建 APK 一致。
- 此次按用户要求提供测试 APK，源码在本地独立分支提交；没有再次发布 GitHub 稳定版。

## 边界

翻译草稿有自动辅助，英文和中文诊断模板、原始内容边界及关键路由指引单独复核；十语言母语审校仍需独立证据。测试使用合成 SSH/SFTP，不改远端脚本、凭据、包名、存储格式或模型提供方，没有真实模型调用或生产任务重启。

双接口本机 HTTP 验证不代表已从用户手机验证局域网可达。此前直接 Wi-Fi 访问失败，Tailscale 下载成功；本轮没有修改手机 VPN、路由器或系统防火墙。目标手机的覆盖安装、诊断显示和实际服务器目录仍待用户确认。原始服务器中文在英文 UI 中继续保留，这是批准的内容边界。
