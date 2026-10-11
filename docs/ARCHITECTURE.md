# 架构、职责与状态归属

当前链路：原生 Android 界面 → 应用协调层 → SSH 上的 JSON-RPC → 可选 Python 持久托管 → 远端 Codex App Server。

## 模块边界

| 模块 | 入口及职责 |
| --- | --- |
| 界面生命周期 | `MainActivity` 管理页面、消息渲染、系统选择器和 Activity 回调；`BubbleActivity` 管理绑定会话的气泡页面 |
| 独立窗口 | `ProfileEditor` 管理连接草稿／凭据表单／私钥导入；`ModelPicker` 管理模型／强度／速度选择及监听器；`DialogUi` 保持聊天窗口的原有样式与触摸行为 |
| 导航／展示组件 | `RemoteBrowser`、`DirectoryPicker`、`RemoteScreens`；消息、问答、图像预览、缩略图和滚动各有独立组件 |
| 应用协调 | `RemoteApp` 维持连接、初始化、恢复、事件路由和当前选中状态的兼容接口；它不再实现窗口表单、完整发送流程或附件传输流程 |
| 会话容器 | `ConversationStore` 是会话缓存和临时背景分发的唯一所有者；`ConversationState` 保存每条会话的消息、草稿、权限／速度和运行状态 |
| 轮次／队列 | `TurnController` 管理创建／发送／引导轮次、一条待发消息及其附件、可自动发送标记；请求仍走统一回调路由 |
| 附件 | `AttachmentController` 管理批量上传、媒体读取、连接变化取消和主线程结果提交；`MediaFiles`／`FileAttachment` 定义格式、路径与限额 |
| 消息图片 | `MessageImageTile` 拥有可见缩略图、读取任务与有界重试；同服务器／会话的连接 generation 变化时释放旧任务并重新绑定，主线程核对作用域后才显示结果。消息区暂停更新仍刷新图片连接状态；`ThumbnailLoader`／`ThumbnailCache` 分别提供任务调度和共享有界缓存 |
| 目录／会话偏好 | `DirectoryController` 管理目录操作的 worker 和迟到结果；`ConversationPreferences` 管理稳定存储键及权限／速度／问答历史快照 |
| 传输／协议 | `SshTransport` 管理 SSH、SFTP 和辅助通道；`RpcClient` 管理 JSON-RPC framing、请求匹配和事件分发，不操作 Android View |
| 配置／数据 | `Vault`／`Profile` 保存连接；模型、额度、权限、版本、线程快照与问答日志由对应类解析 |
| 后台／通知 | `ConnectionService` 维持前台服务与唤醒锁；`LiveStatusNotifications` 及 `LiveActionReceiver` 处理会话通知、请求身份和点击路由 |
| 远端资产 | `remote_session.py` 托管 Codex 进程、SQLite 事件及重放；`conversation_state.py` 有界读取问答日志；`remote_maintenance.py` 检查／更新／明确重启 |

所有主 Java 文件按职责登记在 [模块登记](../tools/project_structure.json)。添加新类时登记所属模块，不靠一个平铺目录猜归属。当前保留包名与目录，避免纯目录搬迁造成大范围引用／测试变更。

`DialogUi` 和 `RemoteScreens` 分别保存聊天窗口与导航页面的现有视觉风格；本轮没有强行统一两套颜色或按钮尺寸。

## 三种状态生命周期

| 生命周期 | 示例 | 约束 |
| --- | --- | --- |
| 连接级 | SSH/RPC、连接 generation、登录初始化、恢复队列、全局模型目录 | 换连接使旧异步结果无效；不可保存为某条会话的状态 |
| 会话级 | threadId/turnId、消息、草稿、审批、权限／速度选择、busy/readOnly、问答历史 | 由 `ConversationState` 和 `ConversationStore` 隔离；后台结果按目标 threadId 分发 |
| 界面级 | View、Dialog、滚动位置、选择器待处理标识、可见缩略图 | Activity 拥有并清理；不能让旋转重启远端，也不能把销毁窗口的监听器留下 |

附件上传批次目前属于连接上的串行操作，上传／创建／恢复中的短暂切换限制是现有行为；不能简单移除这些限制。

## 会话分发与兼容接口

现有 UI 和通知读取 `RemoteApp` 的选中会话字段。为保持行为，本轮保留该兼容接口，`ConversationState.capture/apply` 做显式快照；**它尚未变成完全无副本的状态模型**。

`ConversationStore.dispatch` 按以下顺序运行：保存选中会话 → 应用目标会话 → 执行动作 → 在 finally 中保存目标并恢复选中会话 → 最外层结束后发布合并的变化。嵌套动作／异常也恢复选择、项目和最后会话。缓存的读写及作用域切换必须在主线程；后台分发期间不得让通知或 UI 看见临时选择。

新增会话字段须同时更新 `ConversationState` 的声明、capture/apply，并验证 A/B 隔离、异常恢复和重连。不要增加第二套手写切换代码。直接按状态对象工作的下一阶段迁移见 [技术债](MAINTENANCE.md)。

## 异步和协议规则

- SSH／文件 I/O 在 worker 执行；结果回到主线程。检查 generation，以及必要的传输实例、配置 revision、服务器／会话身份，迟到结果无效。
- `RemoteApp.request` 捕获发起请求的会话，完成时通过 `inConversation` 路由。独立控制器必须复用该入口，不能自行给 RPC 回调更新当前页面。
- 浏览用 `thread/read`，接续用 `thread/resume`；恢复活动会话与只读历史有不同路径。重放是同步屏障，不能当作实时完成来排队发送。
- ThreadStart 的沙盒使用配置枚举，TurnStart 使用 tagged policy；不要互换格式或随意添加未知协议字段。
- `TurnController` 的排队键包含连接配置 ID 和 threadId。断开清除自动发送许可，保留现有进程内待发内容；队列不保证手机进程被杀后的持久恢复。
- Python 桥接会记录恢复基线；新增客户端字段须同时检查透传、基线和旧托管兼容。资产更新不会自动替换正在运行的旧进程。
- 问答日志兼容只处理可确认身份的已接受请求；不从普通文字猜审批或表单，不输出未公开推理。

## 持久化与安全

保留已有 Vault 加密格式、SharedPreferences 名称／键、包名和签名。权限／速度键对可能含私人值的启动身份做哈希；不得把命令或凭据直接打印到日志。重构数据格式必须有旧数据迁移和可逆性说明。

远端托管脚本按内容哈希安装到 `~/.remote-gpt`，目录 0700、脚本 0600。生产 SSH、主机信任、上游登录和活跃任务不属于自动化夹具。此轮不更改资产协议或部署到真实远端。
