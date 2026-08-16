# Adding Shared UI Components

Shared UI components live in:

`app/src/main/java/me/wasddestroy/avbtoolandroid/ui/components/`

Currently the file `PreferenceComponents.kt` contains the shared
preference-row system used by all screens.

## 1. When to create a shared component

Create a shared component when the same row, card, dialog button, or layout is
used by more than one screen, or when a screen is built from repeated
preference rows.

Keep screen-specific composables in their screen file. For example:

- `CommandArgRow` is screen-specific and lives in `CommandScreen.kt`.
- `PreferenceRow` is shared and lives in `ui/components/PreferenceComponents.kt`.

## 2. Existing shared component APIs

### `SettingsList`

```kotlin
@Composable
fun SettingsList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    content: LazyListScope.() -> Unit,
)
```

The standard list container for every screen.

### `preferenceGroup`

```kotlin
fun LazyListScope.preferenceGroup(
    key: Any? = null,
    titleRes: Int? = null,
    content: PreferenceGroupScope.() -> Unit,
)
```

Adds a card group to a `SettingsList`. `titleRes` is a string resource.

### `PreferenceGroupScope.row`

```kotlin
fun row(key: Any? = null, content: @Composable () -> Unit)
```

Adds one row inside the group. `key` is optional but recommended for stable
LazyColumn item identity.

### `PreferenceRow`

```kotlin
@Composable
fun PreferenceRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    iconContent: (@Composable () -> Unit)? = null,
    iconTint: Color? = null,
    summary: String? = null,
    summaryContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
)
```

Use `iconContent` with `Icon(imageVector = ...)` for Material icons. The
`icon` parameter is kept for drawable compatibility but new code should use
`iconContent`.

### `PreferenceSwitchRow`

```kotlin
@Composable
fun PreferenceSwitchRow(
    checked: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    iconContent: (@Composable () -> Unit)? = null,
    summary: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
)
```

### Dialog buttons

| Component | Material role |
|---|---|
| `DialogConfirmButton` | `FilledTonalButton` |
| `DialogNeutralButton` | `OutlinedButton` |
| `DialogDismissButton` | `TextButton` |

## 3. How to add a new shared row component

This example adds a checkbox row.

### Step 1: Create the component

In `ui/components/PreferenceComponents.kt`, add:

```kotlin
@Composable
fun PreferenceCheckboxRow(
    checked: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    iconContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    PreferenceRow(
        title = title,
        modifier = modifier,
        iconContent = iconContent,
        enabled = enabled,
        trailing = {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}
```

### Step 2: Use it from a screen

```kotlin
preferenceGroup(titleRes = R.string.section) {
    row("checkbox") {
        PreferenceCheckboxRow(
            checked = checked,
            title = stringResource(R.string.checkbox_title),
            iconContent = {
                Icon(Icons.Filled.CheckBox, contentDescription = null)
            },
            onCheckedChange = { checked = it },
        )
    }
}
```

## 4. Visual rules for shared rows

These values are already encoded in `PreferenceComponents.kt`. New components
must follow them so every row looks the same.

| Token | Value |
|---|---|
| Outer card corner radius | `16.dp` |
| Inner segmented corner radius | `4.dp` |
| Segmented gap between rows | `2.dp` |
| Row minimum height | `56.dp` |
| Row horizontal padding | `16.dp` |
| Row vertical padding | `8.dp` |
| Leading icon size | `24.dp` |
| Row container color | `MaterialTheme.colorScheme.surfaceContainer` |
| Row text color | `MaterialTheme.colorScheme.onSurface` / `onSurfaceVariant` |
| Disabled text color | `onSurface.copy(alpha = 0.38f)` |
| Divider color | `MaterialTheme.colorScheme.outlineVariant` (if ever needed) |

## 5. Segmented corner behavior

Rows inside `PreferenceGroup` automatically get the correct corner treatment:

- Single row: all corners `16.dp`.
- First row: top corners `16.dp`, bottom corners `4.dp`.
- Middle rows: all corners `4.dp`.
- Last row: top corners `4.dp`, bottom corners `16.dp`.

This behavior is implemented in `preferenceRowShape()` using
`LocalPreferenceRowPosition`. A new row component should build on
`PreferenceRow` or `PreferenceSwitchRow`; it then inherits the shape logic
automatically.

## 6. Accessibility and styling checklist

- Use `contentDescription = null` for decorative icons.
- Use `stringResource(...)` for all visible text.
- Use `Modifier.size(24.dp)` for leading icons.
- Keep touch targets at least `56.dp` high.
- Follow Material 3 button roles for dialogs.
- Do not use raw color literals in composables; use theme color-scheme
  properties.
