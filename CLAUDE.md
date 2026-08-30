# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

---

## What this is

**nextgenlauncher** is a no-root Android **game-mod launcher / `.so` injector**. It clones a target game with the vendored
**NextGen** app-virtualization engine and injects a per-game mod-menu `.so` into the cloned **guest** game process,
then activates it — all without root and **without modifying or re-signing the target APK** (so the game's Play
signature and PairIP integrity stay valid).

The product is **three separate projects** joined by one cryptographic contract:

1. **nextgenlauncher** (this repo) — the native **Rust/egui launcher**: authenticates the user, drives NextGen to
   clone/launch a game, and runs the injection + `Start()` activation handshake.
2. **server** (separate, not built yet) — auth + entitlement + the key/artifact service. The **durable gate**.
3. **libmenu** (separate C++ project, not built yet) — the per-game mod menu, O-MVLL-obfuscated + build-time
   AEAD-encrypted, exporting a `Start(secret,…)` entry that is inert unless the launcher activates it.

**The full architecture + implementation plans live in [`docs/`](docs/README.md).** Read those before starting any
M2+ work — this file is the day-to-day working guide for the code that exists today.

### Core rules (apply everywhere)
- **NextGen (`vendor/ALEX5402_NewNextGen/Bcore`) stays byte-for-byte upstream.** Never fork/edit Bcore Java. The
  guest-injection trigger uses a **stock** `AppLifecycleCallback.beforeCreateApplication` registered from the
  launcher's own (small) Java floor. (The current vendored tree still has old custom edits — see *Status* — that the
  M2 plan reverts.)
- **O-MVLL is C/C++ only.** It cannot obfuscate the Rust launcher (rustc's LLVM ≠ NDK clang's LLVM). Rust gets
  Rust-native hardening; only the C++ libmenu gets O-MVLL.
- **The durable security is the server withholding keys.** Every client-side check (handshake, HWID, obfuscation,
  "inert standalone") is **cost-raising + kill-switch + telemetry**, never a cryptographic barrier on the attacker's
  own device. Design and communicate to that.
- **AEAD everywhere** (`chacha20poly1305` / `aes-gcm`) for real payloads — never the overlay sample's bare
  unauthenticated `chacha20` (that is a debug trace cipher only).
- Target is **`arm64-v8a`, minSdk 28 / compileSdk 35, NDK `27.0.12077973` (clang 18)**. x86_64 (MuMu) is an optional
  test-only ABI.

---

## Status (what exists vs. what's planned)

- **M1 — done.** The native-UI + multi-ABI build/deploy chain is proven end to end:
  - `native/loader` → `libloader.so`: a real **eframe/egui/wgpu** launcher rendered via GameActivity. Currently a
    **stub screen** (target label + a dead "Clone & Launch" button).
  - `native/overlay` → `libmodmenu.so`: a **standalone sample** proving in-guest injection — an `egui_glow` overlay +
    ByteDance ShadowHook/Dobby hook of `libil2cpp.so`, with an encrypted trace logger. **This is a sample, not the
    product** — the real per-game menu is the separate `libmenu` project.
- **M2+ — planned only, in `docs/`.** The launcher is *not yet wired to NextGen*; there is no auth, no data layer, no
  real launch pipeline yet. `engine/` and `native/src/` are empty placeholders.

---

## Repository layout

```
app/                     Android app module (com.nextgenlauncher.loader) — thin GameActivity host of libloader.so
  src/main/java/com/nextgenlauncher/loader/MainActivity.java   the ONLY authored Java today (GameActivity glue)
  src/main/AndroidManifest.xml                           declares MainActivity + android.app.lib_name=loader
  src/main/jniLibs/arm64-v8a/libloader.so                Rust output, dropped in by cargo-ndk (gitignored)
native/
  loader/     Rust cdylib "loader" → libloader.so — the LAUNCHER UI (eframe + egui + wgpu + winit, pinned)
  overlay/    Rust cdylib "modmenu" → libmodmenu.so — the injected SAMPLE (egui_glow + EGL, ShadowHook/Dobby)
    vendor/{dobby,shadowhook}/   prebuilt hook engines (arm64-v8a)  ⚠ libshadowhook_nothing.so is MISSING (see gotchas)
  src/        empty placeholder
engine/       empty placeholder (NextGen is consumed from vendor/, per the M2 plan)
scripts/
  build.ps1        M1 build+install+launch (loader only)
  decrypt_trace.py decrypt the overlay's ChaCha20 trace file
  screenshot.ps1   capture + downscale a device screenshot
docs/         ARCHITECTURE + IMPLEMENTATION PLANS (read these before M2+ work)
vendor/       cloned reference engines/examples — LARGE, gitignored, NOT our source:
  ALEX5402_NewNextGen/   the NextGen virtualization engine (has its own detailed CLAUDE.md)
  rust-android-examples/  agdk-eframe is the known-good egui-on-GameActivity reference for native/loader
keystore/     signing keys (as needed)
```
The project is a **git repo**; `vendor/`, `**/build/`, `**/target/`, `app/src/main/jniLibs/`, `keystore/`, and device
traces (`*.enc`, `*.log`, …) are gitignored — see `.gitignore`. AGP 8.7.3, Gradle 8.10 wrapper, JDK 17.

