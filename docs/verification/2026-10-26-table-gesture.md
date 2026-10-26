# 2026-10-26 表格横滑错位修复

需求 R-22。恢复基线 `437cfca`；功能分支 `fix/table-horizontal-gesture`。仅修改原生滚动组件与测试／文档，不改版本、依赖、模型、服务器、连接配置或持久化，不发布／安装 APK。

## 问题与修复

用户报告向左滑动表格会异常上下错位。使用嵌套在 ConversationScrollView 中的长宽表，从外层容器分发真实 MotionEvent，并启用 Robolectric 原生绘制／字体度量。修复前在 Android 8／35、左／右横滑及纯横滑场景复现：消息区 Y 从 650 变为 558。单元格可选择文字在松手时获取焦点，触发外层 ScrollView 的自动焦点显示位移；横向／纵向手势也需要明确分配，避免带纵向偏移的横滑被外层抢占。

TableContentView.GridScroll 在 DOWN 暂缓父容器抢占，超过系统触摸阈值后按主要方向锁定横滑或释放纵滑。横滑时登记所属 ConversationScrollView，使其焦点矩形显示位移暂为零；Android 正常更新焦点与文字选择，不强行回滚已经发生的滚动。UP／CANCEL 后释放，detach 也释放焦点保护。该状态只属于当前 View，不进入会话容器；不改变表格原文、列宽、复制或流式复用规则。

## 定向证据

新增 TableGestureTest，从纵向父容器分发事件，覆盖左右横滑、纵向偏移、纯横滑松手焦点、纵滑、窄表、取消后纵滑、拖动中移除，以及 Android 8；同时检查横滑后正常程序／无障碍矩形显示请求恢复。原代码原生度量下 7 项测试有 4 项失败；修复后新增移除测试，共 8 项，全部通过、0 失败／错误／跳过。此前用默认非原生字体度量的开发运行不能正确模拟宽表宽度，不作为宽表回归证据。

## 完整验收

开发 Python 环境执行 `.venv/bin/python tools/verify.py --offline`：

- Android 412 项，0 失败／错误／跳过；包含 8 项 TableGestureTest 与已有表格、公式、消息滚动回归。
- Python 44 项全部通过；文档／模块登记／源文件规模、图标及公式字形检查通过。
- Debug APK 构建成功，Lint 0 Error/Fatal、19 项既有 Warning，无新增警告。
- Gradle 用时 3 分 19 秒，完整入口 `full_acceptance=true`。保留原有 AGP compileSdk 36 提示与 deprecated／unchecked 编译提示。
- 补全本记录后重新检查项目结构与 `git diff --check`，均通过。

产物为本地 debug 验证 APK，没有执行版本升级、发布或目标手机覆盖安装。

## 边界与回退

测试使用合成文本、Robolectric、回环 SSH/SFTP 与假 App Server，不调用真实模型、不读取真实凭据，不重启生产任务。自动化不能代替目标手机的手指滑动、OEM 焦点／选择行为和安装验收。新源码尚未安装到用户手机；已有手机问题与本次模拟复现的表现相符，但不宣称目标手机已修复验收。

可回退本次功能提交，恢复基线见上述提交及 docs/ROLLBACK.md。保留包名、签名身份与既有数据格式。
