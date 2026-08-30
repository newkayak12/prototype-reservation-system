#!/usr/bin/env bash
# 유저 풀 프로비저닝 + 토큰 사전 발급.
#
# 회원가입 API를 POOL_SIZE번 호출하면 수 분이 걸리고, 측정 대상도 아니다(측정 대상은 예약 API).
# 계정 생성만 DB 벌크 INSERT로 우회하고, 로그인/토큰 발급은 실제 API를 쓴다.
#
# 매번 지우고 다시 넣는 이유: 반복 실행 중 로그인 실패가 누적돼 fail_count가 SignInPolicy
# 한계를 넘으면 계정이 잠긴다(DEACTIVATED). 그러면 다음 런의 setup()이 조용히 토큰을 못 받는다.
#
# 토큰은 tokens.json에 캐시한다. 개방형 시나리오는 초당 수천 요청을 쏘므로 매 런마다
# 수천 명을 로그인시키면 setup에만 수 분이 걸린다. 토큰 TTL 내에는 재사용한다.
#
#   POOL_SIZE      유저 수
#   FORCE_TOKENS=1 캐시 무시하고 토큰 재발급
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

POOL_SIZE="${POOL_SIZE:-2000}"
# 앱을 --security.jwt.properties.expire-time=31536000000 (1년) 으로 기동하므로
# 토큰이 스위트가 도는 내내 유효하다. 캐시 TTL을 그에 맞춰 1년으로 둔다.
#
# 원래 20분이었는데, 그것 때문에 S6가 통째로 날아갔다. 시나리오 사이마다 캐시가 만료돼
# 500명을 재로그인시켰고, S4의 42만 요청 스톰 직후라 앱이 아직 회복 중이어서
# 0/500 이 나왔다(다음 시도는 468/500). users.sh는 전량 성공을 요구하므로 exit 1,
# run.sh는 set -e 라 k6를 한 번도 못 돌리고 종료했다.
# 재로그인을 없애면 이 실패 모드 자체가 사라진다.
TOKEN_TTL_SECONDS="${TOKEN_TTL_SECONDS:-31536000}"

# 'K6perf!2026'을 Spring BCryptPasswordEncoder(strength 10)로 인코딩한 값.
# BCrypt는 해시에 salt가 박혀 있어 전원이 같은 해시 문자열을 써도 된다.
PASSWORD_HASH='$2a$10$XRUpNNPbgz/DSUtLZX4BxOxDSq/DU47QZ2N7X3wqea6TZANie.tkS'

need_users() {
  local actual
  actual="$(mysql_q -e "SELECT COUNT(*) FROM \`user\`
     WHERE login_id LIKE '${USER_PREFIX}%' AND user_status='ACTIVATED' AND fail_count=0;")"
  [ "${actual:-0}" -ne "$POOL_SIZE" ]
}

if need_users; then
  log "==> 유저 ${POOL_SIZE}명 프로비저닝 (벌크 INSERT)"
  python3 - "$PERF_LIB" "$POOL_SIZE" "$USER_PASSWORD" "$PASSWORD_HASH" "$USER_PREFIX" \
    <<'PY' > "$PERF_LIB/users.generated.sql"
import json, sys, uuid
lib, pool, password, pw_hash, prefix = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5]
width, CHUNK = max(4, len(str(pool))), 1000
print(f"DELETE FROM `user` WHERE login_id LIKE '{prefix}%';")
users, rows = [], []
for i in range(1, pool + 1):
    lid = f"{prefix}{str(i).zfill(width)}"
    users.append({"loginId": lid, "password": password})
    rows.append("('{id}','{l}','{p}','{l}@t.local','{l}','010{m}','USER',0,NULL,'ACTIVATED',0)"
                .format(id=str(uuid.uuid4()), l=lid, p=pw_hash, m=str(i).zfill(8)))
cols = ("INSERT INTO `user` (id, login_id, password, email, nickname, mobile, role, "
        "fail_count, locked_datetime, user_status, is_need_to_change_password) VALUES")
for s in range(0, len(rows), CHUNK):
    print(cols); print(",\n".join(rows[s:s+CHUNK]) + ";")
with open(f"{lib}/users.json", "w") as f: json.dump(users, f)
print(f"-- {len(users)} users", file=sys.stderr)
PY
  mysql_q < "$PERF_LIB/users.generated.sql"
  rm -f "$PERF_LIB/tokens.json"    # 유저가 바뀌었으면 토큰 캐시 무효
else
  log "==> 유저 ${POOL_SIZE}명 이미 준비됨 (건너뜀)"
fi

# --- 토큰 캐시 ---------------------------------------------------------------
TOKENS="$PERF_LIB/tokens.json"
cache_fresh() {
  [ "${FORCE_TOKENS:-0}" != "1" ] && [ -s "$TOKENS" ] || return 1
  local age count
  age=$(( $(date +%s) - $(stat -f %m "$TOKENS" 2>/dev/null || stat -c %Y "$TOKENS") ))
  count="$(python3 -c "import json,sys;print(len(json.load(open(sys.argv[1]))))" "$TOKENS" 2>/dev/null || echo 0)"
  [ "$age" -lt "$TOKEN_TTL_SECONDS" ] && [ "$count" -ge "$POOL_SIZE" ]
}

if cache_fresh; then
  log "==> 토큰 캐시 재사용 ($TOKENS)"
else
  log "==> 토큰 ${POOL_SIZE}개 발급 (병렬 로그인)"
  python3 - "$PERF_LIB" "$POOL_SIZE" "$BASE_URL" <<'PY'
import json, sys, time, urllib.request
from concurrent.futures import ThreadPoolExecutor

lib, pool, base = sys.argv[1], int(sys.argv[2]), sys.argv[3]
users = json.load(open(f"{lib}/users.json"))[:pool]

def login(u):
    req = urllib.request.Request(
        f"{base}/api/v1/user/sign-in", method="PUT",
        data=json.dumps({"loginId": u["loginId"], "password": u["password"]}).encode(),
        headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read()).get("accessToken")
    except Exception:
        return None

# 실패한 유저만 골라 다시 시도한다. 전량 성공을 요구하면서 한 번만 시도하면,
# 앱이 잠깐 느린 것만으로 시나리오 하나가 통째로 날아간다(실제로 S6가 그렇게 죽었다).
# 부하를 낮춰가며(32 → 16 → 8) 재시도하는 이유: 실패 원인이 앱 포화라면
# 같은 동시성으로 다시 때려봐야 같은 결과가 나온다.
tokens, pending = [], users
for attempt, workers in enumerate((32, 16, 8, 8), start=1):
    with ThreadPoolExecutor(max_workers=workers) as ex:
        got = list(ex.map(login, pending))
    tokens += [t for t in got if t]
    pending = [u for u, t in zip(pending, got) if not t]
    if not pending:
        break
    print(f"-- 재시도 {attempt}: {len(pending)}명 실패, {workers//2 or 8}-way 로 5초 후 재시도",
          file=sys.stderr)
    time.sleep(5)

if len(tokens) < pool:
    print(f"ERROR: 토큰 {len(tokens)}/{pool} 발급 (재시도 4회 후). 앱 상태 확인.", file=sys.stderr)
    sys.exit(1)
json.dump(tokens, open(f"{lib}/tokens.json", "w"))
print(f"-- {len(tokens)} tokens", file=sys.stderr)
PY
fi

log "==> 유저 풀 준비 완료 (${POOL_SIZE}명)"