---

## Build & run

The M1 pipeline is `scripts/build.ps1` (PowerShell). It builds the Rust `.so` with **cargo-ndk**, then the APK with
Gradle, then installs + launches on the device.

```powershell
scripts\build.ps1              # arm64 only; build loader → jniLibs, assembleDebug, install+launch on the Pixel 8
scripts\build.ps1 -X86         # also build x86_64 (MuMu)
scripts\build.ps1 -Release     # release Rust build
scripts\build.ps1 -NoLaunch    # build + install, don't `am start`
```
It gates on `$LASTEXITCODE` (not `-ErrorAction Stop`) because cargo-ndk/gradle write progress to stderr. Key vars in
the script: SDK `C:\Users\<user>\AppData\Local\Android\Sdk`, NDK `27.0.12077973`, device serial `39051FDJH00FEP`,
appId `com.nextgenlauncher.loader`.

Under the hood, the loader build is:
```powershell
cd native\loader
cargo ndk -t arm64-v8a -p 28 -o ..\..\app\src\main\jniLibs build      # add -t x86_64 for MuMu
cd ..\.. ; .\gradlew.bat assembleDebug
adb -s 39051FDJH00FEP install -r app\build\outputs\apk\debug\app-debug.apk
```

**The overlay (`libmodmenu.so`) is built separately** — it is not in `build.ps1`. It needs the vendored
`dobby`/`shadowhook` prebuilts and is loaded into the guest at runtime (today via the vendored NextGen's
`NgInjector.java`; per the M2 plan this becomes a native trigger).

Verification is **build + run on device** (there are no meaningful unit tests). Logs:
```bash
adb -s 39051FDJH00FEP logcat | grep -iE "nextgenlauncher|MODMENU|overlay|ShadowHook"
```

Decrypt the overlay's encrypted trace:
```bash
adb -s <serial> shell run-as menu.nextgen.launcher cat files/nextgenlauncher_trace.enc > trace.enc
python scripts/decrypt_trace.py trace.enc          # needs pycryptodome
```

---

## Components in this repo

### `app/` — Android host
`MainActivity` extends `com.google.androidgamesdk.GameActivity`, `System.loadLibrary("loader")`, hides system UI. The
manifest points `android.app.lib_name=loader` and declares the launcher intent-filter. Per the M2 plan this module
gains a 2-class NextGen bootstrap (`NgInjectorApp` + `ClientConfiguration`) and the `beforeCreateApplication` trigger,
plus `INTERNET` + `QUERY_ALL_PACKAGES` permissions — see [`docs/launcher-plan.md`](docs/launcher-plan.md).

### `native/loader` — the launcher UI (Rust)
`android_main` (winit android-game-activity glue) → `eframe::run_native` with the **Wgpu** renderer. Deps are pinned
to the agdk-eframe reference (`egui`/`eframe` `=0.33.3`, `winit` `=0.30.13`, `wgpu` `=27.0.1`). `build.rs` forces
`-Wl,-z,max-page-size=16384` (16 KB ELF alignment for Android 15+). This is where the whole native launcher gets built
out in M2+.

### `native/overlay` — the injected sample (Rust)
Runs **inside the guest** (`JNI_OnLoad`). Spawns a hook thread (ShadowHook SHARED-mode inline hook of
`il2cpp_string_new`; a Dobby shim also exists) and renders an egui overlay. **Renders via `egui_glow` + a
hand-managed EGL context**, *not* wgpu — wgpu's GLES backend stalls in `queue.submit` on MuMu's emulated GPU
("Waiting on last submission"); `egui_glow` issues immediate `glDraw` calls with no fence and works on emulated GL.
The JNI surface/touch entries are named `Java_menu_nextgen_launcher_OverlayHost_*` (the vendored NextGen's overlay
host). `MODMENU_HOOK=0` = overlay-only (skips the il2cpp code patch that CodeStage ACTk flags as "Cheater"). Treat this
as a reference for the separate C++ `libmenu` — don't build the product here.

