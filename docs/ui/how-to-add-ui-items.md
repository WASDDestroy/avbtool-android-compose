# How to Add UI Items to Screens

This guide shows how to add new rows and content to the existing screens.

## 1. Add a new command row to `HomeScreen`

### Step 1: Add strings

In `app/src/main/res/values/strings.xml`, add:

```xml
<string name="command_my_command_title">My command</string>
<string name="command_my_command_description">Description shown as the row summary.</string>
```

### Step 2: Add the model entry

In `AvbModels.kt`, add an `AvbCommand` entry to `AvbCommands.all`:

```kotlin
AvbCommand(
    id = "my_command",
    titleRes = R.string.command_my_command_title,
    descriptionRes = R.string.command_my_command_description,
    args = listOf(
        AvbArg("--image", R.string.arg_my_command_image_label, ArgType.IMAGE, required = true),
        AvbArg("--flag", R.string.arg_my_command_flag_label, ArgType.BOOL),
    ),
    readOnly = true,
)
```

Add the argument label string too:

```xml
<string name="arg_my_command_image_label">Image file</string>
<string name="arg_my_command_flag_label">Enable flag</string>
```

### Step 3: Pick an icon

Open `HomeScreen.kt` and extend `commandIcon()`:

```kotlin
"my_command" -> Icons.Filled.Info
```

Use a Material icon that matches the command semantics. The app uses
`material-icons-core` and `material-icons-extended`, so most
`Icons.Filled.*` / `Icons.AutoMirrored.Filled.*` icons are available.

The row is then picked up automatically by the existing `preferenceGroup`
loop in `HomeScreen`.

## 2. Add a new row to an existing `SettingsList`

Use `preferenceGroup` and `row` inside any `SettingsList`:

```kotlin
SettingsList {
    preferenceGroup(titleRes = R.string.section_title) {
        row("unique-row-key") {
            PreferenceRow(
                title = stringResource(R.string.row_title),
                iconContent = {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                summary = stringResource(R.string.row_summary),
                onClick = { /* open dialog, picker, or perform action */ },
            )
        }
    }
}
```

For a switch row:

```kotlin
row("switch-row-key") {
    PreferenceSwitchRow(
        checked = checkedState,
        title = stringResource(R.string.switch_title),
        iconContent = {
            Icon(
                imageVector = Icons.Filled.CheckBox,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        onCheckedChange = { newValue -> checkedState = newValue },
    )
}
```

## 3. Add a new argument row type in `CommandScreen`

Argument rows are rendered by `CommandArgRow` in `CommandScreen.kt`.

### Text / integer / file / image / algorithm rows

These are already supported. To change their icon, extend `argIcon()`:

```kotlin
arg.key == "--my_key" -> Icons.Filled.Tag
```

The rules are:

- `ArgType.IMAGE` → uses the special `__image__` storage key and a SAF picker.
- `ArgType.FILE` → uses a SAF picker. Repeatable files open `CommandFileListDialog`.
- `ArgType.TEXT` / `ArgType.INT` → opens `CommandTextEditDialog`.
- `ArgType.ALGORITHM` → opens `AlgorithmChoiceDialog`.
- `ArgType.BOOL` → rendered as `PreferenceSwitchRow`.

### Add an argument to a command

Add an `AvbArg` to the command's `args` list and add the label string:

```xml
<string name="arg_my_command_my_arg_label">My argument</string>
```

The screen will render it automatically in the correct section.

### Add a new section

In `CommandScreen`, inside `SettingsList`, add another group:

```kotlin
preferenceGroup(titleRes = R.string.command_section_my_section) {
    myArgs.forEach { arg ->
        row(arg.key) {
            CommandArgRow(
                arg = arg,
                value = values[storageKey(arg)].orEmpty(),
                values = values,
                onPickFile = { key, index -> ... },
                onManageFile = { managingFileArg = arg },
                onEditText = { editingArg = arg },
                onChooseAlgorithm = { choosingAlgorithm = true },
                onToggleBoolean = { checked -> values = values + (arg.key to checked.toString()) },
            )
        }
    }
}
```

## 4. Add a new root tab

Root tabs live in `AppDestinations` in `MainActivity.kt`. Note that the
console is not a tab — it is a full-screen overlay entered from
`SettingsScreen` (see `docs/ui/architecture.md` §3).

1. Add an enum entry with a `@StringRes` label and an `ImageVector` icon.
2. Add the screen to the `HorizontalPager` `when` branch.
3. Update the pager page count if needed. Currently it is
   `AppDestinations.entries.size`, so adding an enum entry is enough.

## 5. Add a new dialog / bottom sheet

Dialog buttons follow Material 3 roles:

| Button role | Component |
|---|---|
| Confirm | `DialogConfirmButton` |
| Neutral | `DialogNeutralButton` |
| Dismiss | `DialogDismissButton` |

Example:

```kotlin
AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.dialog_title)) },
    text = { Text(stringResource(R.string.dialog_message)) },
    confirmButton = {
        DialogConfirmButton(onClick = onConfirm) {
            Text(stringResource(android.R.string.ok))
        }
    },
    dismissButton = {
        DialogDismissButton(onClick = onDismiss) {
            Text(stringResource(android.R.string.cancel))
        }
    },
)
```

For a bottom sheet, follow the same `SettingsList` + `preferenceGroup`
pattern and use `surfaceContainer` as the sheet/list container.

## 6. Checklist

- [ ] All user-visible text is in `strings.xml`.
- [ ] Every row has a 24dp Material icon.
- [ ] Row keys are unique inside their `preferenceGroup`.
- [ ] Dialog buttons use the shared button components.
- [ ] New command arguments have the correct `ArgType`.
- [ ] Dark mode and light mode use color-scheme tokens, not raw colors.
