@echo off
setlocal enabledelayedexpansion
title NextGEN - debug build

REM ============================================================
REM  Double-click to build the NextGEN app (debug) into out\
REM
REM  Three stages, in this order:
REM    1) cargo-ndk builds the Rust UI (libloader.so) straight into the
REM       engine's jniLibs. THE WHOLE UI LIVES IN THAT .so -- if you skip
REM       this, Gradle just repackages the previous one and your UI edits
REM       silently do not appear in the APK.
REM    2) cargo-ndk builds the overlay / mod menu (libmodmenu.so) the same way.
REM    3) the engine's Gradle build produces the APK.
REM
REM  Optional:  build-debug.bat norust  -> stage 3 only (reuse the existing
REM             libloader.so + libmodmenu.so; only correct if no Rust changed).
REM ============================================================

REM --- paths resolved relative to this script ---
set "ROOT=%~dp0"
set "PROJ=%ROOT%vendor\NextGEN"
set "OUT=%ROOT%out"
set "LOADER=%ROOT%native\loader"
set "OVERLAY=%ROOT%native\overlay"
set "JNILIBS=%PROJ%\app\src\main\jniLibs"

REM --- toolchain: the engine needs JDK 21. The ambient JAVA_HOME is deliberately
REM     NOT used -- it usually points at the JDK 17 the loader module compiles at,
REM     which the engine's Gradle rejects. Set NEXTGEN_JDK if your JDK 21 is not
REM     Android Studio's bundled JBR. ---
if defined NEXTGEN_JDK (set "JAVA_HOME=%NEXTGEN_JDK%") else (set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr")
REM Android SDK: honor an existing ANDROID_HOME / ANDROID_SDK_ROOT, else the
REM standard per-user location.
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
REM NDK for the Rust cross-build (pinned; see app\build.gradle ndkVersion).
if not defined ANDROID_NDK_HOME set "ANDROID_NDK_HOME=%ANDROID_HOME%\ndk\27.0.12077973"

REM --- optional `norust` as first arg skips stage 1 ---
set "SKIPRUST="
if /I "%~1"=="norust" set "SKIPRUST=1"

echo ============================================
echo  Building NextGEN ^(debug^)
echo  project : %PROJ%
echo  loader  : %LOADER%
echo  jdk     : %JAVA_HOME%
echo  output  : %OUT%
if defined SKIPRUST echo  rust    : SKIPPED ^(norust^)
echo ============================================
echo.

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK 21 not found at:
    echo         %JAVA_HOME%
    echo Set NEXTGEN_JDK to a JDK 21, or install Android Studio ^(bundled JBR^).
    goto :fail
)

if not exist "%PROJ%\gradlew.bat" (
    echo [ERROR] gradlew.bat not found at:
    echo         %PROJ%\gradlew.bat
    echo Is the NextGEN engine checked out under vendor\NextGEN ?
    goto :fail
)

REM ============ stages 1-2: Rust .so -> the engine's jniLibs ============
if defined SKIPRUST goto :skiprust

where cargo >nul 2>&1
if errorlevel 1 (
    echo [ERROR] cargo is not on PATH, so libloader.so cannot be rebuilt and the
    echo         APK would ship the PREVIOUS UI. Install Rust ^(https://rustup.rs^)
    echo         plus:  rustup target add aarch64-linux-android
    echo                cargo install cargo-ndk
    echo         ...or re-run as:  %~nx0 norust
    goto :fail
)

if not exist "%ANDROID_NDK_HOME%\source.properties" (
    echo [ERROR] NDK not found at:
    echo         %ANDROID_NDK_HOME%
    echo Install it with:  sdkmanager "ndk;27.0.12077973"
    echo or set ANDROID_NDK_HOME to that NDK.
    goto :fail
)

echo ==^> [1/3] cargo-ndk build loader ^(arm64-v8a, debug^)
echo      out: %JNILIBS%\arm64-v8a\libloader.so
pushd "%LOADER%"
call cargo ndk -t arm64-v8a -p 28 -o "%JNILIBS%" build
set "RC=%ERRORLEVEL%"
popd

if not "%RC%"=="0" (
    echo.
    echo [ERROR] cargo ndk failed ^(exit %RC%^) building the loader.
    echo         Missing cargo-ndk?  cargo install cargo-ndk
    goto :fail
)
echo.

echo ==^> [2/3] cargo-ndk build overlay / mod menu ^(arm64-v8a, debug^)
echo      out: %JNILIBS%\arm64-v8a\libmodmenu.so
pushd "%OVERLAY%"
call cargo ndk -t arm64-v8a -p 28 -o "%JNILIBS%" build
set "RC=%ERRORLEVEL%"
popd

if not "%RC%"=="0" (
    echo.
    echo [ERROR] cargo ndk failed ^(exit %RC%^) building the overlay.
    echo         The overlay links libshadowhook.so from native\overlay\vendor.
    goto :fail
)
echo.

:skiprust
if not exist "%JNILIBS%\arm64-v8a\libloader.so" (
    echo [ERROR] %JNILIBS%\arm64-v8a\libloader.so is missing -- the APK would have
    echo         no UI. Re-run without `norust`.
    goto :fail
)
if not exist "%JNILIBS%\arm64-v8a\libmodmenu.so" (
    echo [ERROR] %JNILIBS%\arm64-v8a\libmodmenu.so is missing -- clones would launch
    echo         with no mod menu. Re-run without `norust`.
    goto :fail
)

REM ============ stage 3: the engine APK ============
echo ==^> [3/3] gradle :app:assembleDebug
pushd "%PROJ%"
call "%PROJ%\gradlew.bat" :app:assembleDebug --console=plain
set "RC=%ERRORLEVEL%"
popd

if not "%RC%"=="0" (
    echo.
    echo [ERROR] Gradle build failed ^(exit %RC%^).
    goto :fail
)

if not exist "%OUT%" mkdir "%OUT%"
copy /Y "%PROJ%\app\build\outputs\apk\debug\*.apk" "%OUT%\" >nul

echo.
echo ============================================
echo  BUILD OK  -  APKs in %OUT%
echo ============================================
for %%F in ("%OUT%\*.apk") do echo   %%~nxF  ^(%%~zF bytes^)
echo.
pause
exit /b 0

:fail
echo.
pause
exit /b 1
