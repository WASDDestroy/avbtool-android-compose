# MVVM 架构说明

本文说明 AVBTool Android 的状态管理分层：哪些状态由 ViewModel 持有、哪些刻意留在
Composable 里、以及新增功能时该把状态放在哪里。

英文版散落在 [`ui/architecture.md`](ui/architecture.md) 第 6 节，本文是更完整的中文
版本，额外记录了设计取舍的原因。

## 1. 分层结构

```
用户操作 → Composable 事件回调 → ViewModel 方法
                                      ↓
                          AvbTaskRunner / SafFileBridge
                                      ↓
                          MutableStateFlow<UiState> 更新
                                      ↓
              collectAsStateWithLifecycle() → Composable 重组
```

三层各自的职责：

| 层 | 文件 | 职责 |
|---|---|---|
| Model / 运行时 | `AvbRuntime.kt` | `PythonRuntime`（Chaquopy 解释器启停）、`AvbTaskRunner`（执行 avbtool 并捕获输出）、`SafFileBridge`（SAF fd 与私有拷贝）。纯 Kotlin，不依赖 Compose。 |
| ViewModel | `CommandViewModel.kt`、`ConsoleViewModel.kt`、`SettingsViewModel.kt` | 持有业务状态，暴露 `StateFlow`。 |
| View | `CommandScreen.kt`、`ConsoleScreen.kt`、`SettingsScreen.kt`、`HomeScreen.kt`、`MainActivity.kt` | 渲染 + 转发事件 + 持有纯 UI 瞬态。 |

`AvbRuntime.kt` 在重构前就已经是不依赖 Compose 的纯 Kotlin 类，本身就具备 Model 层
的形态，因此本次重构没有改动它。这也是重构工作量可控的主要原因。

## 2. 三个 ViewModel

### CommandViewModel

命令执行的全部逻辑：argv 构建、SAF 文件描述符生命周期管理、结果解析。

```kotlin
data class CommandUiState(
    val running: Boolean = false,
    val result: AvbCommandResult? = null,
    val outputFile: File? = null,
)
```

公开方法：

- `run(cmd, values, uri)` — 执行命令。执行中重复调用会被忽略（`if (running) return`）。
- `failWithMissingImage()` — 用户未选镜像时的前置校验失败。
- `dismissOutputFile()` — 关闭输出文件对话框。

**获取方式必须带 key：**

```kotlin
val viewModel: CommandViewModel = viewModel(
    key = command.id,
    factory = CommandViewModel.factory(context),
)
```

`key = command.id` 让每个命令拥有独立实例。**去掉这个 key 会导致一个命令的执行结果
串到另一个命令的界面上**，这是最容易踩的坑。代价是每访问一个命令会在 Activity 的
ViewModelStore 里留下一个实例，但命令总数约 20 个、每个只持有一份 UiState，开销可
忽略。

`execute()` 整体运行在 `Dispatchers.IO` 上。重构前 argv 构建和
`bridge.copyToPrivate()`（大镜像文件的整体拷贝）跑在主线程，只有 `runner.run()` 内部
切到了 IO，存在 ANR 风险；现已消除。

### ConsoleViewModel

这个 ViewModel **不套用统一的 UiState 数据类模板**，因为它的核心状态是
`AvbtoolTermSession`——一个绑定原生 `AndroidView` 的会话对象，不是若干原始值。强行
包成 data class 没有意义。

```kotlin
class ConsoleViewModel(
    runner: AvbTaskRunner,
    val bridge: SafFileBridge,
    appContext: Context,
) : ViewModel() {
    val session = AvbtoolTermSession(runner, viewModelScope, appContext)
    val storageGranted: StateFlow<Boolean>
    fun refreshStoragePermission(context: Context)
    override fun onCleared() { session.finish() }
}
```

会话绑定 `viewModelScope`（而非重构前的 `rememberCoroutineScope()`），因此正在执行的
console 命令不会因为 Composable 离开组合而被取消，终端内容也会在屏幕旋转后保留。
`session.finish()` 相应地从 `DisposableEffect.onDispose` 移到了 `onCleared()`。

