#!/bin/bash
set -euo pipefail

BASE_URL="${1:-http://localhost:9090}"
ENDPOINT="$BASE_URL/dev/jobs/run-all"
TIMEOUT=300

echo "[SMOKE TEST] Hitting $ENDPOINT ..."

HTTP_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST --max-time "$TIMEOUT" "$ENDPOINT" 2>&1) || {
    echo "[SMOKE TEST] FAIL - curl error (is the app running at $BASE_URL?)"
    exit 1
}

HTTP_BODY=$(echo "$HTTP_RESPONSE" | sed '$d')
HTTP_CODE=$(echo "$HTTP_RESPONSE" | tail -1)

echo "[SMOKE TEST] HTTP $HTTP_CODE"
echo "$HTTP_BODY" | python3 -m json.tool 2>/dev/null || echo "$HTTP_BODY"

STATUS=$(echo "$HTTP_BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" 2>/dev/null)

if [ "$STATUS" = "ok" ]; then
    echo "[SMOKE TEST] PASS - all jobs succeeded"
    exit 0
else
    echo "[SMOKE TEST] FAIL - status: $STATUS"
    exit 1
fi
