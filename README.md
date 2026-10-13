# RemoteGPT for Android

原生 Android SSH 客户端，通过远端 `codex app-server` 管理项目、对话、审批和附件。手机没有内置模型服务，也没有调用 ChatGPT 私有接口。

当前交付版本为 **1.0.50 / versionCode 51**，包名 `app.remotessh`。本版增加首次连接环境诊断及经明确确认的会话级强制权限审批；保留此前的问答历史定位、图片重连恢复和有界重试。保留原签名和存储格式，可覆盖安装。最低 Android 8（API 26），compileSdk 36，targetSdk 35。

[局域网下载安装包](http://192.168.1.74:8000/RemoteGPT-1.0.50-Android-universal-debug.apk)，或手机启用同一网络的 Tailscale 后使用 [Tailscale 下载](http://100.109.221.115:8000/RemoteGPT-1.0.50-Android-universal-debug.apk)。校验和及验收边界见 [1.0.50 交付记录](docs/verification/2026-10-12-1.0.50.md)。

## 使用

1. 在远端安装 Codex CLI，并用运行 SSH 的同一账号完成登录。远端需要能访问所配置的模型服务。
2. 手机添加主机、端口和用户名，选择密码、私钥或 Tailscale SSH 身份认证；首次连接核对 SSH 主机指纹。
3. 默认启动命令是 `codex app-server`。非交互 SSH 找不到程序时填写其绝对路径；交互式 TUI 不能直接替代 JSON-RPC stdio 服务。
4. 默认开启远端持久会话，需要 Python 3；应用会在远端 `~/.remote-gpt` 安装托管脚本。断网后任务可继续，重连补回输出和待处理请求。
5. 浏览历史不会自动接续或发送消息；可以先准备文字、图片和普通文件，接续后手动发送。运行中的消息可以立即补充或排队。

Tailscale 需先在手机连接同一网络。普通 SSH 仍需要密码或私钥；Tailscale SSH 免密码登录取决于 tailnet 的 SSH 策略。系统气泡、流体云和后台保活还取决于 Android 与厂商设置。

本版包含连接环境诊断和临时强制审批。持久模式检查 Python 3.8 以上及托管标准库；缺少 Python 时可在错误详情中明确选择关闭持久模式重连。环境检查不调用模型，不能代替模型服务网络验收。

当前源码的强制审批入口按频率显示：同一对话在前 30 秒已有 3 次不同的实时权限审批后，第 4 次及后续符合条件的弹窗才提供红字“全部允许(强制)”。正常权限菜单隐藏该项，下方仅显示“发送消息以更新权限”；在审批弹窗确认后，底部和菜单才显示红色闪电“强制允许”。仅自动允许本对话的命令／文件权限请求，方案选择和补充信息仍正常弹出。启用后下一轮选择完全访问，用户仍可改选权限，下一轮成功应用后结束临时授权并隐藏该项；点击菜单中的强制允许可关闭，断开连接也会清除临时授权。频率计数不包含重复请求、历史重放和普通提问。该显示调整尚未发布到既有 1.0.50 下载文件。

## 当前能力与边界

- 按服务器浏览项目和对话；一条 SSH 连接中运行多个会话，分别处理消息、草稿、审批和通知。
- 远端模型目录分页刷新；按服务器提供的能力选择推理强度和速度。权限包含沿用远端、按需审批、只读和完全访问。
- 原生结构化询问，以及已接受异步询问的日志兼容；查看思考摘要、执行过程、历史问答和错误。
- 每条消息最多 4 张图片（每张 20 MiB）及 8 个普通文件（每个 100 MiB）；预览、缩放、复制和保存。
- 账户额度查看、远端版本检查、明确确认后的更新和重启。

“只读”是权限设置，仍可能读取文件或使用允许的工具；它不等于严格仅聊天，也不保证零 token 或免费。托管进程／主机故障仍会中断任务。通知是否成为厂商流体云、设备杀进程后的后台表现，以及 HEIF/AVIF 等平台格式，都不能用模拟测试代替真机验收。

完整范围、例外与证据见 [需求](docs/REQUIREMENTS.md) 和 [验收边界](docs/ACCEPTANCE.md)。

## 开发与验证

需要 JDK 17、Android SDK 36、Python 3.12。在独立 Python 环境安装测试依赖：

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements-dev.txt
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_HOME="/path/to/android-sdk"
.venv/bin/python tools/verify.py
```

完整验证会自行启动并清理回环 SSH 夹具，运行 Python 测试、文档和结构检查、图标一致性检查、Android 测试、构建和 Lint。不会调用真实模型或连接生产 SSH。Gradle 首次运行需要下载依赖；已有缓存时可加 `--offline`。测试报告在 `app/build/`，完整流程结果在 `app/build/verification/summary.json`。

## 维护导航

| 文档 | 用途 |
| --- | --- |
| [需求与行为](docs/REQUIREMENTS.md) | 当前产品范围、稳定需求编号、测试对应关系 |
| [架构与状态归属](docs/ARCHITECTURE.md) | 模块职责、线程和异步边界、兼容设计 |
| [开发手册](docs/DEVELOPMENT.md) | 环境、验证入口、定向测试、CI、构建产物 |
| [验收边界](docs/ACCEPTANCE.md) | 自动化能证明什么、哪些仍需真机或真实服务 |
| [维护规则与技术债](docs/MAINTENANCE.md) | 变更流程、风险、后续整理顺序 |
| [回退说明](docs/ROLLBACK.md) | 原始提交、备份、分阶段撤回、手机安装限制 |
| [1.0.50 验证与下载](docs/verification/2026-10-12-1.0.50.md) | 连接诊断、强制权限审批、安装包与下载地址 |
| [1.0.49 验证](docs/verification/2026-10-12-1.0.49.md) | 前一版问答历史排序修复 |
| [1.0.48 验证](docs/verification/2026-10-11-1.0.48.md) | 前一版问答副本／图片修复的实际证据 |
| [项目整理验证](docs/verification/2026-10-11.md) | 整理基线、模块拆分与恢复证据 |
| [图标系统](docs/icon-system.md) | 图标来源和生成方式 |
| [历史版本记录](docs/history/CHANGELOG-through-1.0.47.md) | 原始发布历史，仅作追溯 |

后续维护先读 [AGENTS.md](AGENTS.md)。当前说明优先于历史归档；发生行为变化时同时更新需求、验收和验证记录。

协议依据：[Codex App Server](https://learn.chatgpt.com/docs/app-server)。
