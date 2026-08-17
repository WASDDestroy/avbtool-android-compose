# AGENTS.md

Instructions for AI agents and contributors working on this repository.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Before building:

- `org.gradle.configuration-cache` must stay `false` in `gradle.properties` (Chaquopy compatibility).
- `compileSdk = 37`, `targetSdk = 36`, `minSdk = 27`.
- Only `arm64-v8a` ABI is configured.
- Python build version is `3.13`; the build machine must have Python 3.13 installed.
- The checked-in `keystore/testkey.jks` signs both debug and release. Do not use for production.

## Key files

- `app/src/main/python/avbtool.py` — patched AOSP avbtool. Keep patches:
  - `cryptography` replaces OpenSSL for key load/sign/verify.
  - `libavbfec.so` replaces `fec` subprocess.
  - `ImageHandler.close()`/`__del__` close files.
  - `AvbTool.run` closes `argparse.FileType` objects.
- `app/src/main/python/android_bridge.py` — native FEC binding, SAF fd hook, avbtool runner, FEC self-test.
- `app/src/main/java/me/wasddestroy/avbtoolandroid/` — Compose UI and runtime.
  - `CommandViewModel.kt` / `ConsoleViewModel.kt` / `SettingsViewModel.kt` — business
    state, exposed as `StateFlow`. See "State management" below.
- `app/src/main/cpp/` — native FEC encoder and AOSP `libfec_rs` sources.
  - `libfec_rs.so` is built from `fec_rs/` and is **LGPL-2.1**; keep it a separate shared library.
  - `libavbfec.so` links dynamically to `libfec_rs.so`.
- `app/src/main/java/jackpal/androidterm/emulatorview/` — vendored TermOnePlus emulator view.

## License

- Project code: Apache-2.0 (`LICENSE`), except:
  - `app/src/main/cpp/fec_rs/*`: LGPL-2.1 (`LICENSES/LGPL-2.1.txt`)
  - `app/src/main/python/avbtool.py`: MIT
- `app/src/main/assets/open_source_licenses.txt` and `app/src/main/assets/LGPL-2.1.txt`
  are shipped in the APK. Keep both when changing native code.

## Important gotchas

### SAF fd pseudo-paths

- `/saf/fd/<fd>` is intercepted by `android_bridge.py`'s `builtins.open` hook.
- Only numeric final segments are intercepted. Non-numeric siblings (`/saf/fd/boot`) fall through to normal `open`.
- Commands that derive sibling paths from the selected image (`verify_image`, `print_partition_digests`, `calculate_vbmeta_digest`, `calculate_kernel_cmdline`) must use a private copy, not a SAF fd. This logic is in `runCommand` in `CommandScreen.kt`.

### FEC

- FEC is raw-image only. `avbtool.py` rejects sparse magic `0xed26ff3a`.
- `libavbfec.so` is loaded from `applicationInfo.nativeLibraryDir`; `jniLibs.useLegacyPackaging = true` is required.
- Use `avbtool_fec_self_test` in the console to verify byte-exactness against host `fec`.

### Console terminal

- Vendored TermOnePlus `emulatorview` uses `R` from the app package; if compiling breaks, check `Bitmap4x8FontRenderer.java` imports `me.wasddestroy.avbtoolandroid.R`.
- `CopyableEmulatorView` long-press starts selection mode and feeds a synthetic `ACTION_DOWN` to initialize selection anchors.
- Terminal session is in-process; reader/writer threads are neutralized with dummy streams.

### Editing Python files

- Use the existing file formats.
- Avoid writing `\n` escape sequences through shell heredocs; prefer `chr(92) + "n"` when generating Kotlin/Python source with Python scripts.

### State management

The app is MVVM. Business state lives in ViewModels and is exposed as
`StateFlow<UiState>`, collected with `collectAsStateWithLifecycle()`.

- There is **no DI framework**. Each ViewModel that needs dependencies exposes a
  `companion object` factory built with `viewModelFactory { initializer { ... } }`.
  Do not add Hilt or Koin.
- ViewModels must **never** hold an Activity `Context` or a `View`. Pass
  `context.applicationContext` into the factory.
- `CommandViewModel` is obtained with `viewModel(key = command.id)` so each
  command gets its own instance. Dropping the key would leak one command's
  result into another.
- Settings are **in-memory only** — not persisted. Adding DataStore is a
  deliberate non-goal; do not add it without discussion.
- Keep in composables: navigation state (`commandId`, `commandBackProgress`),
  dialog/editing flags, `CommandScreen`'s `values` form map, `terminalView`, the
  `rememberLauncherForActivityResult` launchers, and `applyAppLanguage()`
  (it calls `Activity.recreate()` below API 33).

Full rationale: `docs/ui/architecture.md` §6 and `docs/mvvm-架构说明.md`.

### Theme

- The preset palette lives in `ui/theme/Color.kt`, seeded from `#0061A4` (blue).
  Generate replacements with Material Theme Builder — do not hand-pick roles.
- `AmoledBlackColorScheme` must stay `DarkColorScheme.copy(...)`. Rebuilding it
  from a bare `darkColorScheme()` silently restores M3's default purple accents.
- `splash_screen_background` in `values/colors.xml` and `values-night/colors.xml`
  must match `LightBackground` / `DarkBackground` in `Color.kt`, or the launch
  flashes a different color before Compose draws.
- **Dynamic color defaults to on**, so on Android 12+ palette edits are invisible
  until "dynamic theme color" is turned off in Settings. Test both states.
- Never hardcode colors in composables; use `MaterialTheme.colorScheme.*`.

### UI

- GUI command list is ordered by practical use frequency in `AvbModels.kt`.
- Command screen sections: Image configs, Key configs, Advanced configs.
- Within sections: text/file fields first, dropdowns second, switches last.
- Boolean flags use `Switch`, signing algorithm uses a dropdown.

## Workflow

- Commit every change to the repository before moving on to the next task.
- Use [Conventional Commits](https://www.conventionalcommits.org/) format: `type(scope): description` (e.g. `feat(gui): add extract_public_key command`).
- This machine supports GPG-signed Git commits. If GPG signing times out on a commit, retry once. If the retry also fails, skip GPG signing for that commit only (`git commit --no-gpg-sign`).

## Before committing

- Run `./gradlew :app:assembleDebug` **and** `./gradlew :app:assembleRelease` (both must pass; `lintVitalRelease` catches issues invisible to debug builds, and release-mode Compose brings significant performance improvements).
- Ensure `git status` does not include build outputs (`app/build/`, `app/release/`, `.kotlin/` are ignored).
