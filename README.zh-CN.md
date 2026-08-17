# AVBTool Android (Compose)

[English](README.md) | **简体中文**

一个 Android Compose 应用，通过 [Chaquopy](https://chaquo.com/chaquopy/) 内嵌
**Python 3.13**，直接在设备上运行 AOSP 的 `avbtool.py`。

## 功能

- **图形界面模式** —— 扁平的、按使用频率排序的命令列表，配表单式命令页：
  - 映像配置
  - 密钥配置（签名算法下拉框 + RSA PEM 密钥选择器）
  - 可折叠的高级配置
  - 布尔标志用开关控制
- **控制台模式** —— 真正的终端模拟器视图（内置的 TermOnePlus `emulatorview`），
  后端是同进程内的 Python 运行器。
- **SAF 文件描述符桥接** —— 选中的 `content://` 文件以 `/saf/fd/<fd>` 路径传入
  Python，避免复制大体积映像。那些会从选中路径**派生同级路径**的命令
  （`verify_image`、`print_partition_digests`、`calculate_vbmeta_digest`、
  `calculate_kernel_cmdline`）改用私有副本，以保证链式分区能正确解析。
- **原生 FEC** —— `libavbfec.so` 以原生代码实现 AOSP 的 RS(255, N) FEC 编码，
  已与 AOSP 主机版 `fec` 做逐字节比对验证。
- **预测性返回** —— 命令页和「控制台→首页」的导航支持预测性返回手势。
- **多语言界面** —— 支持英文与简体中文，在「设置 → 语言」中切换（跟随系统 /
  English / 简体中文）。
- **已签名构建** —— debug 与 release APK 均用本地测试密钥签名。
- **MVVM 架构** —— 业务状态由 `CommandViewModel` / `ConsoleViewModel` /
  `SettingsViewModel` 持有，以 `StateFlow<UiState>` 暴露；Composable 只负责渲染
  和转发事件。不使用任何 DI 框架，仅用 `viewModelFactory` DSL。
  详见 [中文架构说明](docs/mvvm-架构说明.md)。

## 环境要求

- Android SDK 37
- Android NDK `29.0.14206865`
- JDK 17 或更高版本
- 构建机需安装 Python 3.13
- 一台 `arm64-v8a` 设备或模拟器

## 构建

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

> **注意**：`app/build.gradle.kts` 中 Chaquopy 的 `buildPython(...)` 是一条
> **硬编码的绝对路径**，指向构建机上的 Python 3.13 可执行文件。在新机器上首次
> 构建前，需要把它改成本机的实际路径，否则构建会失败。

## 签名

项目使用一个**已签入仓库的测试密钥**：

```text
keystore/testkey.jks
alias: avbtool
store/key password: avbtool123
```

请勿将此密钥用于生产环境。正式发布请自行添加签名配置。

## Python 集成

- Python 源码位于 `app/src/main/python/`。
- `avbtool.py` 是 AOSP `avbtool.py` 的打补丁副本：
  - OpenSSL 调用替换为 `cryptography` 包。
  - `fec` 子进程调用替换为原生 `libavbfec.so`。
  - `ImageHandler` 会关闭其内部的文件描述符。
  - 每条命令执行完毕后关闭 `argparse.FileType` 产生的文件对象。
- `android_bridge.py`：
  - 加载 `libavbfec.so`
  - 安装 `/saf/fd/<fd>` 的 `builtins.open` 钩子
  - 运行 avbtool 并捕获 stdout/stderr

## 原生 FEC

源码：`app/src/main/cpp/avb_fec.cpp`，以及 `app/src/main/cpp/fec_rs/` 下来自 AOSP
`external/fec` 的 RS 编码器文件。

- 仅构建 `arm64-v8a`。
- 仅支持 raw 映像；稀疏映像做 FEC 时会被拒绝，并提示使用 `simg2img`。
- 在控制台中运行 FEC 自检：

```text
avbtool_fec_self_test
```

主机端参照命令：

```bash
python -c "open('selftest.img','wb').write((bytes(range(256))*16)*256)"
fec --encode --roots 2 selftest.img ref.fec
sha256sum selftest.img ref.fec
```

## 许可证

项目自身的源代码采用 **Apache License 2.0**（`LICENSE`），但有以下明确的例外：

- `app/src/main/cpp/fec_rs/*` —— **LGPL-2.1**。
  Copyright (C) 2002-2004 Phil Karn, KA9Q。
  这些文件被单独编译进 `libfec_rs.so` 共享库，以便该 LGPL 组件可以被独立替换或
  重新链接。完整文本见 `LICENSES/LGPL-2.1.txt` 与
  `app/src/main/assets/LGPL-2.1.txt`。
- `app/src/main/python/avbtool.py` —— **MIT**。
  Copyright 2016, The Android Open Source Project。
- TermOnePlus emulatorview —— **Apache-2.0**。
- Chaquopy —— **MIT**。

随 APK 一同分发的开源声明：`app/src/main/assets/open_source_licenses.txt`。

## 已知限制

- FEC 生成仅支持 raw 映像。
- 控制台不是真正的 PTY：没有 shell 作业控制，`Ctrl+C` 的作用是取消/清除。
- 控制台的命令解析不支持带引号的路径。
- 图形界面的文件输出选项是文本输入框；二进制输出请使用 SAF 导出。
- Python 在应用进程内运行，长时间的原生操作无法被强制终止。

## 文档

| 文档 | 说明 |
|---|---|
| [MVVM 架构说明](docs/mvvm-架构说明.md) | 状态管理分层、三个 ViewModel 的职责、主题配色的改法 |
| [UI Architecture](docs/ui/architecture.md) | UI 架构总览（英文），含状态管理与主题章节 |
| [Adding UI Items to Screens](docs/ui/how-to-add-ui-items.md) | 如何新增命令行、参数行与页面内容（英文） |
| [Adding Shared UI Components](docs/ui/shared-components.md) | 如何扩展共享的偏好设置行组件库（英文） |
| [AGENTS.md](AGENTS.md) | 给贡献者与 AI 助手的开发约定（英文） |
