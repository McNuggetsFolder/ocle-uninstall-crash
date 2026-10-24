@echo off
rem ============================================================
rem  Build the mod jar WITHOUT Gradle / WITHOUT internet.
rem  It reuses the Forge libraries already present in your
rem  .minecraft\libraries folder.
rem
rem  Usage:
rem     tools\build-offline.bat
rem     tools\build-offline.bat "D:\your\.minecraft"
rem
rem  Output goes to dist\ (file name comes from gradle.properties)
rem
rem  NOTE: never put ">" or "<" inside echo/rem text in this file.
rem        cmd treats them as redirection and will overwrite files.
rem ============================================================
setlocal enabledelayedexpansion
rem Keep the console code page untouched: all messages here are ASCII,
rem and switching to 65001 can mangle non-ASCII paths passed as arguments.

set "ROOT=%~dp0.."
set "WORK=%ROOT%\.build-offline"
set "DIST=%ROOT%\dist"

rem ---------- 1. JDK ----------
set "JDK=%JAVA_HOME%"
if not exist "%JDK%\bin\javac.exe" set "JDK=C:\Program Files\Java\jdk-17"
if not exist "%JDK%\bin\javac.exe" (
    echo [E] JDK 17 not found. Install JDK 17 or set JAVA_HOME.
    exit /b 1
)
echo [1/7] JDK        : %JDK%

rem ---------- 2. locate .minecraft ----------
set "MC=%~1"
if defined MC goto :check_mc

for %%C in (
    "%APPDATA%\.minecraft"
    "%USERPROFILE%\.minecraft"
    "D:\.minecraft"
    "E:\.minecraft"
    "F:\.minecraft"
    "C:\.minecraft"
) do (
    if not defined MC if exist "%%~C\libraries\net\minecraftforge\forge" set "MC=%%~C"
)
if not defined MC (
    echo [E] Cannot find a .minecraft containing Forge libraries.
    echo     Usage: build-offline.bat "D:\path\to\.minecraft"
    exit /b 1
)

:check_mc
if not exist "%MC%\libraries\net\minecraftforge\forge" (
    echo [E] "%MC%" has no net\minecraftforge\forge in libraries.
    exit /b 1
)
echo [2/7] .minecraft  : %MC%

set "LIB=%MC%\libraries"

rem ---------- 3. pick library versions (newest complete Forge first) ----------
set "FORGEVER="
for /f "delims=" %%V in ('dir /b /ad /o-d "%LIB%\net\minecraftforge\forge\1.20.1-47.*" 2^>nul') do (
    if not defined FORGEVER call :probe_forge "%%V"
)
if not defined FORGEVER (
    echo [E] No complete Forge 1.20.1-47.x found in libraries.
    exit /b 1
)

set "EBVER="
for /f "delims=" %%V in ('dir /b /ad /o-d "%LIB%\net\minecraftforge\eventbus\6.*" 2^>nul') do (
    if not defined EBVER if exist "%LIB%\net\minecraftforge\eventbus\%%V\eventbus-%%V.jar" set "EBVER=%%V"
)
set "L4VER="
for /f "delims=" %%V in ('dir /b /ad /o-d "%LIB%\org\apache\logging\log4j\log4j-api\2.*" 2^>nul') do (
    if not defined L4VER if exist "%LIB%\org\apache\logging\log4j\log4j-api\%%V\log4j-api-%%V.jar" set "L4VER=%%V"
)

if not defined EBVER (
    echo [E] No eventbus jar found in libraries.
    exit /b 1
)
if not defined L4VER (
    echo [E] No log4j-api jar found in libraries.
    exit /b 1
)
echo [3/7] forge      : %FORGEVER%   (eventbus %EBVER%, log4j %L4VER%)

set "CP="
for %%J in (
    "%LIB%\net\minecraftforge\forge\%FORGEVER%\forge-%FORGEVER%-universal.jar"
    "%LIB%\net\minecraftforge\javafmllanguage\%FORGEVER%\javafmllanguage-%FORGEVER%.jar"
    "%LIB%\net\minecraftforge\fmlloader\%FORGEVER%\fmlloader-%FORGEVER%.jar"
    "%LIB%\net\minecraftforge\fmlcore\%FORGEVER%\fmlcore-%FORGEVER%.jar"
    "%LIB%\net\minecraftforge\eventbus\%EBVER%\eventbus-%EBVER%.jar"
    "%LIB%\org\apache\logging\log4j\log4j-api\%L4VER%\log4j-api-%L4VER%.jar"
) do (
    if not exist "%%~J" (
        echo [E] missing library: %%~J
        exit /b 1
    )
    if defined CP (set "CP=!CP!;%%~J") else (set "CP=%%~J")
)

