# 1.0.14 验证记录

流体云改为持续显示最新思考正文、执行输出和回复片段，去除静态会话标题。新审批/询问采用高优先级渠道请求弹出，提供审批按钮或填写信息入口；同请求只提醒一次，收起运行状态后新请求可重新提醒。原有气泡、历史冻结和远端托管协议未修改。

执行 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`，BUILD SUCCESSFUL。100 项测试，90 通过、0 失败、10 项 SSH 集成用例因未启用独立服务而跳过。Lint 0 errors / 8 warnings。新增测试覆盖服务节流后的正文流式更新、长输出保留最新片段、新轮不显示旧回复、新审批唤起及去重、同 ID 新请求再次提醒、询问正文及入口。

测试采用 Robolectric SDK 35，尚未在一加 15T 真机验证本版本。用户已确认上一版能显示流体云；本版自动弹出和按钮布局仍由 ColorOS 与通知渠道/横幅设置决定，未使用强制全屏 Activity。新增渠道名称“流体云 · 审批与询问提醒”。

版本 1.0.14 / versionCode 15，签名保持原 Debug 证书。产物 dist/RemoteGPT-1.0.14-debug.apk 与 dist/RemoteGPT-1.0.14-source.zip。

---

# 1.0.13 验证记录

日期：2026-09-28。

本版新增面向一加 15T / ColorOS 16 的 Android Live Updates 实时状态/审批通知，保留已有会话气泡；阅读历史时冻结消息区，返回底部才补齐最新消息。服务端托管协议和已安装脚本不变，未迁移到 tmux，也未重启用户远端任务。

验证命令：`./gradlew :app:assembleDebug :app:testDebugUnitTest` 和 `./gradlew :app:lintDebug`，使用 JDK 17、Android SDK 36。

结果：两次最终命令均 BUILD SUCCESSFUL。97 项用例中 87 项通过、0 失败、10 项 SSH 集成用例因本轮未启用独立服务而跳过。Lint 0 errors / 8 warnings，均为国际化/既有资源目录提示。构建工具另提示 AGP 8.7.3 官方测试上限为 SDK 35，本轮 SDK 36 的编译、打包与检查已完成。

新增回归覆盖：

- 历史区上方消息变长、底部消息持续变长、新消息插入时，显示文字、原 TextView 实例、消息区总高度、滚动坐标不变；应用数据继续接收更新。
- 手动滑回已显示内容底部或点向下按钮后补回新消息、更新旧消息内容，定位最新并继续跟随。
- 实时卡片具有标准 BigTextStyle、ongoing 标记、实时通知申请字段、私密可见性及审批操作；已有 BubbleMetadata 会话气泡同时存在。
- 同 ID 新审批不会复用旧审批的按钮 token；过期按钮无效；重复点击只发送一次；断线后旧按钮无效；重放及补充信息请求不提供直接批准按钮。
- 本轮实时通知被收起后不会因后续更新重新出现，下一轮任务可再次显示；完成后撤销实时通知且保留气泡。

已查看 Robolectric 原生渲染的 freeze-reading.png 历史阅读截图。测试运行时为 Android 15（含既有 Android 9 回退测试）；本机没有连接 Android 真机/模拟器，未在一加 15T 上实测 Android 16/ColorOS 流体云展示、展开动作和锁屏授权。API 36 专有调用由版本检查保护并通过 SDK 36 编译，最终是否提升为流体云由 ColorOS 与用户设置决定。本记录不把通知申请标记等同于已在真机显示流体云。

包名 app.remotessh，versionCode 14 / versionName 1.0.13。签名保持原 Debug 证书，可覆盖已有版本；最低 Android 8，targetSdk 35，compileSdk 36。

产物：dist/RemoteGPT-1.0.13-debug.apk、dist/RemoteGPT-1.0.13-source.zip、dist/screenshots/1.0.13/。

---

# 1.0.12 验证记录

日期：2026-09-28。

完成内容：只在消息底部跟随更新；Android 系统气泡及展开审批界面；通过 SSH/SFTP 安装的独立远端托管进程与重连事件恢复。未查看路径中含 claude 字符的目录内容。

执行：`JAVA_HOME=/home/qzh-n100/local/jdk-17.0.19+10 ANDROID_HOME=/home/qzh-n100/Android/Sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -PsshFixture`。

结果：BUILD SUCCESSFUL，91 项测试全部通过，0 失败、0 跳过。其中 10 项在本机回环地址的隔离 SSH 服务上执行，覆盖主机指纹、密码/私钥认证、SFTP 操作，以及托管脚本经 SFTP 安装后断开 SSH、重连同一远端进程、补回 3,000 条流式更新和审批请求。Lint 0 errors、7 warnings（文字国际化及已有资源目录限定符提示）。

另执行 `python3 -m unittest tools/test_remote_session.py -v`，2 项通过：直接杀掉客户端进程后远端继续输出；重连完整补回历史并衔接实时消息；未处理审批保留，重复审批不重复提交；旧连接延迟返回的请求 ID 不会污染新连接。

原生 UI 回归覆盖：上翻到顶部/中间时新增消息、流式更新、延迟布局和可视区域尺寸变化保持阅读位置；点回到底部后继续跟随；气泡通知具有 MessagingStyle、会话快捷方式和 BubbleMetadata；Android 9 回退普通通知；展开气泡后只有点击按钮才答复，已失效审批按钮不会发送答案。历史重放期间禁用发送/引导，历史完成事件不会自动提交排队消息。

已查看 Robolectric 原生渲染的审批面板和历史阅读截图。截图使用构造数据，只展示应用面板，不代表手机系统实际弹出的气泡外壳。

APK 包名 app.remotessh，versionCode 13 / versionName 1.0.12。apksigner 验证通过，签名 SHA-256 与 1.0.11 一致，可覆盖升级。

限制：没有连接 Android 真机或用户实际 SSH/Codex 账户，没有发出真实模型请求；厂商气泡设置、通知授权、Doze/VPN 后台联网仍需在手机上验收。远端需 Unix/Linux/macOS、Python 3.8+（sqlite3）及 SFTP。持久模式保证的是 SSH/手机进程脱离后不主动结束远端，不能抵抗主机重启、系统清理用户进程、托管/Codex 崩溃、磁盘故障。旧版正在运行的 stdio 任务不会自动迁入新托管进程。

产物：`dist/RemoteGPT-1.0.12-debug.apk`、`dist/RemoteGPT-1.0.12-source.zip`、`dist/screenshots/1.0.12/`。

---

# 1.0.11 验证记录

日期：2026-09-25。

执行：`JAVA_HOME=/home/qzh-n100/local/jdk-17.0.19+10 ANDROID_HOME=/home/qzh-n100/Android/Sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。

