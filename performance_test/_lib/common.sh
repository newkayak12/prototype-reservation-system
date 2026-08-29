#!/usr/bin/env bash
# 전 시나리오 공용 설정. 각 run.sh에서 `source`한다.

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-verysecret}"
DB_NAME="${DB_NAME:-prototype_reservation}"
BASE_URL="${BASE_URL:-http://localhost:8081}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"

PERF_LIB="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PERF_ROOT="$(cd "$PERF_LIB/.." && pwd)"
REPO_ROOT="$(cd "$PERF_ROOT/.." && pwd)"

# 픽스처 이름 규약. seed.sh가 만들고 integrity.sh가 검사한다.
HOT_PREFIX="K6_HOT"        # 경합점(핫슬롯) 식당: K6_HOT_0001 ...
BG_NAME="K6_BG"            # 배경 트래픽 조회용 식당 (락 경합 없음)
USER_PREFIX="k6perf"
USER_PASSWORD="K6perf!2026"

mysql_q() { mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" \
              --default-character-set=utf8mb4 -N -B "$DB_NAME" "$@" 2>/dev/null; }

redis_cli() { docker exec "$(docker ps -qf name=redis | head -1)" redis-cli "$@" 2>/dev/null; }

# 이전 런의 락/세마포어/대기열 잔재를 지운다.
# 세마포어 TTL이 10분이라 이걸 안 하면 다음 런이 오염된다.
flush_redis() { redis_cli FLUSHALL >/dev/null || echo "  (redis flush 실패 - 무시)" >&2; }

log() { printf '%s\n' "$*"; }
hr()  { printf '%s\n' "--------------------------------------------------"; }
