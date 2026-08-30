#!/usr/bin/env bash
# k6 가 도는 동안 인프라 포화도를 1초 간격으로 기록한다.
#
# 왜 필요한가
#   "44~62 req/s 가 아키텍처 한계다" 라고 말하려면 그 순간 인프라가 놀고 있었다는
#   증거가 있어야 한다. 없으면 "MySQL 이 2 vCPU 라서 그런 거 아니냐"에 답할 수 없고,
#   before/after 비교 전체가 무너진다. 사후에 docker stats 를 찍어봐야 이미 idle 이라
#   아무것도 증명하지 못한다 — 부하 중에 찍어야 한다.
#
# 기록 항목
#   mysqlCpu/redisCpu  컨테이너 CPU% (Docker VM 은 6 vCPU 이므로 600% 가 상한)
#   dbThreadsRunning   MySQL 에서 실제로 실행 중인 스레드. 앱이 DB 를 얼마나 밀어붙였는지.
#   dbThreadsConnected 앱이 연 커넥션 수. 풀 상한에 붙어 있으면 앱 쪽 병목이다.
#   hostLoad1          호스트 1분 부하. k6 와 JVM 이 12 코어를 다 먹었는지.
#   timeWait           TIME_WAIT 소켓. macOS 임시 포트 16,383 개를 향해 가면 발생기 한계다.
#
#   사용: infra-sample.sh start <출력경로>   → 백그라운드 시작, PID 파일 기록
#         infra-sample.sh stop  <출력경로>   → 종료 + 요약(JSON) 을 stdout 으로
set -uo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

ACTION="${1:?usage: infra-sample.sh <start|stop> <path>}"
OUT="${2:?출력 경로 필요}"
PIDFILE="$OUT.pid"

sample_loop() {
  local mysql_c redis_c
  mysql_c="$(docker ps -qf name=mysql | head -1)"
  redis_c="$(docker ps -qf name=redis | head -1)"
  : > "$OUT"
  while :; do
    # docker stats 한 번에 두 컨테이너를 찍는다. 컨테이너당 호출하면 1초 안에 못 끝난다.
    local stats cpu_mysql=0 cpu_redis=0
    stats="$(docker stats --no-stream --format '{{.ID}} {{.CPUPerc}}' "$mysql_c" "$redis_c" 2>/dev/null)"
    cpu_mysql="$(echo "$stats" | awk -v id="${mysql_c:0:12}" '$1==id{gsub(/%/,"",$2);print $2}')"
    cpu_redis="$(echo "$stats" | awk -v id="${redis_c:0:12}" '$1==id{gsub(/%/,"",$2);print $2}')"

    local running connected
    running="$(mysql_q -e "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Threads_running';" 2>/dev/null)"
    connected="$(mysql_q -e "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Threads_connected';" 2>/dev/null)"

    local load1 tw
    load1="$(sysctl -n vm.loadavg 2>/dev/null | awk '{print $2}')"
    tw="$(netstat -an 2>/dev/null | grep -c TIME_WAIT)"

    printf '{"t":%s,"mysqlCpu":%s,"redisCpu":%s,"dbThreadsRunning":%s,"dbThreadsConnected":%s,"hostLoad1":%s,"timeWait":%s}\n' \
      "$(date +%s)" "${cpu_mysql:-0}" "${cpu_redis:-0}" "${running:-0}" "${connected:-0}" "${load1:-0}" "${tw:-0}" >> "$OUT"
    sleep 1
  done
}

case "$ACTION" in
  start)
    sample_loop &
    echo $! > "$PIDFILE"
    ;;
  stop)
    [ -f "$PIDFILE" ] && { kill "$(cat "$PIDFILE")" 2>/dev/null; rm -f "$PIDFILE"; }
    sleep 0.3
    python3 - "$OUT" <<'PY'
import json, sys
rows = []
for line in open(sys.argv[1]):
    line = line.strip()
    if line:
        try: rows.append(json.loads(line))
        except Exception: pass
if not rows:
    print('{"samples":0}'); sys.exit()
def peak(k): return max(r.get(k, 0) or 0 for r in rows)
def mean(k): return round(sum(r.get(k, 0) or 0 for r in rows) / len(rows), 1)
out = {
    "samples": len(rows),
    "mysqlCpuPeak": peak("mysqlCpu"), "mysqlCpuMean": mean("mysqlCpu"),
    "redisCpuPeak": peak("redisCpu"),
    "dbThreadsRunningPeak": peak("dbThreadsRunning"),
    "dbThreadsConnectedPeak": peak("dbThreadsConnected"),
    "hostLoad1Peak": peak("hostLoad1"),
    "timeWaitPeak": peak("timeWait"),
    # Docker VM 은 6 vCPU 다. 컨테이너 CPU% 는 600% 가 상한이므로 480%(80%) 를 넘으면
    # DB 가 포화됐다는 뜻이고, 그러면 측정된 상한은 앱이 아니라 인프라 한계다.
    # VM 자원을 바꾸면 이 값도 같이 바꿔야 한다 — 안 그러면 판정이 영영 안 켜진다.
    "dbSaturated": peak("mysqlCpu") >= 480,
    # macOS 임시 포트는 49152~65535 = 16,383 개. 12,000 을 넘으면 발생기 쪽에서
    # 포트가 말라 연결 실패가 나기 시작한다 — 서버 한계와 구분이 안 된다.
    "generatorPortPressure": peak("timeWait") >= 12000,
}
print(json.dumps(out))
PY
    ;;
  *) echo "unknown action: $ACTION" >&2; exit 1 ;;
esac
