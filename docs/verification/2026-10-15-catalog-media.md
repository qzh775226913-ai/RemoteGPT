# 模型目录与下载类型验证

日期：2026-10-15。恢复基线：`3bca15e`。

## 变更

速度采用远端名称、描述和精确 ID；保存 Intent 按扩展名或内嵌图片 MIME 声明类型。

## 验证

- Android：359 项通过，0 失败、0 错误、0 跳过。
- Python、文档、结构、图标、APK 构建与 Lint 通过；Lint 0 Error/Fatal、17 项 Warning。
Python 44 项。对照 codex-cli 0.162.1 生成的 ModelListResponse、ConfigRequirementsReadResponse、ThreadStartParams 和 TurnStartParams Schema。覆盖未来档位、别名精确匹配、默认模型识别和 PNG／JPEG／WebP／GIF 文件类型。

## 适用范围

自动化使用 Robolectric、合成协议和回环 SSH/SFTP。真机、OEM 通知、后台网络、Tailscale、真实模型及覆盖安装的证据范围见 [验收标准](../ACCEPTANCE.md)。历史验证结果不代表后续版本已执行相同检查。
