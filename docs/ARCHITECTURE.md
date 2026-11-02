# 架构、职责与状态归属

当前链路：原生 Android Java 界面 → 应用协调层 → SSH 上的 JSON-RPC → 可选 Python 持久托管 → 远端 Codex App Server。

本文件说明修改应放在哪里及必须保留的约束。具体界面尺寸、限额和产品行为以 [REQUIREMENTS.md](REQUIREMENTS.md) 为准；逐次修改与执行结果在 `docs/verification/`，不构成新需求。

## 模块边界

全部主 Java 文件的唯一模块归属及规模上限见 [模块登记](../tools/project_structure.json)。MainActivity 和 RemoteApp 接近上限时应提取职责，不提高预算。

| 职责 | 所有者与协作关系 |
| --- | --- |
| 页面与系统回调 | `MainActivity` 组合页面、消息组件和系统选择器；`BubbleActivity` 管理绑定会话的气泡页面 |
| 应用协调 | `RemoteApp` 负责连接、初始化、恢复、事件路由，以及供现有 UI／通知读取的选中会话字段；发送、附件和独立窗口交给对应模块 |
| 会话状态 | `ConversationStore` 唯一管理缓存与临时背景分发；`ConversationState.capture/apply` 对选中字段做显式快照；`QuestionUiState` 保存非秘密表单草稿 |
| 轮次与附件 | `TurnController` 管理创建、发送、引导及每会话一条待发消息；`AttachmentController` 管理上传和有界媒体读取；`MediaFiles`／`FileAttachment` 定义格式、路径与限额 |
| 目录与偏好 | `DirectoryController` 负责 SSH 目录 I/O；`ThreadDirectory` 负责标题搜索、分页、重命名和本机置顶；`ConversationPreferences` 负责既有权限／速度／问答存储键及快照 |
| 协议传输 | `SshTransport` 管理 SSH、SFTP、辅助通道及资产安装；`RpcClient` 负责 framing、请求匹配和事件分发，不操作 View |
| 连接诊断 | `ConnectionPreflight` 生成有界无凭据探测、检查公开账户状态；`ConnectionFailure` 分类原始失败；`DiagnosticText` 保留客户端诊断的显示来源；`ConnectionSetupUi` 拥有关闭持久模式的确认窗口 |
| 模型与权限 | `ModelCatalog` 原子合并完整分页；`ModelOptions`、`ModelSettings`、`ServiceTierOptions` 解析远端能力；连接级 `ModelRefreshScheduler` 定时刷新；`ForcedApprovalController` 调度已明确授权的权限审批 |
| 独立窗口 | `ProfileEditor` 拥有连接草稿和私钥导入；`ModelPicker`／`PermissionPicker` 拥有各自窗口与监听器；`DialogUi` 提供统一窗口、点击反馈和有界说明区 |
| 导航与上下文 | `RemoteBrowser`／`DirectoryPicker`／`RemoteScreens` 组合导航；`ConversationHub`、`CapabilityDirectoryDialog`、`McpServerDetail` 拥有详情与只读能力查询；`ContextUsage` 经会话快照隔离，`ContextCompactionController` 管理待发压缩，`ContextMenuPanel` 拥有确认窗口 |
| 输入区与滚动 | `ConversationChromeController` 管理顶部／输入区目标状态；`ConversationFooterMotion` 协调动画空间和位移；`ConversationScrollView` 管理手势、惯性与跟随；`ConversationOverlayLayout` 固定接续按钮尺寸；`ComposerSurface` 绘制表面，`ComposerRenderCache` 跳过无变化的控件重建 |
| 消息与执行详情 | `ChatItem` 保留原文和轮次；`InteractionHistory` 合并问答呈现；`ToolActivityPresenter` 分组，`DiffSummary` 统计实际 diff；`ToolActivityGroupView`、`ToolInlineDetailView`、`ToolDetailDialog` 呈现详情；`ToolCodeViewport` 管理代码缩放，`RollingChangeNumbers` 只动画变化的数字，结束或 detach 后把已完成的数字 span 换成静态着色文字；`ToolActivityGroupView` 即使离屏也提交落定文字，保留图标和详情子树 |
| 结构化问题 | `QuestionForm` 显示表单并过滤秘密快照；`QuestionBubbleController` 拥有窗口、作用域校验和输入框定位；`QuestionBubbleMotion` 拥有有界动画快照并释放 |
| 正文、公式与表格 | `MessageBodyView` 拆分代码／正文；`Markdown`、`MathMarkup`、`TableMarkup` 解析；`MathRenderer` 在有界独立 worker 排版，`MathContentView` 管理异步 generation；`TableContentView` 复用单元格与整表横向滚动；`MathDrawable`／`MathGlyphs` 隔离引擎绘制与字体兼容 |
| 图片 | `MessageImageTile` 管理可见加载、重试和作用域；`ThumbnailLoader`／`ThumbnailCache` 提供有界共享缩略图；`ImageDialog` 拥有冻结的画廊列表、任务和原图缓存；`ZoomImageView` 管理缩放／边缘翻页；`ImageSourceReader`／`ImageDiskCache` 提供精确作用域的缺失文件回退 |
| 后台通知 | `ConnectionService` 维持前台服务、唤醒锁和会话快捷入口；`LiveStatusNotifications`／`LiveActionReceiver` 管理会话通知与请求身份路由 |
| 语言与图标 | `AppLanguage` 管理显示偏好和 Activity 刷新；`UiText`／`UiPresentation` 负责明确来源的 UI 显示；图标由 `tools/icon_spec.json` 生成 |
| 远端资产 | `remote_session.py` 托管进程、SQLite 事件和重放；`conversation_state.py` 有界读取问答元数据；`remote_maintenance.py` 检查、更新和明确重启 |

