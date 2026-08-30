# Building a release APK

How to produce a release build of **NextGen** — the virtualization engine app
(`menu.nextgen.launcher`, version `4.0.0` / code `400`) that lives in
`vendor/ALEX5402_NewNextGen/`.

> ### This repo contains two separate Gradle projects
>
> | | Root of the project | Builds | Doc |
> |---|---|---|---|
> | **NextGen** (this document) | `vendor/ALEX5402_NewNextGen/` | `menu.nextgen.launcher` | this file |
> | nextgenlauncher loader | `com.nextgenlauncher.loader` | [`CLAUDE.md`](CLAUDE.md), [`docs/`](docs/README.md), [`scripts/build.ps1`](scripts/build.ps1) |
>
> They have their own wrappers, their own `settings.gradle`, and their own `app/` module.
> **Running `./gradlew` from the repo root builds the nextgenlauncher loader, not NextGen.**
> Every command below must run from `vendor/ALEX5402_NewNextGen`, and every relative path
> below is relative to that directory unless written out in full.

The one-liner, if your environment is already set up:

```bash
cd vendor/ALEX5402_NewNextGen
./gradlew :app:assembleRelease
```

Artifacts land in `vendor/ALEX5402_NewNextGen/app/build/outputs/apk/release/`. Everything
below is the detail that makes that line work, plus the one thing you must not skip before
shipping (see [Signing](#3-signing)).

---

## 1. Prerequisites

| Requirement | Value | Notes |
|---|---|---|
| JDK | **21** | `app` and `Bcore` compile at `JavaVersion.VERSION_21`. `reflect-lib` and `compiler` are Java 17, but a 21 JDK builds them fine. (The nextgenlauncher loader at the repo root is Java 17 — a 21 JDK builds that too.) |
| Android SDK | compileSdk **35** | Path comes from `vendor/ALEX5402_NewNextGen/local.properties` -> `sdk.dir`. |
| NDK | **exactly `27.0.12077973`** | Pinned in both `app/build.gradle` and `Bcore/build.gradle`. |
| Gradle | 8.14.5 | Supplied by the NextGen wrapper — always use `./gradlew` from `vendor/ALEX5402_NewNextGen`, never a system `gradle` and never the repo-root wrapper (that one is Gradle 8.10). |
| Network | jitpack + google + mavenCentral | `FreeReflection` resolves only from jitpack. |

`local.properties` is not in version control, and the two projects need their own. The NextGen
one is `vendor/ALEX5402_NewNextGen/local.properties`; create it if missing:

```properties
sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk
```

Install the pinned NDK if you don't have it:

```bash
sdkmanager "ndk;27.0.12077973"
```

### The JDK 21 gotcha

A JDK 17 on `PATH` is the single most common build failure here:

```
> error: invalid source release: 21
```

Gradle uses whatever JVM it starts under. Either put a JDK 21 on `PATH`, or point Gradle at one
per-invocation — Android Studio ships a suitable JBR 21:

```bash
cd vendor/ALEX5402_NewNextGen
./gradlew :app:assembleRelease -Dorg.gradle.java.home="C:/Program Files/Android/Android Studio/jbr"
```

To make it permanent instead, add to `vendor/ALEX5402_NewNextGen/gradle.properties` (the
NextGen one — not the repo-root `gradle.properties`, which configures the loader build):

```properties
org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr
```

> Forward slashes even on Windows — a backslash in `gradle.properties` is an escape character.

---

## 2. Build

```bash
cd vendor/ALEX5402_NewNextGen

# release APKs (this is what you want)
./gradlew :app:assembleRelease

# from a genuinely clean tree (also rebuilds the native .so via ndk-build — slow)
./gradlew clean :app:assembleRelease
```

The release build runs **R8** (`minifyEnabled true`) over both `:app` and `:Bcore`, so it is
substantially slower than `assembleDebug` and it is the only configuration where
`app/proguard-rules.pro` has any effect. A debug build ignores those rules entirely — if you
change keep-rules, you must build release to test them.

### Output

ABI splits are enabled (`armeabi-v7a`, `arm64-v8a`) with `universalApk true`, so one build
produces three APKs, renamed by the `applicationVariants` block to
`NextGen_<versionName>_<abi>-<buildType>.apk`:

```
vendor/ALEX5402_NewNextGen/app/build/outputs/apk/release/
├── NextGen_4.0.0_arm64-v8a-release.apk     ~58 MB
├── NextGen_4.0.0_armeabi-v7a-release.apk   ~57 MB
├── NextGen_4.0.0_universal-release.apk     ~58 MB   <- install this one if unsure
└── output-metadata.json
```

Sizes are dominated by `app/src/main/assets/`, which bundles microG for installation into the
container: `microg_gms.apk` (~49 MB, microG Services) and `microg_vending.apk` (~4.4 MB, microG
Companion). That is expected, not bloat to strip.

### Install

From the repo root:

```bash
adb -s <serial> install -r vendor/ALEX5402_NewNextGen/app/build/outputs/apk/release/NextGen_4.0.0_universal-release.apk
```

On an **x86 emulator** (MuMu, LDPlayer, Nox) install the `arm64-v8a` split explicitly rather
than the universal one. MuMu reports `ro.product.cpu.abilist =
x86_64,arm64-v8a,x86,armeabi-v7a,armeabi`, so x86_64 is preferred first — but neither Bcore nor
`libmodmenu.so` is built for x86_64, so there is nothing to prefer. Installing the arm64 split
pins `primaryCpuAbi=arm64-v8a` and everything runs under the emulator's arm translation:

```bash
adb -s 127.0.0.1:5555 install -r vendor/ALEX5402_NewNextGen/app/build/outputs/apk/release/NextGen_4.0.0_arm64-v8a-release.apk
adb -s 127.0.0.1:5555 shell dumpsys package menu.nextgen.launcher | grep primaryCpuAbi
```

Expect a noticeably slower cold start there — around 2.2 s to first frame versus 0.9 s on a
Pixel 8 — because of the translation layer.

Bumping the version for a real release means editing `vendor/ALEX5402_NewNextGen/build.gradle`
`ext` block (`versionCode`, `versionName`) — the module files read from there, so change it in
one place.

---

## 3. Signing

**Read this before distributing anything.**

`app/build.gradle` currently declares:

```groovy
buildTypes {
    release {
        signingConfig signingConfigs.debug   // <-- the debug keystore
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

So `assembleRelease` emits an APK signed with `~/.android/debug.keystore`. Verified on the
current build:

```
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
```

That is fine for local testing — and convenient, because it shares a signature with the debug
build, so a release APK installs straight over a debug one with no uninstall. It is **not**
acceptable for distribution: the debug key is not yours, it is on every developer machine, and
Play will reject it.

### Option A — sign from Gradle (preferred)

Replace the `signingConfig` with a real one driven by environment variables, so no secret is
committed:

```groovy
android {
    signingConfigs {
        upload {
            def ks = System.getenv("KEYSTORE_FILE")
            if (ks && file(ks).exists()) {
                storeFile file(ks)
                storePassword System.getenv("KEYSTORE_PASSWORD")
                keyAlias System.getenv("KEY_ALIAS")
                keyPassword System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.upload
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

```bash
export KEYSTORE_FILE=/path/to/release.jks KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=...
cd vendor/ALEX5402_NewNextGen && ./gradlew :app:assembleRelease
```

### Option B — re-sign the built APK

Leaves the build files untouched. From the repo root:

```bash
OUT=vendor/ALEX5402_NewNextGen/app/build/outputs/apk/release

apksigner sign \
  --ks release.jks --ks-key-alias <alias> \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out NextGen_4.0.0_universal-release-signed.apk \
  $OUT/NextGen_4.0.0_universal-release.apk

apksigner verify --verbose --print-certs NextGen_4.0.0_universal-release-signed.apk
```

Keep **v1 enabled** here: `minSdk` is 21, and API 21-23 cannot verify v2/v3-only APKs. (This
differs from the mod-APK workflow, where v1 is always disabled — that rule assumes a modern
`minSdk`.) No separate `zipalign` step is needed; AGP already aligns its output.

---

## 4. What R8 must not strip

`app/proguard-rules.pro` carries keep-rules that are load-bearing at runtime. R8 sees no Java
caller for these, so without the rules they get renamed or removed and the release build breaks
in ways debug never does:

- `menu.nextgen.launcher.**`, `mirror.**`, `android.**`, `com.android.**` — the engine and the
  framework mirrors it reflects against.
- `menu.nextgen.reflect.**` and everything annotated `@BClass` / `@BClassName` /
  `@BField` / `@BMethod` / ... — the generated `BR*` reflection layer resolves these **by name**.
- `menu.nextgen.launchera.app.VBridge` (and its inner classes) — called from native code over
  JNI; its name and signatures are the contract with `libloader.so`, which is built by the
  *other* project in this repo (see [section 5](#5-the-other-project-the-nextgenlauncher-loader)).
- `menu.nextgen.launchera.view.login.**` — reached by class name from the engine
  (`ActivityManagerCommonProxy` -> `ConsentActivity`, `FacebookOAuth` -> `WebLoginActivity`),
  never by a Java reference.

`Bcore/consumer-rules.pro` ships engine keep-rules to consumers as well.

If a release build starts up but a virtual app dies where debug worked, suspect a missing keep
rule before anything else.

---

## 5. The other project: the nextgenlauncher loader

Built from the **repo root**, not from `vendor/`. It is a separate app
(`com.nextgenlauncher.loader`) whose Rust `.so` is produced by cargo-ndk and dropped into
`app/src/main/jniLibs/` before Gradle runs.

### How the pieces fit

```
nextgenlauncher\                          <- Gradle build #1, appId com.nextgenlauncher.loader
├─ app/                                    M1 prototype: MainActivity.java = GameActivity glue
│  └─ src/main/jniLibs/arm64-v8a/
│     └─ libloader.so   (~23.5 MB)  <──cargo-ndk──┐
├─ native/loader/       Rust + egui launcher UI ──┘   (scripts\build.ps1 automates this)
├─ native/overlay/      Rust + egui overlay, crate name "modmenu"
│  └─ target/aarch64-linux-android/release/
│     └─ libmodmenu.so  (~3.7 MB)   ──COPIED BY HAND──┐
├─ engine/              (empty — reserved for the future NextGen integration)
├─ scripts/build.ps1    builds ONLY native/loader + the loader APK
│                                                     │
└─ vendor\ALEX5402_NewNextGen\          <- Gradle build #2, appId menu.nextgen.launcher
   ├─ app/  Bcore/  reflect-lib/  compiler/      │
   └─ app/src/main/jniLibs/arm64-v8a/                 │
      └─ libmodmenu.so  (~2.9 MB, stripped)  <────────┘
```

The two apps are independent: `vendor/.../app` has **no** dependency on the loader, and the
repo-root `app` has **no** dependency on `:Bcore` — its only dependencies are `appcompat` and
`games-activity`. `settings.gradle` at the repo root includes just `:app`.

The repo-root `app` is an **M1 proof of the native-UI and build/deploy chain**, not a working
launcher. `native/loader/src/lib.rs` is ~110 lines with a hardcoded target package, and its
"Clone & Launch" button only sets a status string — it has no `jni` or `ndk-context` dependency,
so it cannot reach the engine at all yet. Merging the two projects is the M2 work.

### The one real coupling: `libmodmenu.so`

The overlay — not the launcher — is where the two projects actually touch today.
`native/overlay` builds `libmodmenu.so`, which NextGen ships in its own `jniLibs/` and
`System.load`s inside the guest process from `NgInjector` (resolved via
`getApplicationInfo().nativeLibraryDir`). Bcore has no `jniLibs` of its own.

> **Nothing automates this copy.** `scripts\build.ps1` builds `native/loader` only, and no Gradle
> task in either project references `libmodmenu`. After rebuilding the overlay you must copy it
> across by hand, or NextGen silently keeps packaging the previous mod menu — the clone still
> launches perfectly, just with stale (or missing) hooks and overlay.

```powershell
cargo ndk -t arm64-v8a -p 28 --manifest-path native\overlay\Cargo.toml build --release
Copy-Item -Force `
    native\overlay\target\aarch64-linux-android\release\libmodmenu.so `
    vendor\ALEX5402_NewNextGen\app\src\main\jniLibs\arm64-v8a\libmodmenu.so
```

A plain copy is enough — AGP's `stripDebugDebugSymbols` strips the library on the way into the
APK, which is why the packaged copy is smaller again. Check the two timestamps match when a mod
change appears not to take effect. `libshadowhook.so` and `libshadowhook_nothing.so` sit beside
it and must ship together; `SHADOWHOOK_MODE_SHARED` needs the second one. All three are
**arm64-v8a only**, so the `armeabi-v7a` split ships a working engine with no mod payload.

### Building the loader

```powershell
scripts\build.ps1              # arm64 only: cargo-ndk -> jniLibs, assembleDebug, install + launch
scripts\build.ps1 -Release     # release Rust build
scripts\build.ps1 -X86         # also build x86_64 (MuMu)
scripts\build.ps1 -NoLaunch    # build + install, don't `am start`
```

`build.ps1` hardcodes `$Device = "39051FDJH00FEP"` (the Pixel 8) and installs there regardless of
what else is attached — edit that variable to target another device. It also hardcodes the SDK
and NDK paths.

Note the differing toolchains — do not copy settings between the two projects:

| | NextGen (`vendor/ALEX5402_NewNextGen`) | nextgenlauncher loader (repo root) |
|---|---|---|
| Gradle wrapper | 8.14.5 | 8.10 |
| AGP | 8.13.2 | 8.7.3 |
| Java level | 21 | 17 |
| `minSdk` / `targetSdk` | 21 / 28 | 28 / 35 |
| `minifyEnabled` (release) | `true` | `false` |
| ABI | `armeabi-v7a` + `arm64-v8a`, split | `arm64-v8a` only |

See [`CLAUDE.md`](CLAUDE.md) and [`docs/`](docs/README.md) for the loader's architecture and
milestones.

---

## 6. Other NextGen module targets

All from `vendor/ALEX5402_NewNextGen`:

```bash
./gradlew :Bcore:assembleRelease     # engine AAR only
./gradlew :app:assembleDebug         # debug APK (no R8, debuggable, run-as works)
./gradlew :Bcore:lint                # advisory only: abortOnError=false, NewApi/InlinedApi
./gradlew clean                      # drops native objects too
```

There is **no meaningful test suite** — `Bcore` sets `unitTests.returnDefaultValues = true`, so
`./gradlew test` passes vacuously. Verification is build + run on a device.

There is no CI workflow in this tree; releases are built locally.

---

## 7. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Build produced `com.nextgenlauncher.loader` / `app-release.apk`, not `NextGen_4.0.0_*.apk` | You ran `./gradlew` at the repo root. `cd vendor/ALEX5402_NewNextGen` first. |
| `error: invalid source release: 21` | Gradle is running on JDK 17. See [the JDK 21 gotcha](#the-jdk-21-gotcha). |
| `Build was configured to prefer settings repositories` | A module added a `repositories {}` block. Both projects set `FAIL_ON_PROJECT_REPOS` — declare repositories **only** in that project's `settings.gradle`. |
| `Could not resolve com.github.tiann:FreeReflection` | jitpack unreachable. It is required; do not swap in a mirror inside a module file. Note the repo-root project does **not** declare jitpack — another reason not to build NextGen from there. |
| NDK not found / wrong version | Install `ndk;27.0.12077973`. `app` and `Bcore` both pin it — keep the two in sync if you ever change it. |
| `illegal unicode escape` in AIDL-generated Java | The `doFirst` block in `Bcore/build.gradle` strips the ` * Using:` comment lines containing Windows backslash paths. Keep that task if you touch AIDL or AGP. |
| Duplicate `libandroidx.graphics.path.so` | Handled by the `packaging { excludes }` entries in `app/build.gradle`. Do not remove them. |
| Injection silently no-ops on an otherwise working clone | Two causes, both silent. (1) `useLegacyPackaging = true` was dropped — `libmodmenu.so` must be extracted to disk for `nativeLibraryDir` resolution to find it. (2) The `.so` is missing from `app/src/main/jniLibs/arm64-v8a/`; `NgInjector` logs `mod .so not found; skipping injection` and returns. Confirm with `adb shell run-as menu.nextgen.launcher ls lib/arm64/`. |
| Overlay/hook changes don't take effect after rebuilding the overlay | You rebuilt `native/overlay` but did not copy `libmodmenu.so` into NextGen's `jniLibs`. Nothing automates that — see [section 5](#the-one-real-coupling-libmodmenuso). Compare the two files' timestamps. |
| Clone runs on an x86 emulator but the mod menu never appears | The universal or `armeabi-v7a` APK was installed. The mod payload is arm64-v8a only — install `NextGen_4.0.0_arm64-v8a-*.apk` and check `primaryCpuAbi`. |
| `adb install` -> `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Signature changed (e.g. debug -> real keystore). Uninstall first — this wipes every cloned app and the container. |
| `adb uninstall` -> `DELETE_FAILED_INTERNAL_ERROR` | Usually cosmetic: the data wipe succeeds and only the package-record removal complains. Confirm with `adb shell run-as menu.nextgen.launcher ls /data/data/menu.nextgen.launcher/`. |
| Gradle says `UP-TO-DATE` after you edited proguard rules | Expected for `assembleDebug` — proguard is not an input to a non-minified build. Only `assembleRelease` consumes it. |

---

## 8. NextGen build-configuration reference

Paths relative to `vendor/ALEX5402_NewNextGen/`.

| Setting | Value | Where |
|---|---|---|
| `applicationId` | `menu.nextgen.launcher` | `app/build.gradle` |
| `versionCode` / `versionName` | `400` / `4.0.0` | root `build.gradle` `ext` |
| `compileSdk` | 35 | root `build.gradle` `ext` |
| `targetSdk` | **28** (deliberate) | root `build.gradle` `ext` |
| `minSdk` | 21 | root `build.gradle` `ext` |
| AGP / Kotlin | 8.13.2 / 1.9.23 | `gradle/libs.versions.toml` |
| Gradle wrapper | 8.14.5 | `gradle/wrapper/gradle-wrapper.properties` |
| App ABIs | `armeabi-v7a`, `arm64-v8a` + universal | `app/build.gradle` `splits.abi` |
| Native ABIs | `armeabi-v7a`, `arm64-v8a` | `Bcore/build.gradle` `ndk.abiFilters` |
| Native build | **ndk-build** via `Bcore/src/main/cpp/Android.mk` | there is no `CMakeLists.txt` — do not add one |

`targetSdk 28` is intentional: the proxy layer depends on pre-29 behaviours (implicit-intent
visibility, package visibility, background-start limits, legacy storage). Raising it requires an
audit of `Bcore/.../fake/service/*`.
