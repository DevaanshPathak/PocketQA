param(
    [string]$ModelPath = "$PSScriptRoot\..\model_artifacts\gemma-4-E4B-it-gpu.litertlm",
    [string]$Serial = ""
)

$resolvedModel = (Resolve-Path -LiteralPath $ModelPath -ErrorAction Stop).Path
$requiredBytes = 2GB
if ((Get-Item -LiteralPath $resolvedModel).Length -lt $requiredBytes) {
    throw "Model file is unexpectedly small: $resolvedModel"
}

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $adb)) { throw "adb was not found at $adb" }

$deviceArgs = @()
if ($Serial) { $deviceArgs = @("-s", $Serial) }
$package = "com.indium.pocketqa.controller"
$modelName = Split-Path -Leaf $resolvedModel
$stagingPath = "/data/local/tmp/$modelName"
$targetPath = "files/models/$modelName"
& $adb @deviceArgs push $resolvedModel $stagingPath
if ($LASTEXITCODE -ne 0) { throw "Model transfer failed" }
& $adb @deviceArgs shell run-as $package mkdir -p files/models
if ($LASTEXITCODE -ne 0) { throw "Could not create PocketQA private model directory" }
& $adb @deviceArgs shell run-as $package cp $stagingPath $targetPath
if ($LASTEXITCODE -ne 0) { throw "Could not copy model into PocketQA private storage" }
& $adb @deviceArgs shell run-as $package ls -lh $targetPath
& $adb @deviceArgs shell rm -f $stagingPath
