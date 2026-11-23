<#
.SYNOPSIS
    Installiert den ForscherFreunde Hub für den aktuellen Windows-Benutzer (keine Admin-Rechte nötig).

.DESCRIPTION
    1. Legt auf dem Desktop die Ordnerstruktur "ForscherFreundeOberflaeche" an
       (profiles.json, Profile und shared\* bleiben unangetastet, falls schon vorhanden).
    2. Lädt die neueste Hub-Version von GitHub (geprüft per SHA-256) nach
       Desktop\ForscherFreundeOberflaeche\hub – nur wenn sie dort noch nicht liegt.
       Spätere Updates erledigt der Hub selbst.
    3. Lädt eine Java-Laufzeit (Eclipse Temurin 21, geprüft per SHA-256) nach
       %LOCALAPPDATA%\ForscherFreundeHub\jre – nur wenn sie noch fehlt.
    4. Erstellt die Verknüpfung "ForscherFreunde Hub" mit ForscherFreunde-Symbol auf dem
       Desktop und im Startmenü.

    Das Skript kann beliebig oft ausgeführt werden; vorhandene Daten werden nie überschrieben.

.PARAMETER OfflineDir
    Ordner (z. B. USB-Stick) mit ForscherFreundeHub.jar, ForscherFreunde.ico und optional
    einer Temurin-JRE als ZIP (OpenJDK21U-jre_x64_windows_*.zip). Dann wird nichts
    heruntergeladen. Liegt install.ps1 selbst neben einer ForscherFreundeHub.jar, wird dieser
    Ordner automatisch verwendet.

.PARAMETER UpdateApp
    Ersetzt eine bereits vorhandene ForscherFreundeHub.jar durch die neueste Version.

.PARAMETER Start
    Startet den Hub nach der Installation.

.PARAMETER Quiet
    Keine Rückfragen (für die Verteilung auf viele Laptops).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File install.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File install.ps1 -OfflineDir E:\ForscherFreunde -Quiet
#>
[CmdletBinding()]
param(
    [string]$OfflineDir,
    [string]$DataDir,
    [string]$Repo = 'ForscherViking/ForscherFreundeOberflaeche',
    [switch]$UpdateApp,
    [switch]$Start,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # macht Invoke-WebRequest in PowerShell 5.1 um ein Vielfaches schneller
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$AppName      = 'ForscherFreunde Hub'
$JarName      = 'ForscherFreundeHub.jar'
$IconName     = 'ForscherFreunde.ico'
$JavaFeature  = 21
$JvmOptions   = '-XX:+UseSerialGC -Xms24m -Xmx192m -XX:TieredStopAtLevel=1 -Xss512k'
$ReleaseBase  = "https://github.com/$Repo/releases/latest/download"

function Write-Step([string]$Text) { Write-Host ''; Write-Host "==> $Text" -ForegroundColor Cyan }
function Write-Ok([string]$Text)   { Write-Host "    $Text" -ForegroundColor Green }
function Write-Info([string]$Text) { Write-Host "    $Text" -ForegroundColor Gray }

function Ensure-Directory([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
        Write-Ok "angelegt: $Path"
    }
}

function Get-Download([string]$Url, [string]$OutFile) {
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing -UserAgent 'ForscherFreundeHub-Installer'
            return
        } catch {
            if ($attempt -eq 3) { throw "Download fehlgeschlagen: $Url`n$($_.Exception.Message)" }
            Start-Sleep -Seconds (2 * $attempt)
        }
    }
}

