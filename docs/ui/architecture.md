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

## 2. Theme and styling

`AVBToolAndroidTheme` (`ui/theme/Theme.kt`):

- Uses **dynamic color** on Android 12+ (`dynamicLightColorScheme` /
  `dynamicDarkColorScheme`).
- Falls back to Material 3 `lightColorScheme()` / `darkColorScheme()`.
- Platform window/splash colors are defined in:
  - `app/src/main/res/values/colors.xml` and `values/themes.xml`
  - `app/src/main/res/values-night/colors.xml` and `values-night/themes.xml`

Do **not** hardcode product colors in composables. Use
`MaterialTheme.colorScheme.*` tokens.

## 3. Navigation model

All navigation is kept in `MainActivity.kt`.

### Root destinations

`AppDestinations` contains the two tab destinations:

- `HOME` (`Icons.Filled.Home`)
- `CONSOLE` (`Icons.Filled.Terminal`)

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

## 6. Data layer for UI

`AvbModels.kt` contains `AvbCommand` and `AvbArg`. All user-visible labels,
titles, and descriptions are stored as `@StringRes` resource IDs and resolved
in composables with `stringResource(...)`. Do not put user-visible strings in
these model classes.

`strings.xml` is generated/maintained with keys such as:

- `command_<id>_title`
- `command_<id>_description`
- `arg_<commandId>_<sanitizedKey>_label`
