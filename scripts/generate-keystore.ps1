<#
.SYNOPSIS
    Membuat keystore rilis RSA 4096-bit untuk Alunara (Android TV), menulis keystore.properties lokal
    (git-ignored), dan mencetak nilai Base64 untuk GitHub Secret ALUNARA_KEYSTORE_BASE64.

.EXAMPLE
    .\scripts\generate-keystore.ps1
#>
[CmdletBinding()]
param(
    [string]$KeystorePath = "alunara-release.jks",
    [string]$Alias = "alunara-release",
    [string]$DName = "CN=Alunara Android TV, OU=Release, O=Alunara, C=ID",
    [int]$ValidityDays = 10000,
    [switch]$WriteProperties
)

$ErrorActionPreference = "Stop"

function Resolve-Keytool {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command "keytool" -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $jdkDefault = "C:\Program Files\Java\jdk-21.0.12.1\bin\keytool.exe"
    if (Test-Path $jdkDefault) { return $jdkDefault }
    throw "keytool tidak ditemukan. Pastikan JAVA_HOME mengarah ke JDK 21."
}

function Read-SecretPlain([string]$PromptText) {
    $sec = Read-Host -Prompt $PromptText -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringAuto($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

$keytool = Resolve-Keytool
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$resolvedKeystore = if ([System.IO.Path]::IsPathRooted($KeystorePath)) {
    $KeystorePath
} else {
    Join-Path $repoRoot $KeystorePath
}

if (Test-Path $resolvedKeystore) {
    throw "Keystore sudah ada di '$resolvedKeystore'. Hapus atau tentukan -KeystorePath lain."
}

$storePassword = Read-SecretPlain "Masukkan Store Password (minimal 8 karakter)"
if ($storePassword.Length -lt 8) {
    throw "Store password terlalu pendek (minimal 8 karakter)."
}
$keyPassword = Read-SecretPlain "Masukkan Key Password (kosongkan jika sama dengan Store Password)"
if ([string]::IsNullOrWhiteSpace($keyPassword)) {
    $keyPassword = $storePassword
}

& $keytool -genkeypair `
    -v `
    -keystore $resolvedKeystore `
    -alias $Alias `
    -keyalg RSA `
    -keysize 4096 `
    -validity $ValidityDays `
    -storetype JKS `
    -dname $DName `
    -storepass $storePassword `
    -keypass $keyPassword

if ($LASTEXITCODE -ne 0) {
    throw "keytool gagal dengan exit code $LASTEXITCODE"
}

if ($WriteProperties -or -not (Test-Path (Join-Path $repoRoot "keystore.properties"))) {
    $relStore = [System.IO.Path]::GetRelativePath($repoRoot, $resolvedKeystore).Replace("\", "/")
    $propsContent = @"
storeFile=$relStore
storePassword=$storePassword
keyAlias=$Alias
keyPassword=$keyPassword
"@
    Set-Content -Path (Join-Path $repoRoot "keystore.properties") -Value $propsContent -Encoding UTF8
    Write-Host "keystore.properties dibuat (git-ignored)." -ForegroundColor Green
}

$bytes = [System.IO.File]::ReadAllBytes($resolvedKeystore)
$base64 = [Convert]::ToBase64String($bytes)

Write-Host ""
Write-Host "=== Keystore berhasil dibuat ===" -ForegroundColor Cyan
Write-Host "Lokasi Keystore : $resolvedKeystore"
Write-Host "Alias           : $Alias"
Write-Host ""
Write-Host "Simpan nilai berikut ke GitHub Repository -> Settings -> Secrets -> Actions:" -ForegroundColor Yellow
Write-Host "  - ALUNARA_KEYSTORE_BASE64   : (disalin ke clipboard jika tersedia / dicetak di bawah)"
Write-Host "  - ALUNARA_KEYSTORE_PASSWORD : <Store Password Anda>"
Write-Host "  - ALUNARA_KEY_ALIAS         : $Alias"
Write-Host "  - ALUNARA_KEY_PASSWORD      : <Key Password Anda>"
Write-Host ""
if (Get-Command "Set-Clipboard" -ErrorAction SilentlyContinue) {
    Set-Clipboard -Value $base64
    Write-Host "ALUNARA_KEYSTORE_BASE64 telah disalin ke clipboard!" -ForegroundColor Green
} else {
    Write-Output $base64
}