结果：BUILD SUCCESSFUL。86 项测试中 77 项通过、0 失败、9 项 SSH 集成测试因未启用测试服务而跳过。Lint 0 errors、6 warnings：5 项文字国际化提示，1 项 minSdk 26 下资源目录 v26 限定符冗余提示。

使用 aapt2 检查实际 APK：应用名称 RemoteGPT，桌面入口指向 `res/mipmap-anydpi-v26/ic_desktop.xml`；确认选定 PNG 和自适应图标资源已打包。新图标只配置在启动 Activity，通知栏仍引用原单色 drawable，应用内未加入新图标。已查看原生测试渲染的首页截图，RemoteGPT 标题正常显示。

APK versionCode 12 / versionName 1.0.11，包名仍为 app.remotessh；apksigner 验证通过，签名 SHA-256 与 1.0.10 一致，可以覆盖安装。安装包及源码位于 `dist/RemoteGPT-1.0.11-debug.apk`、`dist/RemoteGPT-1.0.11-source.zip`。

本轮未连接 Android 真机，未验收不同厂商桌面的图标裁切和缓存刷新。未执行真实 SSH 或模型请求。

---

# 1.0.10 验证记录

日期：2026-09-25。

执行：`JAVA_HOME=/home/qzh-n100/local/jdk-17.0.19+10 ANDROID_HOME=/home/qzh-n100/Android/Sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。

结果：BUILD SUCCESSFUL。86 项测试中 77 项通过，0 失败，9 项 SSH 集成测试因未启用本地 SSH 夹具而跳过。Lint 0 errors、5 warnings，均为界面文字国际化提示。

新增原生布局回归覆盖 320dp 窄屏、360dp 深色手机、1280dp 平板：加号位于操作行左端，模型文字无省略且完整布局，权限与发送按钮不重叠。四张草稿图片可显示缩略图，界面重建后保留；移除图片与新建对话会清除对应预览；上传中显示预览，断开时清除待上传预览。发送失败测试补充断言缩略图仍保留。

已查看 `dist/screenshots/1.0.10/composer-narrow.png`、`composer-dark.png`、`composer-tablet.png`。截图由 Robolectric 原生 Android 渲染生成，使用合成图片及模型数据。未连接 Android 真机或模拟器，未验证真实系统文件选择器、真实网络上传或用户账户模型请求。

APK versionCode 11 / versionName 1.0.10，签名验证成功，与 1.0.9 相同，可覆盖安装。

---

# 1.0.9 验证记录

日期：2026-09-25。

**83 项测试全部通过，0 失败、0 跳过**。assembleDebug、testDebugUnitTest（启用 SSH 夹具）、lintDebug 成功；Lint 0 errors、5 warnings。

模型面板回归验证：使用服务器正式名称，模型按钮与权限图标同一行；模型的 Low / Medium / High 映射为滑条三个档位，默认 Medium，切换到 High 并应用后按钮显示 Model A · High。按钮存在背景，窗口动画已配置；原有 RPC、SSH/SFTP、滚动、运行中发送选择等回归通过。

已查看原生渲染截图 model-effort-picker.png 和 inline-model-permission.png，保存于 dist/screenshots/1.0.9/。截图使用合成模型数据；对话框截图由测试工具独立布局，不代表真机窗口高度。未进行真机拖动手感、触觉与动画流畅度验收。

APK 签名与前版一致，versionCode 10 / versionName 1.0.9，可覆盖安装。本轮没有对真实账户发起模型请求。

---

# 1.0.8 验证记录（含滚动跟随修复）

日期：2026-09-25。

最终：**83 项测试全部通过，0 失败、0 跳过**；assembleDebug、testDebugUnitTest（启用 SSH 夹具）、lintDebug 成功。Lint 0 errors、5 warnings。

新增验证：

- 运行中发送先展示“立即引导 / 稍后发送”，打开选择面板不会自行发送；选择后显示可取消的待发消息；面板打开后轮次变化时不发送并保留草稿。
- 待发消息只在当前轮正常完成后以 turn/start 发送，按主机和对话隔离；禁止覆盖未发送消息；不附带无关草稿图片，断开连接后保留但不自动发送。
- 多条长消息重建、流式文字增长、异步内容变高、可视区域缩小时均定位最新底部。绘制前校正位置，避免布局中间态造成短暂上跳。
- 原有模型/推理强度、权限、字号、思考摘要、RPC、SSH/SFTP 回归通过。

原生截图已查看：`running-send-options.png`、`queued-message.png`；保留滚动回归截图及模型面板、权限图标、字体设置截图于 `dist/screenshots/1.0.8/`。截图为测试夹具，无真实账户模型输出。

APK 签名与前版一致，versionCode 9 / versionName 1.0.8，可覆盖安装。没有额外发布 1.0.7 APK，滚动修复合并于 1.0.8。

边界：队列在 App 进程内，每个对话一条；系统终止进程后不保留。当前引导/排队仅文字。停止或失败后可手动发送待发消息，不自动继续失败任务。真实账户的摘要可用性、真机后台保活和 403 的远端原因仍不在本轮自动化验收范围。

---

# 1.0.6 验证记录

日期：2026-09-25。

最终构建、Lint、SSH 集成回归成功。**79 项测试，0 失败，0 跳过**；Lint 0 errors、5 warnings。执行 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug -PsshFixture --offline`。

