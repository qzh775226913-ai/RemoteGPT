# 2026-10-27 GitHub 兼容性与使用说明

恢复基线：`b27fd78`。

本轮仅调整公开文档：中英文首页补充 Android、Linux、macOS、原生 Windows 的适用范围、远端依赖、网络入口和首次使用步骤；中英文详细指南同步到 1.0.65，并补充 WSL、Linux 虚拟机、NAS 与容器的条件性说明。未修改应用行为、依赖、版本、签名或发布制品。

本地验证结果：

- `.venv/bin/python tools/check_project.py`：通过。
- `.venv/bin/python tools/verify.py --python-only`：项目结构与链接、28 个图标、数学字形及 50 项 Python 测试全部通过。
- `git diff --check`：通过。

本轮未重新构建 APK 或运行 Android 测试，也未连接真实 Linux、macOS、Windows、WSL、NAS 或容器主机。Linux 为项目主要目标；macOS 和满足条件的 WSL／Linux 虚拟机说明基于当前协议与环境约束，不代表这些平台已经完成真实设备验收。原生 Windows 明确列为暂不支持。
