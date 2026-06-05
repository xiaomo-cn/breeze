@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

:: ==========================================
:: Breeze 一键部署脚本 (Windows)
:: 用法:
::   start.bat             交互式部署
::   start.bat --infra     使用内置数据库（默认）
::   start.bat --external  使用外部数据库
::   start.bat --help      查看帮助
:: ==========================================

echo.
echo =========================================
echo   Breeze 项目管理系统 — Docker 部署
echo =========================================
echo.

:: 检查模式参数
set MODE=infra
set BUILD_FLAG=

:parse_args
if "%~1"=="" goto :check_deps
if "%~1"=="--infra" (
    set MODE=infra
    shift
    goto :parse_args
)
if "%~1"=="--external" (
    set MODE=external
    shift
    goto :parse_args
)
if "%~1"=="--build" (
    set BUILD_FLAG=--build
    shift
    goto :parse_args
)
if "%~1"=="--help" (
    echo 用法: start.bat [选项]
    echo.
    echo    --infra      使用内置 PostgreSQL + Redis + MinIO（默认）
    echo    --external   使用外部已有的数据库服务
    echo    --build      强制重新构建镜像
    echo    --help       显示此帮助
    exit /b 0
)
echo [错误] 未知参数: %~1
exit /b 1

:check_deps
:: 检查 Docker
where docker >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [错误] 未安装 Docker，请先安装 Docker Desktop
    echo 下载: https://docs.docker.com/desktop/setup/windows/
    pause
    exit /b 1
)

:: 检查 .env
if not exist .env (
    echo [提示] 未找到 .env 文件
    if not exist .env.example (
        echo [错误] .env.example 也不存在，无法继续
        pause
        exit /b 1
    )

    :: 交互式选择
    if "%MODE%"=="infra" (
        if "%BUILD_FLAG%"=="" (
            echo.
            echo 请选择部署方式:
            echo   [1] 使用内置服务（PostgreSQL + Redis + MinIO）— 推荐
            echo   [2] 使用外部已有的数据库服务
            echo.
            set /p choice="请选择 [1/2]（默认 1）: "
            if "!choice!"=="2" set MODE=external
        )
    )

    echo.
    copy .env.example .env > nul
    echo [OK] 已从 .env.example 创建 .env

    if "!MODE!"=="external" (
        echo.
        echo --- 配置外部数据库 ---
        set /p DB_HOST_INPUT="PostgreSQL 主机地址 [localhost]: "
        if "!DB_HOST_INPUT!"=="" set DB_HOST_INPUT=localhost
        set /p DB_PORT_INPUT="PostgreSQL 端口 [5432]: "
        if "!DB_PORT_INPUT!"=="" set DB_PORT_INPUT=5432
        set /p REDIS_HOST_INPUT="Redis 主机地址 [localhost]: "
        if "!REDIS_HOST_INPUT!"=="" set REDIS_HOST_INPUT=localhost
        set /p REDIS_PORT_INPUT="Redis 端口 [6379]: "
        if "!REDIS_PORT_INPUT!"=="" set REDIS_PORT_INPUT=6379

        :: 使用 PowerShell 替换（Windows 原生 sed 不可靠）
        powershell -Command "(Get-Content .env) -replace '^DB_HOST=.*', 'DB_HOST=!DB_HOST_INPUT!' | Set-Content .env"
        powershell -Command "(Get-Content .env) -replace '^DB_PORT=.*', 'DB_PORT=!DB_PORT_INPUT!' | Set-Content .env"
        powershell -Command "(Get-Content .env) -replace '^REDIS_HOST=.*', 'REDIS_HOST=!REDIS_HOST_INPUT!' | Set-Content .env"
        powershell -Command "(Get-Content .env) -replace '^REDIS_PORT=.*', 'REDIS_PORT=!REDIS_PORT_INPUT!' | Set-Content .env"
        echo [OK] 已配置外部数据库
    )

    echo.
    echo ⚠ 重要：请编辑 .env 文件，填入你的 API Key:
    echo   DEEPSEEK_API_KEY  — AI 对话功能必需
    echo   EMBEDDING_API_KEY — 语义搜索功能必需
    echo.
    pause
)

:: 启动服务
echo.
echo --- 启动服务 ---
if "%MODE%"=="infra" (
    echo [启动] 内置数据库 + 应用服务...
    docker compose --profile infra up -d %BUILD_FLAG%
) else (
    echo [启动] 应用服务（使用外部数据库）...
    docker compose up -d %BUILD_FLAG%
)

if %ERRORLEVEL% neq 0 (
    echo [错误] 启动失败，请查看上方日志
    pause
    exit /b 1
)

:: 等待后端就绪
echo.
echo 等待后端启动...

for /f "tokens=2 delims==" %%a in ('findstr "^BACKEND_PORT=" .env 2^>nul') do set BACKEND_PORT=%%a
if "%BACKEND_PORT%"=="" set BACKEND_PORT=8080

echo 可通过以下地址访问:
echo.
echo   前端页面:  http://localhost:80
echo   后端 API:  http://localhost:%BACKEND_PORT%
if "%MODE%"=="infra" echo   MinIO:      http://localhost:9001
echo.
echo   查看日志:  docker compose logs -f
echo   停止服务:  docker compose down
echo.

echo 部署完成!
pause
