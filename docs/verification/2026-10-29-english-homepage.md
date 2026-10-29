# 英文 GitHub 首页

- 恢复基线：`b27fd78`（GitHub main，修改前）。
- 将原英文 README 用作默认首页，替换用户指定的标题和简介；原中文首页保留为 README.zh-CN.md，更新语言入口。
- 验证：`git diff --check` 和 `tools/check_project.py` 通过。仅涉及文档，未运行完整 Android 验收；不涉及 APK、真机或服务验证。
