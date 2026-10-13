# 回退与恢复

2026-10-13 GitHub 项目说明整理前恢复提交：`f487d89`（空快照提交，源码与 1.0.51 交付 `e532f91` 一致）。本阶段仅整理 README 和使用说明，不更改应用行为、版本、签名或远端服务；可通过独立 revert 撤回文档提交。

2026-10-13 条件显示强制审批入口前恢复提交：`7743a9b`（空快照提交，源码与 1.0.50 交付 `8ed50e5` 一致）。本轮仅调整权限面板说明、入口显示与审批频率资格，恢复方式仍为独立分支或逐阶段 revert。

2026-10-12 连接诊断／强制审批变更前恢复提交：`8da2d25`（空快照提交，源码与 `b8c0866` 的 1.0.49 一致）。各阶段分别提交；恢复时从该提交建立独立分支或逐阶段 revert，不 reset/clean，不影响真实远端进程。

本轮开始前工作区干净，原始提交为 `75ec5b699c03e106b7c3cf3d6a83061db8eb1166`（1.0.47）。维护分支：`maintenance/project-organization-20261011`。

## 已建立的保护

- `main` 保持原始提交。
- Git 标签 `maintenance-baseline-20261011` 与备份分支 `backup/before-maintenance-20261011` 指向原始提交。
- 仓库外完整历史备份：`/home/qzh-n100/AI-Workspace/remotesshapp-before-maintenance-20261011.bundle`，已执行 `git bundle verify`。
- 仓库外基线 APK：`/home/qzh-n100/AI-Workspace/remotesshapp-before-maintenance-20261011.apk`。
- 基线验证摘要：`/home/qzh-n100/AI-Workspace/remotesshapp-before-maintenance-20261011-validation.json`。

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
git bundle verify /home/qzh-n100/AI-Workspace/remotesshapp-before-maintenance-20261011.bundle
git clone /home/qzh-n100/AI-Workspace/remotesshapp-before-maintenance-20261011.bundle /path/to/recovered-project
git -C /path/to/recovered-project switch backup/before-maintenance-20261011
```

该 bundle 是**整理前**快照，不包含后续维护提交。整理完成后的完整历史另存为 `/home/qzh-n100/AI-Workspace/remotesshapp-after-maintenance-20261011.bundle`，恢复维护成果时使用它并切换维护分支。前后备份各自保留，不覆盖基线。

已在独立临时目录实际执行整理前 bundle 的克隆和切换，核对原始提交与干净工作区，并通过 `git fsck --full`；证据见本轮验证记录。

## 手机与远端边界

本轮保持 versionName、versionCode、包名、存储键和数据格式，且没有安装到手机。基线 APK 保留原签名；如果后来发布了更高版本 APK，Android 可能拒绝直接覆盖降级。不要为回退默认卸载应用，卸载会丢失本机数据；先核对已安装版本／签名和可行的恢复方式。

切换 Git 不能撤回曾经部署到远端的脚本、任务操作或升级。本轮未部署／重启真实 Codex；后续若修改远端协议，须另外记录当前脚本、活跃进程、部署和恢复方法，不将源码回退等同于服务恢复。
