# nextgenlauncher build/deploy — M1
# Usage:
#   scripts\build.ps1                # arm64 only, install+launch on the Pixel 8
#   scripts\build.ps1 -X86           # also build x86_64 (MuMu)
#   scripts\build.ps1 -Release       # release Rust build
#   scripts\build.ps1 -NoLaunch      # build+install, don't am start
param(
    [switch]$X86,
    [switch]$Release,
    [switch]$NoLaunch
)
# NOTE: keep this "Continue", not "Stop". cargo-ndk and gradle write progress to
# stderr; under "Stop" PowerShell 5.1 wraps native stderr as a terminating
# NativeCommandError even on exit code 0. We gate on $LASTEXITCODE explicitly.
$ErrorActionPreference = "Continue"

# Repo root = one level up from this script — portable, no hardcoded drive/user.
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

# Android SDK, auto-detected (no hardcoded C:\Users\<name>): ANDROID_HOME /
# ANDROID_SDK_ROOT if set, else local.properties (sdk.dir), else the standard
# per-user location %LOCALAPPDATA%\Android\Sdk.
function Find-AndroidSdk {
    foreach ($c in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($c -and (Test-Path $c)) { return (Resolve-Path $c).Path }
    }
    $lp = Join-Path $Root "local.properties"
    if (Test-Path $lp) {
        $line = Get-Content $lp | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($line) {
            # local.properties is a Java props file: unescape \\ and \: to a real path.
            $p = ($line -replace '^\s*sdk\.dir\s*=\s*', '').Trim() -replace '\\\\', '\' -replace '\\:', ':'
            if (Test-Path $p) { return (Resolve-Path $p).Path }
        }
    }
    $def = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $def) { return $def }
    throw "Android SDK not found. Set ANDROID_HOME, add sdk.dir to local.properties, or install to $def"
}

$Sdk    = Find-AndroidSdk
$Ndk    = "$Sdk\ndk\27.0.12077973"
$Device = "39051FDJH00FEP"                # ← your `adb devices` serial (still machine-specific)
$AppId  = "menu.nextgen.loader"

$env:ANDROID_HOME     = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:ANDROID_NDK_HOME = $Ndk

$abis   = @("arm64-v8a")
if ($X86) { $abis += "x86_64" }
$targetArgs = @(); foreach ($a in $abis) { $targetArgs += @("-t", $a) }
$profileArgs = @("build"); if ($Release) { $profileArgs = @("build", "--release") }

# The plugin framework (native C++ registry + plugins) is ALWAYS compiled — no
# feature gate. Which plugins run is chosen in native/loader/plugins/register.cpp.
$featureArgs = @()

Write-Host "==> [1/3] cargo-ndk build ($($abis -join ', '))" -ForegroundColor Cyan
Push-Location "$Root\native\loader"
try {
    cargo ndk @targetArgs -p 28 -o "$Root\app\src\main\jniLibs" @profileArgs @featureArgs
    if ($LASTEXITCODE -ne 0) { throw "cargo ndk failed ($LASTEXITCODE)" }
} finally { Pop-Location }

Write-Host "==> [2/3] gradle assembleDebug" -ForegroundColor Cyan
Push-Location $Root
try {
    & "$Root\gradlew.bat" assembleDebug --stacktrace
    if ($LASTEXITCODE -ne 0) { throw "gradlew assembleDebug failed ($LASTEXITCODE)" }
} finally { Pop-Location }

$Apk = "$Root\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $Apk)) { throw "APK not found at $Apk" }
Write-Host "==> install to $Device ($([math]::Round((Get-Item $Apk).Length/1MB,1)) MB)" -ForegroundColor Cyan
# Multiple devices are connected -> always target the Pixel 8 explicitly.
adb -s $Device install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "adb install failed ($LASTEXITCODE)" }

if (-not $NoLaunch) {
    Write-Host "==> [3/3] launch on $Device" -ForegroundColor Cyan
    adb -s $Device shell am start -n "$AppId/.MainActivity"
}
Write-Host "==> done" -ForegroundColor Green