新增与扩展验证：服务器能力决定可选推理强度、模型面板选择生效、不显示未支持的档位、effort 和 summary:auto 实际 RPC 字段、空摘要回退读取 content、权限图标与模型同一行、短选项选择更新图标、底部说明消失、字号设置立即应用并持久化。原有引导、连接、SSH/SFTP 回归通过。

已查看原生渲染截图：`model-effort-picker.png`、`permission-icons.png`、`font-large-phone.png`；另提供 `inline-model-permission.png`，均位于 `dist/screenshots/1.0.6/`。面板的窗口进出动画资源及切模型淡入动画已配置；自动化断言窗口动画设置，未进行真机动画流畅度验收。

字体测试初次因测试代码手动销毁 Activity 与待执行 recreate 冲突失败，调整测试以等待实际重建后通过；没有屏蔽异常或跳过测试。360dp 手机、1.3 倍系统字号截图已检查。

APK 签名与前版相同，versionCode 7 / versionName 1.0.6，可覆盖安装。

思考摘要边界：以前的请求没有明确指定 summary，本次显式请求 auto 并修复空数组项的解析回退。这个缺项可能沿用远端禁用摘要的配置，但尚未通过用户目标主机证实是唯一原因。测试确认后续请求携带摘要设置，不保证服务会返回摘要，也无法补回未保存的历史内容。界面不会推测或生成未返回的思考过程。

