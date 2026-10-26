# 当前需求与行为契约

适用：1.0.60 / versionCode 62。最后核对：2026-10-26。

本文件是当前产品行为的唯一需求清单。版本记录说明曾经发生什么，不覆盖此处。编号永久保留；修改行为时修改对应条目和验收，取消需求时写明取消原因，不把编号转用于别的需求。

## 用户与运行模型

用户在 Android 手机连接自己有权限使用的 SSH 主机，控制远端 Codex 对话。模型登录和推理发生在远端，Android 客户端负责协议、交互与文件传输。用户可以浏览历史，也可以显式接续取得写入权限；这些状态不等于 Codex 的沙盒只读权限。

## 需求与自动化对应关系

“自动化”列代表相关回归测试入口，不代表全部真实环境已经验收。具体限制见 [验收边界](ACCEPTANCE.md)。

| 编号 | 当前行为及失败边界 | 自动化入口 |
| --- | --- | --- |
| R-01 | 保存多个 SSH 连接；密码、加密私钥、Tailscale SSH 身份认证。添加／编辑连接窗口允许系统截图，密码与私钥输入沿用原有遮挡。凭据可选择加密保存；验证失败保留编辑内容，不误保存无效端口。 | [ProfileTest](../app/src/test/java/app/remotessh/ProfileTest.java)、[SshIntegrationTest](../app/src/test/java/app/remotessh/SshIntegrationTest.java)、[MainActivityTest](../app/src/test/java/app/remotessh/MainActivityTest.java) |
| R-02 | 首次连接由用户核对主机指纹；已信任主机密钥变化时拒绝连接。SSH 认证后以有界辅助通道检查非交互环境中的 Python 3.8／托管标准库（仅持久模式）、可直接识别的 Codex 程序／app-server、SFTP 和家目录；托管写入失败独立提示。自定义启动器不猜路径、不执行额外启动；握手后检查服务器公开的登录状态，不读取凭据、不调用模型。未知旧服务登录能力不伪造成功；环境通过不保证真实模型网络可用。Python 不可用时必须用户明确选择关闭持久模式后重连，不能静默降级；诊断区分认证、程序缺失和上游拒绝。 | [ConnectionPreflightTest](../app/src/test/java/app/remotessh/ConnectionPreflightTest.java)、[SshIntegrationTest](../app/src/test/java/app/remotessh/SshIntegrationTest.java)、[ConnectionFailureTest](../app/src/test/java/app/remotessh/ConnectionFailureTest.java) |
| R-03 | stdio JSON-RPC 双向通信，支持分片、并发请求和服务器请求；未知交互明确不支持，不能自动批准。自定义命令必须兼容协议。 | [RpcClientTest](../app/src/test/java/app/remotessh/RpcClientTest.java)、[InitializationTest](../app/src/test/java/app/remotessh/InitializationTest.java) |
| R-04 | 默认远端持久托管，SSH 断开后保留模型进程；重连恢复会话、输出和待审批，不重发旧 `turn/start`。主机／进程崩溃不保证继续执行。 | [托管测试](../tools/test_remote_session.py)、[BackgroundConnectionTest](../app/src/test/java/app/remotessh/BackgroundConnectionTest.java)、[FinalAuditTest](../app/src/test/java/app/remotessh/FinalAuditTest.java) |
| R-05 | 一条连接支持多个会话并行；输出、停止、审批、草稿、附件、权限和速度各归其所属会话。切换、迟到回调和恢复不得串会话。 | [MultiConversationTest](../app/src/test/java/app/remotessh/MultiConversationTest.java)、[ConversationStoreTest](../app/src/test/java/app/remotessh/ConversationStoreTest.java) |
| R-06 | 按当前服务器浏览项目和历史，目录浏览／创建沿用 SSH。只查看历史不接续、不更新远端会话时间；恢复空闲历史不占用写入者。 | [RemoteDirectoryTest](../app/src/test/java/app/remotessh/RemoteDirectoryTest.java)、[RecoveryPerformanceTest](../app/src/test/java/app/remotessh/RecoveryPerformanceTest.java)、[MainActivityTest](../app/src/test/java/app/remotessh/MainActivityTest.java) |
| R-07 | 未接续历史可准备文字和上传附件；接续保留草稿，必须手动发送。发送失败保留可恢复内容，空消息不得无故创建任务。 | [QuestionRecoveryTest](../app/src/test/java/app/remotessh/QuestionRecoveryTest.java)、[AttachmentStateTest](../app/src/test/java/app/remotessh/AttachmentStateTest.java) |
| R-08 | 工作中可立即补充输入或排队下一条；每个会话最多一条待发消息，取消返还附件。仅实时成功完成才允许自动发送；重放、失败、只读和恢复期间不得误发送。 | [ConversationStateTest](../app/src/test/java/app/remotessh/ConversationStateTest.java)、[AttachmentStateTest](../app/src/test/java/app/remotessh/AttachmentStateTest.java)、[ResumeRegressionTest](../app/src/test/java/app/remotessh/ResumeRegressionTest.java) |
| R-09 | 命令／文件审批按服务器请求身份回复，重复或过期请求不再执行。同一对话前 30 秒（含边界）已有 3 次不同的实时命令／文件权限审批时，从第 4 次及后续符合条件的请求才提供红字“全部允许(强制)”；当前请求在到达时取得资格，阅读时不闪退按钮。重复身份、重放、只读历史、方案选择／补充信息不计数，跨会话不累计；断开清空计数。确认警告“新的审批权限生效前，客户端将会自动允许全部审批请求，继续吗？”后仅为请求所属对话自动允许当前及后续命令／文件权限审批；方案选择、补充信息和未知交互不自动回答。后台授权不改变选中对话，重放屏障内不发送审批；提交失败结束临时授权并保留手动请求。通知和气泡操作绑定服务器、会话与请求；锁屏操作遵守系统解锁要求。 | [ApprovalBurstTest](../app/src/test/java/app/remotessh/ApprovalBurstTest.java)、[ForcedApprovalTest](../app/src/test/java/app/remotessh/ForcedApprovalTest.java)、[ForcedApprovalUiTest](../app/src/test/java/app/remotessh/ForcedApprovalUiTest.java)、[LiveStatusTest](../app/src/test/java/app/remotessh/LiveStatusTest.java)、[BubbleTest](../app/src/test/java/app/remotessh/BubbleTest.java) |
| R-10 | 原生结构化提问显示可点击表单；兼容已接受异步提问的日志恢复、答案历史和同身份副本合并，包括附完整选项列表的提问副本。同轮次、请求身份和完整题目／选项均须匹配，额外回复、图片和不同请求不得隐藏。问答记录定位到原始提问调用／工具的位置；回答及同轮次后续输出不将旧卡片下移，多题按来源顺序显示，同轮次的未来同题不能取得较早答案。普通文字问句不伪造为服务器请求；失败可重试。 | [QuestionFormTest](../app/src/test/java/app/remotessh/QuestionFormTest.java)、[QuestionRecoveryTest](../app/src/test/java/app/remotessh/QuestionRecoveryTest.java)、[InteractionHistoryTest](../app/src/test/java/app/remotessh/InteractionHistoryTest.java)、[ReasoningStateTest](../app/src/test/java/app/remotessh/ReasoningStateTest.java) |
| R-11 | 模型分页动态发现，连接／打开面板时重新读取，连接期间由应用级定时器每 30 秒自动刷新目录与模型配置，慢请求不重复发送，断开取消、重连重建，不依赖面板是否打开；面板无手动刷新按钮，说明为“发送消息以更新模型”，成功状态仍为“已同步远端模型”，刷新失败保留上次目录；推理强度和速度按远端能力提供，不维护模型白名单。速度保留远端 ID、名称和描述，未知档位不猜速度或用量；同时出现 fast/priority 时优先精确匹配，旧目录仍兼容别名。沿用默认时不覆盖远端；选项失效明确处理，切换连接后的旧响应无效。 | [ModelCatalogTest](../app/src/test/java/app/remotessh/ModelCatalogTest.java)、[ModelRefreshTest](../app/src/test/java/app/remotessh/ModelRefreshTest.java)、[ServiceTierStateTest](../app/src/test/java/app/remotessh/ServiceTierStateTest.java) |
| R-12 | 权限可选沿用、按需审批、只读、完全访问，下一轮生效，保存会话选择并展示可确定的实际权限。权限面板下方仅显示“发送消息以更新权限”，不再显示当前远端／长说明。强制允许是独立的会话临时授权：仅在符合 R-09 的交互式审批中确认后启用，正常权限菜单隐藏该项、不能从菜单开启；启用时下一轮选择完全访问，用户仍可更改下一轮权限。临时授权生效期间底部显示与完全访问同形的红色闪电，权限菜单才显示红色闪电“强制允许”，点击可关闭；关闭或结束后再次隐藏。只有包含当前权限选择的下一轮启动成功才结束临时授权并清空审批频率窗口，失败、旧轮完成／重放和迟到响应不能误清除或误显示新权限生效。断开／换连接清除临时授权，不持久保存；只读历史不能启用。只读不是禁用所有工具或免费聊天。 | [ForcedApprovalTest](../app/src/test/java/app/remotessh/ForcedApprovalTest.java)、[ForcedApprovalUiTest](../app/src/test/java/app/remotessh/ForcedApprovalUiTest.java)、[PermissionModeTest](../app/src/test/java/app/remotessh/PermissionModeTest.java)、[QuestionRecoveryTest](../app/src/test/java/app/remotessh/QuestionRecoveryTest.java) |
| R-13 | 图片来源选择／系统回退、多选和取消；每条最多 4 张、每张 20 MiB。上传结果必须属于原连接／对话；文件操作开始前遇到 SSH 文件通道未打开时，释放失败通道并最多重试一次；持续失败明确提示 SFTP 通道与重连／服务器检查建议，不重放已经开始的上传。中途失败保留成功项；支持格式按平台解码能力判断。 | [ImagePickerTest](../app/src/test/java/app/remotessh/ImagePickerTest.java)、[ImageUploadTest](../app/src/test/java/app/remotessh/ImageUploadTest.java)、[ImageFormatTest](../app/src/test/java/app/remotessh/ImageFormatTest.java)、[SshIntegrationTest](../app/src/test/java/app/remotessh/SshIntegrationTest.java) |
| R-14 | 普通文件每条最多 8 个、每个 100 MiB；流式原样上传，以远端路径引用；文件名不能越界。下载限制 100 MiB，失败时清理未完成的目标文件。保存窗口按文件扩展名或内嵌图片声明提供匹配的 MIME 与文件名；未知类型仍按二进制保存，不转换原文件。 | [FilePickerTest](../app/src/test/java/app/remotessh/FilePickerTest.java)、[MediaTest](../app/src/test/java/app/remotessh/MediaTest.java)、[SshIntegrationTest](../app/src/test/java/app/remotessh/SshIntegrationTest.java) |
| R-15 | 消息／草稿图片共享原图预览，双指缩放、拖动、双击复位和保存。消息图片可按当前对话已加载消息的图片顺序左右翻看；路径规范化去重，打开时冻结列表，显示当前位置，首尾不循环。放大后优先拖动，只有从对应水平边缘开始的向外横滑才翻页，拖到边缘的同一手势不误翻。翻页重置缩放、只加载当前原图并取消旧任务；失败可重试当前页，保存对应当前图片。草稿仍为单图预览；关闭／换会话／换连接取消迟到结果并释放原图和临时文件，不回收共享缩略图。同服务器／会话重连后可见缩略图恢复加载，即使正在阅读旧消息；单次可见加载最多尝试 3 次，短暂失败以 500／1500ms 间隔重试，最终失败仍可点按重试；离屏取消任务及重试。阅读旧消息不被新输出强制拉回；正文复制保留原文。消息／回复／计划的复制图标仅显示一层 32dp 圆形点击反馈，与图标中心一致，无方形遮罩；用户消息旁按右下视觉区域对齐，保留 48dp 点击区域。 | [CopyFeedbackTest](../app/src/test/java/app/remotessh/CopyFeedbackTest.java)、[MessageImageTileTest](../app/src/test/java/app/remotessh/MessageImageTileTest.java)、[ZoomImageViewTest](../app/src/test/java/app/remotessh/ZoomImageViewTest.java)、[ImageGalleryTest](../app/src/test/java/app/remotessh/ImageGalleryTest.java)、[MessageBodyViewTest](../app/src/test/java/app/remotessh/MessageBodyViewTest.java)、[MainActivityTest](../app/src/test/java/app/remotessh/MainActivityTest.java) |
| R-16 | 请求并显示远端公开的思考摘要，兼容空摘要和最终快照，不推测未公开内容。历史错误与当前连接错误分别记录；标题不被空元数据覆盖。 | [ReasoningStateTest](../app/src/test/java/app/remotessh/ReasoningStateTest.java)、[ResumeRegressionTest](../app/src/test/java/app/remotessh/ResumeRegressionTest.java) |
| R-17 | 前台服务维持连接；每个活跃会话独立通知／气泡。普通状态无审批按钮，待答提供入口，完成可确认隐藏。系统决定流体云资格和数量。 | [LiveStatusTest](../app/src/test/java/app/remotessh/LiveStatusTest.java)、[MultiConversationTest](../app/src/test/java/app/remotessh/MultiConversationTest.java)、[BackgroundConnectionTest](../app/src/test/java/app/remotessh/BackgroundConnectionTest.java) |
| R-18 | 账户额度来自服务器；未知或不支持时显示原因，不伪造百分比；账户额度与上下文 token 区分。 | [UsageLimitsTest](../app/src/test/java/app/remotessh/UsageLimitsTest.java) |
| R-19 | 远端版本检查分清运行版本与磁盘安装版本；更新和重启需明确用户操作，检查失败不宣称最新，不自动重启活跃任务。 | [RemoteVersionTest](../app/src/test/java/app/remotessh/RemoteVersionTest.java)、[维护脚本测试](../tools/test_remote_maintenance.py)、[MainActivityTest](../app/src/test/java/app/remotessh/MainActivityTest.java) |
| R-20 | 保持 1.0.47 导航：远程控制菜单为添加连接、编辑当前连接、刷新项目与对话、设置；对话菜单为新对话、最近一次错误、当前项目与对话。主题／字体／后台设置可用；字号仅提供跟随系统、小（0.75 倍）、中（0.9 倍）、大（1 倍）、更大（1.15 倍），倍率作用于系统字号；原“小”按原存储值显示为“中”。移除原 1.3 倍档，已有该值读取时按 1.15 倍显示并选中“更大”，保留原偏好键和值以便回退。编辑连接表单的“移除此连接”与上方主机指纹按钮保持 12dp 间距；Activity 销毁释放所拥有的模型监听器。 | [MainActivityTest](../app/src/test/java/app/remotessh/MainActivityTest.java)、[DialogStyleTest](../app/src/test/java/app/remotessh/DialogStyleTest.java)、[DialogLifecycleTest](../app/src/test/java/app/remotessh/DialogLifecycleTest.java) |

