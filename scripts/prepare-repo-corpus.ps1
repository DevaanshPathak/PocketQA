param(
  [Parameter(Mandatory=$true)][string]$Repo,
  [Parameter(Mandatory=$true)][string]$OutputDir
)

$ErrorActionPreference = 'Stop'
if (Test-Path $Repo) { $source = (Resolve-Path $Repo).Path } else {
  $source = Join-Path $env:TEMP ("pocketqa-repo-" + [guid]::NewGuid())
  git clone --depth 1 $Repo $source
}
New-Item -ItemType Directory -Force $OutputDir | Out-Null
$allowed = Get-ChildItem $source -Recurse -File | Where-Object {
  $_.FullName -notmatch '\\(\.git|build|\.gradle|node_modules|\.idea)\\' -and
  $_.Extension -in '.kt','.java','.dart','.ts','.tsx','.js','.py','.go','.swift','.xml','.json','.md'
}
$manifest = foreach ($file in $allowed) {
  $relative = $file.FullName.Substring($source.Length).TrimStart('\','/')
  $target = Join-Path $OutputDir $relative
  New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
  Copy-Item $file.FullName $target
  [pscustomobject]@{ sourceKey=$relative; sha256=(Get-FileHash $file.FullName -Algorithm SHA256).Hash; bytes=$file.Length }
}
$manifest | ConvertTo-Json | Set-Content (Join-Path $OutputDir 'manifest.json')
Write-Host "Prepared $($manifest.Count) approved source files in $OutputDir"
