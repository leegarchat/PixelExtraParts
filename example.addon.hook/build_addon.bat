@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

if "%~1"=="" goto SHOW_HELP
if "%~1"=="-h" goto SHOW_HELP
if "%~1"=="--help" goto SHOW_HELP

set "ADDON_NAME=%~1"
set "PROJECT_PATH=%~2"

echo %ADDON_NAME% | findstr /R " " >nul
if %errorlevel%==0 (
    echo Error: addon name must not contain spaces: '%ADDON_NAME%'
    exit /b 1
)

if not "%PROJECT_PATH%"=="" (
    set "PROJECT_ROOT=%PROJECT_PATH%"
) else if exist "%SCRIPT_DIR%\%ADDON_NAME%" (
    set "PROJECT_ROOT=%SCRIPT_DIR%\%ADDON_NAME%"
) else (
    set "PROJECT_ROOT=%SCRIPT_DIR%"
)

set "SRC_DIR=%PROJECT_ROOT%\src"
set "META_DIR=%PROJECT_ROOT%\META-INF"
set "OUT_DIR=%PROJECT_ROOT%\out"
set "BUILD_DIR=%PROJECT_ROOT%\build"

set "PINE_XPOSED_JAR=%SCRIPT_DIR%\prebuild\pine\pine-xposed.jar"
set "PINE_CORE_JAR=%SCRIPT_DIR%\prebuild\pine\pine-core.jar"
set "XPOSED_API_JAR=%SCRIPT_DIR%\prebuild\xposed\api-82.jar"
set "CORE_SRC=%SCRIPT_DIR%\prebuild\IAddonHook.java"

:: Check commands
where java >nul 2>nul || (echo Error: java not found & exit /b 1)
where javac >nul 2>nul || (echo Error: javac not found & exit /b 1)
where jar >nul 2>nul || (echo Error: jar not found & exit /b 1)

:: Find d8.jar
if not "%D8_JAR%"=="" if exist "%D8_JAR%" goto D8_FOUND
set "D8_JAR=%SCRIPT_DIR%\prebuild\sdk\d8.jar"
if exist "%D8_JAR%" goto D8_FOUND
echo Error: d8.jar not found! Set D8_JAR environment variable.
exit /b 1
:D8_FOUND

:: Find android.jar
if not "%ANDROID_JAR%"=="" if exist "%ANDROID_JAR%" goto ANDROID_FOUND
set "ANDROID_JAR=%SCRIPT_DIR%\prebuild\android.jar"
if exist "%ANDROID_JAR%" goto ANDROID_FOUND
echo Error: android.jar not found! Set ANDROID_JAR environment variable.
exit /b 1
:ANDROID_FOUND

echo === Building addon: %ADDON_NAME% ===
echo   Using android.jar: %ANDROID_JAR%
echo   Using d8: %D8_JAR%

if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%BUILD_DIR%\stubs" "%BUILD_DIR%\classes" "%BUILD_DIR%\dex" "%OUT_DIR%"

echo   [1/4] Compiling IAddonHook...
javac -J-Dfile.encoding=UTF-8 -encoding UTF-8 --release 11 -parameters -classpath "%ANDROID_JAR%" -d "%BUILD_DIR%\stubs" "%CORE_SRC%"
if %errorlevel% neq 0 exit /b %errorlevel%

echo   [2/4] Compiling addon sources...
:: Windows classpath separator is a semicolon (;)
set "ADDON_CP=%ANDROID_JAR%;%BUILD_DIR%\stubs"
if exist "%PINE_XPOSED_JAR%" set "ADDON_CP=%ADDON_CP%;%PINE_XPOSED_JAR%"
if exist "%PINE_CORE_JAR%" set "ADDON_CP=%ADDON_CP%;%PINE_CORE_JAR%"
if exist "%XPOSED_API_JAR%" set "ADDON_CP=%ADDON_CP%;%XPOSED_API_JAR%"

dir /s /b "%SRC_DIR%\*.java" > "%BUILD_DIR%\sources.txt" 2>nul
for %%i in ("%BUILD_DIR%\sources.txt") do if %%~zi gtr 0 (
    javac -J-Dfile.encoding=UTF-8 -encoding UTF-8 --release 11 -parameters -classpath "%ADDON_CP%" -d "%BUILD_DIR%\classes" @"%BUILD_DIR%\sources.txt"
    if !errorlevel! neq 0 exit /b !errorlevel!
)

echo   [3/4] Converting to DEX...
set "D8_CP_ARGS="
if exist "%PINE_XPOSED_JAR%" set D8_CP_ARGS=!D8_CP_ARGS! --classpath "%PINE_XPOSED_JAR%"
if exist "%PINE_CORE_JAR%" set D8_CP_ARGS=!D8_CP_ARGS! --classpath "%PINE_CORE_JAR%"
if exist "%XPOSED_API_JAR%" set D8_CP_ARGS=!D8_CP_ARGS! --classpath "%XPOSED_API_JAR%"

set "CLASSES_JAR=%BUILD_DIR%\classes_tmp.jar"
pushd "%BUILD_DIR%\classes"
jar -J-Dfile.encoding=UTF-8 cf "%CLASSES_JAR%" .
popd

java -Dfile.encoding=UTF-8 -cp "%D8_JAR%" com.android.tools.r8.D8 --lib "%ANDROID_JAR%" %D8_CP_ARGS% --output "%BUILD_DIR%\dex" --min-api 26 "%CLASSES_JAR%"
if %errorlevel% neq 0 exit /b %errorlevel%

echo   [4/4] Packaging JAR...
pushd "%BUILD_DIR%\dex"
mkdir META-INF
copy /Y "%META_DIR%\addon.json" META-INF\ >nul
jar -J-Dfile.encoding=UTF-8 cf "%OUT_DIR%\%ADDON_NAME%.jar" classes.dex META-INF\
popd

echo   Cleaning up build directory...
rmdir /s /q "%BUILD_DIR%"

echo.
echo === Done ===
echo   Result: %OUT_DIR%\%ADDON_NAME%.jar
echo.
exit /b 0

:SHOW_HELP
echo Builds a Pine addon module into a DEX JAR for Windows.
echo.
echo Usage:
echo   build_addon.bat ^<ADDON_NAME^> [PROJECT_PATH]
echo.
echo Examples:
echo   build_addon.bat test_project
echo   build_addon.bat my_hook .\my_hook
exit /b 0