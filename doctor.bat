@echo off
setlocal enabledelayedexpansion
title NextGEN - release build doctor

REM ============================================================
REM  Double-click: checks every tool/path needed to build a
REM  NextGEN release APK (vendor\NextGEN :app:assembleRelease),
REM  plus the Rust toolchain used to (re)build the native .so
REM  payloads it packages. Read-only: nothing is built or changed.
REM ============================================================

set "ROOT=%~dp0"
set "PROJ=%ROOT%vendor\NextGEN"

REM --- SDK: honour ANDROID_HOME if already set, else the machine default ---
if defined ANDROID_HOME (set "SDK=%ANDROID_HOME%") else (set "SDK=C:\Users\<user>\AppData\Local\Android\Sdk")
set "JBR=C:\Program Files\Android\Android Studio\jbr"
set "NDK=%SDK%\ndk\27.0.12077973"

set /a OKC=0
set /a WARNC=0
set /a FAILC=0

echo ============================================================
echo   NextGEN release build - environment doctor
echo   repo : %ROOT%
echo   sdk  : %SDK%
echo ============================================================
echo.

echo [1/9] Java Development Kit - JDK 21
if exist "%JBR%\bin\java.exe" (
    call :javaver "%JBR%\bin\java.exe"
    if "!JVMAJ!"=="21" ( call :ok "Android Studio JBR is JDK !JV! - build-release.bat uses this" ) else ( call :err "Android Studio JBR is JDK !JV!, need 21" )
) else (
    call :err "JDK 21 not found at %JBR%"
    call :info "Install Android Studio bundled JBR 21, or edit JAVA_HOME in build-release.bat"
)
set "PATHJAVA="
for /f "delims=" %%p in ('where java 2^>nul') do if not defined PATHJAVA set "PATHJAVA=%%p"
if not defined PATHJAVA (
    call :info "No java on PATH - OK; build-release.bat sets JAVA_HOME to the JBR"
) else (
    call :javaver "!PATHJAVA!"
    if "!JVMAJ!"=="21" ( call :ok "PATH java is JDK !JV!" ) else ( call :warn "PATH java is JDK !JV!, not 21 - do not run gradlew from PATH; use build-release.bat" )
)
echo.

echo [2/9] Android SDK
if exist "%SDK%\" ( call :ok "SDK directory: %SDK%" ) else ( call :err "SDK directory not found: %SDK% - set ANDROID_HOME or fix the path in this script" )
echo.

echo [3/9] Android NDK - pinned 27.0.12077973
if exist "%NDK%\ndk-build.cmd" ( call :ok "NDK 27.0.12077973 installed" ) else ( call :err "NDK 27.0.12077973 not found at %NDK%" )
if exist "%NDK%\ndk-build.cmd" call :info "cargo-ndk uses this NDK clang to build libloader.so and libmodmenu.so"
if not exist "%NDK%\ndk-build.cmd" call :info "Install with: sdkmanager ndk;27.0.12077973"
echo.

echo [4/9] Android SDK components
if exist "%SDK%\platforms\android-35\android.jar" ( call :ok "Platform android-35 installed" ) else ( call :warn "Platform android-35 missing - Gradle may auto-download it" )
set "BT="
for /f "delims=" %%d in ('dir /b /o-n /ad "%SDK%\build-tools" 2^>nul') do if not defined BT if exist "%SDK%\build-tools\%%d\apksigner.bat" set "BT=%%d"
if defined BT ( call :ok "build-tools with apksigner: !BT!" ) else ( call :warn "No build-tools apksigner found - needed only to re-sign for distribution" )
if exist "%SDK%\platform-tools\adb.exe" (
    call :ok "platform-tools adb present"
    for /f "usebackq skip=1 tokens=1,2" %%a in (`"%SDK%\platform-tools\adb.exe" devices 2^>nul`) do if "%%b"=="device" call :info "device attached: %%a"
) else (
    call :warn "adb missing - needed to install the APK on a device"
)
if exist "%USERPROFILE%\.android\debug.keystore" ( call :info "debug.keystore present - release APK is DEBUG-SIGNED via signingConfigs.debug" ) else ( call :info "debug.keystore absent - AGP auto-creates it on first build" )
echo.

echo [5/9] NextGEN engine project - vendor\NextGEN
if exist "%PROJ%\gradlew.bat" ( call :ok "Gradle wrapper present" ) else ( call :err "gradlew.bat missing at %PROJ% - engine not checked out?" )
if exist "%PROJ%\gradle\wrapper\gradle-wrapper.properties" for /f "tokens=2 delims==" %%u in ('findstr /i /b distributionUrl= "%PROJ%\gradle\wrapper\gradle-wrapper.properties"') do call :info "wrapper: %%u"
if exist "%PROJ%\local.properties" ( call :ok "engine local.properties present" ) else ( call :err "engine local.properties missing - add sdk.dir=%SDK% under %PROJ%" )
echo.