### `vendor/ALEX5402_NewNextGen` — the engine (reference)
A VirtualApp-lineage app-virtualization engine (~532 Java + native). **Consumed as-is; never edited.** It has its own
thorough `CLAUDE.md` — read that for engine internals (the four layers, `NextGenCore` API, the stub-process model,
`AppLifecycleCallback` hooks). Note: the currently-checked-out tree still carries the old nextgenlauncher custom edits
(`NgInjector.java`, `OverlayHost.java`, a `BActivityThread:431` call, `AppManager.kt`/`ShortcutActivity.kt` edits) — the
M2 plan **reverts** these to pure upstream and moves the logic native.

---

## Conventions & gotchas

1. **Never edit `vendor/Bcore` Java.** Injection triggers go through the stock `AppLifecycleCallback` public API.
2. **NDK is pinned to `27.0.12077973`** (clang 18) and ABI is arm64-v8a. 16 KB page alignment is forced in `build.rs`
   — keep it (Android 15/16 KB pages).
3. **Loader = wgpu, overlay = egui_glow.** Don't "unify" them: the overlay deliberately avoids wgpu because of the
   MuMu emulated-GPU `queue.submit` stall. The launcher (real device / Pixel 8) is fine on wgpu.
4. **`libshadowhook_nothing.so` is MISSING** from `native/overlay/vendor/shadowhook/arm64-v8a/` (only
   `libshadowhook.so` is present). `SHADOWHOOK_MODE_SHARED`'s `shadowhook_init` returns non-zero and installs **no
   hooks** without it — add the matching prebuilt before relying on ShadowHook. The C++ libmenu needs it too.
5. **PowerShell build script**: keep `$ErrorActionPreference = "Continue"` and gate on `$LASTEXITCODE`; under "Stop",
   PS 5.1 wraps native stderr as a terminating error even on exit 0.
6. **The encrypted trace logger uses a hardcoded ChaCha20 key/nonce** and is a *debug* facility (see
   `scripts/decrypt_trace.py`). It is **not** an example of production crypto — real payloads use AEAD (see the plans).
7. **For M2 networking/JNI** (per the plans, not yet in code): add `jni = "0.21"` as a direct dep (android-activity
   pulls the rewritten 0.22.4 transitively; 0.21 matches the overlay idioms and coexists); `rustls` on the **ring**
   provider + `webpki-roots`; add `INTERNET` + `QUERY_ALL_PACKAGES` to the app manifest.

---

## Where the plans live

| Doc | Scope |
|---|---|
| [`docs/README.md`](docs/README.md) | Index + system overview + ground rules |
| [`docs/architecture.md`](docs/architecture.md) | Cross-cutting crypto protocol: key hierarchy, the 120-byte `VinjTicket` + `Start` handshake ABI, end-to-end flow, anti-replay, revocation, honest threat model |
| [`docs/launcher-plan.md`](docs/launcher-plan.md) | This repo's M2+ build: data adapter, screens, auth, launch/injection pipeline, Java floor, milestones |
| [`docs/launcher-updater-plan.md`](docs/launcher-updater-plan.md) | Launcher self-update (U0–U5): Settings version display, signed update manifest, APK download + verify + `PackageInstaller` install, release signing + force-update. Ships the engine host `menu.nextgen.launcher` |
| [`docs/xapk-apks-install-plan.md`](docs/xapk-apks-install-plan.md) | Split-APK installer (M-X1…X4): pick `.xapk`/`.apks`/`.apkm`, select device splits, install **to the device** via `PackageInstaller` (reuses the updater's installer, generalized to N splits). Device install only — no sandbox clone; engine uninvolved |
| [`docs/server-plan.md`](docs/server-plan.md) | The Rust/axum + Postgres license/key server (the durable gate) |
| [`docs/libmenu-plan.md`](docs/libmenu-plan.md) | The external C++ libmenu (O-MVLL + build-time encryption + `Start` + release pipeline) |

The plans were multi-agent designed and adversarially verified; their verified corrections are authoritative — prefer
them over re-deriving. Build order starts at launcher **M2a** (the Bcore gradle + Java-floor spike).
