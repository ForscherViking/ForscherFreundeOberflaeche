@echo off
rem ForscherFreunde Hub - Installation (Doppelklick genuegt, keine Admin-Rechte noetig)
rem Liegt install.ps1 daneben (z. B. auf einem USB-Stick), wird diese verwendet,
rem sonst wird die neueste Version von GitHub geladen.
setlocal
title ForscherFreunde Hub - Installation
set "PSEXE=powershell.exe -NoProfile -ExecutionPolicy Bypass"

if exist "%~dp0install.ps1" (
    %PSEXE% -File "%~dp0install.ps1" %*
) else (
    set "FFHUB_PS1=%TEMP%\ForscherFreunde-install.ps1"
    %PSEXE% -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor 3072; $ProgressPreference = 'SilentlyContinue'; Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/ForscherViking/ForscherFreundeOberflaeche/releases/latest/download/install.ps1' -OutFile $env:FFHUB_PS1"
    if errorlevel 1 (
        echo.
        echo Das Installationsskript konnte nicht geladen werden. Besteht eine Internetverbindung?
        pause
        exit /b 1
    )
    call %PSEXE% -File "%%FFHUB_PS1%%" %*
)
set "RC=%ERRORLEVEL%"
echo %* | find /i "-Quiet" >nul || pause
exit /b %RC%
