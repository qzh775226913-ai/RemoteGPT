# 2026-10-25 SFTP 文件通道打开失败恢复

涉及需求 R-13，沿用 R-14／R-15 的统一 SFTP 传输入口。恢复基线：`1cc8905`；功能分支：`fix/sftp-channel-open`。本次不递增版本、不发布、不安装手机、不修改生产 SSH／Tailscale 配置或重启远端任务。

## 现象与证据

用户报告：通过 Tailscale 连接时，对话仍可收发，图片上传显示 `channel is not opened`；断开重连后图片恢复正常。这是原安装包的一次用户操作反馈，不是本次源码改动的真机验收。

检查固定依赖 JSch 2.28.0 的 Channel 字节码，确认此文案来自 channel-open 未获得有效确认（拒绝或超时）。只读检查当前主机 SSH／Tailscale 日志，未发现能直接归因本次上传失败的记录。具体网络／服务器触发原因仍未确定，不能据此认定 Tailscale 服务故障或并发通道超限。

## 变更

SshTransport 仅对 SFTP 连接建立阶段的这一明确错误释放通道、等待 500ms 并最多重试一次；重新检查关闭、连接和线程取消状态。持续失败显示中文 SFTP 通道说明与重连／服务器检查建议，保留异常原因。文件传输开始后不重放上传、rename 或其他文件操作。没有新增会话状态、存储格式、UI 或依赖。

合成 SSH 夹具增加首次拒绝与持续拒绝两种身份。新增集成回归覆盖拒绝后成功上传／原字节下载，以及持续拒绝有界失败；两种情况下原对话 RPC 仍可往返。

## 验证

使用开发 Python 环境执行完整入口 `.venv/bin/python tools/verify.py --offline`：

- Android 404 项测试，0 失败／错误／跳过；其中 SshIntegrationTest 17 项，含新增两项通道拒绝回归。
- Python 44 项测试全部通过；文档／模块登记／源文件规模、生成图标和公式字形检查通过。
- Debug APK 构建成功；Lint 0 Error/Fatal、19 项既有 Warning，与上一轮相同，无新增 Warning。保留 AGP compileSdk 36 兼容性提示及既有 deprecated／unchecked 编译提示。
- Gradle 用时 5 分 31 秒；完整入口 `full_acceptance=true`。
- 更新验证记录后重跑项目结构检查与 `git diff --check`，均通过。

## 限制

本轮自动化只使用回环 SSH/SFTP 与假 App Server，不使用真实 Codex 凭据或调用模型。本次恢复逻辑尚未在目标手机／真实 Tailscale SSH 上安装验收；OEM、网络切换、长时间后台及真实模型兼容性不在本轮证据内。用户重连后的成功不能证明新增重试能消除所有持续通道故障。
