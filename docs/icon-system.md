# RemoteGPT 图标

本套图标为项目自行绘制的 Android 矢量路径。参考 [OpenAI 官方设计语言](https://openai.com/brand/) 的简洁几何与留白，未采用官方 Blossom 图形。

## 统一几何

- 基准画布：24 × 24，描边 1.8，圆端点、圆转角。主题决定前景颜色，状态颜色沿用既有设置。
- 应用标识：两个圆角回路围绕 `(12, 12)` 呈 180° 旋转对称。内部端点位于 `x = 8` 和 `x = 16`，中央箭头包围框中心也为 `(12, 12)`；两侧保持相同净空。
- 标识用于安装／桌面、空白对话、通知栏、持续状态通知及系统气泡入口。通知使用透明背景的单色轮廓，应用图标使用 `#171717` 平面背景。
- 启动图标使用 108 × 108 图层，标识的缩放与位移固定，轮廓位于中央 66 dp 圆形安全区内；Android 13 及以上提供相同前景的主题单色图层。系统如何显示持续通知和流体云由手机系统决定。
- 界面控件与状态符号统一使用同一组矢量资源，包括选中／未选中的单选框与复选框。保留原触摸区域、原生选中状态、无障碍文字、复制文本及正文排版。

## 修改与验证

唯一绘图源为 [tools/icon_spec.json](../tools/icon_spec.json)。修改后运行：

```sh
python3 tools/generate_icons.py
python3 tools/generate_icons.py --check
```

生成器同步更新界面资源、应用图标、通知图标及 Java 资源目录。不要分别手改生成的路径，以免入口之间出现不同版本。

`IconStyleTest` 使用 Android 原生图形渲染检查 24 px 的留白、中心箭头与回路净空、通知透明轮廓、Android 8 自适应图标、Android 13 主题图标安全区、主题切换和原生选择状态，并输出原生预览至 `app/build/screenshots/`。预览包括 `launcher-icon-masks.png`、`icon-system-dark.png` 和 `RemoteGPT-icon-512.png`。

Android 图层依据：[自适应图标](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)。