## 1.0.56 公式显示

| 编号 | 当前行为及失败边界 | 自动化入口 |
| --- | --- | --- |
| R-21 | Ω 与带边框公式必须保留完整实心字形，反三角函数及合法简写分式正常排版；浅色界面正文、公式、次要文字维持清晰对比度。 远端正文显式标记的 `\(…\)`、`$…$` 行内公式及 `\[…\]`、`$$…$$` 独立公式由本地原生 TeX 排版；支持分式、上下标、上横线、根式、希腊字母、求和／积分、矩阵及常用对齐环境。分式等与正文基线及行高匹配，跟随字号缩放和明暗主题；长公式可横向滚动，独立公式长按复制原始标记和 TeX。代码块、行内代码、转义标记、链接地址、普通货币和不完整流式片段不误转为公式。传输与会话原文不变；新片段、历史恢复、换会话和窗口释放不得发布迟到排版结果。排版在有界独立 worker 中执行，保留 64 项缓存；单正文最多 512 个公式、单式最多 4096 字符／32 层花括号／256 个命令，采用显式命令与环境允许列表，不执行文件、网络、宏定义或远端代码。队列拥塞、不支持、无效或超过尺寸限制时保留可读原文。 | [MathMarkupTest](../app/src/test/java/app/remotessh/MathMarkupTest.java)、[MathRenderingTest](../app/src/test/java/app/remotessh/MathRenderingTest.java)、[MathDefectsTest](../app/src/test/java/app/remotessh/MathDefectsTest.java)、[MathReadabilityTest](../app/src/test/java/app/remotessh/MathReadabilityTest.java) |
| R-22 | 标准 Markdown 表格使用原生行列布局与表头，保留横纵分隔线和外框，全部采用同样浅细规格，超出屏幕时整张表横向滚动，不按屏幕强行挤压列宽；横滑允许轻微纵向偏移且保持消息纵向位置，松手不因单元格文字获取焦点而上跳；在表格上以纵向为主的手势仍滚动消息，结束／取消／移除释放横滑保护。表头与内容同为 14sp（正文 16sp），表头加粗；单元格左右 4dp／上下 2dp 留白、额外行距 1dp；列宽按相同字号和留白度量，范围 48–240dp。所有边线均为 1 物理像素、正文前景色约 8% 不透明度，由行统一绘制且交点不重复叠加；隐藏横向滚动条，仍可通过手势横滑。表格内公式同步按较小字号排版，跟随应用和系统字号缩放。列内容在有界列宽内换行，支持左／中／右对齐、单元格内既有 Markdown 和公式。复制表格及完整消息保留原始文本；流式追加保留表格、已有单元格与横向位置，相同表头的不同表格不共用位置。代码围栏、空格排版、分隔行不完整或无效的内容不误转表格；GFM 缺项补空、多余项不展示但保留原文。单正文上限 131072 字符／32 张表／1024 个单元格，单表最多 32 列／128 行（含表头），超限保留完整原文。浅色页面使用灰白底色、白色回复卡片及稍深的输入框／用户消息／表头，保留原布局与文字对比度。 | [TableMarkupTest](../app/src/test/java/app/remotessh/TableMarkupTest.java)、[TableRenderingTest](../app/src/test/java/app/remotessh/TableRenderingTest.java)、[TableGestureTest](../app/src/test/java/app/remotessh/TableGestureTest.java)、[MathReadabilityTest](../app/src/test/java/app/remotessh/MathReadabilityTest.java) |

## 当前不承诺的能力

- 零 token／免扣 Codex 额度的聊天开关，以及技术上彻底禁用所有工具的独立聊天后端。
- 语音、远端文件内容编辑器、全量历史服务器搜索、应用内远端 Codex 登录。
- 任意 CLI/TUI 自动适配；非 Codex 后端需提供兼容的 JSON-RPC 服务。
- 手机重启／系统强杀后无条件后台存活，主机重启后任务无损继续，跨多控制端同时写同一会话。
- 任意完整 TeX 文档／自定义宏／外部资源的排版，或与官方 ChatGPT 跨设备逐像素一致的公式效果。
- 所有厂商系统均显示同样的流体云、所有图像格式均可解码、完整复刻官方 ChatGPT 的界面或动画。

行为变化需同步更新需求、失败场景与验收标准。
