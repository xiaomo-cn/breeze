#!/bin/bash
# ============================================================
# 通知功能测试脚本
# 用法: ./scripts/test-notifications.sh [BASE_URL]
# 默认 BASE_URL=http://localhost:8080
#
# 前提: 后端服务已启动
# ============================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
API="$BASE_URL/api/v1"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}PASS${NC} $*"; }
fail() { echo -e "${RED}FAIL${NC} $*"; exit 1; }
info() { echo -e "${YELLOW}INFO${NC} $*"; }

# ---- 工具函数 ----
json_get() {
  # 从 JSON 中提取字段值 (简单场景，不用 jq)
  python3 -c "import sys,json; print(json.load(sys.stdin).get('$1',''))" 2>/dev/null || echo ""
}

api() {
  local method="$1" url="$2" token="$3" body="$4"
  local cmd=(curl -s -o /tmp/resp.txt -w "%{http_code}" -X "$method" "$url")
  if [ -n "$token" ]; then
    cmd+=(-H "Authorization: Bearer $token")
  fi
  cmd+=(-H "Content-Type: application/json")
  if [ -n "$body" ]; then
    cmd+=(-d "$body")
  fi
  local code=$("${cmd[@]}")
  if [ "$code" -ge 400 ]; then
    echo "HTTP $code: $(cat /tmp/resp.txt)" >&2
    return 1
  fi
  echo "$code"
  cat /tmp/resp.txt
}

# ---- 步骤 1: 注册/登录两个测试用户 ----
info "步骤 1: 准备测试用户..."

# 用时间戳生成唯一用户名避免冲突
TS=$(date +%s)
USER_A="notif-tester-a-$TS"
USER_B="notif-tester-b-$TS"
PASS="test123456"

# 注册用户 A
HTTP_A=$(curl -s -o /tmp/reg_a.json -w "%{http_code}" -X POST "$API/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER_A\",\"password\":\"$PASS\",\"displayName\":\"Tester A\"}")
if [ "$HTTP_A" -eq 200 ] || [ "$HTTP_A" -eq 201 ]; then
  TOKEN_A=$(python3 -c "import json; print(json.load(open('/tmp/reg_a.json')).get('accessToken',''))" 2>/dev/null || echo "")
  USER_A_ID=$(python3 -c "import json; d=json.load(open('/tmp/reg_a.json')); print(d.get('user',{}).get('id','') or d.get('id',''))" 2>/dev/null || echo "")
  pass "用户 A 注册成功 (id=$USER_A_ID)"
elif [ "$HTTP_A" -eq 400 ]; then
  # 可能已存在，尝试登录
  HTTP_A=$(curl -s -o /tmp/reg_a.json -w "%{http_code}" -X POST "$API/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USER_A\",\"password\":\"$PASS\"}")
  TOKEN_A=$(python3 -c "import json; print(json.load(open('/tmp/reg_a.json')).get('accessToken',''))" 2>/dev/null || echo "")
  USER_A_ID=$(python3 -c "import json; d=json.load(open('/tmp/reg_a.json')); print(d.get('user',{}).get('id','') or d.get('id',''))" 2>/dev/null || echo "")
  pass "用户 A 登录成功"
else
  fail "用户 A 注册/登录失败: HTTP $HTTP_A"
fi

# 注册用户 B
HTTP_B=$(curl -s -o /tmp/reg_b.json -w "%{http_code}" -X POST "$API/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER_B\",\"password\":\"$PASS\",\"displayName\":\"Tester B\"}")
if [ "$HTTP_B" -eq 200 ] || [ "$HTTP_B" -eq 201 ]; then
  TOKEN_B=$(python3 -c "import json; print(json.load(open('/tmp/reg_b.json')).get('accessToken',''))" 2>/dev/null || echo "")
  USER_B_ID=$(python3 -c "import json; d=json.load(open('/tmp/reg_b.json')); print(d.get('user',{}).get('id','') or d.get('id',''))" 2>/dev/null || echo "")
  pass "用户 B 注册成功 (id=$USER_B_ID)"
elif [ "$HTTP_B" -eq 400 ]; then
  HTTP_B=$(curl -s -o /tmp/reg_b.json -w "%{http_code}" -X POST "$API/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USER_B\",\"password\":\"$PASS\"}")
  TOKEN_B=$(python3 -c "import json; print(json.load(open('/tmp/reg_b.json')).get('accessToken',''))" 2>/dev/null || echo "")
  USER_B_ID=$(python3 -c "import json; d=json.load(open('/tmp/reg_b.json')); print(d.get('user',{}).get('id','') or d.get('id',''))" 2>/dev/null || echo "")
  pass "用户 B 登录成功"
else
  fail "用户 B 注册/登录失败: HTTP $HTTP_B"
fi

# ---- 步骤 2: 用户 A 创建项目 ----
info "步骤 2: 用户 A 创建项目..."
PROJ=$(curl -s -X POST "$API/projects" \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"name":"通知测试项目","key":"NT","description":"用于测试通知功能"}')
PROJ_ID=$(echo "$PROJ" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
if [ -z "$PROJ_ID" ] || [ "$PROJ_ID" = "None" ]; then
  fail "项目创建失败: $PROJ"
fi
pass "项目创建成功 (id=$PROJ_ID)"

# ---- 步骤 3: 用户 A 添加用户 B 为项目成员 ----
info "步骤 3: 添加用户 B 为项目成员..."
HTTP_ADD=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API/projects/$PROJ_ID/members" \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"userId\":$USER_B_ID,\"role\":\"member\"}")
if [ "$HTTP_ADD" -ge 400 ]; then
  # 可能已经添加了，继续
  info "添加成员返回 HTTP $HTTP_ADD (可能已存在，继续)"
