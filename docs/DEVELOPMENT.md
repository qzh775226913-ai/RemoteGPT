# 开发与验证手册

先读 [需求](REQUIREMENTS.md)、[架构](ARCHITECTURE.md) 和 [验收标准](ACCEPTANCE.md)。仓库有一个 Android app 模块，使用 Java 17、Gradle wrapper 8.9、AGP 8.7.3；保持当前依赖，不把重构与工具链升级混合。

## 环境

- JDK 17；设置 `JAVA_HOME`，不要把个人绝对路径写进 Gradle。
- Android SDK：`platforms;android-36`、`build-tools;35.0.0`；设置 `ANDROID_HOME` 或本地、不提交的 `local.properties`。
- Python 3.12，使用 venv；测试夹具依赖在 `tools/requirements-dev.txt`。生产托管只需 Python 标准库。
- Gradle 首次需要访问依赖仓库。项目保留原 Maven 配置，Robolectric 测试运行时从 Maven Central 获取。

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements-dev.txt
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_HOME="/path/to/android-sdk"
.venv/bin/python tools/verify.py
```

完整入口管理独立回环 SSH 夹具，端口 22228 被占用时终止验证。结束或 Ctrl+C 时清理自己的夹具和假托管进程。合成测试密钥暂存 `/tmp/remote-ssh-fixture-key.pem`，只服务于这一夹具；结束后不再对应运行主机。

## 分层验证

```sh
# 完整验收；依赖已缓存时可限制 Gradle 联网。
.venv/bin/python tools/verify.py --offline

# 开发中的局部入口；不能报告完整验收。
.venv/bin/python tools/verify.py --unit
.venv/bin/python tools/verify.py --python-only

# 已知目标行为的定向回归；不需要真实模型。
./gradlew :app:testDebugUnitTest --tests 'app.remotessh.ConversationStoreTest'

# 图标唯一编辑来源及同步检查。
python3 tools/generate_icons.py
python3 tools/generate_icons.py --check
```

完整入口强制重新执行任务，避免 SSH 测试开关变化时误用缓存结果。`--unit` 允许 SSH 用例按原设计跳过，报告明确不满足完整验收。Python-only 只检查源码／文档／图标和 Python 逻辑。

| 产物 | 路径 |
| --- | --- |
| Android 测试 XML/HTML | `app/build/test-results/testDebugUnitTest/`、`app/build/reports/tests/testDebugUnitTest/` |
| Lint XML/HTML | `app/build/reports/lint-results-debug.*` |
| 合成界面截图 | `app/build/screenshots/` |
| 本地 debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 完整入口摘要／SSH 日志 | `app/build/verification/summary.json`、`ssh-fixture.log` |

失败运行会删除前次摘要。`app/build`、`.venv`、`.gradle`、个人配置与 `dist` 不提交。测试截图是构造数据；没有真实回复／真机证据时必须标明。

## CI

[GitHub Actions](../.github/workflows/verify.yml) 在 push、pull_request 和手动触发时设置 JDK 17、SDK 36、Python 3.12，运行同一个完整入口并保存报告。仅有 `contents: read` 权限，不读取生产 SSH／模型凭据，不安装手机，不发布 APK，也不更新服务器。

SDK 初始化显式安装 `platform-tools`，再安装指定 platform／build-tools；不请求上游已不可下载的旧 `tools` 包。该配置不改变应用工具链版本。

本地可执行结果不代表托管 CI 已跑过；CI 需要仓库推送后才会触发。维护报告必须区分“工作流已配置”与“远端 CI 已通过”。

## 公开安装包发布

[显式发布流程](../.github/workflows/publish-release.yml) 只由维护者推送 `publish-release-*` 临时注释标签触发，不在普通 push 或 PR 中发布。注释为 JSON，包含发布标签、源码提交和 3 个制品（APK、安装 ZIP、LICENSE.txt）的公开 HTTPS URL 与摘要；摘要仅供发布流程内部核对，不进入公开说明或单独附件，这些临时输入不写入源码文件。

发布器校验源码版本／正式标签、制品摘要、ZIP 内 APK 与许可的一致性，逐项比对 APK／ZIP 的项目与第三方许可、字体声明和公式库对应源码摘要，并等待该源码提交完整 Verify 成功。随后创建 Release 草稿、上传全部附件并核对摘要，最后公开为最新版；不覆盖已有公开 Release。短期上传服务与临时触发标签完成后清理，正式版本标签与 GitHub 制品保留。

这里只重分发已验证的原签名 APK，不在 GitHub 上重建或上传签名私钥。更改应用或重新交付新 APK 仍按版本递增、完整验证与签名检查执行。通用维护入口不会更新生产 SSH 或调用模型。

## 交付和日常变更

1. 从干净提交建立功能分支，记录回退基线。已有未提交内容独立保存。
2. 找到需求编号与模块负责人（代码职责），说明行为变化和失败路径；实现复用原生组件与统一请求路由。
3. 运行相关定向检查；状态／协议变动补真实风险回归，避免只验证实现自身的测试。
4. 完整入口、差异检查通过后，更新日期验证记录和技术债；将每个可回退阶段单独提交。
5. 发布需要单独处理版本号、签名、覆盖安装和目标设备验收。发布与部署独立记录。

## 已知环境问题

AGP 8.7.3 对 compileSdk 36 会发出兼容性提示，当前构建通过；Lint 另有已记录的既有警告。告警按原级别保留。工具链升级另开变更，验证 Android 8/旧 API 回退、API 36 通知、Robolectric 和原生绘制。

环境记录使用通用变量；私钥、密码、token 和个人 SSH 配置不纳入源码。

公式字形轮廓通过 `python3 tools/generate_math_glyphs.py --check` 检查；生成依赖 fontTools，版本在开发依赖文件中固定。首次检查无 Gradle 缓存时从 Maven Central 下载固定哈希的原始 AAR，已有缓存时无需联网。
