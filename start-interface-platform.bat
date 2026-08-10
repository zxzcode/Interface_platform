@echo off
setlocal
cd /d "%~dp0"
title Interface Platform

where py >nul 2>nul
if not errorlevel 1 goto use_py

where python >nul 2>nul
if errorlevel 1 goto use_powershell
python -c "import sys; exit(0 if sys.version_info.major == 3 else 1)" >nul 2>nul
if errorlevel 1 goto use_powershell

python build.py
if errorlevel 1 goto build_failed
python statr.py %*
exit /b %errorlevel%

:use_py
py -3 build.py
if errorlevel 1 goto build_failed
py -3 statr.py %*
exit /b %errorlevel%

:use_powershell
echo Python 3 is not available; using the built-in PowerShell launchers.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build.ps1"
if errorlevel 1 goto build_failed
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start.ps1" %*
exit /b %errorlevel%

:build_failed
echo.
echo Build failed. Stop any running interface-platform process and try again.
pause
exit /b 1
