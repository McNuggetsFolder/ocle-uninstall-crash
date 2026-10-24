@echo off
chcp 65001 >nul
setlocal

rem Offline self-test: compiles ONLY the Forge-free core package and runs it.
rem Usage: double-click, or run from cmd.

set "ROOT=%~dp0.."
set "OUT=%~dp0out"

if defined JAVA_HOME (
    set "JDK=%JAVA_HOME%"
) else (
    set "JDK=C:\Program Files\Java\jdk-17"
)

if not exist "%JDK%\bin\javac.exe" (
    echo [ERROR] javac not found. Set JAVA_HOME to a JDK 17 installation.
    exit /b 1
)

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

echo [1/2] compiling core + selftest ...
"%JDK%\bin\javac.exe" -encoding UTF-8 -d "%OUT%" -sourcepath "%ROOT%\src\main\java" ^
    "%ROOT%\src\main\java\cn\jikuai\ocleuninstall\core\GuardConst.java" ^
    "%ROOT%\src\main\java\cn\jikuai\ocleuninstall\core\NameMatch.java" ^
    "%ROOT%\src\main\java\cn\jikuai\ocleuninstall\core\Evidence.java" ^
    "%ROOT%\src\main\java\cn\jikuai\ocleuninstall\core\Artifact.java" ^
    "%ROOT%\src\main\java\cn\jikuai\ocleuninstall\core\GunpackProbe.java" ^
    "%ROOT%\src\main\java\cn\jikuai\ocleuninstall\core\ArtifactScanner.java" ^
    "%ROOT%\src\main\java\cn\jikuai\ocleuninstall\core\ArtifactDeleter.java" ^
    "%~dp0OfflineSelfTest.java"
if errorlevel 1 goto :fail

echo [2/2] running selftest ...
rem report file is written as UTF-8 by the test itself, console may show GBK
"%JDK%\bin\java.exe" -cp "%OUT%" -Docle.report="%~dp0selftest-report.txt" OfflineSelfTest
if errorlevel 1 goto :fail

echo.
echo SELFTEST OK
exit /b 0

:fail
echo.
echo SELFTEST FAILED
exit /b 1
