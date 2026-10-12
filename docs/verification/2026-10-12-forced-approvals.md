# 首次连接诊断与强制权限审批交付验证

变更前恢复提交：`8da2d25`（源码与 `b8c0866` 的 1.0.49 相同）。第一阶段连接诊断提交：`cfa4a23`，单独验证见 [连接诊断记录](2026-10-12-connection-diagnostics.md)。本记录覆盖最终源码的完整验收；阶段分别提交，保持版本、依赖、包名、签名和偏好格式，不发布或部署生产服务。

## 强制审批行为

审批弹窗增加红字“全部允许(强制)”。警告文本严格为“新的审批权限生效前，客户端将会自动允许全部审批请求，继续吗？”。仅在用户确认后，为请求所属对话自动回复当前和后续的命令／文件权限审批；普通方案选择、补充信息、未知交互均不自动回答。取消没有授权效果。

启用时为下一轮选择完全访问；用户仍可改选下一轮权限。输入区底部使用完全访问同形的红色闪电，权限菜单增加红色闪电“强制允许”；再次点击可关闭。临时授权不持久保存，断开连接即清除。只有实际应用最新权限选择的下一轮启动成功才结束授权；旧轮结束、历史重放、启动失败、提交后的改选或新增授权不能被迟到响应误清除。请求参数和实际生效权限使用提交时快照，不使用回调时的新选择。

`ForcedApprovalController.State` 通过 ConversationState capture/apply 归属会话，调度只在主线程，经既有 answer/RPC 通道提交。背景对话授权、嵌套异常、迟到响应不会暴露或改变所选对话；重复请求仅提交一次。重放屏障内不发送自动审批。只读历史、缺失请求身份或不能提交的请求不隐藏为已处理；提交失败恢复手动审批，不循环自动重试。PermissionPicker 拥有列表和确认窗口并释放监听器。

## 实际验证

最终执行：`JAVA_HOME=/home/qzh-n100/.local/jdk/jdk-17.0.20.1+1 ANDROID_HOME=/home/qzh-n100/Android/Sdk PYTHONPATH=/tmp/remote-ssh-test-libs python3 tools/verify.py --offline`。

| 检查 | 结果 |
| --- | --- |
| Android 测试 | 346 项通过，0 失败、0 错误、0 跳过 |
| Python 测试 | 44 项通过 |
| 文档／结构／规模 | 通过，未提高文件预算 |
| 图标一致性 | 28 项及共享变体通过，复用现有 bolt |
| APK | assembleDebug 成功 |
| Lint | 0 Error/Fatal，17 项既有 Warning |
| 差异空白检查 | git diff --check 通过 |

最终 Gradle 全量执行 1 分 30 秒，summary 的 scope=full、full_acceptance=true。另有 AGP 8.7.3 对 compileSdk 36 的既有兼容提示。中途完整运行发现暗色测试 qualifier 顺序错误（342 项中 1 项配置失败），修正并补充只读／身份／迟到授权边界回归后，定向及最终完整运行通过；不采用中途或旧报告代替最终结果。

新增强制审批相关 21 项回归：14 项状态／协议、7 项原生绘制／UI。覆盖确认和取消、当前及后续权限、正常问题表单、背景会话隔离与异常恢复、重复身份、重放屏障、下一轮生效／拒绝／改选／迟到、新授权、跨会话响应、发送异常、断开清理、只读和身份无效、警告失效及监听器释放。

已查看构造数据的原生绘制截图：`app/build/screenshots/force-approval-prompt.png`、`force-approval-warning.png`、`force-permission-menu.png`、`force-approval-narrow.png`、`force-permission-menu-dark.png`。411dp／320dp、明暗主题可见红字审批按钮、原形红色闪电和警告；窄屏按钮无省略。截图属于 Robolectric，不是手机截屏，也不证明 OEM 表现。

本地测试 APK：`app/build/outputs/apk/debug/app-debug.apk`；包名 app.remotessh、versionName 1.0.49、versionCode 50，未作为新版发布。APK SHA-256：`dee9b4dc53313fa81ff5ec6d56fcb42d8a5687cb7238b4e26cb11300e111f6d3`。apksigner 校验通过，签名 SHA-256 为 `43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a`，与既有版本一致。

## 边界与恢复

只使用合成 RPC、Robolectric 和回环 SSH/SFTP。没有连接生产 SSH、读取真实模型凭据、调用模型、更新／重启真实任务、安装手机或发布下载地址。真实 Codex 指定版本、macOS、Tailscale、目标手机后台／通知、升级覆盖安装继续按 [验收边界](../ACCEPTANCE.md) 待验。

阶段可以分别 revert，完整回到变更前时从 `8da2d25` 建立独立分支。不要 reset/clean；Git 回退不能撤回运行中远端的权限决定，也不能用来回退真实任务。本轮未部署真实远端。
