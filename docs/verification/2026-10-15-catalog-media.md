# 2026-10-15 模型选项对齐与保存类型修复

恢复基线：`3bca15e`，初始工作区干净。关联需求 R-11、R-14、R-15。保持 1.0.51 / versionCode 52、Java 原生 UI、依赖、签名和存储键；未发布／安装 APK，未升级或重启生产远端。

## 研究结论

依据 [OpenAI App Server 文档](https://learn.chatgpt.com/docs/app-server) 的 `model/list` 动态发现和版本专属 Schema 说明，并用本机 `codex-cli 0.162.1` 的 `app-server generate-json-schema` 离线生成协议（仅临时目录，不启动模型／登录），核对 `ModelListResponse`、`ConfigRequirementsReadResponse`、`ThreadStartParams`、`TurnStartParams`。

- 现有客户端已在连接、打开模型面板、手动刷新时请求完整分页目录，原子替换，失败保留旧目录，并隔离连接 generation。模型、推理强度以及新增速度 ID 不依赖本地模型白名单。
- 当前 Schema 提供 `serviceTiers` 的 id/name/description、`defaultServiceTier`，保留废弃的 `additionalSpeedTiers`；请求 `serviceTier` 是字符串。现有现代／旧目录解析与此一致，现代空目录优先，不复活旧 Fast。
- 发现展示偏差：fast/priority 总被本地“快速”覆盖；未来缺描述的档位被推测为“更快响应”；两个别名同时存在时可能选中列表中第一个 ID／最后一个描述；远端默认模型未确认时可能借用目录推荐模型的推理强度。
- 修复后使用远端速度名称／描述，精确 ID 优先再做既有别名兼容；未来无描述档位只展示 ID 和下一轮生效。不确定实际默认模型时不借用推荐模型能力。
- 此机制可自动适配现有协议中的目录／档位变化，不保证未来不兼容协议也免维护。CLI 更新不自动替换已经运行的持久 app-server；需要远端实际运行新版，再连接／刷新。此次未读取生产目录、登录凭据或调用真实模型，不能据此声明某账号／生产服务器全部档位已实测。

## 保存问题

原代码对所有保存操作声明 `application/octet-stream`，Android 文件提供器因此可能附加 `.bin`；内嵌图片还固定命名为 `image.png`，即使声明的是 JPEG/WebP。现由 MediaFiles 集中生成保存 Intent，按文件扩展名或内嵌图片声明选择 MIME 和文件名，保留原始字节。普通文件也使用已知类型；未知类型保持二进制兜底。没有扩展名／类型声明的远端链接不额外下载或猜 PNG。

## 验证

新增覆盖远端原始速度名称、未来档位说明、fast/priority 同时存在的精确匹配、默认模型未确认与配置确认后的面板刷新、保存窗口真实 Intent，以及 PNG/JPEG/WebP/GIF／中文大写扩展名／带查询 URL／PDF／未知文件类型。

首轮完整验证 358 项 Android 测试中 1 项失败：Robolectric 的 MimeTypeMap 默认为空，未提供 Android 标准映射。已给相关测试夹具显式提供标准映射，未改变应用代码来绕过测试。期间补充默认模型与保存窗口回归，最终结果另记于下。

目标手机的系统保存窗口和实际文件提供器、真实模型速度／用量、OEM 通知、Tailscale、HEIF/AVIF 硬件解码仍未验收。自动化只证明合成协议及 Android Intent／逻辑，不等于真实服务端承诺。

最终重新执行完整入口（开发 Python 环境由 `PYTHONPATH=/tmp/remote-ssh-test-libs` 提供夹具依赖，JDK 17 / SDK 36，`python3 tools/verify.py --offline`）：

- 359 项 Android 测试通过，0 失败、0 错误、0 跳过；44 项 Python 测试通过。
- 文档／模块／规模、生成图标一致性检查通过；assembleDebug 成功，生成本地 debug APK。
- lintDebug：0 Error/Fatal、17 项既有 Warning，与前次记录数量一致。AGP 对 compileSdk 36 的既有兼容性提示，以及既有 deprecated/unchecked 编译提示仍保留，未屏蔽。
- Gradle 49 个任务执行完成，耗时 1 分 46 秒；完整入口 scope=full、full_acceptance=true，合成回环 SSH 夹具由入口启动并清理。
- 最终 `python3 tools/check_project.py`、`git diff --check` 通过；未提交生成产物或个人环境配置。源码回退使用恢复基线或本阶段独立 revert，不清理其他工作。