---

# 1.0.5 验证记录

日期：2026-09-25。

最终结果：**BUILD SUCCESSFUL，75 项测试通过，0 失败，0 跳过**。执行 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug -PsshFixture --offline`，JDK 17 / Android SDK 35。Lint 0 errors、6 warnings，均为动态中文文案的国际化建议。

新增验证覆盖：

- turn/steer 带 expectedTurnId、禁止重复提交；拒绝时不清除运行状态并保留草稿；成功后显示消息，与服务器回显去重。
- reasoning/textDelta 和 summaryTextDelta、分段与空完成事件保留流式内容。
- 权限模式对应 thread/start 与 turn/start 的不同 sandbox 格式；沿用远端不发送覆盖值。
- 折叠工具单行显示，运行中发送/空输入停止的按钮变化，展开思考内容持续更新，以及选择只读权限。
- 前台服务获取并释放唤醒锁，界面转后台保留会话状态，只有明确运行状态才显示运行标识。
- 只读轮询更新内容和运行标识，同时保持不可写；重入不产生重叠读取请求。
- 模型 WebSocket 403 的专用诊断与 ANSI 去除；原有 SSH / SFTP 回归通过。

已检查原生渲染截图：`dist/screenshots/1.0.5/steering-compact-permissions.png` 和 `live-reasoning.png`。截图内容为测试数据，其他手机/平板/深色截图由原有回归测试生成。

一次集成运行因独立 SSH 测试服务退出出现 Connection refused；改为让同一测试驱动管理夹具启动与清理后，75 项全部通过。没有忽略失败测试。

APK 签名校验成功，与 1.0.4 相同证书；versionCode 6、versionName 1.0.5，可覆盖安装。

边界：没有用户真机 Doze、厂商省电策略或真实后台 VPN 验收；没有连接报错目标服务器或使用真实账户运行模型。后台唤醒锁不能保证永不断线。远端进程退出后只能恢复历史，现有 stdio 架构不提供断网期间的远端任务持久托管。只读视图使用保存历史的轮询，并非接管另一客户端的实时事件流。403 的具体登录/账户/网络原因未确认，本版本只改进诊断和恢复体验，不宣称修复服务端拒绝。

源码变化主要位于 `RemoteApp.java`、`MainActivity.java`、`ChatItem.java`、`PermissionMode.java`、`ConnectionService.java`、`RemoteBrowser.java`、`ConnectionFailure.java` 和 Android Manifest。

---

# 1.0.4 验证记录

日期：2026-09-24。

结果：**67 项测试通过，0 失败，0 跳过**。`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline` 成功；随后启用本机 SSH 夹具运行 `:app:testDebugUnitTest -PsshFixture --offline` 成功。使用 JDK 17 与 Android SDK 35。Lint 0 errors、5 warnings（动态中文文案的国际化提示）。

本轮新增与扩展验证：

- 服务器实时 idle 覆盖历史残留 inProgress；旧服务器只检查最后一轮。
- thread/status/changed 与 thread/closed 清除忙碌状态和过期线程缓存。
- active writer 冲突保持只读，模拟占用解除后接续同一 ID 成功；新对话发送 thread/unsubscribe。
- 账户用量通知、剩余百分比换算、多额度桶、缺失窗口、稀疏更新、越界值和断开清理。
- 当前项目路径独立于后续项目选择；聊天页常驻项目/用量，详情可查看对话 ID。
- 原有 SSH 密码/私钥/none 认证、指纹、SFTP 和 RPC 回归全部通过。

已查看 `dist/screenshots/1.0.4/conversation-context-usage.png` 原生 Android 渲染截图；同时保留手机、平板、深色等原有界面回归截图。截图使用测试数据，百分比不代表真实账户用量。

APK 签名验证成功，证书 SHA-256 与 1.0.3 一致；versionCode 5、versionName 1.0.4，可覆盖安装。

限制：未连接用户真机或目标 Codex 主机；Terminus 关闭后实际远端进程/写锁是否存续尚未确认。测试验证客户端状态与模拟协议恢复路径，不代表外部残留进程已解除。取消订阅的释放时机由远端版本决定；新版不会强制删除锁、杀进程或抢占其他客户端。用量是否可用取决于远端协议和登录方式。

实现文件：`RemoteApp.java`、`UsageLimits.java`、`MainActivity.java`、`ConnectionFailure.java`；对应测试位于 `ConversationStateTest.java`、`UsageLimitsTest.java`、`MainActivityTest.java`。

---

# 1.0.3 验证记录

日期：2026-09-24。

执行：`JAVA_HOME=/home/qzh-n100/local/jdk-17.0.19+10 ANDROID_HOME=/home/qzh-n100/Android/Sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -PsshFixture`。

结果：**BUILD SUCCESSFUL，60 项测试通过，0 失败，0 跳过**。Lint 0 errors、4 warnings（现有及新增界面文字的国际化提示）。

新增验证覆盖：

- 本地真实 SSH 握手的 none 认证（模拟 Tailscale SSH），不要求密码/私钥，仍校验主机指纹。
- 同一 SSH 会话 SFTP 上传、二进制下载、大小限制，以及传输后 RPC 继续工作。
- 图片历史、生成图片、查看图片、MCP 图片解析；图片单独发送的实际 RPC 内容；发送失败保留附件。
- 远端路径和 sandbox 下载链接解析；非法协议拒绝；未知大小流的限制。
- 对话菜单与导航分离、项目菜单与主机菜单分离、免密码表单通过凭据验证。
- 原生图片预览成功渲染；保存按钮启动 ACTION_CREATE_DOCUMENT；已人工查看图片与下载链接截图、连接表单截图。
- TCP 错误明确提示尚未进入 SSH 身份认证，并提供按应用分流检查建议。

截图位于 `dist/screenshots/1.0.3/`，全部来自 Robolectric 测试夹具，不是实际模型输出。真机/模拟器未连接 ADB，因此没有验证手机系统文件提供器、厂商 VPN 路由、真实 tailnet 或实际远端模型读取图片的完整流程。系统 Keystore 无法由 Robolectric 仿真，新增表单测试验证通过凭据检查进入保存流程；持久化模式另以 Profile 序列化测试验证。

用户报告的 ECONNABORTED 发生在 TCP 建连阶段。当前证据不能确认是分流、VPN 状态、系统限制还是服务端规则；本版本改进诊断与取消，并未声称解决用户真机网络路由问题。客户端继续使用系统默认路由，没有强制绕过或绑定 VPN。

图片上传需要 SFTP，附件保留于远端用户目录；预览最多 20 MiB、下载最多 100 MiB、每次发送最多 4 张。HTTPS 图片重定向及网页登录图片尚不支持。预览不提供完整官方客户端的手势缩放体验；本次没有宣称完整复刻官方全部交互。

---

# 交付验证记录

日期：2026-09-23，版本：1.0.2。

执行命令：

```sh
JAVA_HOME=/home/qzh-n100/local/jdk-17.0.19+10 \
ANDROID_HOME=/home/qzh-n100/Android/Sdk \
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -PsshFixture
```

结果：**BUILD SUCCESSFUL**。47 个测试全部通过，无失败，无跳过。

| 测试范围 | 数量 | 结果 |
| --- | ---: | --- |
| SSH 实际网络连接、认证、指纹校验、SFTP 目录创建和浏览 | 7 | 通过 |
| JSON-RPC 并发、UTF-8 分片、错误代码及断线处理 | 7 | 通过 |
| 凭据序列化和连接参数验证 | 6 | 通过 |
| 历史消息、命令输出和文件差异解析 | 3 | 通过 |
| 流式消息、会话隔离、审批、只读回退、错误自动消失 | 9 | 通过 |
| Android 手机 / 平板 / 深色界面、旋转、连接表单、项目和目录界面 | 7 | 通过 |
| 初始化通知后台线程执行、取消后的过期回调隔离 | 2 | 通过 |
| 无消息异常保留类型、诊断警告和真实错误分离 | 3 | 通过 |
| 目录名称安全验证、中文路径、项目持久化序列化 | 3 | 通过 |

Android Lint：0 errors，2 warnings。两条提示均为当前中文界面中的动态文字拼接国际化建议，不影响构建。没有禁用 Lint，也没有创建忽略错误的 baseline。

SSH 测试使用 Python Paramiko 在 `127.0.0.1:22228` 上运行独立测试服务，验证 App 使用的 JSch 连接与 RpcClient。服务先向 stderr 输出与用户截图相同类型的 UNDICI 警告及中文诊断，测试确认 JSON-RPC 仍正常通信、诊断文本无乱码。它只回显测试协议数据，不运行模型、不执行系统命令、不使用实际用户凭据。

界面测试使用 Robolectric 的 Android API 35 原生图形渲染。检查过手机首页、手机聊天、平板首页和深色首页截图。聊天截图内容是测试夹具，不是实际模型生成的内容。

新增检查了 360dp 宽、深色模式的连接编辑器截图。键盘测试向编辑器注入 IME Insets，断言操作按钮位于模拟键盘区域上方、长表单可滚动、无效端口不会关闭编辑器。这是布局层回归测试，不是实际厂商输入法测试；截图中的键盘区域留白，不渲染真实输入法。

1.0.2 APK 签名校验通过，与已交付旧版签名证书一致，应用 ID 不变、versionCode 升至 3。

新增原生截图检查：远程控制项目列表、项目线程列表、SFTP 目录选择器。界面数据为测试夹具。SFTP 集成测试实际创建含中文和空格的目录、验证重复创建失败，并确认原 SSH RPC 通道仍正常工作；测试文件只写入临时隔离目录。

线程冲突测试用真实 RpcClient 流模拟 `thread/resume` 返回 `-32600 / already has an active writer`，断言只发出一次恢复请求，随后使用 `thread/read` 加载历史；其他客户端的进行中任务不会变成本客户端可停止的任务。另验证只读禁止发送、错误约 6.5 秒后消失、完整诊断仍保留。截图中的服务器错误被截断，实际用户线程是否完全符合该错误仍需新版完整诊断确认。

用户截图不能独立证明所有连接失败原因。代码中已确认并修复主线程发送 SSH 握手通知的问题，该异常可能没有 message，旧版恰好会将其显示成“连接失败”并附加 stderr。当前回归测试覆盖了这一代码缺陷；用户目标服务器仍需覆盖安装后验证。

## 尚未验收的项目

- 当前环境没有连接 Android 真机，也没有 Android 模拟器；没有完成真机安装、输入法、厂商后台限制和实际系统通知体验的验收。
- 未提供目标 SSH 服务器，因此未运行真实 Codex 模型会话、真实文件更改或真实远端操作审批。
- 已提供原版 remote 手机截图并用于调整项目导航，但缺少完整录屏及字体动画规格，不能确认所有像素和动画与原版完全一致。
- Android API 26–34 的兼容性经过编译和 Lint 检查，界面运行测试使用 API 35；仍应在目标设备测试。
- 交付 APK 使用 Android 调试签名，不是正式商店发行签名。

安装和远端准备步骤见 [README.md](README.md)。
