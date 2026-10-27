# Codex 对话显示覆盖

核对：2026-10-27；客户端基线 1.0.64；安装版 Codex 0.164.0 生成的协议。
这是显示能力调查，不自动新增功能需求，也不代表运行中的服务或账户一定启用所有字段。

## 证据

- [官方 App Server 文档](https://learn.chatgpt.com/docs/app-server)：item 生命周期、请求和通知。
- 本机离线生成 `v2/ThreadReadResponse.json`、`ServerNotification.json`、`ServerRequest.json`，未登录、调用模型或重启现有进程：

```sh
codex app-server generate-json-schema --out dist/codex-schema-0.164.0
```

- 客户端：[数据解析](../app/src/main/java/app/remotessh/ChatItem.java)、[事件处理](../app/src/main/java/app/remotessh/RemoteApp.java)、[显示](../app/src/main/java/app/remotessh/MainActivity.java)。
- 当前对话只读事件确认 MCP 带 `appContext.appName = GitHub`、`actionName = fetch`；客户端只取 `server` 与 `tool`。未保存私人会话、路径、连接身份或原始结果到仓库。

## 已有专门解析，但字段覆盖不全

| 信息 | 协议 | 当前显示与缺口 |
| --- | --- | --- |
| 用户／回复 | `userMessage`、`agentMessage` | 文字、图片和回复阶段已有；`memoryCitation`、`delivery`、消息内 `questions` 未专门处理 |
| 计划 | `plan`、`turn/plan/updated` | 计划正文及 delta 已有；结构化步骤、状态、解释未接入 |
| 思考摘要 | `reasoning` | 已有摘要、流式与恢复；仅显示服务端公开内容 |
| 命令 | `commandExecution` | 命令、输出和状态已有；`cwd`、`exitCode`、`durationMs`、`pluginId`、命令动作未展示 |
| 文件变化 | `fileChange`、`turn/diff/updated` | 路径、diff、整轮 diff 已有；变化类型、拒绝状态和增量 patch 元数据未完整呈现 |
| 插件／MCP 调用 | `mcpToolCall` | 已有 `server · tool`、结果 JSON、结构化图片；忽略 `appContext`、`pluginId`、`arguments`、`error`、耗时、只读提示及进度通知 |
| 搜索 | `webSearch` | 显示 query；忽略 `action` 和 `results`，未专门显示打开页面／查找及结果来源 |
| 看图／生成图片 | `imageView`、`imageGeneration` | 文件预览及旧版 result 回退已有；生成失败原因、修订提示、输入图片及透明背景等元数据未完整显示 |

## 只有类型名回退，缺少有效内容

| 信息 | Codex 0.164.0 item 类型 | 缺口 |
| --- | --- | --- |
| 动态工具 | `dynamicToolCall` | 工具名／命名空间、参数、内容项、结果和耗时未解析 |
| 外部工具结果 | `functionCallOutput` | 名称、命名空间与输出未解析 |
| 子代理 | `collabAgentToolCall`、`subAgentActivity` | 调用对象、代理状态、任务、模型和推理强度未解析 |
| 审查 | `enteredReviewMode`、`exitedReviewMode` | 审查开始／结束及内容未解析 |
| 压缩上下文 | `contextCompaction` | 缺少可读的压缩标记；`thread/compacted` 未处理 |
| 等待 | `sleep` | 未显示等待时长 |
| Hook 注入 | `hookPrompt` | 未解析内容片段；hook 开始／结束通知未显示 |

“只有类型名”来自 `ChatItem.from` 的默认回退，并非完整支持。
官方文档也可能使用旧名称 `collabToolCall`；此处以本机生成的版本化 schema 为准。

## 通知与交互缺口

- `thread/tokenUsage/updated`：未接入上下文 token、模型上下文窗口及本轮／累计用量；现有账户额度不是上下文用量。
- `item/mcpToolCall/progress`、终端交互及 `item/fileChange/patchUpdated`：没有对应展示。
- `thread/name/updated`、服务端队列／目标变化、`model/rerouted`：没有专门同步或提示。
- 自动审批审查、弃用通知、模型验证等事件缺少专门呈现；普通 `warning`／`configWarning` 已有。
- 权限增量请求、MCP elicitation 表单／URL、客户端动态工具执行不在现有三个支持请求中，按“不支持”回复；不能只增加标题就视为已支持。
- MCP App 交互资源与实时音频需要独立交互／渲染链路，当前未实现。

## 建议顺序

1. 插件可读名称、动作、成功／失败原因及进度；保留缺字段时的技术名称回退。
2. 结构化计划、退出码／耗时、上下文用量及压缩标记。
3. 子代理、审查、搜索来源与引用。
4. 另行设计 MCP 交互资源、权限表单和实时媒体，不把服务器请求当普通显示事件自动处理。

## 本轮图片故障

1.0.63 将所有 item 的 `text` 都按 Markdown 图片扫描；README 写入命令中的三个徽章被误当作附件，形成三块失败占位。
只读事件与命令内容能对应三条徽章 URL，原始看图文件存在；不是凭截图推断文件丢失。
1.0.65 限制为正文图片链接与显式结构化图片，并排除代码示例及转义语法。
真实图片读取、格式支持和网络失败仍遵守原有限制；本次未新增 SVG 解码。
