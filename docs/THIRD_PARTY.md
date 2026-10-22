# 第三方公式组件

1.0.56 使用未经修改的 `ru.noties:jlatexmath-android:0.2.0`，用于本地 Canvas 数学排版。

- [上游项目](https://github.com/noties/jlatexmath-android)
- [0.2.0 原始源码归档](https://repo.maven.apache.org/maven2/ru/noties/jlatexmath-android/0.2.0/jlatexmath-android-0.2.0-sources.jar)
- [库许可及独立模块链接例外](../app/src/main/assets/third_party/jlatexmath-android/LICENSE)
- [GPL 正文](../app/src/main/assets/third_party/jlatexmath-android/COPYING)
- [随包声明](../app/src/main/assets/third_party/jlatexmath-android/NOTICE.txt)

上游字体许可随 AAR 合并到 APK 的 `org/scilab/forge/jlatexmath/fonts/licences` 资产目录，包含 Knuth License、OFL 和 dsrom 字体许可。未引入可选希腊语正文或西里尔文字体包；数学希腊符号由基本数学字体提供。

分发 1.0.56 制品时同时提供未经修改的对应源码归档与许可材料。项目自己的独立模块继续使用原有项目许可；第三方库与字体适用各自许可。

应用通过公开 TeX API 适配绘制，未替换或修改依赖中的字体。`math_glyphs.json` 由 `tools/generate_math_glyphs.py` 从固定 SHA-256 的 0.2.0 AAR 提取 U+00AD 原始字形轮廓，以避免 Android 将其作为软连字符隐藏。轮廓适用来源字体的原许可，完整验收检查其可重现性。
