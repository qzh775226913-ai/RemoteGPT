# 连接环境诊断验证

变更前恢复提交：`8da2d25`，源码与已交付 1.0.49 的 `b8c0866` 一致。保持版本号、依赖、签名、存储格式及真实远端不变。

SSH 认证后检查非交互环境；持久模式检查 Python 3.8 及 sqlite3/fcntl/Unix socket，可识别的直接启动命令检查程序和 app-server 帮助，所有连接检查 SFTP/家目录。托管写入错误单独解释。自定义启动器不执行附加启动探测；握手后读取公开账户状态，旧服务不支持该 API 时保持兼容。Python 失败的诊断窗口提供明确确认后关闭持久模式并重连的入口，不静默降级。检查不读取认证文件、不调用模型，不保证上游真实网络连通。

完整入口实际执行：`JAVA_HOME=/home/qzh-n100/.local/jdk/jdk-17.0.20.1+1 ANDROID_HOME=/home/qzh-n100/Android/Sdk PYTHONPATH=/tmp/remote-ssh-test-libs python3 tools/verify.py --offline`。325 项 Android 测试，失败/错误/跳过均为 0；44 项 Python 测试通过；结构、文档、图标检查通过；assembleDebug/lintDebug 成功，Lint 0 阻断项、17 项既有 Warning。Gradle 运行 1 分 43 秒。首次验证发现测试中的 Files.writeString 不在编译 API 中，替换为 Files.write 后完整重跑通过。

新增回归覆盖缺 Python 不自动降级、非持久模式在缺 Python 环境可连接、真实辅助 SSH 检查、缺 Codex/SFTP 分类、带空格的程序路径、自定义启动器秘密不进入探测输出、缺登录与自定义 provider/旧 API 兼容。均使用合成 RPC 和 loopback SSH，不连接生产服务。

未取得真实 Android 手机、macOS、tailnet、真实模型登录/推理、生产安装权限或覆盖安装证据；这些继续按 [验收边界](../ACCEPTANCE.md) 待验。本阶段未发布 APK或部署远端。撤回时独立 revert 本阶段，或从恢复提交建立新分支，不 reset/clean。
