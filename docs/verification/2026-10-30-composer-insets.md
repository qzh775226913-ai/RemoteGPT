# 2026-10-30 输入区键盘边距修复

恢复提交 `0b856a4`，功能提交 `03ff8c5`。用户报告偶发输入框尺寸和位置异常，点击输入框再点外部才恢复。主界面使用 `adjustResize`，同时旧式系统窗口 Insets 回调给根布局设置底部 padding；Android 11+ 对输入法显示、隐藏的 Insets 与窗口缩放协同时可能出现不一致。修复让 Android 11+ 主窗口自行处理系统栏与输入法 Insets，底部只取两者较大值；Android 8–10 沿用原路径。聊天输入区结构、输入状态和传输逻辑未改变。

新增 MainActivityTest：依次派发系统栏、输入法显示、输入法收起 Insets，核对底部留白 32→560→32，输入框对象不被重建。定向测试通过。

完整 `.venv/bin/python tools/verify.py --offline` 通过：Android 457 项，0 失败／错误／跳过；Python 54 项；结构、图标、公式字形和 APK 构建通过；Lint 16 项 Warning、0 Error/Fatal，full_acceptance=true。Gradle 用时 4 分 14 秒。合成 SSH 夹具，无真实模型调用或生产服务改动。

测试 APK：app.remotessh 1.0.68 / code 70，Android 8+，4,776,335 字节，SHA-256 `148406edff390723ee6cf55f74cac0eba2e062d04f9bd8de12feac6d68c025c3`，原有调试签名证书 SHA-256 `43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a`。独立临时目录的 LAN 192.168.1.74:18765 与 Tailscale 100.109.221.115:18765 均返回 HTTP 200，完整下载字节和摘要一致。未发布 GitHub 稳定版；正式版仍为 1.0.67。

Robolectric 验证 Insets 和视图尺寸路径，不能代替实际手机和输入法厂商组合验收。请在发生问题的目标手机上验证键盘显示、收起、多行输入与对话切换；此前局域网从手机直连失败的边界仍在，Tailscale 入口由后台下载服务维持。