echo [6/9] Rust/native toolchain - builds libloader.so + libmodmenu.so fresh
where cargo >nul 2>nul
if errorlevel 1 ( call :err "cargo not found - install the Rust toolchain via rustup" ) else ( for /f "delims=" %%r in ('cargo --version 2^>nul') do call :ok "%%r" )
where rustc >nul 2>nul
if errorlevel 1 ( call :err "rustc not found - install the Rust toolchain via rustup" ) else ( for /f "delims=" %%r in ('rustc --version 2^>nul') do call :ok "%%r" )
where cargo-ndk >nul 2>nul
if errorlevel 1 ( call :err "cargo-ndk not found - install: cargo install cargo-ndk" ) else ( call :ok "cargo-ndk present" )
where rustup >nul 2>nul
if errorlevel 1 (
    call :warn "rustup not found - cannot verify the Android build targets"
) else (
    rustup target list --installed 2>nul | findstr /i /c:"aarch64-linux-android" >nul
    if errorlevel 1 ( call :err "target aarch64-linux-android not installed - run: rustup target add aarch64-linux-android" ) else ( call :ok "Rust target aarch64-linux-android installed" )
    rustup target list --installed 2>nul | findstr /i /c:"x86_64-linux-android" >nul
    if errorlevel 1 ( call :info "x86_64-linux-android not installed - optional, only for the MuMu ABI" ) else ( call :info "x86_64-linux-android installed - optional MuMu ABI" )
)
echo.

echo [7/9] Native project sources and build inputs
if exist "%ROOT%native\loader\Cargo.toml" ( call :ok "native\loader present - builds libloader.so, the egui launcher UI" ) else ( call :err "native\loader\Cargo.toml missing - loader source not found" )
if exist "%ROOT%native\overlay\Cargo.toml" ( call :ok "native\overlay present - builds libmodmenu.so, crate modmenu" ) else ( call :err "native\overlay\Cargo.toml missing - overlay source not found" )
set "SH=%ROOT%native\overlay\vendor\shadowhook"
if exist "%SH%\arm64-v8a\libshadowhook.so" ( call :ok "overlay link input: vendor\shadowhook\arm64-v8a\libshadowhook.so" ) else ( call :err "libshadowhook.so missing under native\overlay\vendor\shadowhook\arm64-v8a - overlay build.rs links -lshadowhook" )
if exist "%SH%\shadowhook.h" ( call :ok "overlay include: vendor\shadowhook\shadowhook.h" ) else ( call :err "shadowhook.h missing - overlay build.rs compiles shadowhook_shim.c against it" )
set "DB=%ROOT%native\overlay\vendor\dobby"
if exist "%DB%\arm64-v8a\libdobby.a" ( call :info "dobby prebuilt present - alternate hook engine, used only if the Dobby shim is enabled" ) else ( call :info "dobby prebuilt absent - only needed if the Dobby shim is enabled" )
call :info "build-debug.bat / build-release.bat build BOTH .so fresh into these jniLibs, then the APK"
call :info "libloader.so from native\loader, libmodmenu.so from native\overlay - cargo-ndk, no manual copy"
call :info "Runtime companions already in jniLibs: libshadowhook.so + libshadowhook_nothing.so (vendored, not rebuilt)"
echo.

echo [8/9] Repo-root loader project - optional, host app only
if exist "%ROOT%local.properties" ( call :ok "root local.properties present" ) else ( call :warn "root local.properties missing - needed only to build the loader app at repo root" )
if exist "%ROOT%gradlew.bat" ( call :info "root gradlew present - Gradle 8.10 loader; do not cross with the engine 8.14.5" )
echo.

echo [9/9] Network reachability - Gradle dependency repositories
where curl >nul 2>nul
if errorlevel 1 (
    call :warn "curl.exe not found - skipping network checks"
) else (
    call :net "https://dl.google.com" "Google Maven - dl.google.com [engine deps]"
    call :net "https://repo1.maven.org" "Maven Central - repo1.maven.org [engine deps]"
    call :net "https://jitpack.io" "JitPack - jitpack.io [required for FreeReflection]"
    call :net "https://static.crates.io" "crates.io - Rust crate downloads [fresh .so build]"
)
echo.

echo ============================================================
echo   SUMMARY:  !OKC! OK    !WARNC! WARN    !FAILC! FAIL
if !FAILC! GTR 0 (
    echo   RESULT:   NOT READY - fix the FAIL items above, then re-run.
    echo ============================================================
    echo.
    pause
    exit /b 1
)
if !WARNC! GTR 0 ( echo   RESULT:   Release build should work; review the WARN items. ) else ( echo   RESULT:   All prerequisites present. Ready to build release. )
echo   Next:     build-release.bat  - builds libloader.so + libmodmenu.so fresh, then the release APK
echo             build-debug.bat for a fast debug build; add norust to reuse the existing .so
echo ============================================================
echo.
pause
exit /b 0

:ok
echo   [ OK ] %~1
set /a OKC+=1
goto :eof

:warn
echo   [WARN] %~1
set /a WARNC+=1
goto :eof

:err
echo   [FAIL] %~1
set /a FAILC+=1
goto :eof

:info
echo          %~1
goto :eof

:net
curl.exe -s -o NUL --max-time 8 "%~1"
if errorlevel 1 ( call :warn "unreachable: %~2" ) else ( call :ok "reachable:   %~2" )
goto :eof

:javaver
set "JV="
set "JVMAJ="
"%~1" -version 2>"%TEMP%\ngdoc_jv.txt"
for /f tokens^=2^ delims^=^" %%v in ('findstr /i version "%TEMP%\ngdoc_jv.txt"') do if not defined JV set "JV=%%v"
del "%TEMP%\ngdoc_jv.txt" 2>nul
if defined JV for /f "delims=." %%a in ("%JV%") do set "JVMAJ=%%a"
goto :eof
