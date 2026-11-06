# 简易文本编辑器 v1.0.0

Android 8（API 26）及以上可用的纯本地文本编辑器，界面简单、打开即用。

## 功能

- 打开本地文件 / 本地文件夹，任意选中一个文件编辑
- 修改后 1.2 秒自动保存，切走页面或退出时补存
- 新建文件
- 自动识别 UTF-8 / UTF-8 BOM / GBK(GB18030)，写回时保留原有 LF / CRLF
- 识别 `.md` 文件并提供 Markdown 预览：标题、列表、任务列表、表格、代码块、引用、链接、删除线
- 不联网、不申请存储权限、不收集任何数据；文件通过系统文件选择器（SAF）临时授权访问

## 附件

- `SimpleTextEditor-v1.0.0.apk` — 正式版，直接安装
- `SimpleTextEditor-v1.0.0-debug.apk` — 调试版

仓库 `dist/` 下另有中文文件名的同两份 APK，内容完全相同。

## 从源码构建

需要 JDK 17、Android SDK 34（build-tools 34.0.0）、Gradle 8.13，详见仓库 README。

MIT 许可证。同系列另有 MusicPlayer 与 GitSyncApp。
