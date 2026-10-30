# 2026-10-30 新对话输入框状态复位

恢复提交 `eacd512`，修复提交 `d112495`。用户反馈 1.0.69 在进入新对话时仍需点击输入框才能恢复正常。先前仅在焦点／键盘可见性变化时收起输入框；对话切换虽然替换草稿，却保留了上一对话的焦点和最多六行高度。现在由 MainActivity 在可见对话切换时把焦点交给根视图、收起输入框并重新应用窗口边距；直接新建空对话也执行该复位。原有草稿所属会话和输入文本存储不变。

定向 MainActivityTest 验证从展开且带多行草稿的旧对话切换到新对话后，草稿清空、最多一行且输入框失焦，无需额外点击。完整 `.venv/bin/python tools/verify.py --offline` 通过：Android 459 项、0 失败／错误／跳过，Python 54 项；结构、图标、公式字形、APK 构建通过；Lint 16 Warning、0 Error/Fatal，full_acceptance=true。Gradle 用时 3 分 28 秒。

按用户要求继续使用 app.remotessh 1.0.69 / versionCode 71，不递增版本。新制品采用新文件名避免手机／浏览器缓存取到之前同版本测试包：`RemoteGPT-1.0.69-new-chat-composer-debug.apk`，4,776,815 字节，SHA-256 `2399aa51ad4242282b5cbf5b6f7756e1aa8f2bf192ccddb7967eddea5cda2410`；调试签名证书仍为 `43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a`。LAN 192.168.1.74:18765 和 Tailscale 100.109.221.115:18765 均完整下载、HTTP 200、字节摘要一致。未更新 GitHub 正式版；真机与厂商输入法的实际效果需目标设备确认。