### SettingsViewModel

五个设置项：动态取色、主题模式、AMOLED 纯黑、预测性返回手势、语言。

**设置项仅存在于内存中**，与重构前的 `rememberSaveable` 行为一致：进程被杀死后回到
默认值。不引入 DataStore 是本次重构明确的非目标——如果要加持久化，那是一次独立的、
有意的功能变更，不应该混在结构重构里。

重构前这五个值连同五个 `on...Change` 回调从 `AVBToolAndroidApp` 经 `RootScreen` 一路
透传到 `SettingsScreen`，构成一个十参数的函数签名。现在 `RootScreen` 只剩 4 个参数，
`SettingsScreen` 只剩 2 个。

## 3. 依赖注入

**项目不使用任何 DI 框架**，不要引入 Hilt 或 Koin。需要依赖的 ViewModel 通过
`companion object` 里的工厂方法构造：

```kotlin
companion object {
    fun factory(context: Context): ViewModelProvider.Factory {
        val appContext = context.applicationContext
        return viewModelFactory {
            initializer {
                CommandViewModel(
                    runner = AvbTaskRunner(appContext),
                    bridge = SafFileBridge(appContext),
                    appContext = appContext,
                )
            }
        }
    }
}
```

注意 `context.applicationContext`：**ViewModel 绝不能持有 Activity Context 或任何
View 引用**，否则会造成内存泄漏（ViewModel 的生命周期比 Activity 长）。

`SettingsViewModel` 没有构造参数，直接 `viewModel()` 即可，不需要工厂。

依赖方面只额外引入了 `androidx.lifecycle:lifecycle-viewmodel-compose` 一个库；
`lifecycle-runtime-compose`（提供 `collectAsStateWithLifecycle()`）和
`lifecycle-viewmodel`（提供 `viewModelFactory` DSL）本来就已通过其他依赖传递引入。

## 4. 刻意留在 Composable 里的状态

这部分是新人最容易搞错的地方——**不是所有状态都该进 ViewModel**。

### 平台限制导致必须留下

- **`terminalView`（`CopyableEmulatorView` 引用）** —— ViewModel 持有 View 会泄漏内存。
- **`rememberLauncherForActivityResult`（存储权限、SAF 文档选择器）** —— Activity
  Result API 绑定 Activity 生命周期，无法在 ViewModel 中注册。
- **IME 显隐辅助函数** —— 需要 View 实例。
- **`applyAppLanguage()`** —— 在 API 33 以下会调用 `Activity.recreate()`，属于
  Activity 级副作用。ViewModel 只保存选中的 `LanguageMode`，实际的语言切换由
  `AVBToolAndroidApp` 里的 `LaunchedEffect(settings.languageMode)` 响应状态变化来触发。

### 设计取舍导致留下

- **导航状态**（`commandId`、`commandBackProgress`、`PredictiveBackHandler` 逻辑）——
  项目刻意不使用 Navigation 库，导航保持简单，全部留在 `AVBToolAndroidApp` /
  `RootScreen`。
- **`CommandScreen` 的 `values` 表单字段** —— 它承载参数编辑的全部交互（文件选择器
  追加行、chain partition 编辑器等），迁入 ViewModel 会把大量 UI 逻辑一并拖进去。
  注意它用的是 `remember(command.id)` 而非 `rememberSaveable`，旋转屏幕即重置——这是
  重构前就有的行为，本次未改变。
- **所有弹窗开关与编辑瞬态**：`copyWarning`、`editingArg`、`choosingAlgorithm`、
  `managingFileArg`、`managingChainArg`、`chainEditor`、`chainKeyPickRequest`、
  `advancedExpanded`、`rawExpanded`，以及 `SettingsScreen` 的三个对话框开关。这些只
  影响当前界面呈现，不是业务状态。

### `HomeScreen` 没有 ViewModel

它只渲染命令列表并转发点击，没有任何状态。给它加一个空壳 ViewModel 只会增加样板
代码，不会带来任何好处。

