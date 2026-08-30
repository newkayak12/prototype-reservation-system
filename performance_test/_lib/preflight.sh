#!/usr/bin/env bash
# 측정 시작 전 환경 점검. 하나라도 실패하면 측정하지 않는다.
#
# 1차 측정에서 실제로 겪은 사고: 8081에 after 워크트리 빌드가 떠 있는 줄 모르고
# before를 측정했고, 동시에 다른 세션이 같은 DB 픽스처를 재시딩하고 있었다.
# 결과가 조용히 오염됐고 전량 폐기했다. 그래서 이 스크립트가 있다.
#
#   사용: preflight.sh <before|after|probe>
set -uo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

LABEL="${1:?usage: preflight.sh <before|after|probe>}"
FAIL=0
ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$*"; FAIL=1; }
warn() { printf '  \033[33m!\033[0m %s\n' "$*"; }

hr; log " preflight — label=$LABEL"; hr

# --- 1. 앱이 떠 있는가 -------------------------------------------------------
# -sTCP:LISTEN 이 없으면 8081에 '접속한' 프로세스(= 측정 중인 k6)까지 잡힌다.
# 그러면 아래 CWD 검사가 k6의 CWD(시나리오 디렉터리)를 보고 "before 빌드가 아니다"라고
# 오판한다. 리스닝 소켓을 가진 프로세스만 서버다.
PIDS="$(lsof -ti :8081 -sTCP:LISTEN 2>/dev/null || true)"
if [ -z "$PIDS" ]; then
  bad "8081에 앱이 없다. 먼저 기동할 것 (ENVIRONMENT.md의 기동 인자 사용)"
else
  ok "8081 리스닝 (pid $(echo "$PIDS" | tr '\n' ' '))"
fi

# --- 2. 어느 워크트리 빌드인가 (가장 중요) -----------------------------------
for p in $PIDS; do
  CWD="$(lsof -p "$p" 2>/dev/null | awk '$4=="cwd"{print $NF}' | head -1)"
  [ -z "$CWD" ] && continue
  log "  앱 CWD: $CWD"
  # before-2, after-3 같은 재측정 라벨도 같은 검사를 받아야 한다.
  # 이전 버전은 정확히 'before'/'after'만 매칭해서, before-2 는 *) 로 빠지며
  # 워크트리 검사를 통째로 건너뛰었다. 이 스크립트에서 가장 중요한 검사가
  # 재측정에서만 조용히 꺼지는 셈이라, 접두사로 매칭한다.
  # 이 하네스는 두 워크트리에 같은 내용으로 존재한다. 그래서 "내가 있는 곳"($REPO_ROOT)을
  # 기준으로 판정하면 안 된다 — after 워크트리의 사본으로 before를 재려 할 때 REPO_ROOT가
  # after를 가리켜 검사가 거꾸로 통과한다. 워크트리는 경로 자체로 판정한다.
  case "$LABEL" in
    before*) [[ "$CWD" != *"chore-performance-test-after"* ]] \
              && ok "before 워크트리 빌드 확인 (label=$LABEL)" \
              || bad "before 계열을 재려는데 8081에 after 빌드가 떠 있다" ;;
    after*)  [[ "$CWD" == *"chore-performance-test-after"* ]] \
              && ok "after 워크트리 빌드 확인 (label=$LABEL)" \
              || bad "after 계열을 재려는데 after 워크트리가 아니다" ;;
    *)      warn "label=$LABEL — 워크트리 검사 생략" ;;
  esac
done

# --- 3. 인프라 -------------------------------------------------------------
mysql_q -e "SELECT 1;" >/dev/null 2>&1 && ok "MySQL 연결" || bad "MySQL 연결 실패"
[ "$(redis_cli PING 2>/dev/null)" = "PONG" ] && ok "Redis 연결" || bad "Redis 연결 실패"

# --- 4. 다른 세션이 동시에 측정 중인가 ---------------------------------------
# 프로세스 '이름'으로만 찾는다. -f(전체 명령줄) 매칭을 쓰면 "k6 run"이라는 문자열이
# 들어간 셸/모니터링 명령까지 잡혀서, 실제로는 k6가 없는데도 측정을 거부한다.
# (실제로 겪음: 진행 상황을 보려고 띄운 `pgrep -fl "k6 run"` 이 스스로를 매치시켰다)
OTHER="$(pgrep -x k6 2>/dev/null || true)"
if [ -n "$OTHER" ]; then
  bad "다른 k6 프로세스가 실행 중이다 (pid $(echo "$OTHER" | tr '\n' ' ')). 두 측정이 같은 DB/Redis를 공유한다."
else
  ok "동시 실행 중인 k6 없음"
fi

# --- 5. 파일 디스크립터 -----------------------------------------------------
NOFILE="$(ulimit -n)"
if [ "$NOFILE" = "unlimited" ] || [ "$NOFILE" -ge 100000 ]; then
  ok "ulimit -n = $NOFILE"
else
  warn "ulimit -n = $NOFILE (낮음. run.sh가 올리지만, 고 VU에서 발생기 한계를 잴 수 있다)"
fi

hr
if [ "$FAIL" -ne 0 ]; then
  log " preflight 실패 — 측정하지 않는다."
  exit 1
fi
log " preflight 통과"
