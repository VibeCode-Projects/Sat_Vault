@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   SatVault - Windows Quick Start
echo ============================================
echo.

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java is not installed or not in PATH.
    echo Please install Java 25 JDK and try again.
    pause
    exit /b 1
)

where docker >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not installed or not in PATH.
    echo Please install Docker Desktop and try again.
    pause
    exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Desktop is not running.
    echo Please start Docker Desktop, wait until it is ready, and re-run start.bat.
    pause
    exit /b 1
)

echo [1/3] Starting MySQL container...
docker compose up -d
if errorlevel 1 (
    echo [ERROR] Failed to start MySQL container.
    pause
    exit /b 1
)

echo [2/3] Waiting for MySQL to become healthy...
set /a COUNT=0
:wait_loop
set "STATUS="
for /f "delims=" %%i in ('docker inspect -f "{{.State.Health.Status}}" satvault-mysql 2^>nul') do set "STATUS=%%i"
if /i "!STATUS!"=="healthy" goto mysql_ready
set /a COUNT+=1
if !COUNT! geq 30 (
    echo [WARNING] MySQL health check timed out, attempting to start app anyway...
    goto mysql_ready
)
timeout /t 2 /nobreak >nul
goto wait_loop

:mysql_ready
echo MySQL is ready!
echo [3/3] Starting SatVault on http://localhost:8080 ...
echo (First run will download Maven dependencies and may take a minute.)
echo.
call mvnw.cmd spring-boot:run
