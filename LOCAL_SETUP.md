# 本机远端配置记录

日期：2026-09-28。账户：qzh-n100。

- Python 3.12.3 / sqlite3 3.45.1 可用。
- codex-cli 0.156.1 已安装，现有 ChatGPT 登录有效；未读取或输出凭据，未变更登录。
- 系统 SSH 服务 active/running，SFTP 子系统 `/usr/lib/openssh/sftp-server` 已配置且可访问托管目录。
- 非交互 SSH shell 的 PATH 可解析 `codex` 和 `python3`；启动命令保持 `codex app-server`。
- 用户 linger 已启用；未更改 SSH 认证策略、系统端口、现有任务或其他项目。
- 已安装与 1.0.12 APK 完全一致的托管脚本：`/home/qzh-n100/.remote-gpt/host-aS_cSb3Bogetn7mCnHGOiIquaUmCA4T84l2VtS_J6G4.py`。目录 0700，脚本 0600。

使用真实 Codex 完成 initialize 握手，杀掉检查客户端后托管与 Codex 进程仍存活，重连复用原 PID。未发送 thread/start 或 turn/start，不创建模型任务。检查专用进程和目录已清理。

手机使用 1.0.12，沿用原主机/账号/认证设置，开启默认的“远端持久会话”，启动命令为 `codex app-server`。首次连接按手机保存的配置 ID 自动启动专属托管进程，不需要手工启动服务。未预建任意手机配置 ID 的常驻实例，以免与手机实际实例错配。

本机回环 SSH 客户端没有可用的免密身份，未通过该路径测试 SSH 登录；没有为测试添加新 SSH 密钥或修改密码。SFTP 子系统及真实 Codex 托管验证分别完成，手机到本机的实际登录需沿用已有认证。

## 1.0.16 更新

新版托管脚本已安装到 `/home/qzh-n100/.remote-gpt/host-pXsPdV2KKY2ozXzW9YDwABlfCmNLvCIS15KrVZ9cKAs.py`，权限 0600，与 APK 资产一致。支持返回托管会话列表与恢复期间其他会话事件补发。

检测到旧托管实例还有活跃任务，因此未终止或重启该进程。1.0.16 手机端包含旧端兼容：记录已加载会话 ID，并在历史重放后补读其他会话快照。新脚本启动时仍优先连接存活的原托管实例，只有新建实例使用新守护进程代码，避免升级中断任务。

通过本机回环地址的隔离 SSH 测试服务验证，不改动系统 SSH 配置或用户凭据。
