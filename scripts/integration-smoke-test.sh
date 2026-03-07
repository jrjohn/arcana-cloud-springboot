#!/usr/bin/env bash
# ============================================================
# Integration Smoke Test — Layered Deployment (HTTP & gRPC)
# Tests the full register → login → authenticated call cycle.
# Works for both HTTP and gRPC modes: inter-service protocol
# is internal; the controller always exposes HTTP REST.
#
# Usage:
#   ./scripts/integration-smoke-test.sh [BASE_URL] [PROTOCOL_LABEL]
#   ./scripts/integration-smoke-test.sh http://localhost:8090 grpc
# ============================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8090}"
LABEL="${2:-http}"
MAX_WAIT="${3:-120}"   # seconds to wait for controller health

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Integration Smoke Test — Layered + ${LABEL^^}              ║"
echo "║  Target: ${BASE_URL}"
echo "╚══════════════════════════════════════════════════════╝"

# ── 1. Wait for controller health ───────────────────────────
echo ""
echo "▶ [1/4] Waiting for controller at ${BASE_URL}/actuator/health ..."
ELAPSED=0
until curl -sf "${BASE_URL}/actuator/health" > /dev/null 2>&1; do
    if [ "$ELAPSED" -ge "$MAX_WAIT" ]; then
        echo "✗ Controller not healthy after ${MAX_WAIT}s — aborting"
        exit 1
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
    echo "  ... waiting (${ELAPSED}s)"
done
HEALTH=$(curl -sf "${BASE_URL}/actuator/health")
echo "  ✓ Controller healthy: $HEALTH"

# ── 2. Register a unique test user ──────────────────────────
echo ""
echo "▶ [2/4] Registering test user ..."
TS=$(date +%s%3N)
USERNAME="ci_${LABEL}_${TS}"
EMAIL="ci_${LABEL}_${TS}@test.com"
PASSWORD="CiTest123!"

REG_BODY=$(cat <<EOF
{
  "username": "${USERNAME}",
  "email": "${EMAIL}",
  "password": "${PASSWORD}",
  "confirmPassword": "${PASSWORD}",
  "firstName": "CI",
  "lastName": "${LABEL^^}"
}
EOF
)

REG_HTTP_CODE=$(curl -s -o /tmp/smoke-reg.json -w "%{http_code}" \
    -X POST "${BASE_URL}/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d "${REG_BODY}" 2>/dev/null || echo "000")

if [ "${REG_HTTP_CODE}" = "000" ]; then
    echo "  ✗ Register request failed (connection error)"
    exit 1
fi

echo "  HTTP ${REG_HTTP_CODE} — $(cat /tmp/smoke-reg.json | python3 -c 'import json,sys; d=json.load(sys.stdin); dd=d.get('data',d); print(f"token={dd.get('accessToken','?')[:20]}...")' 2>/dev/null || cat /tmp/smoke-reg.json | head -c 200)"

if [ "${REG_HTTP_CODE}" != "200" ] && [ "${REG_HTTP_CODE}" != "201" ]; then
    echo "  ✗ Registration failed — HTTP ${REG_HTTP_CODE}"
    cat /tmp/smoke-reg.json
    exit 1
fi
echo "  ✓ User '${USERNAME}' registered"

# ── 3. Login ────────────────────────────────────────────────
echo ""
echo "▶ [3/4] Logging in ..."
LOGIN_BODY=$(cat <<EOF
{
  "usernameOrEmail": "${USERNAME}",
  "password": "${PASSWORD}"
}
EOF
)

LOGIN_HTTP_CODE=$(curl -s -o /tmp/smoke-login.json -w "%{http_code}" \
    -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "${LOGIN_BODY}" 2>/dev/null || echo "000")

if [ "${LOGIN_HTTP_CODE}" != "200" ]; then
    echo "  ✗ Login failed — HTTP ${LOGIN_HTTP_CODE}"
    cat /tmp/smoke-login.json 2>/dev/null || true
    exit 1
fi

TOKEN=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('data',d).get('accessToken',''))" < /tmp/smoke-login.json)
if [ -z "${TOKEN}" ]; then
    echo "  ✗ No accessToken in login response"
    cat /tmp/smoke-login.json
    exit 1
fi
echo "  ✓ Login OK — token: ${TOKEN:0:30}..."

# ── 4. Authenticated call ────────────────────────────────────
# Use /api/v1/users/{id} (owner-accessible) instead of /api/v1/users (ADMIN-only)
USER_ID=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('data',{}).get('user',{}).get('id','1'))" < /tmp/smoke-reg.json 2>/dev/null || echo "1")
echo ""
echo "▶ [4/4] Calling authenticated endpoint (GET /api/v1/users/${USER_ID}) ..."
AUTH_HTTP_CODE=$(curl -s -o /tmp/smoke-users.json -w "%{http_code}" \
    "${BASE_URL}/api/v1/users/${USER_ID}" \
    -H "Authorization: Bearer ${TOKEN}" 2>/dev/null || echo "000")

if [ "${AUTH_HTTP_CODE}" != "200" ]; then
    echo "  ✗ Authenticated call failed — HTTP ${AUTH_HTTP_CODE}"
    cat /tmp/smoke-users.json 2>/dev/null || true
    exit 1
fi

USER_NAME=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('data',{}).get('username','?'))" < /tmp/smoke-users.json 2>/dev/null || echo "?")
echo "  ✓ Auth call OK — user: ${USER_NAME}"

# ── Summary ─────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  ✅ ALL SMOKE TESTS PASSED — Layered + ${LABEL^^}           ║"
echo "║  Protocol validated through full register→login→api  ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
