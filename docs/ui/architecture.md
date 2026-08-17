# UI Architecture

## 1. Overview

The app UI is built with **Jetpack Compose** and **Material 3**. There is no
Navigation library; navigation is intentionally small and implemented with
Compose state plus activity back handlers.

Key sources:

- `app/src/main/java/me/wasddestroy/avbtoolandroid/MainActivity.kt`
- `app/src/main/java/me/wasddestroy/avbtoolandroid/HomeScreen.kt`
- `app/src/main/java/me/wasddestroy/avbtoolandroid/ConsoleScreen.kt`
- `app/src/main/java/me/wasddestroy/avbtoolandroid/CommandScreen.kt`
- `app/src/main/java/me/wasddestroy/avbtoolandroid/ui/components/PreferenceComponents.kt`
- `app/src/main/java/me/wasddestroy/avbtoolandroid/ui/theme/Theme.kt`
- `app/src/main/java/me/wasddestroy/avbtoolandroid/AvbModels.kt`
- `app/src/main/java/me/wasddestroy/avbtoolandroid/CommandViewModel.kt`
- `app/src/main/java/me/wasddestroy/avbtoolandroid/ConsoleViewModel.kt`
- `app/src/main/java/me/wasddestroy/avbtoolandroid/SettingsViewModel.kt`

## 2. Theme and styling

`AVBToolAndroidTheme` (`ui/theme/Theme.kt`) picks a color scheme in this
priority order:

1. `amoledBlack` **and** dark → `AmoledBlackColorScheme`
2. `dynamicColor` **and** Android 12+ → `dynamicLightColorScheme` /
   `dynamicDarkColorScheme` (extracted from the wallpaper; overrides the preset
   palette entirely)
3. otherwise → the app's preset `LightColorScheme` / `DarkColorScheme`

**Dynamic color defaults to on**, so on Android 12+ the preset palette is only
visible after turning "dynamic theme color" off in Settings. Keep this in mind
when testing palette changes — a wallpaper-tinted build looks nothing like the
preset.

### Preset palette

`ui/theme/Color.kt` holds the full Material 3 palette, seeded from **`#0061A4`
(blue)**. Both light and dark sets are complete — primary, secondary, tertiary,
error, surface containers, inverse, and outline roles.

`AmoledBlackColorScheme` is derived with `DarkColorScheme.copy(...)`, overriding
only the surface roles to pure black. It must stay a `copy()` of
`DarkColorScheme`: building it from a bare `darkColorScheme()` would silently
keep the Material 3 default purple accents while the rest of the app is blue.

### Platform colors

`splash_screen_background` in `values/colors.xml` (`#FDFCFF`) and
`values-night/colors.xml` (`#1A1C1E`) must match `LightBackground` /
`DarkBackground` in `Color.kt`. These paint the window before Compose takes
over; a mismatch shows as a color flash on launch.

Do **not** hardcode product colors in composables. Use
`MaterialTheme.colorScheme.*` tokens.

## 3. Navigation model

All navigation is kept in `MainActivity.kt`.

### Root destinations

`AppDestinations` contains the three tab destinations:

- `HOME` (`Icons.Filled.Home`)
- `CONSOLE` (`Icons.Filled.Terminal`)
- `SETTINGS` (`Icons.Filled.Settings`)

`RootScreen` renders:

- `NavigationSuiteScaffold` for the bottom navigation bar.
- A `HorizontalPager` that hosts `HomeScreen` and `ConsoleScreen`.
- A `PredictiveBackHandler` that drives the pager from the Console-to-Home
  predictive back gesture. The handler:
  - scrolls the pager while the user drags,
  - animates to Home on commit,
  - animates back to Console on cancel.

### Command destination

`CommandScreen` is a real full-screen destination, not an overlay owned by
`HomeScreen`.

In `AVBToolAndroidApp`:

```kotlin
var commandId by rememberSaveable { mutableStateOf<String?>(null) }
```

- `commandId == null` means the root pager destination is visible.
- `commandId != null` means `CommandScreen` is visible on top of the root
  pager. The root stays composed behind it so predictive back can reveal it.

`CommandScreen` is translated by an `Animatable<Float>`:

```kotlin
translationX = commandBackProgress.value * size.width
```

A `PredictiveBackHandler` updates that progress during the gesture. On commit
it animates the command screen fully off-screen and clears `commandId`; on
cancel it animates it back to `0f`.

### Back handling summary

| Gesture | Handler | Behaviour |
|---|---|---|
| Console → Home predictive back | `RootScreen` | Pager is dragged; commit/cancel animates to the target page. |
| Command → Home predictive back | `AVBToolAndroidApp` | Command screen slides right; root is revealed behind. |
| System back with Command open | `BackHandler` | Animates command screen out and clears `commandId`. |

