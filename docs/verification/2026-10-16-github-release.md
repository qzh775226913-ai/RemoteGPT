# 1.0.54 GitHub APK 发布

2026-10-16，恢复提交 `6610e04`。按用户要求补发已经构建和完整验证的 1.0.54 APK，不重建安装包或改变版本／依赖／签名，不调用模型或操作生产远端。

原应用源码 `0a5a1e6` 的 [完整 GitHub Verify](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38870356651) 已成功，本地 362 项 Android／44 项 Python、构建、Lint 结果见 [版本验证](2026-10-16-1.0.54.md)。此次源码变化仅为发布文档，同一发布提交另由 GitHub Verify 重新完整验证。

制品为 RemoteGPT-1.0.54-Android-universal-debug.apk（3,813,075 字节），SHA-256 `5080a6aad0b487dbdf4816bd27d4e4a16686586ca7d195fd5ea168c14daecb8b`，apksigner 证书 SHA-256 仍为 `43ef05b33d4e9505eff7370a3a830af59dbc0b256c9925a12f76273e353f020a`。另生成含同一 APK、LICENSE 和安装说明的 ZIP，附 APK／ZIP 校验文件及 LICENSE.txt，共五个附件。

使用现有显式发布工作流、正式版本标签及临时注释标签。已签名制品经临时 HTTPS 托管交给发布器，设置 7200 秒自动删除；临时地址不写入源码。工作流核对全部摘要、ZIP／许可及同一源码完整 CI，草稿附件齐全后才公开；签名私钥不上传。实际公开结果与下载核验在完成后追加，不将准备阶段当作发布成功。


## 实际公开结果

[发布任务](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38871585075) 成功公开 [RemoteGPT 1.0.54](https://github.com/qzh775226913-ai/RemoteGPT/releases/tag/v1.0.54)，Release ID `414697976`。正式标签固定在文档发布提交 `da6c30a`，应用代码与原已验证 `0a5a1e6` 一致；[同一发布提交完整 Verify](https://github.com/qzh775226913-ai/RemoteGPT/actions/runs/38871419294) 成功。releases/latest 实际返回 v1.0.54，draft=false、prerelease=false。

五个附件均为 uploaded，GitHub 返回的大小及 SHA-256 与本地逐一匹配。实际从公开 GitHub APK 地址完整下载 3,813,075 字节，SHA-256 与原包一致，下载副本 apksigner 验签成功、签名与旧版一致；不需要 tailnet。公开结果与下载元数据保存在 ignored 的 app/build/verification。

临时托管曾拒收 .sha256 后缀；仅将传输端的文件名改为 .txt，内容逐字节不变，GitHub 上保留正式 .sha256 文件名。没有绕过账户授权或安全检查。临时触发标签在发布后清理，托管文件按上传参数 7200 秒自动到期；正式版本标签、Release 和本地已签名制品保留。最终文档／结构与 git diff --check 通过，未部署或重启生产 Codex，真机安装与真实服务的原验收边界继续有效。
