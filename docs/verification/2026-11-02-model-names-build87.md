# 2026-11-02 模型名称与输入区动画检查

恢复基线 `cd29d2b01cbf89dffb1d7a074a91b14b0b46d7c5`；模型显示提交 `280434b`，版本阶段 `921079c`。模型显示名在所有位置只移除开头的 `GPT-`，其余空格和连字符保留远端原样；目录与协议 ID 不变。

输入区偶发放大：检查 `ConversationChromeController` 的目标高度测量、动画取消与方向切换，以及 `ConversationFooterMotion`、`ConversationScrollView` 的底部预留和惯性处理。既有 `MainActivityTest` 覆盖动画逐帧单调性、反向、附件及输入高度变化，完整验收通过；没有复现用户观察到的偶发问题，也没有确认根因或更改动画。合成测试不能证明真机不会再出现该问题，仍待目标设备复现。

完整验收使用 `.venv/bin/python tools/verify.py --offline`：Android 579 项，失败／错误／跳过均为 0；Python 54 项、结构、图标、数学字形、合成 SSH 夹具和 APK 构建通过；Lint 30 Warning、0 阻断。初次因文档版本号未同步未通过结构检查，修正后重新执行完整验收通过。验证不调用真实模型或重启生产任务。

APK 包名 `app.remotessh`，versionName `1.1.1`，versionCode 87；签名校验通过，证书 SHA-256 `43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a` 与既有包一致。文件 4,870,546 字节，SHA-256 `63a313b8f90ea1e533937555d6a64f139bba78f3a3493427b43317bb07e112fc`。

Tailnet 地址 `http://100.109.221.115:8024/RemoteGPT-1.1.1-build87-Android-universal-debug.apk`，服务仅开放本包所在目录。本机绕过代理完整下载 HTTP 200，文件与本地 APK 逐字节一致；未验证另一台设备的网络、覆盖安装或动画效果。