## 4. Screen inventory

### `HomeScreen`

- Renders the command list inside `SettingsList`.
- Uses one `preferenceGroup` for the command card.
- Each command row is a `PreferenceRow` with:
  - title from `stringResource(command.titleRes)`
  - description from `stringResource(command.descriptionRes)`
  - icon from `commandIcon(command.id)`
  - click callback `onOpenCommand(command.id)`

### `ConsoleScreen`

- Renders the terminal emulator via `AndroidView`.
- Shows a storage-permission card when storage is not granted.
- Uses `surfaceContainer` for the card background.

### `CommandScreen`

- Renders command arguments grouped in cards:
  - **Image configs**
  - **Key configs**
  - **Options**
  - **Advanced configs**
- Uses only two row primitives:
  - `PreferenceRow` (ActionRow) for values edited through a dialog or SAF
    picker.
  - `PreferenceSwitchRow` for boolean arguments.
- Contains the Run button in a bottom bar.
- Shows command output as a plain text item below the groups.

## 5. Shared component system

`ui/components/PreferenceComponents.kt` is the shared UI component library.
See [Adding Shared UI Components](shared-components.md) for details.

The most important components are:

| Component | Purpose |
|---|---|
| `SettingsList` | `LazyColumn` used by every screen. |
| `preferenceGroup` | Adds a card group to a `SettingsList`. |
| `PreferenceGroup` | A card with segmented rows. |
| `PreferenceRow` | The standard row. Opens dialogs/pickers or performs an action. |
| `PreferenceSwitchRow` | The standard switch row. |
| `DialogConfirmButton` / `DialogNeutralButton` / `DialogDismissButton` | Material 3 dialog button hierarchy. |

## 6. State management

The app uses ViewModels for business state and plain Compose `remember` for
transient UI state. There is no DI framework; each ViewModel exposes a
`companion object` factory built with `viewModelFactory { initializer { ... } }`.

| ViewModel | Scope | Holds |
|---|---|---|
| `CommandViewModel` | `viewModel(key = command.id)` — one per command | `CommandUiState`: `running`, `result`, `outputFile`. Owns argv construction, SAF fd lifetime, and result parsing. |
| `ConsoleViewModel` | Activity | `AvbtoolTermSession` (bound to `viewModelScope`) and the storage-permission flag. |
| `SettingsViewModel` | Activity | `SettingsUiState`: theme, AMOLED, dynamic color, predictive back, language. In-memory only — not persisted. |

State is exposed as `StateFlow<UiState>` and collected with
`collectAsStateWithLifecycle()`.

### What deliberately stays in composables

- **Navigation state** — `commandId`, `commandBackProgress`, and the
  `PredictiveBackHandler` logic stay in `AVBToolAndroidApp` / `RootScreen`.
  Navigation is intentionally small (see section 3).
- **`CommandScreen` form values** — `values` remains a `remember(command.id)`
  map. It drives argument editing (file-picker line appends, chain editor),
  and moving it would pull that UI logic into the ViewModel.
- **Dialog and editing flags** — `copyWarning`, `editingArg`,
  `choosingAlgorithm`, `managingFileArg`, `managingChainArg`, `chainEditor`,
  `advancedExpanded`, `rawExpanded`, and the three `SettingsScreen` dialog
  flags.
- **`ConsoleScreen` platform-bound objects** — `terminalView` (a ViewModel must
  never hold a `View`), the `rememberLauncherForActivityResult` launchers
  (Activity Result API is bound to the Activity), and the IME show/hide
  helpers.
- **Language side effect** — `applyAppLanguage()` calls
  `Activity.recreate()` below API 33, so it runs from a
  `LaunchedEffect(settings.languageMode)` in `AVBToolAndroidApp`, not from the
  ViewModel. The ViewModel stores only the selected `LanguageMode`.

### Consequences of ViewModel lifetimes

- A command's result survives leaving and re-entering that command's screen.
- The console session survives configuration changes and tab switches;
  `session.finish()` runs from `ConsoleViewModel.onCleared()`.

## 7. Data layer for UI

`AvbModels.kt` contains `AvbCommand` and `AvbArg`. All user-visible labels,
titles, and descriptions are stored as `@StringRes` resource IDs and resolved
in composables with `stringResource(...)`. Do not put user-visible strings in
these model classes.

`strings.xml` is generated/maintained with keys such as:

- `command_<id>_title`
- `command_<id>_description`
- `arg_<commandId>_<sanitizedKey>_label`
