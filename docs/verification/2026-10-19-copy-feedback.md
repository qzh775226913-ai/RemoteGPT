# 对话复制图标点击反馈修复

日期：2026-10-19。恢复基线：`2a82feb`。源码基于 1.0.54 / versionCode 55；本记录不代表已发布新版 APK。

## 问题与修复

对话复制图标经 DialogUi 创建时同时具有背景与前景 RippleDrawable；前景使用覆盖整块点击区域的矩形遮罩。用户消息复制图标单独右下对齐，而反馈仍按整个 View 定位。

PressFeedback 对复制图标移除重复的背景波纹。IconView 将圆形遮罩、波纹半径、触点中心和图标定位统一到最大 32dp 的视觉区域；用户消息旁按右下区域对齐，图标在圆内居中。点击区域保留 48dp，复制回调和原文内容不变。其他按钮与文字复制入口不变。

## 验证

- CopyFeedbackTest 在 Android 8／35、xhdpi／mdpi 和明暗主题下检查单层反馈、圆形遮罩像素、图标落在圆内、反馈尺寸、右下对齐和尺寸变化；点击区域外侧边缘触发一次回调。
- 原生绘制生成静态遮罩示意 `app/build/screenshots/copy-feedback-geometry-1x.png` 与 `copy-feedback-geometry-2x.png`，已检查 2x 图的形状和对齐。这些图片不是动态波纹录屏。
- 开发 Python 环境执行 `.venv/bin/python tools/verify.py --offline`：364 项 Android 测试，0 失败／错误／跳过；44 项 Python 测试通过；文档／结构、生成图标、APK 构建和 Lint 通过。Lint 0 Error/Fatal，17 项既有 Warning。Gradle 2 分 1 秒，full_acceptance=true。
- `git diff --check` 通过；需求 R-15 和验收条目已同步。

## 边界

自动化验证遮罩、布局、点击分发及原有复制回归，不替代目标手机的硬件渲染波纹动画验收。未使用真实 SSH 账户或模型服务，未发布或安装新版 APK。
