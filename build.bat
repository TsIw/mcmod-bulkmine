@echo off
setlocal

rem ---------------------------------------------------------------------
rem  Builds BulkMine and puts the finished jar in dist\.
rem
rem    build.bat              run the game tests, then build
rem    build.bat --no-tests   build only
rem
rem  The version comes from mod_version in gradle.properties, so there is
rem  only ever one place to change it.
rem ---------------------------------------------------------------------

cd /d "%~dp0"

set "RUN_TESTS=1"
if /i "%~1"=="--no-tests" set "RUN_TESTS=0"
if /i "%~1"=="-n" set "RUN_TESTS=0"

set "MOD_VERSION="
for /f "usebackq tokens=1,* delims==" %%a in ("gradle.properties") do (
	if "%%a"=="mod_version" set "MOD_VERSION=%%b"
)
if not defined MOD_VERSION (
	echo [build] mod_version is missing from gradle.properties.
	exit /b 1
)

set "JAR=build\libs\bulkmine-%MOD_VERSION%.jar"
set "OUT=dist\bulkmine-%MOD_VERSION%.jar"

echo [build] BulkMine %MOD_VERSION%

if "%RUN_TESTS%"=="1" (
	echo [build] running game tests...
	call "%~dp0gradlew.bat" runGametest
	rem Tests gate the build on purpose: a jar that fails them should never
	rem reach dist\, because that is the folder people hand around.
	if errorlevel 1 (
		echo.
		echo [build] game tests FAILED -- dist\ was left untouched.
		exit /b 1
	)
)

echo [build] compiling...
call "%~dp0gradlew.bat" build
if errorlevel 1 (
	echo.
	echo [build] gradle build FAILED.
	exit /b 1
)

if not exist "%JAR%" (
	echo.
	echo [build] expected "%JAR%" but it is not there.
	echo [build] check that mod_version matches what gradle produced.
	exit /b 1
)

if not exist "dist" mkdir "dist"
copy /y "%JAR%" "%OUT%" >nul
if errorlevel 1 (
	echo.
	echo [build] could not write "%OUT%".
	exit /b 1
)

echo.
echo [build] done -- %OUT%
exit /b 0