else
  pass "用户 B 已添加为项目成员"
fi

# ---- 步骤 4: 检查用户 B 当前未读通知数 ----
info "步骤 4: 检查用户 B 初始未读通知数..."
BEFORE=$(curl -s "$API/notifications/unread-count" \
  -H "Authorization: Bearer $TOKEN_B")
BEFORE_COUNT=$(echo "$BEFORE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',-1))" 2>/dev/null || echo "-1")
info "用户 B 初始未读通知数: $BEFORE_COUNT"

# ---- 步骤 5: 用户 A 创建任务，指定用户 B 为指派人 ----
info "步骤 5: 用户 A 创建任务并分配给用户 B..."
TASK=$(curl -s -X POST "$API/projects/$PROJ_ID/tasks" \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"测试通知任务\",\"description\":\"验证通知功能\",\"priority\":\"high\",\"assigneeId\":$USER_B_ID}")
TASK_ID=$(echo "$TASK" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
TASK_KEY=$(echo "$TASK" | python3 -c "import sys,json; print(json.load(sys.stdin).get('key',''))" 2>/dev/null || echo "")
if [ -z "$TASK_ID" ] || [ "$TASK_ID" = "None" ]; then
  fail "任务创建失败: $TASK"
fi
pass "任务创建成功 (id=$TASK_ID, key=$TASK_KEY)"

# ---- 步骤 6: 验证用户 B 收到了通知 ----
info "步骤 6: 验证用户 B 收到任务分配通知..."
sleep 1  # 等待事务提交
AFTER=$(curl -s "$API/notifications/unread-count" \
  -H "Authorization: Bearer $TOKEN_B")
AFTER_COUNT=$(echo "$AFTER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',-1))" 2>/dev/null || echo "-1")

if [ "$AFTER_COUNT" -gt "$BEFORE_COUNT" ]; then
  pass "用户 B 未读通知数: $BEFORE_COUNT -> $AFTER_COUNT (增加了!)"
else
  fail "用户 B 未读通知数没有增加: $BEFORE_COUNT -> $AFTER_COUNT"
fi

# ---- 步骤 7: 查看通知内容 ----
info "步骤 7: 查看用户 B 的通知列表..."
NOTIF_LIST=$(curl -s "$API/notifications?page=1&size=5" \
  -H "Authorization: Bearer $TOKEN_B")
echo "$NOTIF_LIST" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for item in data.get('items', []):
    print(f\"  [{item['id']}] {item['type']}: {item['title']} (read={item['isRead']})\")
" 2>/dev/null || echo "  (无法解析通知列表)"

# ---- 步骤 8: 用户 A 对任务添加评论 ----
info "步骤 8: 用户 A 对任务添加评论..."
COMMENT=$(curl -s -X POST "$API/tasks/$TASK_ID/comments" \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"content\":\"请尽快处理这个任务 [@Tester B](/users/$USER_B_ID)\"}")

# ---- 步骤 9: 验证用户 B 收到评论通知 ----
info "步骤 9: 验证用户 B 收到评论 + @ 提及通知..."
AFTER2=$(curl -s "$API/notifications/unread-count" \
  -H "Authorization: Bearer $TOKEN_B")
AFTER2_COUNT=$(echo "$AFTER2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',-1))" 2>/dev/null || echo "-1")

if [ "$AFTER2_COUNT" -gt "$AFTER_COUNT" ]; then
  pass "用户 B 未读通知数: $AFTER_COUNT -> $AFTER2_COUNT (评论通知生效!)"
else
  fail "用户 B 未读通知数没有因评论增加: $AFTER_COUNT -> $AFTER2_COUNT"
fi

# ---- 步骤 10: 用户 B 标记全部已读 ----
info "步骤 10: 用户 B 标记全部已读..."
curl -s -X PATCH "$API/notifications/read-all" \
  -H "Authorization: Bearer $TOKEN_B" > /dev/null

FINAL=$(curl -s "$API/notifications/unread-count" \
  -H "Authorization: Bearer $TOKEN_B")
FINAL_COUNT=$(echo "$FINAL" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',-1))" 2>/dev/null || echo "-1")
if [ "$FINAL_COUNT" -eq 0 ]; then
  pass "标记已读后未读数归零"
else
  fail "标记已读后未读数应为 0，实际: $FINAL_COUNT"
fi

# ---- 完成 ----
echo ""
echo "========================================"
echo -e "${GREEN}所有测试通过!${NC}"
echo "========================================"
echo ""
echo "测试摘要:"
echo "  用户 A: $USER_A (id=$USER_A_ID)"
echo "  用户 B: $USER_B (id=$USER_B_ID)"
echo "  项目 ID: $PROJ_ID"
echo "  任务: $TASK_KEY (id=$TASK_ID)"
echo ""
echo "注意: SSE 实时推送需要用浏览器登录两个用户来验证。"
echo "此脚本已覆盖 REST API 层面的通知创建/查询/已读全流程。"
