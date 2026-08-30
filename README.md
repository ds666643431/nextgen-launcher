# NextGEN Launcher

A **no-root Android game-mod launcher / `.so` injector**, written as a native **Rust + egui** app.
It clones a target game with the vendored **NextGen** app-virtualization engine and injects a
per-game mod-menu `.so` into the cloned **guest** process — **without modifying or re-signing the
target APK**, so the game's Play signature and integrity stay valid.

This README covers **the launcher only**: the Rust/egui UI (`libloader.so`), its Android host, and
how to build, run and customize it.

- [About this project](#about-this-project)
- [How to run](#how-to-run)
- [Where to customize](#where-to-customize)
- [Further reading](#further-reading)

---

## About this project

**The whole thing runs without root.** Instead of patching the target game, NextGen *virtualizes* it:
the game is cloned into a sandbox process, and the mod `.so` is injected into that guest at process
start. The target APK is never touched, so its signature and integrity checks stay valid.

Everything you see on screen is **Rust + egui inside `libloader.so`** — the only authored Java in this
repo is a minimal `GameActivity` host (`app/src/main/java/menu/nextgen/loader/MainActivity.java`).
The UI is an **onboarding permission gate** (All-Files-Access) followed by two tabs — **Apps**
(the catalog of supported games, split into *Installed* / *Supported, not installed*) and **Settings**
(permissions, sign-in, theme, language) — plus an **App details** sub-screen carrying the
*Install Mod* / *Launch* / *Uninstall Mod* actions.

- **App id / package:** `menu.nextgen.loader`  ·  **App name:** `NextGEN`  ·  **Native lib:** `loader` → `libloader.so`
- **Target:** `arm64-v8a`, minSdk 28 / compileSdk 35, NDK `27.0.12077973` (clang 18).
- **Renderer:** eframe's **Glow** (GLES/EGL) backend — chosen over wgpu because wgpu intermittently
  fails to rebuild its Android surface after a resume (see the comment in `native/loader/src/lib.rs`).

**What actually works today:** the native UI and the build chain are real and run on-device, but the
app is driven entirely by **fake in-binary data** (`LocalFakeDataSource`; the `remote` backend is a
compiled-out stub). The clone/launch actions go through a JNI bridge to `VBridge`, a class that lives
in the **engine app** — so they only succeed when `libloader.so` is hosted there, not in this repo's
standalone host (see [Building the engine host app](#building-the-engine-host-app)).

---

## How to run

### Prerequisites

| Requirement | Version / value | Notes |
|---|---|---|
| **Rust** | stable toolchain | Install via [rustup](https://rustup.rs). |
| **Android target** | `aarch64-linux-android` | `rustup target add aarch64-linux-android` (add `x86_64-linux-android` for `-X86`). |
| **cargo-ndk** | latest | `cargo install cargo-ndk` — cross-compiles the Rust `.so` for Android. |
| **Android SDK** | compileSdk **35** | Auto-detected: `ANDROID_HOME`/`ANDROID_SDK_ROOT` → `local.properties` (`sdk.dir`) → `%LOCALAPPDATA%\Android\Sdk`. |
| **Android NDK** | **exactly `27.0.12077973`** | clang 18. Pinned in `app/build.gradle`. Install under the SDK with `sdkmanager "ndk;27.0.12077973"`. |
| **JDK** | **17** | The loader module compiles at Java 17. *(The vendored engine wants JDK 21.)* |
| **Gradle** | wrapper (repo root = **8.10**) | Always use the repo-root `./gradlew`. AGP 8.7.3. **The engine has a *different* wrapper (8.14.5)** — don't cross them. |
| **Device** | arm64, USB-debugging | `adb` must see it. `abiFilters` is `arm64-v8a` only; x86_64 (MuMu) is test-only. |

Two things are environment-specific: the **device serial** in `scripts/build.ps1` (`$Device`) — replace
it with your own `adb devices` serial, or drop the `-s $Device` arguments if only one device is
attached — and the **JDK** used by `build-debug.bat` / `build-release.bat`, which point `JAVA_HOME` at
Android Studio's bundled JBR (a JDK 21).

`local.properties` is gitignored, so create it if missing (`sdk.dir=<sdk path>`, with forward slashes
or Java-properties-escaped backslashes). The engine project needs its own
`vendor/NextGEN/local.properties`.

### One-time setup

```bash
rustup target add aarch64-linux-android      # + x86_64-linux-android if you use -X86
cargo install cargo-ndk
# ensure local.properties has: sdk.dir=<path to your Android SDK>
```

### Build & run (the script)

From the repo root (PowerShell):

```powershell
scripts\build.ps1              # arm64 only: build loader .so → jniLibs, assembleDebug, install + launch
scripts\build.ps1 -X86         # also build the x86_64 (MuMu) ABI
scripts\build.ps1 -Release     # release Rust build
scripts\build.ps1 -NoLaunch    # build + install, don't `am start`
```

The script (a) builds `libloader.so` with cargo-ndk into `app/src/main/jniLibs/`, (b) runs
`gradlew assembleDebug`, (c) `adb install -r` and launches `menu.nextgen.loader`. It intentionally
uses `$ErrorActionPreference = "Continue"` and gates on `$LASTEXITCODE` (cargo-ndk/gradle write
progress to stderr, which PowerShell 5.1 otherwise treats as a fatal error).

This standalone host is the fast loop for **UI work**. Clone/launch actions need `VBridge`, which
only exists in the engine app — see [Building the engine host app](#building-the-engine-host-app).

> `-X86` builds the x86_64 `.so`, but `app/build.gradle` sets `abiFilters 'arm64-v8a'`, so the APK
> still ships arm64 only until you widen that filter too.

### Raw commands (no script)

```powershell
cd native\loader
cargo ndk -t arm64-v8a -p 28 -o ..\..\app\src\main\jniLibs build   # add -t x86_64 for MuMu
cd ..\..
.\gradlew.bat assembleDebug
adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <serial> shell am start -n menu.nextgen.loader/.MainActivity
```

`build.rs` forces 16 KB ELF page alignment (`-Wl,-z,max-page-size=16384`) for Android 15+ — keep it.

A host-side `cargo check` (no Android target) is the fastest way to type-check UI changes; the
desktop `StubEngine` exists precisely so the crate still builds off-device.

Logs:

```bash
adb -s <serial> logcat | grep -iE "nextgen|loader"
```

### Build variants

The loader crate (`native/loader/Cargo.toml`) has three Cargo features:

| Feature | Default? | Effect |
|---|---|---|
| `fake` | ✅ (default) | Runs on `LocalFakeDataSource` — no network, no external data. |
| `remote` | off | Compiles the `RemoteDataSource` **and** flips the default backend to Remote. ⚠️ A **stub** — every method returns `Offline`. |
| `real_engine` | off | Legacy gate for the JNI engine bridge. **Inert today** — the Android build always uses `RealEngine`. |

```bash
cargo ndk -t arm64-v8a -p 28 -o ../../app/src/main/jniLibs build             # default
cargo ndk -t arm64-v8a -p 28 -o ../../app/src/main/jniLibs build --release   # release
```

`build.ps1` exposes `-X86` / `-Release` / `-NoLaunch`; the Cargo features are command-line only.

### Building the engine host app

The vendored virtualization engine (`menu.nextgen.launcher`, project root `vendor/NextGEN/`) is a
**separate** Gradle project with its own wrapper (8.14.5) and JDK 21 requirement. It is also the host
that actually runs the launcher UI in production: its manifest carries the same
`android.app.lib_name=loader` meta-data, and it ships `libloader.so` from
`vendor/NextGEN/app/src/main/jniLibs/arm64-v8a/`.

So, to run the launcher against the real engine:

```powershell
# 1. build the loader .so (see above), then copy it into the engine's jniLibs
copy app\src\main\jniLibs\arm64-v8a\libloader.so vendor\NextGEN\app\src\main\jniLibs\arm64-v8a\

# 2. build + collect the engine APK into out\  (double-clickable)
build-debug.bat            # or: build-release.bat  (release/R8)
```

Both `.bat` files set `JAVA_HOME` to Android Studio's bundled JBR (JDK 21), run
`vendor\NextGEN\gradlew :app:assembleDebug` / `:app:assembleRelease`, and copy the APKs into `out\`.
Release APKs are **debug-signed** — fine for local testing, not for distribution. See
[`BUILD.md`](BUILD.md) for the signing detail.

> **`vendor/NextGEN` is not stock upstream.** It carries this project's own edits: `VBridge.kt`
> (the class the Rust `RealEngine` calls), `Bcore`'s `NgInjector` / `OverlayHost`, the
> `NgInjector.inject(...)` call spliced into `BActivityThread`, and the `.so` files checked into
> its `app/src/main/jniLibs/`. Re-cloning it from upstream would break the launcher, so treat it
> as vendored-and-patched and keep any change there deliberate.

---

## Where to customize

Everything customizable today lives in **`native/loader/`**.

### The list of supported games

Edit `LocalFakeDataSource::catalog_seed()` in **`native/loader/src/data/local.rs`**. It returns a
`Vec<SupportedApp>`; each entry is `(package, title, Tier::{Plus|Vip}, icon_url, description)`, where
`icon_url` is a Play-Store image URL fetched and cached at runtime. The model is `SupportedApp` in
`data/models.rs`.

Screens never name a concrete backend — they go through the `DataSource` trait (`data/source.rs`),
picked by `build_data_source()` in `data/mod.rs` — so swapping to a real backend is a one-line change
once one exists.

*Not yet:* the `remote` backend is a stub with no networking wired; only `catalog()` is consumed by the
UI; `SupportedApp::tier` is carried in the model but rendered only by plugin slots, and no plugin is
registered.

### Multi-language

`native/loader/src/i18n.rs` holds two `const` tables of `(key, value)` pairs — `EN` and `VI` — resolved
by `tr(lang, key)` (falls back to English, then to the key itself). UI code always references **stable
keys** (e.g. `"nav.apps"`), never English literals. The user switches language in
**Settings → 04 LANGUAGE**.

- **Change existing text** → edit the value for that key in **both** the `EN` and `VI` tables.
- **Add a language** (e.g. Spanish):
  1. Add a variant to `enum Lang` in `native/loader/src/app.rs` — e.g. `Es`.
  2. In `i18n.rs`, add `const ES: &[(&str, &str)] = &[ … ]` (copy `EN`, translate the values) and a `Lang::Es => ES` arm in `tr()`.
  3. Add a radio row in `native/loader/src/ui/settings.rs` (section "04 LANGUAGE") that emits `Action::SetLang(Lang::Es)`.
  4. If the language needs glyphs the bundled font lacks, bundle a font that covers them (below).

*Not yet:* only EN + VI ship; the choice is **not persisted** (resets to English on cold start); the
bundled **Be Vietnam Pro** fonts cover Latin + Vietnamese only, and there is no RTL handling.

### Theme & styling

All colors live in one `Palette` struct in **`native/loader/src/theme.rs`**, with two `const fn`
presets — `Palette::light()` and `Palette::dark()`. The live palette is a thread-local read via `pal()`,
so a theme switch is a single `theme::apply()` call. The user picks it in **Settings → 03 THEME**.

- **Colors** → edit the fields in `Palette::light()` / `Palette::dark()` (`Color32::from_rgb(r, g, b)`).
  The accent color drives buttons, selections, and eyebrows.
- **Fonts** → drop new `.ttf` files into `native/loader/assets/fonts/`, then update the `include_bytes!`
  paths and the family insertion in `install_fonts()` (`theme.rs`). Keep a semibold face mapped to the
  `bvp-bold` family — headings use it explicitly and egui does not synthesize weight.
- **Spacing** → global item spacing / button padding is set once in `theme::install()`
  (`egui::Style::spacing`); per-widget margins are set at each call site.

*Not yet:* no central token for corner radius or per-widget text size — both are hardcoded at each
widget's call site (search `CornerRadius::same(` / `.size(` in `ui/`); the theme choice is not
persisted; only the two schemes exist, with no custom-palette picker.

### Branding: logo & text

**In-app logo.** `native/loader/assets/nextgen_logo.rgba` is a **raw, header-less RGBA8888** blob —
**360 × 78 × 4 = 112,320 bytes** (not a PNG) — embedded via `include_bytes!` in `app.rs` and drawn by
the `ui::logo` helper. The dark-theme variant is generated at runtime (`dark_logo_image()`), so only one
blob ships. To replace it, overwrite the file with a new raw blob; if the pixel dimensions change,
update `LOGO_W` / `LOGO_H` in `native/loader/src/app.rs`. **No converter script ships** — convert a PNG
yourself:

```bash
# ImageMagick
convert logo.png -depth 8 RGBA:nextgen_logo.rgba
# or Python / Pillow
python -c "from PIL import Image; open('nextgen_logo.rgba','wb').write(Image.open('logo.png').convert('RGBA').tobytes())"
```

**In-app brand text** (scattered — there is no single `BRAND` constant):

| Text | Where |
|---|---|
| eframe app/window title `"NextGEN"` | `native/loader/src/lib.rs` (the `eframe::run_native` title) |
| Tagline `"A NEXT GENERATION OF GAME MODDING"` | i18n key `onb.tagline` — edit in **both** EN + VI (`i18n.rs`) |
| Onboarding body (contains `"NextGEN"`) | i18n key `onb.body` — edit in both EN + VI (`i18n.rs`) |
| Base URL constant | `native/loader/src/data/mod.rs` (`AppConfig::default().base_url`) |

**Android app name.** Hardcoded as `android:label="NextGEN"` in `app/src/main/AndroidManifest.xml`.
There is **no `res/values/strings.xml`** — either edit the literal, or (cleaner) add one with `app_name`
and change the manifest to `android:label="@string/app_name"`.

**Android launcher icon.** The standard adaptive-icon set under `app/src/main/res/`:
`mipmap-*dpi/ic_launcher*.webp` + `ic_launcher_foreground.webp`, the adaptive XML in
`mipmap-anydpi-v26/`, and the values under `res/values/`. Regenerate with Android Studio's
*Image Asset* wizard, or hand-swap the WebP files.

**Package / application id.** `menu.nextgen.loader`. Renaming touches `app/build.gradle` (`namespace`
**and** `applicationId`) and `app/src/main/java/menu/nextgen/loader/MainActivity.java` (the directory
path **and** the `package` line). The manifest uses `.MainActivity`, so it follows the namespace
automatically, and `platform.rs` reads the running package via `getPackageName()` at runtime.

The **native lib name** (`loader` → `libloader.so`) is independent of branding and should generally be
left alone; changing it means editing `Cargo.toml` (`[lib] name`), the manifest `android.app.lib_name`
meta-data, and — because the engine app hosts the same library — `vendor/NextGEN`'s manifest too.

---

## Further reading

| Doc | Scope |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Day-to-day working conventions, gotchas, and the repo's ground rules |
| [`BUILD.md`](BUILD.md) | Building + signing the vendored NextGen engine app |
| [`native/loader/hardening/README.md`](native/loader/hardening/README.md) | The C/C++ shim + obfuscation headers compiled by `build.rs` |