## 5. 新增功能时状态该放哪

按这个顺序自问：

1. **它需要跨屏幕旋转存活吗？** 不需要 → 留在 Composable。
2. **它是"界面当前长什么样"还是"业务上发生了什么"？** 前者（哪个弹窗开着、哪个
   项目正在编辑、某区块是否展开）→ 留在 Composable。
3. **它引用 View、Activity、或 Activity Result Launcher 吗？** 是 → 必须留在
   Composable。
4. **它是某个操作的执行状态或结果吗？** 是 → 放进对应的 ViewModel UiState。

新增一个 UiState 字段时，同时更新对应的 `data class` 和所有 `_uiState.update {}`
调用点；`StateFlow` 保证状态变化是原子的（例如 `running` 和 `result` 一起更新，不会
出现"已完成但结果为空"的中间态）。

## 6. 主题与配色

`ui/theme/` 下的调色板是静态 Compose `Color` 定义，**不是状态**，不经过 ViewModel。
ViewModel 里存的只是"用户选了哪个主题模式"（`ThemeMode.LIGHT` / `DARK` /
`FOLLOW_SYSTEM`）和"是否启用动态取色"这类选项值，不存颜色本身。

### 配色方案的选取顺序

`AVBToolAndroidTheme`（`Theme.kt`）按以下优先级挑选配色方案：

1. `amoledBlack` 且深色模式 → `AmoledBlackColorScheme`
2. `dynamicColor` 且 Android 12+ → 动态取色（从壁纸提取，**完全覆盖**预设调色板）
3. 其余情况 → 预设的 `LightColorScheme` / `DarkColorScheme`

**动态取色默认开启。** 所以在 Android 12 以上的设备上，改了预设调色板却看不到变化
是正常的——必须先到设置页关掉「动态主题颜色」开关。调试配色时务必注意这一点。

### 预设调色板在哪

`ui/theme/Color.kt` 保存完整的 Material 3 调色板，种子色为 **`#0061A4`（蓝色）**，
浅色深色各一整套（primary、secondary、tertiary、error、各级 surface container、
inverse、outline 等约 30 个 color role）。

`AmoledBlackColorScheme` 用 `DarkColorScheme.copy(...)` 派生，只覆盖各个 surface
角色为纯黑。**它必须保持是 `DarkColorScheme` 的 `copy()`**：如果改回从裸的
`darkColorScheme()` 构造，强调色会悄悄退回 Material 3 默认紫，而 app 其余部分是蓝色，
形成割裂——这正是本次换色前的实际状态。

### 换主色要改几个地方

1. `ui/theme/Color.kt` —— 改 `LightPrimary` 等一整套色值（建议用
   [Material Theme Builder](https://m3.material.io/theme-builder) 从种子色生成，
   不要手工凑，否则对比度容易不达标）。
2. `app/src/main/res/values/colors.xml` 与 `values-night/colors.xml` 的
   `splash_screen_background` —— 必须与 `Color.kt` 里的 `LightBackground` /
   `DarkBackground` 一致。这两个值绘制的是 Compose 接管之前的原生窗口背景，不一致
   会导致启动瞬间闪一下别的颜色。
3. 无需改动任何 Composable —— 所有界面都用 `MaterialTheme.colorScheme.*` 语义
   token，不硬编码颜色。

## 7. 相关文档

- [`ui/architecture.md`](ui/architecture.md) —— UI 架构总览（英文），第 6 节是状态管理。
- [`superpowers/specs/2026-08-17-mvvm-refactor-design.md`](superpowers/specs/2026-08-17-mvvm-refactor-design.md)
  —— 本次重构的设计文档，记录了目标、非目标与决策依据。
- [`superpowers/plans/2026-08-17-mvvm-refactor.md`](superpowers/plans/2026-08-17-mvvm-refactor.md)
  —— 实施计划，逐任务逐步骤的改动清单。
- `../AGENTS.md` —— 给贡献者和 AI 助手的硬性约定，"State management" 一节是本文的
  精简版。
