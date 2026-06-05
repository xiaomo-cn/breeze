#!/usr/bin/env bash
# ==========================================
# Breeze 一键部署脚本
# 用法:
#   ./start.sh              # 交互式部署
#   ./start.sh --infra      # 使用内置 PostgreSQL + Redis + MinIO
#   ./start.sh --external   # 使用外部数据库
#   ./start.sh --help       # 查看帮助
# ==========================================
set -e

# 检测是否用 bash 运行（sh 不支持本脚本的部分语法）
if [ -z "${BASH_VERSION:-}" ]; then
  echo "请使用 bash 运行此脚本：bash start.sh"
  exit 1
fi

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_banner() {
  echo ""
  echo -e "${CYAN}=========================================${NC}"
  echo -e "${CYAN}  Breeze 项目管理系统 — Docker 部署${NC}"
  echo -e "${CYAN}=========================================${NC}"
  echo ""
}

print_help() {
  echo "用法: ./start.sh [选项]"
  echo ""
  echo "选项:"
  echo "  --infra      使用内置 PostgreSQL + Redis + MinIO（默认）"
  echo "  --external   使用外部已有的数据库服务"
  echo "  --build      强制重新构建镜像"
  echo "  --help       显示此帮助信息"
  echo ""
  echo "示例:"
  echo "  ./start.sh                  # 交互式选择"
  echo "  ./start.sh --infra          # 一键启动全部服务"
  echo "  ./start.sh --infra --build  # 重新构建并启动"
}

# 检查依赖
check_deps() {
  if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: 未安装 Docker，请先安装 Docker${NC}"
    echo "下载: https://docs.docker.com/get-docker/"
    exit 1
  fi

  if ! docker compose version &> /dev/null; then
    echo -e "${RED}错误: Docker Compose 不可用，请安装 Docker Compose${NC}"
    exit 1
  fi
}

# 设置 .env 文件
setup_env() {
  local mode=$1

  if [ -f .env ]; then
    echo -e "${GREEN}✓${NC} 已找到 .env 配置文件"
    return
  fi

  echo -e "${YELLOW}未找到 .env 文件，正在从 .env.example 创建...${NC}"
  cp .env.example .env

  if [ "$mode" = "external" ]; then
    echo ""
    echo -e "${CYAN}--- 配置外部数据库 ---${NC}"

    read -p "PostgreSQL 主机地址 [localhost]: " DB_HOST
    DB_HOST=${DB_HOST:-localhost}
    read -p "PostgreSQL 端口 [5432]: " DB_PORT_VAL
    DB_PORT_VAL=${DB_PORT_VAL:-5432}
    read -p "Redis 主机地址 [localhost]: " REDIS_HOST
    REDIS_HOST=${REDIS_HOST:-localhost}
    read -p "Redis 端口 [6379]: " REDIS_PORT_VAL
    REDIS_PORT_VAL=${REDIS_PORT_VAL:-6379}

    # 跨平台 sed 兼容
    if [[ "$OSTYPE" == "darwin"* ]]; then
      sed -i '' "s/^DB_HOST=.*/DB_HOST=${DB_HOST}/" .env
      sed -i '' "s/^DB_PORT=.*/DB_PORT=${DB_PORT_VAL}/" .env
      sed -i '' "s/^REDIS_HOST=.*/REDIS_HOST=${REDIS_HOST}/" .env
      sed -i '' "s/^REDIS_PORT=.*/REDIS_PORT=${REDIS_PORT_VAL}/" .env
    else
      sed -i "s/^DB_HOST=.*/DB_HOST=${DB_HOST}/" .env
      sed -i "s/^DB_PORT=.*/DB_PORT=${DB_PORT_VAL}/" .env
      sed -i "s/^REDIS_HOST=.*/REDIS_HOST=${REDIS_HOST}/" .env
      sed -i "s/^REDIS_PORT=.*/REDIS_PORT=${REDIS_PORT_VAL}/" .env
    fi
    echo -e "${GREEN}✓${NC} 已配置外部数据库"
  else
    echo -e "${GREEN}✓${NC} 已创建 .env（使用内置数据库）"
  fi

  echo ""
  echo -e "${YELLOW}⚠ 重要：请编辑 .env 文件，填入你的 API Key：${NC}"
  echo -e "  ${CYAN}DEEPSEEK_API_KEY${NC}  — AI 对话功能必需"
  echo -e "  ${CYAN}EMBEDDING_API_KEY${NC} — 语义搜索功能必需"
  echo ""
  read -p "按 Enter 继续部署，或 Ctrl+C 取消..."
}