function Assert-Sha256([string]$File, [string]$Expected) {
    $actual = (Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash
    if ($actual -ine $Expected.Trim()) {
        Remove-Item -LiteralPath $File -Force -ErrorAction SilentlyContinue
        throw "Prüfsumme stimmt nicht für $(Split-Path $File -Leaf) – Download beschädigt oder manipuliert."
    }
}

function New-TempFile([string]$Suffix) {
    return Join-Path ([IO.Path]::GetTempPath()) ("ffhub-" + [Guid]::NewGuid().ToString('N') + $Suffix)
}

try {
    Write-Host ''
    Write-Host "  $AppName – Installation" -ForegroundColor White
    Write-Host '  ------------------------------------------' -ForegroundColor DarkGray

    # ------------------------------------------------------------------ Pfade
    $desktop = [Environment]::GetFolderPath('Desktop')        # berücksichtigt auch einen OneDrive-Desktop
    if (-not $DataDir) { $DataDir = Join-Path $desktop 'ForscherFreundeOberflaeche' }
    $hubDir     = Join-Path $DataDir 'hub'
    $jarPath    = Join-Path $hubDir $JarName
    $iconPath   = Join-Path $hubDir $IconName
    $stateDir   = Join-Path $env:LOCALAPPDATA 'ForscherFreundeHub'
    $jreDir     = Join-Path $stateDir 'jre'
    $javaw      = Join-Path $jreDir 'bin\javaw.exe'

    if (-not $OfflineDir -and $PSScriptRoot -and (Test-Path -LiteralPath (Join-Path $PSScriptRoot $JarName))) {
        $OfflineDir = $PSScriptRoot
    }
    if ($OfflineDir) {
        if (-not (Test-Path -LiteralPath $OfflineDir)) { throw "Offline-Ordner nicht gefunden: $OfflineDir" }
        Write-Info "Offline-Installation aus: $OfflineDir"
    }

    # ------------------------------------------------------------------ 1. Ordnerstruktur
    Write-Step 'Ordnerstruktur auf dem Desktop'
    if (Test-Path -LiteralPath $DataDir) { Write-Info "vorhanden: $DataDir (bestehende Daten bleiben unverändert)" }
    foreach ($dir in @(
            $DataDir,
            (Join-Path $DataDir 'shared\templates'),
            (Join-Path $DataDir 'shared\saves'),
            (Join-Path $DataDir 'shared\backup'),
            (Join-Path $DataDir 'shared\documentation'),
            $hubDir,
            $stateDir)) {
        Ensure-Directory $dir
    }

    # ------------------------------------------------------------------ 2. Hub-Programm
    Write-Step 'ForscherFreunde Hub'
    if ((Test-Path -LiteralPath $jarPath) -and -not $UpdateApp) {
        Write-Info 'bereits vorhanden – Updates installiert der Hub selbst.'
    } else {
        $tmpJar = New-TempFile '.jar'
        if ($OfflineDir) {
            Copy-Item -LiteralPath (Join-Path $OfflineDir $JarName) -Destination $tmpJar
        } else {
            $manifestFile = New-TempFile '.json'
            try {
                Get-Download "$ReleaseBase/update.json" $manifestFile
            } catch {
                throw "Auf GitHub ist noch kein Release veröffentlicht (oder keine Internetverbindung).`n$($_.Exception.Message)"
            }
            $manifest = Get-Content -LiteralPath $manifestFile -Raw -Encoding UTF8 | ConvertFrom-Json
            Remove-Item -LiteralPath $manifestFile -Force
            Write-Info "lade Version $($manifest.version) …"
            Get-Download $manifest.url $tmpJar
            if ((Get-Item -LiteralPath $tmpJar).Length -ne [long]$manifest.size) { throw 'Download unvollständig.' }
            Assert-Sha256 $tmpJar $manifest.sha256
        }
        try {
            Move-Item -LiteralPath $tmpJar -Destination $jarPath -Force
        } catch {
            Remove-Item -LiteralPath $tmpJar -Force -ErrorAction SilentlyContinue
            throw "Die Datei $jarPath ist gesperrt. Bitte den Hub zuerst beenden und erneut versuchen."
        }
        Write-Ok "installiert: $jarPath"
    }

    if (-not (Test-Path -LiteralPath $iconPath) -or $UpdateApp) {
        if ($OfflineDir -and (Test-Path -LiteralPath (Join-Path $OfflineDir $IconName))) {
            Copy-Item -LiteralPath (Join-Path $OfflineDir $IconName) -Destination $iconPath -Force
        } elseif (-not $OfflineDir) {
            Get-Download "$ReleaseBase/$IconName" $iconPath
        }
    }

    # ------------------------------------------------------------------ 3. Java-Laufzeit
    Write-Step "Java-Laufzeit (Temurin $JavaFeature)"
    if (Test-Path -LiteralPath $javaw) {
        Write-Info "bereits vorhanden: $jreDir"
    } else {
        $arch = switch ($env:PROCESSOR_ARCHITECTURE) { 'ARM64' { 'aarch64' } 'x86' { 'x86-32' } default { 'x64' } }
        $zip = $null
        $downloadedZip = $null
        if ($OfflineDir) {
            $zip = Get-ChildItem -LiteralPath $OfflineDir -Filter '*jre*.zip' -File | Sort-Object Name -Descending | Select-Object -First 1
            if ($zip) { $zip = $zip.FullName; Write-Info "verwende $zip" }
        }
        if (-not $zip) {
            if ($OfflineDir) { Write-Info 'keine JRE im Offline-Ordner – lade sie herunter …' }
            $api = "https://api.adoptium.net/v3/assets/latest/$JavaFeature/hotspot?architecture=$arch&image_type=jre&os=windows&vendor=eclipse"
            $assets = Invoke-RestMethod -Uri $api -UseBasicParsing -UserAgent 'ForscherFreundeHub-Installer'
            $pkg = @($assets)[0].binary.package
            if (-not $pkg) { throw "Keine Java-Laufzeit für Windows/$arch gefunden." }
            $zip = New-TempFile '.zip'
            Write-Info "lade $($pkg.name) ($([math]::Round($pkg.size / 1MB)) MB) …"
            Get-Download $pkg.link $zip
            Assert-Sha256 $zip $pkg.checksum
            $downloadedZip = $zip
        }
        $extract = New-TempFile ''
        Expand-Archive -LiteralPath $zip -DestinationPath $extract -Force
        $inner = Get-ChildItem -LiteralPath $extract -Directory | Select-Object -First 1
        if (-not $inner -or -not (Test-Path -LiteralPath (Join-Path $inner.FullName 'bin\javaw.exe'))) {
            throw 'Die Java-Laufzeit hat ein unerwartetes Format.'
        }
        if (Test-Path -LiteralPath $jreDir) { Remove-Item -LiteralPath $jreDir -Recurse -Force }
        Move-Item -LiteralPath $inner.FullName -Destination $jreDir
        Remove-Item -LiteralPath $extract -Recurse -Force -ErrorAction SilentlyContinue
        if ($downloadedZip) { Remove-Item -LiteralPath $downloadedZip -Force -ErrorAction SilentlyContinue }
        Write-Ok "installiert: $jreDir"
    }

    # ------------------------------------------------------------------ 4. Verknüpfungen
    Write-Step 'Verknüpfungen'
    $shell = New-Object -ComObject WScript.Shell
    $startMenu = Join-Path ([Environment]::GetFolderPath('Programs')) "$AppName.lnk"
    foreach ($lnkPath in @((Join-Path $desktop "$AppName.lnk"), $startMenu)) {
        $lnk = $shell.CreateShortcut($lnkPath)
        $lnk.TargetPath = $javaw
        # ffhub.home fest vorgeben, damit der Hub genau diesen Datenordner verwendet
        $lnk.Arguments = "$JvmOptions `"-Dffhub.home=$DataDir`" -jar `"$jarPath`""
        $lnk.WorkingDirectory = $hubDir
        if (Test-Path -LiteralPath $iconPath) { $lnk.IconLocation = "$iconPath,0" }
        $lnk.Description = "$AppName starten"
        $lnk.Save()
        Write-Ok "erstellt: $lnkPath"
    }

    Write-Host ''
    Write-Host "  Fertig! Der $AppName kann jetzt über das Symbol auf dem Desktop gestartet werden." -ForegroundColor Green
    if (-not (Test-Path -LiteralPath (Join-Path $desktop 'Minecraft.lnk'))) {
        Write-Host '  Hinweis: Für Minecraft-Profile wird eine Verknüpfung "Minecraft" auf dem Desktop benötigt.' -ForegroundColor Yellow
    }

    $launch = $Start
    if (-not $Start -and -not $Quiet) {
        $answer = Read-Host '  Hub jetzt starten? (J/n)'
        $launch = ($answer -eq '' -or $answer -match '^[jJyY]')
    }
    if ($launch) {
        Start-Process -FilePath $javaw -ArgumentList "$JvmOptions `"-Dffhub.home=$DataDir`" -jar `"$jarPath`"" -WorkingDirectory $hubDir
    }
    exit 0
} catch {
    Write-Host ''
    Write-Host "  FEHLER: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host '  Es wurden keine vorhandenen Daten gelöscht. Das Skript kann gefahrlos erneut ausgeführt werden.' -ForegroundColor Yellow
    exit 1
}