## 状态生命周期与线程

| 生命周期 | 内容 | 约束 |
| --- | --- | --- |
| 连接级 | SSH/RPC、generation、初始化、恢复队列、模型目录和刷新定时器 | 换连接使旧任务无效，不属于某条会话 |
| 会话级 | threadId/turnId、消息、草稿／附件、运行状态、审批、权限／速度、问答、上下文用量和错误诊断来源 | 经 ConversationStore 和 ConversationState 隔离；后台结果按目标 threadId 分发 |
| 界面级 | View/Dialog、滚动位置、选择器待处理标识、画廊列表和临时原图 | 窗口／Activity 拥有并释放；旋转不重启 SSH；不把 View 存入会话 |

会话容器的读写及作用域切换均须在主线程。`ConversationStore.dispatch` 保存选中会话、应用目标、执行动作，再在 finally 中保存目标并恢复选择、项目和最后会话；最外层恢复后才发布合并变化。嵌套与异常不能暴露临时选择。

新增会话字段必须同时更新 ConversationState 声明、capture/apply，验证 A/B 隔离、异常恢复、重放和重连。兼容字段与快照并存是已知技术债，不另建第二套会话缓存。附件批次属于连接上的串行操作，现有上传／创建／恢复切换限制不可直接移除。

SSH 和文件 I/O 在 worker；回到主线程后核对 generation，必要时再核对传输实例、配置 revision、服务器和会话身份。worker 不读取临时选中状态、不更新 View。`RemoteApp.request` 捕获发起会话并通过 `inConversation` 分发回调；控制器复用此入口。

窗口拥有监听器、Handler 回调和异步任务，关闭／宿主销毁时释放。连接级模型刷新器由 RemoteApp 在握手成功后启动、断开时停止；每 30 秒复用已有读取入口，loading 标记防止重叠，旧 generation 和旧定时器无效。

## 协议与恢复约束