rem ---------- 4. compile ----------
echo [4/7] compiling sources ...
if exist "%WORK%\classes" rmdir /s /q "%WORK%\classes"
mkdir "%WORK%\classes" 2>nul
dir /s /b "%ROOT%\src\main\java\*.java" > "%WORK%\sources.txt"
"%JDK%\bin\javac.exe" -encoding UTF-8 --release 17 -cp "%CP%" -d "%WORK%\classes" "@%WORK%\sources.txt"
if errorlevel 1 (
    echo [E] compile failed.
    exit /b 1
)
if not exist "%WORK%\classes\cn\jikuai\ocleuninstall\OcleUninstallCrash.class" (
    echo [E] compile produced no classes.
    exit /b 1
)
echo       classes ok

rem ---------- 5. resources ----------
echo [5/7] adding resources ...
mkdir "%WORK%\classes\META-INF" 2>nul
copy /y "%ROOT%\src\main\resources\META-INF\mods.toml" "%WORK%\classes\META-INF\mods.toml" >nul
copy /y "%ROOT%\src\main\resources\pack.mcmeta" "%WORK%\classes\pack.mcmeta" >nul
copy /y "%ROOT%\src\main\resources\icon.png" "%WORK%\classes\icon.png" >nul
xcopy /E /I /Y /Q "%ROOT%\src\main\resources\assets" "%WORK%\classes\assets" >nul
if not exist "%WORK%\classes\assets\ocle_uninstall_crash\lang\en_us.json" (
    echo [E] language resources were not copied.
    exit /b 1
)
echo       resources ok

rem ---------- 6. manifest ----------
set "MODID="
set "MODVER="
for /f "tokens=1,2 delims==" %%A in ('findstr /b /c:"mod_id=" "%ROOT%\gradle.properties"') do set "MODID=%%B"
for /f "tokens=1,2 delims==" %%A in ('findstr /b /c:"mod_version=" "%ROOT%\gradle.properties"') do set "MODVER=%%B"
if not defined MODID set "MODID=ocle_uninstall_crash"
if not defined MODVER set "MODVER=1.0.0"

(
    echo Manifest-Version: 1.0
    echo Specification-Title: %MODID%
    echo Specification-Vendor: Jikuai
    echo Specification-Version: 1
    echo Implementation-Title: %MODID%
    echo Implementation-Version: %MODVER%
    echo Implementation-Vendor: Jikuai
) > "%WORK%\MANIFEST.MF"

rem ---------- 7. package ----------
echo [6/7] packaging ...
if not exist "%DIST%" mkdir "%DIST%"
set "JARFILE=%DIST%\%MODID%-%MODVER%.jar"
del /q "%JARFILE%" 2>nul
"%JDK%\bin\jar.exe" --create --file "%JARFILE%" --manifest "%WORK%\MANIFEST.MF" -C "%WORK%\classes" .
if errorlevel 1 (
    echo [E] jar failed.
    exit /b 1
)

rem A real build is well above 30 KB; anything smaller means the archive is broken.
for %%S in ("%JARFILE%") do set "JARSIZE=%%~zS"
if not defined JARSIZE set "JARSIZE=0"
if %JARSIZE% LSS 30000 (
    echo [E] jar looks broken: %JARSIZE% bytes, expected more than 30000.
    exit /b 1
)

"%JDK%\bin\jar.exe" --list --file "%JARFILE%" >nul 2>nul
if errorlevel 1 (
    echo [E] jar is not a readable archive.
    exit /b 1
)

echo.
echo BUILD OK: %JARFILE%
echo size    : %JARSIZE% bytes
echo.
echo Contents:
"%JDK%\bin\jar.exe" --list --file "%JARFILE%"
exit /b 0

rem ---------- helpers ----------
:probe_forge
set "CAND=%~1"
if not exist "%LIB%\net\minecraftforge\forge\%CAND%\forge-%CAND%-universal.jar" exit /b 0
if not exist "%LIB%\net\minecraftforge\javafmllanguage\%CAND%\javafmllanguage-%CAND%.jar" exit /b 0
if not exist "%LIB%\net\minecraftforge\fmlloader\%CAND%\fmlloader-%CAND%.jar" exit /b 0
if not exist "%LIB%\net\minecraftforge\fmlcore\%CAND%\fmlcore-%CAND%.jar" exit /b 0
set "FORGEVER=%CAND%"
exit /b 0
