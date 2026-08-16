# AVBTool Android (Compose)

An Android Compose app that embeds **Python 3.13** via [Chaquopy](https://chaquo.com/chaquopy/) and runs the AOSP `avbtool.py` directly on-device.

## Features

- **GUI mode** — flat, frequency-ordered command list with a form-based command screen:
  - image configs
  - key configs (signing algorithm dropdown + RSA PEM key picker)
  - collapsible advanced configs
  - switches for boolean flags
- **Console mode** — a real terminal emulator view (vendored TermOnePlus `emulatorview`) backed by an in-process Python runner.
- **SAF fd bridge** — selected `content://` files are passed into Python as `/saf/fd/<fd>` paths, avoiding copies for large images. Commands that derive sibling paths (`verify_image`, `print_partition_digests`, `calculate_vbmeta_digest`, `calculate_kernel_cmdline`) use a private copy instead so chain partitions resolve correctly.
- **Native FEC** — `libavbfec.so` implements AOSP RS(255, N) FEC encoding natively, verified byte-exact against AOSP host `fec`.
- **Predictive back** — predictive back gesture support for command screen and console-to-home navigation.
- **Signed builds** — debug and release APKs are signed with a local test key.

## Requirements

- Android SDK 37
- Android NDK `29.0.14206865`
- JDK 17 or newer
- Python 3.13 on the build machine
- An `arm64-v8a` device/emulator

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

## Signing

The project uses a checked-in **test key**:

```text
keystore/testkey.jks
alias: avbtool
store/key password: avbtool123
```

Do not use this key for production. For a real release, add your own signing config.

## Python integration

- Python source lives in `app/src/main/python/`.
- `avbtool.py` is a patched copy of AOSP `avbtool.py`:
  - OpenSSL calls replaced with the `cryptography` package.
  - `fec` subprocess calls replaced with native `libavbfec.so`.
  - `ImageHandler` closes its internal file descriptors.
  - `argparse.FileType` file objects are closed after each command.
- `android_bridge.py`:
  - loads `libavbfec.so`
  - installs the `/saf/fd/<fd>` `builtins.open` hook
  - runs avbtool and captures stdout/stderr

## Native FEC

Source: `app/src/main/cpp/avb_fec.cpp` + AOSP `external/fec` RS encoder files under `app/src/main/cpp/fec_rs/`.

- Only `arm64-v8a` is built.
- Raw images only; sparse images are rejected for FEC with a `simg2img` message.
- FEC self-test in console:

```text
avbtool_fec_self_test
```

Host reference:

```bash
python -c "open('selftest.img','wb').write((bytes(range(256))*16)*256)"
fec --encode --roots 2 selftest.img ref.fec
sha256sum selftest.img ref.fec
```

## Known limitations

- FEC generation is raw-image only.
- Console is not a real PTY: no shell job control, `Ctrl+C` is cancel/clear.
- Console command parsing does not support quoted paths.
- GUI file-output options are text fields; use SAF export for binary outputs.
- Python runs in-process; long native operations cannot be force-killed.
