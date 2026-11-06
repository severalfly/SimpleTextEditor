@echo off
setlocal enabledelayedexpansion
set "ROOT=%~dp0"
set "JAVA_HOME=%ROOT%toolchain\jdk17"
set "ANDROID_HOME=%ROOT%toolchain\android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"

set "TASK=%~1"
if "%TASK%"=="" set "TASK=assembleDebug"

echo [build] task=%TASK%
call "%ROOT%toolchain\gradle-8.13\bin\gradle.bat" %TASK% --console=plain
if errorlevel 1 (
    echo.
    echo [build] FAILED
    exit /b 1
)

echo.
echo [build] done. APK:
dir /b "%ROOT%app\build\outputs\apk\debug\*.apk" 2>nul
dir /b "%ROOT%app\build\outputs\apk\release\*.apk" 2>nul
endlocal
