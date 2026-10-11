> 历史归档：保留当时的说明与验收结论，不代表当前版本。当前需求和验证入口见 [项目首页](../../README.md)。

## 1.0.31 相册与多图上传验收

- 修改基于已提交的 1.0.30（`a518fa1`）。Android 完整回归 195 项全部通过，0 失败、0 跳过，包括隔离 SSH 测试；新增相关定向验证 21 项通过。`assembleDebug`、`lintDebug`、`git diff --check` 成功。
- Android 9 / 15 的模拟验证覆盖相册 App 选择器、图片 App 入口、系统照片多选上限及单个剩余名额、缺少相册或照片选择器时回退到文件多选、没有任何处理入口时不崩溃、单 URI / ClipData / 重复 URI / 取消返回。已查看原生“选择图片来源”面板截图。
- 隔离的真实 SSH / SFTP 验证三张 PNG 按顺序上传且重复项不重复上传，下载逐字节核对；中间一张损坏时继续上传其余两张并保留预览；第二张读取暂停时仍保持批次锁定，禁止切换和发送；断开后迟到结果不加入新连接；超出剩余名额时不上传、不丢弃既有草稿。
- 旋转返回验证复现并修复选图期间旧逻辑把空闲对话标成只读的问题。当前连接、项目、对话标识会随 Activity 保存，改变上下文后的返回不会加入错误对话。多图片在同一个 `turn/start` 中以有序 `localImage` 列表发送；提交错误保留全部待发送图片。
- 格式测试验证 PNG 原字节保留、缩略图有界、平台可解码的 BMP 转换为可解码 JPEG、损坏内容拒绝。HEIF / AVIF 采用相同的“手机可解码才转换”路径，未用真机或实际 HEIF / AVIF 样本验收。每条消息最多 4 张，每张原文件最多 20 MiB；转换后的 JPEG 最长边不超过 2560 像素。
- 测试使用 Robolectric、模拟内容提供器及回环 SSH 夹具，没有真实 Android 手机、厂商相册或云相册账号；厂商相册是否在选图界面开放智能搜索、多选与正确 URI 授权仍由其实现决定。系统照片 / 文件选择器作为兼容回退，不增加全相册、媒体或存储访问权限。未调用真实模型或重启生产远端。
- 交付 `dist/RemoteGPT-1.0.31-Android-universal-debug.apk`，包名 `app.remotessh`，名称 RemoteGPT，版本 1.0.31 / versionCode 32。签名验证通过，证书与 1.0.30 相同，可覆盖安装；截图位于 `dist/screenshots/1.0.31/`。
- APK 为 4,641,362 字节，SHA-256：`1052ac554b87d3794f011d2b0e7dc4e509caa8ce5d51114ad1b9fa7e1a9fe091`。已发布并经 Tailscale HTTP 完整下载，HTTP 200，字节数和哈希一致：[下载安装包](http://100.109.221.115:8000/RemoteGPT-1.0.31-Android-universal-debug.apk)。

## 1.0.30 目录布局与模型手势滚动验收

- 修改基于已提交的 1.0.29（`9f5bc28`）。Android 完整回归 174 项全部通过，0 失败、0 跳过，包括隔离 SSH 测试；`assembleDebug`、`lintDebug` 成功，`git diff --check` 通过。随后补充的固定底部按钮高度及可见范围断言已通过定向验证，未再修改产品代码。
- 使用真实 MotionEvent 序列复现旧版错误：外层设置 ScrollView 截获手势，内层模型列表滚动位置保持不变。修复后，小屏连续滑动可到达第 16 个模型并触摸选中；模型手势不移动设置区，新手势在列表外仍可滚动设置，“完成”按钮保持高度且位于可见范围。
- 320dp 窄屏目录截图已检查：顶部仅“上一级 / 输入路径”，底部“新建项目 / 使用此文件夹”并排，四个按钮均为单行，新建操作仍能打开确认输入框。模型列表具有轻微不同的圆角背景，已检查深色与浅色面板截图。
- 验证使用 Robolectric 原生绘制和回环 SSH 夹具，没有连接 Android 真机，也没有发起真实模型任务或重启远端。截图位于 `dist/screenshots/1.0.30/`。
- 交付 `dist/RemoteGPT-1.0.30-Android-universal-debug.apk`；包名 `app.remotessh`，名称 RemoteGPT，版本 1.0.30 / versionCode 31。签名验证通过，证书与 1.0.29 相同，可覆盖安装。
- APK 为 4,636,954 字节，SHA-256：`db97d665f9c722e189d4534b6b4ac7b64eb6d695265cd5f16c1fd6fb5fe8b947`。已发布并经 Tailscale HTTP 完整下载，HTTP 200，字节数和哈希一致：[下载安装包](http://100.109.221.115:8000/RemoteGPT-1.0.30-Android-universal-debug.apk)。

## 1.0.29 远端运行速度选择验收

- 修改前已提交 1.0.28 工作区基线：`2f8efcd`。
- Android 完整回归 172 项全部通过，0 失败、0 跳过，包括隔离 SSH 夹具测试。最后补充刷新失败保留已知功能开关的回归后，相关配置及速度状态定向测试 17 项全部通过；最终 `assembleDebug` 和 `lintDebug` 成功。Python 28 项全部通过，`git diff --check` 通过。
- 覆盖动态档位、现代与旧版协议、配置默认模型、项目和全局功能开关、分页、刷新失败、模型切换、每条对话存储、重新连接、恢复默认、请求期间改选、成功更新托管恢复配置及拒绝请求不更新配置。已查看普通和小屏原生模型面板截图，固定完成按钮可见。
- 核对本机 Codex 0.162.0 的实际模型目录和生成协议：快速档位 ID 为 `priority`，显式普通档位为 `default`；界面以每台远端实际返回的能力和说明为准。只进行了配置读取及临时线程协议探测，没有发起真实模型推理，也未重启实际远端会话。
- APK 包名 `app.remotessh`，名称 RemoteGPT，版本 1.0.29 / versionCode 30；签名验证通过，与 1.0.28 证书相同，可覆盖安装。交付 `dist/RemoteGPT-1.0.29-Android-universal-debug.apk`，4,797,701 字节，SHA-256：`91b8b6b79dea22700c4f9dd5bc26f2c8b10ac3a5cabe6bfa5ea4ab2ca100739a`。
- 已发布到现有 Tailscale HTTP 服务，并完整下载核对字节数与 SHA-256：[下载安装包](http://100.109.221.115:8000/RemoteGPT-1.0.29-Android-universal-debug.apk)。截图位于 `dist/screenshots/1.0.29/`。未进行 Android 真机验收或实际速度测量；旧托管仍可继续运行，新托管的恢复配置更新在其下次启动后生效。

## 1.0.28 App 内确认重启验收

- Android 共 151 项：141 通过、10 项 SSH 专用夹具测试未启用、0 失败；Python 27 项通过。`assembleDebug`、`lintDebug`、`git diff --check` 通过。
- 已检查确认弹窗的原生渲染截图；还用隔离的真实 Python 进程组验证后端和托管进程的停止及拒绝误停的条件。没有对用户实际远端执行重启。
- 版本弹窗在新版已安装后始终显示“重启并启用新版”，包括没有安全重启 RPC 的旧托管；不再要求用户进入远端操作，也不在安装完成后自动重启。
- 第二层确认明确列出进行中对话、未完成回复和待审批会被停止；取消后连接保持不变。确认后经独立 SSH 维护通道定位当前托管，停止其 Codex 进程组与托管进程组，然后自动重连。
- 远端定位优先使用新版托管返回的目录标识；旧托管按建立连接时相同的连接标识顺序与启动命令计算目录。目录名、托管命令、PID 存活状态和 Codex 子进程父子关系均需验证，不匹配时拒绝重启。

## 1.0.27 远端版本与更新验收

- Android 测试总计 150 项：140 通过、0 失败、10 项专用 SSH fixture 测试未启用而跳过。覆盖远端握手版本（区别于手机版本）、语义版本与预发布版比较、断网检查、安装版与运行版分离、连接后弹窗、拒绝后继续连接、再次打开确认、跨连接过期确认、版本状态点击入口。
- Python 22 项通过，包含现有持久会话集成及新增更新器测试：npm 安装前缀和确认版本、路径/版本变化拒绝旧确认、安装失败与安装后版本核对、二进制摘要校验和原子替换、运行任务及审批期间拒绝重启、空闲重启后重新启动托管。
- `assembleDebug`、`lintDebug`、`git diff --check` 通过。已查看原生 Robolectric 项目浏览器和更新确认截图；截图为夹具数据，不是真机更新结果。
- 在当前主机仅执行只读检测，实际识别 npm / 已安装 0.156.1 / 发布源最新 0.160.0；没有对真实安装执行升级，也没有重启真实会话。Homebrew 和独立二进制的真实安装升级未实机验证。
- 版本 1.0.27 / versionCode 28，包名 app.remotessh；APK 签名验证通过，证书 SHA-256 与既有包一致（43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a）。
- 交付 `dist/RemoteGPT-1.0.27-Android-universal-debug.apk`；已通过现有 Tailscale HTTP 下载服务完整读取并校验 SHA-256。
- 旧版正在运行的托管进程缺少安全重启能力，更新安装后明确显示待重启，需要任务完成后在远端重启旧托管；不将安装成功等同于旧会话已经切换新版。

## 1.0.26 动态模型同步验收

- Android 测试共 142 项：132 项通过，10 项需要专用 SSH fixture 的集成测试未启用而跳过，0 失败。新增模型目录、刷新生命周期及界面回归覆盖分页、GPT-6.1/未知模型、远端顺序、重复/隐藏条目、游标循环、异常重试、旧连接响应和默认语义。
- Python 托管测试 8 项通过。`assembleDebug`、`lintDebug` 成功，`git diff --check` 通过。
- 已查看 Robolectric 的模型面板截图，刷新入口、模型列表与推理滑条完整可见；未进行真机或真实账号的模型调用验证。
- APK 包名 app.remotessh，版本 1.0.26 / 27，签名验证通过且证书与 1.0.22 一致，可覆盖安装。
- 文件：`dist/RemoteGPT-1.0.26-Android-universal-debug.apk`。已通过现有 Tailscale 下载服务 HTTP GET 完整读取并校验 SHA-256。
- 新模型出现依赖远端 `model/list` 实际返回；手机不内置新模型白名单，不替换或重启已有远端托管进程。

# 1.0.19 最终审计（2026-09-28）

128 项 Android 测试全部通过，无失败/跳过；4 项 Python 托管测试通过；assembleDebug、lintDebug 成功，Lint 0 错误、8 项 SDK/资源/本地化非阻断提示。

新增 FinalAuditTest 覆盖旧恢复 ID 在初始化期间保留、后台历史完成不自动发送排队消息、只读空闲清理请求、过时快照不复活任务、旧端刷新不重复审批。非持久连接重开历史改为读取，避免仅重连就更新远端时间。

完整需求映射、验证证据和真机限制见 [CONVERSATION_ACCEPTANCE.md](../../CONVERSATION_ACCEPTANCE.md)。构建环境为 JDK 17 / Android SDK 36；SSH 使用 127.0.0.1:22228 隔离测试服务。未访问生产 SSH 或真实模型。

交付 dist/RemoteGPT-1.0.19-debug.apk 和 dist/RemoteGPT-1.0.19-source.zip。APK 使用相同包名与签名，可覆盖升级。

# 1.0.18 验证记录（2026-09-28）

验证结果：Android 123 项测试通过，无失败、无跳过；assembleDebug / lintDebug 成功。交付 dist/RemoteGPT-1.0.18-debug.apk 和 dist/RemoteGPT-1.0.18-source.zip。

根因：托管恢复会重放旧的 error 和 turn/completed；客户端此前没有区分历史错误与当前错误。托管基线的 name/preview 可能为空，applyThread 直接把它们覆盖到已知标题。

修复：在整个重放边界内，旧错误存入对应会话的历史错误项，不更新 error/lastError；完成后的实时错误照常提示。快照中的轮次错误同样作为历史记录呈现。标题优先接受有效远端 name，空元数据保留已有值，缺失时依次从对应列表元数据、preview 和用户消息补全。回放用户消息时也可恢复初始空基线的标题。

新增 6 项回归测试：旧额度错误的 error/failed turn 重放不告警且历史去重；实时额度错误仍告警；其他会话补发历史错误不告警；空标题保留与有效名称更新；冷恢复列表/首条消息回退且不串会话标题；空基线通过重放用户消息恢复标题。Robolectric 无 AndroidKeyStore，标题快照测试明确排除平台保存警告的干扰。

本次不连接真实模型、不检查或更改用户实际额度，不操作生产服务器。使用隔离 SSH 测试夹具完成集成验证；本修复兼容已有远端托管，不需要远端重启。

# 1.0.17 验证记录（2026-09-28）

补充：气泡标题 18sp 单行、正文 15sp 四行，正文变化参与更新签名；最新四个视觉行保持在气泡内，普通运行去掉大按钮。流体云正文限制四行逻辑摘要；标准通知由系统控制字号与外壳，自绘排版预览不能作为厂商实测。另一个用户机型为 vivo X300 / OriginOS 6。Android 气泡依赖长生命周期会话标识，通知缓存后撤下动态桌面入口，结束后清理缓存；无真机，未验证系统缓存时序。

修改覆盖桌面动态快捷方式清理、电脑直接连接和居中、移除全部筛选、历史只读访问、空闲取消订阅、后台询问弹窗，以及流体云/列表/顶部的统一状态。补充未订阅活跃会话的后台状态复查，防止被动历史状态长期滞留。

结果：Android 117 项测试全部通过，无失败、无跳过；4 项 Python 托管集成测试通过；assembleDebug 与 lintDebug 成功。APK 签名与 1.0.16 一致，可覆盖升级。交付 dist/RemoteGPT-1.0.17-debug.apk 与 dist/RemoteGPT-1.0.17-source.zip。

验证：JDK 17 / Android SDK 36，`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -PsshFixture`。SSH 使用仅绑定 127.0.0.1:22228 的隔离 Paramiko 服务与合成凭据；没有连接朋友或本机的生产 SSH 服务。另运行 `python3 -m unittest discover -s tools -p test_remote_session.py`，4 项托管集成测试通过。

回归用例包括：浏览只发送 thread/read、空闲退出发送 unsubscribe、重放与读取不显示运行、完成事件覆盖陈旧 active 列表、后台状态事件同时更新列表与流体云、后台问题在 MainActivity 弹出、被动后台任务完成后清除状态、电脑按钮直接调用连接、仅显示选中电脑项目、当前电脑居中、运行后桌面动态快捷方式列表为空。人工检查原生渲染截图 selected-host-browser.png，当前电脑居中且没有“全部”。

限制：ADB 无真机/模拟器，未验证厂商桌面缓存清除和流体云的实际展示，也未复现朋友服务器的安装环境。日志中的 zsh 找不到 codex / 退出码 127 表示 SSH 已执行远端命令，不能归因于 IPv6。新版托管脚本不会强制替换仍运行的旧守护进程；旧守护进程需在全部任务完成后重启，才能应用真正取消订阅的改动。后台弹窗测试使用协议夹具，不代表所有模型都会发出结构化问题。

# 1.0.16 多会话验证记录

用户已审核 1.0.15 排版，本版落实多会话并行，复用现有 SSH/RPC、独立托管进程、通知渠道、气泡 Activity 和流体云内容布局。

执行 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug -PsshFixture`，最终 BUILD SUCCESSFUL。109 项 Android/隔离 SSH 测试全部通过，0 失败、0 跳过；Lint 0 errors / 8 warnings。另执行 `python3 -m unittest tools/test_remote_session.py -v`，3 项全部通过。

新增多会话覆盖：

- A/B 使用相同 itemId 的流式输出仍正确分流；A 完成不会结束 B。
- 后台处理期间 UI 观察者只看见当前选中会话，不渲染临时背景状态。
- A/B 独立流体云与气泡、不同 PendingIntent 目的地、相同通知模板；普通状态无按钮。
- 在 B 页面点击 A 通知审批，回复正确 request ID 且 B 的页面/运行状态不变。
- 切换缓存会话保留各自草稿和 turnId；A 的延迟 turn/start 响应不会覆盖 B。
- 重连按托管列表恢复 A/B 的运行状态，并保留原选中的 A；旧托管端兼容保存的会话 ID。
- 杀掉模拟手机客户端后远端继续输出，重连恢复消息与待审批。
- 恢复 A 基线期间 B 继续输出，重放结束前补发 B 的增量；随后恢复 B 获得完整基线后的日志。
- 原有历史阅读冻结、审批防重复/过期、图片和连接回归全部通过。原测试的固定气泡快捷方式及离开会话 unsubscribe 断言已改为新的独立标识和保持订阅行为。

本机部署：`~/.remote-gpt/host-pXsPdV2KKY2ozXzW9YDwABlfCmNLvCIS15KrVZ9cKAs.py`，0600，内容与 APK 资产一致。检测到现存旧实例有 active 任务，未重启；新客户端为旧端提供重放后其他会话快照补读。新建托管实例采用新版会话列表与跨会话日志补发。未变更 SSH 登录凭据或系统服务配置。

测试使用 Robolectric（主要 SDK 35）和本机回环隔离 SSH 服务，未发起真实模型任务，未连接一加 15T 真机。系统控制多条流体云/气泡的实际展示上限、聚合和轮换，测试验证应用分别提交正确通知，不能代替 ColorOS 多卡片真机验收。

版本 1.0.16 / versionCode 17，沿用 Debug 签名与包名 app.remotessh。交付 dist/RemoteGPT-1.0.16-debug.apk 和 dist/RemoteGPT-1.0.16-source.zip。未访问含 claude 字符目录中的内容。

---

# 1.0.15 验证记录（排版审核候选版）

去除普通流体云卡片的查看/收起按钮，保留整卡打开对话；审批只有允许一次/拒绝，询问只有填写信息。正在提交与同步期间不显示操作按钮。正文去除重复标题与空字段，命令和最近输出分段，长输出限制字符与行数。

执行 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`，BUILD SUCCESSFUL。102 项测试中 92 通过、0 失败、10 项 SSH 集成测试未启用而跳过；Lint 0 errors / 8 warnings。新增断言普通通知没有 actions、审批恰好两个动作、审批清除后按钮消失、正文行数受限且保留最近输出。

排版示例 dist/screenshots/1.0.15/live-layout.png 由 Robolectric 原生绘图和实际 Notification extras/actions 构造内容卡片，使用示例任务数据。它是内容排版示意图，不是系统通知模板截图或 ColorOS 真机截图。最初尝试直接绘制系统 RemoteViews 时测试环境未呈现正文，因此没有采用该不完整截图。最终示例已人工查看，文字和按钮完整，无裁切。

本版未实现多会话并行。评估和推荐改造路线见 MULTI_SESSION_ASSESSMENT.md；未放宽 busy 切换限制，也未修改或重启远端托管服务。用户要求先审图，此产物为审核候选版。

版本 1.0.15 / versionCode 16。构建与签名配置沿用现有 Debug 证书，保持应用 ID；没有连接一加真机。本轮未查看含 claude 字符目录中的内容。

---

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

安装和远端准备步骤见 [README.md](../../README.md)。