# 检查 API Key
check_api_keys() {
  DEEPSEEK_API_KEY=$(grep -E '^DEEPSEEK_API_KEY=' .env 2>/dev/null | cut -d= -f2-)
  EMBEDDING_API_KEY=$(grep -E '^EMBEDDING_API_KEY=' .env 2>/dev/null | cut -d= -f2-)

  if [ -z "$DEEPSEEK_API_KEY" ] || [ "$DEEPSEEK_API_KEY" = "sk-your-key-here" ]; then
    echo -e "${YELLOW}⚠ 警告: DEEPSEEK_API_KEY 未设置，AI 功能将不可用${NC}"
  fi
  if [ -z "$EMBEDDING_API_KEY" ] || [ "$EMBEDDING_API_KEY" = "your-openai-api-key" ]; then
    echo -e "${YELLOW}⚠ 警告: EMBEDDING_API_KEY 未设置，语义搜索功能将不可用${NC}"
  fi
}

# 主流程
main() {
  print_banner
  check_deps

  local MODE="infra"
  local BUILD_FLAG=""

  # 解析参数
  while [[ $# -gt 0 ]]; do
    case $1 in
      --infra)    MODE="infra"; shift ;;
      --external) MODE="external"; shift ;;
      --build)    BUILD_FLAG="--build"; shift ;;
      --help)     print_help; exit 0 ;;
      *) echo -e "${RED}未知参数: $1${NC}"; print_help; exit 1 ;;
    esac
  done

  # 交互式选择模式
  if [ ! -f .env ] && [ $# -eq 0 ] && [ -z "$BUILD_FLAG" ]; then
    echo "请选择部署方式："
    echo "  [1] 使用内置服务（PostgreSQL + Redis + MinIO）→ 推荐"
    echo "  [2] 使用外部已有的数据库服务"
    echo ""
    read -p "请选择 [1/2]（默认 1）: " choice
    case ${choice:-1} in
      1) MODE="infra" ;;
      2) MODE="external" ;;
      *) MODE="infra" ;;
    esac
    echo ""
  fi

  setup_env "$MODE"

  # 检查 API Key
  check_api_keys

  # 构建并启动
  echo ""
  echo -e "${CYAN}--- 构建并启动服务 ---${NC}"

  if [ "$MODE" = "infra" ]; then
    echo -e "${GREEN}▶${NC} 启动内置数据库 + 应用服务..."
    docker compose --profile infra up -d $BUILD_FLAG
  else
    echo -e "${GREEN}▶${NC} 启动应用服务（使用外部数据库）..."
    docker compose up -d $BUILD_FLAG
  fi

  # 等待服务就绪
  echo ""
  echo -e "${CYAN}等待服务启动...${NC}"

  BACKEND_PORT=$(grep '^BACKEND_PORT=' .env 2>/dev/null | cut -d= -f2 || echo "8080")
  BACKEND_PORT=${BACKEND_PORT:-8080}
  FRONTEND_PORT=$(grep '^FRONTEND_PORT=' .env 2>/dev/null | cut -d= -f2 || echo "80")
  FRONTEND_PORT=${FRONTEND_PORT:-80}

  echo -n "等待后端就绪"
  for i in $(seq 1 90); do
    if curl -s -o /dev/null "http://localhost:${BACKEND_PORT}/actuator/health" 2>/dev/null; then
      echo ""
      echo -e "${GREEN}✓${NC} 后端已就绪"
      break
    fi
    echo -n "."
    sleep 2
  done

  # 完成
  echo ""
  echo -e "${CYAN}=========================================${NC}"
  echo -e "${GREEN}  部署完成!${NC}"
  echo -e "${CYAN}=========================================${NC}"
  echo ""
  echo -e "  前端页面:  ${GREEN}http://localhost:${FRONTEND_PORT}${NC}"
  echo -e "  后端 API:  ${GREEN}http://localhost:${BACKEND_PORT}${NC}"
  if [ "$MODE" = "infra" ]; then
    MINIO_CONSOLE_PORT=$(grep '^MINIO_CONSOLE_PORT=' .env 2>/dev/null | cut -d= -f2 || echo "9001")
    echo -e "  MinIO:     ${GREEN}http://localhost:${MINIO_CONSOLE_PORT:-9001}${NC}"
  fi
  echo ""
  echo -e "  查看日志:  ${CYAN}docker compose logs -f${NC}"
  echo -e "  停止服务:  ${CYAN}docker compose down${NC}"
  echo ""
}

main "$@"
