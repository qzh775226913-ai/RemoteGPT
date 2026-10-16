# 连接诊断验证

日期：2026-10-12。恢复基线：`8da2d25`。

## 变更

检查非交互 SSH 环境、Python、Codex、SFTP、家目录与公开登录状态；区分失败原因。

## 验证

- Android：325 项通过，0 失败、0 错误、0 跳过。
- Python、文档、结构、图标、APK 构建与 Lint 通过；Lint 0 Error/Fatal、17 项 Warning。
Python 44 项。覆盖持久模式禁用确认、自定义启动器、程序路径及旧服务兼容。

## 适用范围

自动化使用 Robolectric、合成协议和回环 SSH/SFTP。真机、OEM 通知、后台网络、Tailscale、真实模型及覆盖安装的证据范围见 [验收标准](../ACCEPTANCE.md)。历史验证结果不代表后续版本已执行相同检查。
