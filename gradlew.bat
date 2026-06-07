@echo off
setlocal

set ROOT_DIR=%~dp0
set LOCAL_GRADLE=%ROOT_DIR%.deps\gradle-8.7\bin\gradle.bat

if exist "%LOCAL_GRADLE%" (
  call "%LOCAL_GRADLE%" %*
  exit /b %ERRORLEVEL%
)

where gradle >nul 2>nul
if %ERRORLEVEL% equ 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)

echo Gradle not found. Put Gradle 8.7 at .deps\gradle-8.7 or add gradle to PATH. 1>&2
exit /b 1
