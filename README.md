# 简易编辑器（SimpleTextEditor）

一个能装在 Android 8 及以上手机上的极简文本编辑器，用来直接打开手机里的文件、改完自动保存。

## 能做什么

- 选一个本地文件夹，进去浏览，支持一层层往下点、也能返回上一级
- 点任意文件就能打开编辑
- **新建文件**：在当前文件夹里直接新建，默认名「未命名.md」，建完自动打开
- 改完不用管，停止输入 1.2 秒自动保存；切走 / 退出时再补存一次
- 顶部有「保存」按钮，想手动存一下也行
- **Markdown 支持**：`.md` / `.markdown` 等文件编辑页右上角多一个「预览」按钮，
  一键渲染成排版好的页面（标题/列表/表格/代码块/引用/链接/删除线/任务列表），
  再点「编辑」回到改字；列表里 Markdown 文件会带 MD 标记
- 自动识别编码：UTF-8（含 BOM）优先，其次 GB18030（兼容 GBK / GB2312），保存时按原编码写回
- 保留原来的换行格式（Windows 的 CRLF / Unix 的 LF）

## 装到手机

1. 把 `dist/简易编辑器.apk` 拷到手机（微信传文件、数据线、adb 都行）
2. 手机上点开安装，允许「安装未知来源应用」
3. 首次打开点「选择文件夹」，授权一次，之后会记住这个文件夹

也可以用命令行装（手机开 USB 调试、连上电脑）：

```bash
toolchain\android-sdk\platform-tools\adb.exe install -r dist\简易编辑器.apk
```

## 用起来

- **选择文件夹**：系统弹出的目录选择器里挑一个目录，比如 `Download`、`Documents`
- **列表里点文件夹**：进去；点右上「上一级」或按返回键：回上一层
- **列表里点文件**：打开编辑
- **打开文件**：不想在目录里翻，直接挑一个文件打开
- **新建文件**：在输入框里改文件名（默认 `未命名.md`）→「创建」，会直接打开新文件开始写
- **Markdown 预览**：`.md` 文件编辑页右上角点「预览」看渲染效果，再点「编辑」回来改；
  预览前会先自动保存，保证看到的就是文件内容
- 编辑页左上角会显示「编辑中…」和「已保存 HH:mm:ss」

## 说明 / 限制

- 只处理文本文件；二进制文件（图片、apk 等）会提示不是文本、不打开
- 单文件上限 3MB，超过就不编辑了（避免写回时把内容截断）
- 不申请任何存储权限，全部走系统的文件选择器（SAF），所以不用手动开权限
- 没有联网权限，纯本地
- Markdown 预览目前不支持渲染本地图片（图片位置显示为「［图片：说明］」占位），
  外网图片链接会显示为文字链接

## 代码结构

```
SimpleTextEditor/
├─ app/src/main/java/com/leon/simpleeditor/
│  ├─ MainActivity.java      目录浏览 + 选文件夹 / 选文件 / 新建文件
│  ├─ FileEntryAdapter.java  文件列表适配器
│  ├─ EditorActivity.java    编辑器 + 自动保存 + Markdown 预览切换
│  ├─ MarkdownRenderer.java  Markdown → HTML 渲染（内置，无第三方库）
│  └─ TextCodec.java         编码识别（UTF-8 / GB18030）
├─ app/src/main/res/         布局、颜色、图标
├─ dist/                     编译好的安装包
└─ build.bat / build.sh   开发机一键编译脚本
```

## 重新编译

准备 JDK 17、Android SDK 34（build-tools 34.0.0）、Gradle 8.13，
设好 `JAVA_HOME` / `ANDROID_HOME`（或在 `local.properties` 里写 `sdk.dir`）后：

```bash
gradle assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`，也可以直接用 Android Studio 打开本目录编译。
`build.bat` / `build.sh` 是开发机上的一键脚本（硬编码了本机自包含工具链路径），换机器请用上面的命令。

## 技术参数

| 项 | 值 |
|---|---|
| 包名 | com.leon.simpleeditor |
| minSdk | 26（Android 8.0） |
| targetSdk | 34 |
| 语言 | Java 17 |
| 依赖 | androidx.appcompat 1.6.1、androidx.documentfile 1.0.1 |

## 开源

- 许可证：[MIT](LICENSE)
- 纯本地应用：不联网、不申请存储权限、不收集任何数据
- 同系列另有 MusicPlayer（本地音乐播放器）和 GitSyncApp（Git 自动同步客户端）
- 欢迎 Issue / PR，功能保持「够用就好」
