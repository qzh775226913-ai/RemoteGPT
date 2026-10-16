# 1.0.51 GitHub 发布验证

日期：2026-10-13。恢复基线：`7f857f7`。

## 变更

公开已签名的 1.0.51 APK、安装 ZIP、校验文件及许可。

## 验证

- Android：356 项通过，0 失败、0 错误、0 跳过。
- Python、文档、结构、图标、APK 构建与 Lint 通过；Lint 0 Error/Fatal、17 项 Warning。
Python 44 项。正式标签提交 930515ffadeba3376cee95b585c71519e1c15516，Release ID 411346657。五个附件的大小与摘要匹配，公开 APK 完整下载及验签通过。

[CI 1](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38354682540) · [CI 2](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38354688678) · [CI 3](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38355279194) · [CI 4](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38355684278) · [CI 5](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38355595981) · [CI 6](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38355609346) · [CI 7](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38355684307)

## 制品校验

- APK SHA-256：`d53f0b7b40c15a56f48521c3b866c5e64626c27aafeb0af2369b7d123ca7b80e`。
- 签名证书 SHA-256：`43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a`。

## 适用范围

自动化使用 Robolectric、合成协议和回环 SSH/SFTP。真机、OEM 通知、后台网络、Tailscale、真实模型及覆盖安装的证据范围见 [验收标准](../ACCEPTANCE.md)。历史验证结果不代表后续版本已执行相同检查。
