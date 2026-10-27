# 维护规则与技术债

## 文档职责

| 内容 | 位置 |
| --- | --- |
| 当前行为与测试映射 | [REQUIREMENTS.md](REQUIREMENTS.md) |
| 模块与状态归属 | [ARCHITECTURE.md](ARCHITECTURE.md)、[模块登记](../tools/project_structure.json) |
| 验收标准及支持范围 | [ACCEPTANCE.md](ACCEPTANCE.md) |
| 环境与构建流程 | [DEVELOPMENT.md](DEVELOPMENT.md) |
| 版本验证结果 | `docs/verification/` |
| 历史版本记录 | `docs/history/` |

README 和公开 Release 只展示产品功能、截图、下载、基本使用步骤、变更与许可；不展示开发手册、测试／验收记录、校验摘要、排错和内部架构／回退入口。调查结论默认只在对话中交付，明确要求公开后才发布。工程验证、完整性检查和维护留档仍按本文件及 AGENTS.md 执行。

## 代码约束

- 新功能按模块职责组织；文件规模受模块登记约束。
- 会话缓存和临时分发由 ConversationStore 管理，主线程执行；回调绑定会话与连接 generation。
- UI 操作在主线程，SSH 与文件 I/O 在 worker；窗口负责释放监听器。
- 数据迁移、依赖升级、功能修改和发布分别提交。
- 版本、签名、存储格式和设备支持范围的变化需有独立验证记录。

## 技术债

| 项目 | 当前状态 |
| --- | --- |
| 会话状态双份字段 | RemoteApp 兼容字段与 ConversationState 快照并存；由 ConversationStore 统一分发 |
| 协调层规模 | 恢复、快照合并和事件处理仍部分集中于 RemoteApp |
| 视图协调 | 消息与媒体列表的部分生命周期逻辑仍位于 MainActivity |
| 测试边界 | 部分测试通过反射注入 RPC 和事件 |
| 工具链 | AGP/compileSdk 兼容提示及既有 Lint Warning 尚未消除 |
| 环境验证 | 真机后台、OEM 通知、真实 Codex、Tailscale 和覆盖安装证据见验收标准 |

完整维护约束见 [AGENTS.md](../AGENTS.md)。
