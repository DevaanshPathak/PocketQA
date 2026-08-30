param(
    [string]$AvdName = $env:POCKETQA_AVD,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$androidSdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $androidSdk "platform-tools\adb.exe"
$emulator = Join-Path $androidSdk "emulator\emulator.exe"
$flutter = Get-Command flutter -ErrorAction SilentlyContinue
if (-not $flutter) {
    $flutterCandidates = @(
        $env:FLUTTER_ROOT,
        "D:\Toolchains\flutter",
        (Join-Path $env:USERPROFILE "flutter")
    ) | Where-Object { $_ }
    $flutterPath = $flutterCandidates |
        ForEach-Object { Join-Path $_ "bin\flutter.bat" } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
    if ($flutterPath) { $flutter = Get-Item $flutterPath }
}

if (-not (Test-Path $adb)) { throw "adb not found at $adb. Set ANDROID_SDK_ROOT." }
if (-not $flutter -and -not $SkipBuild) { throw "Flutter is required to build bug_app. Add it to PATH, set FLUTTER_ROOT, or use -SkipBuild with an existing APK." }

& $adb start-server | Out-Null
$deviceMatch = & $adb devices | Select-String '^\S+\s+device$' | Select-Object -First 1
$serial = if ($deviceMatch) { $deviceMatch.ToString().Split("`t")[0] } else { $null }
if (-not $serial) {
    if (-not (Test-Path $emulator)) { throw "Android emulator not found at $emulator. Set ANDROID_SDK_ROOT." }
    if (-not $AvdName) {
        $AvdName = (& $emulator -list-avds | Select-Object -First 1)
    }
    if (-not $AvdName) { throw "No Android device is connected and no AVD exists." }
    Write-Host "Starting emulator: $AvdName"
    Start-Process -FilePath $emulator -ArgumentList @('-avd', $AvdName) -WindowStyle Hidden
    & $adb wait-for-device
    $deadline = (Get-Date).AddMinutes(4)
    do {
        Start-Sleep -Seconds 2
        $deviceMatch = & $adb devices | Select-String '^\S+\s+device$' | Select-Object -First 1
        $serial = if ($deviceMatch) { $deviceMatch.ToString().Split("`t")[0] } else { $null }
        $booted = if ($serial) { (& $adb -s $serial shell getprop sys.boot_completed 2>$null).Trim() } else { "" }
    } until ($booted -eq '1' -or (Get-Date) -gt $deadline)
    if ($booted -ne '1') { throw "Emulator did not finish booting within four minutes." }
}
Write-Host "Using Android device: $serial"

$bugApk = Join-Path $repoRoot "bug_app\bugged\build\app\outputs\flutter-apk\app-debug.apk"
$controllerApk = Join-Path $repoRoot "controller\build\outputs\apk\debug\controller-debug.apk"
if (-not $SkipBuild) {
    Push-Location (Join-Path $repoRoot "bug_app\bugged")
    try {
        & $flutter.Source pub get
        if ($LASTEXITCODE -ne 0) { throw "flutter pub get failed" }
        & $flutter.Source build apk --debug -t lib/main_demo.dart
        if ($LASTEXITCODE -ne 0) { throw "bug_app build failed" }
    } finally { Pop-Location }

    $env:ANDROID_HOME = $androidSdk
    & (Join-Path $repoRoot "gradlew.bat") :controller:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "controller build failed" }
}

if (-not (Test-Path $bugApk)) { throw "Buggy app APK not found at $bugApk. Run without -SkipBuild." }
if (-not (Test-Path $controllerApk)) { throw "Controller APK not found at $controllerApk. Run without -SkipBuild." }

& $adb -s $serial install -r $bugApk
if ($LASTEXITCODE -ne 0) { throw "Failed to install buggy app" }
& $adb -s $serial install -r $controllerApk
if ($LASTEXITCODE -ne 0) { throw "Failed to install controller" }

$service = 'com.indium.pocketqa.controller/com.indium.pocketqa.controller.PocketQaAccessibilityService'
& $adb -s $serial shell am force-stop com.indium.pocketqa.controller
& $adb -s $serial shell am force-stop com.quickcart.buggyapp
& $adb -s $serial shell am start -n com.indium.pocketqa.controller/.MainActivity | Out-Null
Start-Sleep -Seconds 1
& $adb -s $serial shell settings put secure accessibility_enabled 0
& $adb -s $serial shell settings delete secure enabled_accessibility_services | Out-Null
Start-Sleep -Seconds 1
$serviceDeadline = (Get-Date).AddSeconds(30)
do {
    # A newly booted emulator can briefly clear accessibility settings while
    # PackageManager is still registering the freshly installed service.
    & $adb -s $serial shell settings put secure enabled_accessibility_services $service
    & $adb -s $serial shell settings put secure accessibility_enabled 1
    Start-Sleep -Seconds 2
    $accessibilityState = (& $adb -s $serial shell dumpsys accessibility) -join "`n"
} until ($accessibilityState -match 'Bound services:\{Service\[label=PocketQA Semantics Reader' -or (Get-Date) -gt $serviceDeadline)
if ($accessibilityState -notmatch 'Bound services:\{Service\[label=PocketQA Semantics Reader') {
    $enabledService = (& $adb -s $serial shell settings get secure enabled_accessibility_services).Trim()
    throw "PocketQA accessibility service did not bind within 30 seconds (enabled_accessibility_services=$enabledService)."
}
& $adb -s $serial shell am start -n com.quickcart.buggyapp/com.pocketqa.pocketqa.MainActivity | Out-Null
Start-Sleep -Seconds 1
& $adb -s $serial shell am start -n com.indium.pocketqa.controller/.MainActivity | Out-Null

Write-Host "PocketQA controller is open; the buggy app is running in the background on $serial."
Write-Host "View proof logs with: $adb -s $serial logcat -s PocketQA"
