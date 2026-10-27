# 第三方组件

项目自有材料适用 [LICENSE](../LICENSE)；下列材料独立授权，不受项目商业限制约束。
完整许可随源码、APK `assets/third_party/` 和安装 ZIP 分发。构建／测试工具不随 APK 发布。

| 组件 | 版本 | 许可与声明 |
| --- | --- | --- |
| [JSch](https://github.com/mwiede/jsch/tree/jsch-2.28.0) | 2.28.0 | [BSD 风格](../app/src/main/assets/third_party/jsch/LICENSE.txt)；内含 [JZlib](../app/src/main/assets/third_party/jsch/LICENSE.JZlib.txt)、[jBCrypt](../app/src/main/assets/third_party/jsch/LICENSE.jBCrypt.txt) 声明 |
| [Bouncy Castle bcprov-jdk18on](https://github.com/bcgit/bc-java/tree/r1rv80) | 1.80 | [Bouncy Castle 许可（MIT 风格）](../app/src/main/assets/third_party/bouncycastle/LICENSE.txt)，文本取自该版 JAR 的 LICENSE 类 |
| [JLaTeXMath Android](https://github.com/noties/jlatexmath-android) | 0.2.0 | [GPL-2.0-or-later 与独立模块链接例外](../app/src/main/assets/third_party/jlatexmath-android/LICENSE)、[GPL 正文](../app/src/main/assets/third_party/jlatexmath-android/COPYING)、[声明](../app/src/main/assets/third_party/jlatexmath-android/NOTICE.txt) |

公式库未经修改，通过公开 TeX API 与独立 Canvas 适配器连接；链接例外允许独立模块采用其他许可，
库及其字体仍遵守原许可。安装 ZIP 同时提供库完整对应源码和
[0.2.0 源码 JAR](https://repo.maven.apache.org/maven2/ru/noties/jlatexmath-android/0.2.0/jlatexmath-android-0.2.0-sources.jar)。

字体许可随 AAR 合并到 APK `assets/org/scilab/forge/jlatexmath/fonts/licences/`，包含 Knuth、OFL、dsrom 许可。
未引入可选希腊语正文／西里尔文字体包；数学希腊符号来自基础数学字体。
`math_glyphs.json` 由生成器从固定摘要 AAR 提取原始 U+00AD 字形轮廓，适用来源字体许可。

发布时核对 APK 内项目许可、全部组件声明、字体许可与源码包；不以项目许可覆盖第三方材料。
