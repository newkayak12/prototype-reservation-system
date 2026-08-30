#!/usr/bin/env bash
# 런과 런 사이의 상태를 지운다. 매 런 시작 전에 부른다.
#
# 왜 필요한가 — 지우지 않으면 앞 런의 잔재가 뒤 런의 조건이 된다. 그러면 사다리
# 뒤쪽 칸이 앞쪽보다 느려지는 게 "부하가 높아서"인지 "테이블이 커져서"인지 구분할 수 없다.
# 측정값이 아니라 측정 순서가 결과를 만들게 된다.
#
# 지우는 것과 근거:
#
#   Redis      분산락 / 세마포어 / 대기열. 세마포어 TTL이 10분이라 안 지우면
#              이전 런의 permit이 남아 다음 런의 동시성이 달라진다. (가장 치명적)
#
#   outbox     예약 1건당 1행이 쌓인다. 이 테이블은 insert-only이고 폴링 스캔이 없어서
#              (OutboxJpaRepository = save + findById 뿐) 요청당 조회 비용은 안 늘지만,
#              행이 수십만으로 커지면 인서트할 때 인덱스 유지 비용이 붙는다.
#              작은 드리프트지만 10회 반복 × 여러 레벨이면 누적된다.
#
#   Kafka      TIME_TABLE_OCCUPIED 토픽에 활성 컨슈머가 없어 메시지가 계속 쌓인다.
#              (실측: outbox 12,515행 = 토픽 12,515건, 그중 12,302건 미소비)
#              브로커 디스크와 프로듀서 지연에 영향을 준다.
#
#   TIME_WAIT  고부하 런 뒤 소켓이 대량으로 TIME_WAIT에 남는다. 임시 포트는
#              49152~65535 = 16,383개뿐이라, 다 빠지기 전에 다음 런을 시작하면
#              서버 포화가 아니라 발생기의 포트 고갈을 재게 된다.
#
# 픽스처(restaurant/timetable/timetable_occupancy) 정리는 seed.sh가 한다 —
# 매번 새 UUID로 다시 깔기 때문에 여기서 중복으로 지우지 않는다.
set -uo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

QUIET="${RESET_QUIET:-0}"
say() { [ "$QUIET" = "1" ] || log "$@"; }

# --- 1. Redis ---------------------------------------------------------------
flush_redis

# --- 2. outbox --------------------------------------------------------------
# TRUNCATE는 DELETE와 달리 auto_increment까지 되돌리고 즉시 공간을 회수한다.
before_rows="$(mysql_q -e "SELECT COUNT(*) FROM outbox;" 2>/dev/null || echo 0)"
mysql_q -e "TRUNCATE TABLE outbox;" 2>/dev/null \
  || mysql_q -e "DELETE FROM outbox;" 2>/dev/null \
  || say "  (outbox 정리 실패 - 무시)"

# --- 3. Kafka ---------------------------------------------------------------
# 토픽을 지우지 않고 레코드만 버린다. 토픽을 삭제하면 파티션 수/설정이 자동생성
# 기본값으로 바뀌어 측정 조건이 조용히 달라진다.
KAFKA_CID="$(docker ps -qf name=kafka-1 2>/dev/null | head -1)"
KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka-1:29092}"
purged_topics=0
if [ -n "$KAFKA_CID" ] && [ "${SKIP_KAFKA_RESET:-0}" != "1" ]; then
  topics="$(docker exec "$KAFKA_CID" /opt/kafka/bin/kafka-topics.sh \
              --bootstrap-server "$KAFKA_BOOTSTRAP" --list 2>/dev/null \
            | grep -vE '^__' || true)"
  for t in $topics; do
    # offset -1 = 현재 high watermark 까지 전부 삭제
    parts="$(docker exec "$KAFKA_CID" /opt/kafka/bin/kafka-topics.sh \
               --bootstrap-server "$KAFKA_BOOTSTRAP" --describe --topic "$t" 2>/dev/null \
             | grep -c 'Partition:' || echo 0)"
    [ "$parts" -eq 0 ] && continue
    spec='{"partitions":['
    for ((p = 0; p < parts; p++)); do
      [ "$p" -gt 0 ] && spec="$spec,"
      spec="$spec{\"topic\":\"$t\",\"partition\":$p,\"offset\":-1}"
    done
    spec="$spec],\"version\":1}"
    if docker exec -i "$KAFKA_CID" sh -c "cat > /tmp/del.json && \
         /opt/kafka/bin/kafka-delete-records.sh --bootstrap-server $KAFKA_BOOTSTRAP \
         --offset-json-file /tmp/del.json" <<<"$spec" >/dev/null 2>&1; then
      purged_topics=$((purged_topics + 1))
    fi
  done

  # 레코드를 지워도 컨슈머 그룹의 커밋 오프셋은 그대로 남는다. 그러면 삭제된 구간을
  # 가리키게 되어 LAG이 실제와 무관한 허수로 보이고(실측: 레코드 0건인데 LAG 12,302),
  # 컨슈머는 out-of-range 오프셋으로 리셋 경고를 낸다. 그룹 오프셋도 끝으로 옮긴다.
  # 활성 컨슈머가 붙어 있는 토픽은 거부되는데, 그건 정상 동작이므로 무시한다.
  docker exec "$KAFKA_CID" /opt/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server "$KAFKA_BOOTSTRAP" --group "${KAFKA_GROUP:-reservation-service}" \
    --reset-offsets --to-latest --all-topics --execute >/dev/null 2>&1 || true
fi

# --- 4. TIME_WAIT 소켓이 빠질 때까지 -----------------------------------------
# macOS 기본 MSL은 15초. 임계값을 넘으면 잠깐 기다린다.
TW_LIMIT="${TW_LIMIT:-8000}"
TW_MAX_WAIT="${TW_MAX_WAIT:-45}"
tw() { netstat -an 2>/dev/null | grep -c TIME_WAIT; }
waited=0
while [ "$(tw)" -gt "$TW_LIMIT" ] && [ "$waited" -lt "$TW_MAX_WAIT" ]; do
  sleep 3
  waited=$((waited + 3))
done
tw_now="$(tw)"
[ "$waited" -gt 0 ] && say "  TIME_WAIT ${waited}초 대기 → $tw_now"

say "  reset: outbox ${before_rows}행 정리, Kafka 토픽 ${purged_topics}개 비움, TIME_WAIT ${tw_now}"

# --- 5. 검증 ----------------------------------------------------------------
# 지웠다고 믿지 말고 확인한다. 여기서 0이 아니면 이후 결과를 신뢰할 수 없다.
left="$(mysql_q -e "SELECT COUNT(*) FROM outbox;" 2>/dev/null || echo 0)"
if [ "$left" != "0" ]; then
  log "  경고: outbox에 ${left}행이 남아 있다"
fi