- 浏览使用 `thread/read`，接续使用 `thread/resume`；恢复活动会话与只读历史分开处理。重放是同步屏障，不授权队列发送或审批。ThreadStart 的沙盒是配置枚举，TurnStart 是 tagged policy。
- 待发消息按连接配置 ID 和 threadId 隔离。断开清除自动发送许可、保留进程内待发内容；不保证手机进程被杀后的恢复。
- 强制允许的状态、审批频率窗口和 revision 经现有会话快照隔离，不持久化。只计实时唯一权限请求；下一轮成功只能结束它提交时捕获的权限选择和授权 revision，不能清除后来的授权。
- `InteractionHistory` 只按明确调用／工具 ID、轮次和完整问题合并；在原调用附近放置卡片，不修改服务器原文。缺来源时保留进程内已观察位置，冷恢复只回退到原轮次，不挂到最新任务。秘密答案不存档，不从普通问句猜请求。
- `item/fileChange/patchUpdated` 按会话、轮次和条目更新原始 diff，空 started 不覆盖先到补丁；已完成条目或错误轮次的迟到补丁无效。无法归属的整轮 diff 只用于最终汇总。完成快照保留 turnId，缺轮次的晚到事件优先沿用原条目归属。
- SFTP 通道未打开时释放失败通道，在同一有效连接上间隔 500ms 最多重试一次。取消／断开终止重试，只重试建立通道，不重放已经开始的文件操作。
- Python 托管保留基线并重放有序事件；新增客户端字段同时核对透传、基线和旧托管兼容。安装新脚本不会自动替换运行中的旧进程；主机／进程崩溃不保证任务继续。

## 绘制、媒体与语言边界

正文先拆出代码，再解析显式公式和表格；无公式正文沿用原路径。TeX 使用固定版本 `ru.noties:jlatexmath-android:0.2.0` 和本地 Canvas／字体，无 WebView 或外部资源。View 自有 generation 丢弃迟到排版；`MathDrawable` 隔离文字／线框 Paint，`MathGlyphs` 由生成器提取原字体 U+00AD 轮廓。表格单个 HorizontalScrollView 滚动整表，横滑期间抑制父容器的焦点纵向位移，结束／取消／detach 释放；行统一绘制边线，避免交点和共享边重复叠加。

顶部／输入区动画不能把布局补偿当作用户方向或惯性到底。FooterMotion 在开始时预留最大输入区高度，用位移跟随，结束恢复准确留白；ScrollView 用真实手势坐标处理方向、取消旧惯性和新触摸解锁。ComposerRenderCache 只在成功渲染后提交内容快照，不跨会话复用操作绑定。

图片来源规则由 MediaFiles 统一：显式结构化图片独立保留，正文链接先屏蔽代码／转义示例，工具摘要与思考不提取。画廊在主线程冻结当前会话已加载图片顺序和作用域；后台读取不依赖临时会话选择。原图缺失时只在同条目无部分输出且 SFTP 明确报告不存在后使用内嵌数据或私有副本；网络／权限失败不回退，重复路径不猜来源。缩略图为借用对象，不回收共享位图。缓存预算与重试次数见 R-15；任务取消、关闭和换作用域都释放窗口原图与临时文件。

英文 strings.xml 是 UI 基准，另有九套本地化资源；Bundle 禁用语言拆分。`AppLanguage` 不改 JVM 默认 Locale 或会话，`UiText` 不持有 Activity。仅翻译应用来源的 UI，服务器文字、命令、错误与复制原文保留；`UiPresentation` 只适配已有内部状态，不能扩成任意文本词典。切换语言只刷新呈现，不触发业务授权；通知复用频道／快捷入口 ID，不重置用户设置。

DiagnosticText 随会话错误快照保存构造来源，最近错误来源随全局 lastError 保留；仅原始字符串引用匹配时重新本地化，不误翻译同内容服务器字符串。来源只保留显示所需的 threadId／path 和错误 data 文本；持久模式判断沿用原诊断。

## 持久化、资产与发布

保留 Vault 加密格式、SharedPreferences 名称／键、包名和签名。可能含私人值的启动身份只用于哈希键，不直接记录命令或凭据。数据格式变化需另行授权迁移和恢复方案。远端资产按内容哈希安装到 `~/.remote-gpt`，目录 0700、脚本 0600。

许可证与第三方字体声明是分发必需资产，不能按未使用资源删除。图标／数学字形由生成器维护，生成检查属于完整验证。Python 开发检查不应向资产目录写入字节码缓存。

常规 Verify 只用合成回环 SSH，与生产凭据、任务和手机隔离。显式发布由 `publish-release.yml`／`tools/publish_release.py` 校验制品、许可、正式标签和完整 CI，先上传草稿再公开；只重分发原签名 APK，不上传私钥。执行说明见 [开发手册](DEVELOPMENT.md)，剩余技术债见 [维护规则](MAINTENANCE.md)。
