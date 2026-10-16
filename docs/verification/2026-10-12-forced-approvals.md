# 临时权限授权验证

日期：2026-10-12。恢复基线：`8da2d25`。

## 变更

命令／文件审批的临时自动授权，按会话与连接隔离。

## 验证

- Android：346 项通过，0 失败、0 错误、0 跳过。
- Python、文档、结构、图标、APK 构建与 Lint 通过；Lint 0 Error/Fatal、17 项 Warning。
Python 44 项；新增 21 项回归，覆盖确认、去重、重放、失败恢复和窗口释放。Robolectric 截图覆盖明暗主题、320dp／411dp。

## 制品校验

- APK SHA-256：`dee9b4dc53313fa81ff5ec6d56fcb42d8a5687cb7238b4e26cb11300e111f6d3`。
- 签名证书 SHA-256：`43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a`。

## 适用范围

自动化使用 Robolectric、合成协议和回环 SSH/SFTP。真机、OEM 通知、后台网络、Tailscale、真实模型及覆盖安装的证据范围见 [验收标准](../ACCEPTANCE.md)。历史验证结果不代表后续版本已执行相同检查。
