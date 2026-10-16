# 回退与恢复

2026-10-16 1.0.54 版本递增前功能基线：`57967c6`，初始恢复提交 `d7e8267`。仅间距修复及源码提交，未发布新的安装包；旧下载文件和签名保留，源码回退不等同于手机降级。

2026-10-16 连接编辑按钮间距修复前恢复提交：`d7e8267`（空快照，工作区干净、上轮交付为 `40c4d4e`）。间距修复与版本递增分别提交，保留原签名／存储，不操作生产远端。

2026-10-15 1.0.53 版本交付前功能基线：`094b7c3`，本轮初始恢复提交 `234a35a`。版本和功能独立提交，旧 1.0.52 下载包保留；不改变签名，不以卸载方式默认降级。

2026-10-15 模型自动刷新调整前恢复提交：`234a35a`（空快照，1.0.52 交付为 `c2622e1`）。功能调整、版本交付分别提交，保留旧安装包，不操作生产 Codex。

2026-10-15 1.0.52 tailnet 交付前恢复提交：`bd3c4bc`（空快照，功能修复为 `1c817e4`）。本阶段仅版本递增与下载交付，不改功能、签名、依赖或生产模型进程；旧安装包保留，低版本回退覆盖安装仍需 Android 设备核验。

2026-10-15 模型目录对齐与保存文件类型修复前恢复提交：`3bca15e`（空快照，初始工作区干净）。本阶段不改版本、依赖、签名、存储键，不发布 APK 或重启生产远端；可独立 revert 撤回。

2026-10-13 公开文档隐私整理前恢复提交：`1809557`。仅整理文档、截图展示与发布说明，不改变 APK、应用行为或签名；旧历史与已发布标签保留，当前文档中的本机路径和地址已替换为示例。

2026-10-13 首次公开 APK 发布前恢复提交：`7f857f7`（空快照，与许可交付 `8075dfc` 一致）。本阶段复用已发布的 1.0.51 APK，添加 Release 说明和显式发布流程，不重构、不改版本或签名。源码回退不会自动删除 GitHub Release 或撤销已下载副本。

2026-10-13 项目专用许可添加前恢复提交：`bb954cc`（空快照，源码与 `a061d3a` 一致）。最终范围按用户确认的建议执行：正常使用免费，商业再利用及修改版发布须书面授权。本阶段只添加 LICENSE 和文档说明，不改应用行为或签名。源码 revert 不会自动撤销已经授予的许可或使权利状态追溯改变；法律效果须另行判断。

2026-10-13 Gradle wrapper 可执行位修复前基线：`d2f2d14`。该阶段仅提交 `gradlew` 的执行权限与验证记录，脚本内容不变；可单独 revert。

2026-10-13 首次 GitHub CI 修复前基线：`709a06b`（已上传项目说明与使用指南）。CI 配置修复单独提交，仅排除不可下载的旧 SDK `tools` 包，不升级应用依赖或工具链；撤回时单独 revert 该 CI 提交。

2026-10-13 GitHub 项目说明整理前恢复提交：`f487d89`（空快照提交，源码与 1.0.51 交付 `e532f91` 一致）。本阶段仅整理 README 和使用说明，不更改应用行为、版本、签名或远端服务；可通过独立 revert 撤回文档提交。

2026-10-13 条件显示强制审批入口前恢复提交：`7743a9b`（空快照提交，源码与 1.0.50 交付 `8ed50e5` 一致）。本轮仅调整权限面板说明、入口显示与审批频率资格，恢复方式仍为独立分支或逐阶段 revert。

2026-10-12 连接诊断／强制审批变更前恢复提交：`8da2d25`（空快照提交，源码与 `b8c0866` 的 1.0.49 一致）。各阶段分别提交；恢复时从该提交建立独立分支或逐阶段 revert，不 reset/clean，不影响真实远端进程。

本轮开始前工作区干净，原始提交为 `75ec5b699c03e106b7c3cf3d6a83061db8eb1166`（1.0.47）。维护分支：`maintenance/project-organization-20261011`。

## 已建立的保护

- `main` 保持原始提交。
- Git 标签 `maintenance-baseline-20261011` 与备份分支 `backup/before-maintenance-20261011` 指向原始提交。
- 仓库外完整历史备份：`/home/example/AI-Workspace/remotesshapp-before-maintenance-20261011.bundle`，已执行 `git bundle verify`。
- 仓库外基线 APK：`/home/example/AI-Workspace/remotesshapp-before-maintenance-20261011.apk`。
- 基线验证摘要：`/home/example/AI-Workspace/remotesshapp-before-maintenance-20261011-validation.json`。

这些是本机备份位置；迁移机器时同时保存 bundle 和 APK。Git bundle 包含已提交源码／历史，**不包含** ignored 文件、Android SDK、Gradle 缓存、个人配置、签名密钥或远端数据库。本轮未改变这些内容或生产远端。

## 回到整理前源码（保留整理成果）

先检查状态。如果有后续未提交内容，先提交到自己的临时分支或另存，不强制清理。

```sh
git status --short --branch
git switch backup/before-maintenance-20261011
```

此操作切回原代码；维护分支仍保留全部改动。继续原版本开发时从基线另建分支：

```sh
git switch -c recovery/from-1.0.47 maintenance-baseline-20261011
```

恢复维护成果：

```sh
git switch maintenance/project-organization-20261011
```

## 单独撤回某一阶段

阶段提交记录见 [本轮验证](verification/2026-10-11.md)。共享分支使用 revert 保留历史；有依赖的阶段须先撤回较新的阶段，不在仍使用新模块的代码中先删模块。

```sh
git log --oneline maintenance-baseline-20261011..HEAD
git revert <需要撤回的提交>
```

不要直接执行未经确认的 hard reset 或删除仓库。只想比较差异时使用：

```sh
git diff maintenance-baseline-20261011..HEAD --stat
```

## 仓库损坏时从 bundle 重建

```sh
git bundle verify /home/example/AI-Workspace/remotesshapp-before-maintenance-20261011.bundle
git clone /home/example/AI-Workspace/remotesshapp-before-maintenance-20261011.bundle /path/to/recovered-project
git -C /path/to/recovered-project switch backup/before-maintenance-20261011
```

该 bundle 是**整理前**快照，不包含后续维护提交。整理完成后的完整历史另存为 `/home/example/AI-Workspace/remotesshapp-after-maintenance-20261011.bundle`，恢复维护成果时使用它并切换维护分支。前后备份各自保留，不覆盖基线。

已在独立临时目录实际执行整理前 bundle 的克隆和切换，核对原始提交与干净工作区，并通过 `git fsck --full`；证据见本轮验证记录。

## 手机与远端边界

本轮保持 versionName、versionCode、包名、存储键和数据格式，且没有安装到手机。基线 APK 保留原签名；如果后来发布了更高版本 APK，Android 可能拒绝直接覆盖降级。不要为回退默认卸载应用，卸载会丢失本机数据；先核对已安装版本／签名和可行的恢复方式。

切换 Git 不能撤回曾经部署到远端的脚本、任务操作或升级。本轮未部署／重启真实 Codex；后续若修改远端协议，须另外记录当前脚本、活跃进程、部署和恢复方法，不将源码回退等同于服务恢复。
