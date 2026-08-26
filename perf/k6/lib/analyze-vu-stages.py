#!/usr/bin/env python3
"""VU 스테이지별 처리량/레이턴시 분석. k6 --out json의 raw NDJSON을 읽어
http_req_duration 포인트를 vu_stage 태그(booking.js가 붙임)로 묶어 스테이지별
요청수/처리량(req/s)/상태코드 분포/레이턴시 백분위수를 표로 출력한다.

사용법: python3 analyze-vu-stages.py <raw.json>
"""
import json
import sys
from collections import defaultdict

STAGE_ORDER = [100, 300, 600, 1000, 1500, 2000]
STAGE_HOLD_SECONDS = 20  # booking.js 각 target의 hold 구간 길이(ramp-up 구간 제외 근사치)


def percentile(sorted_values, p):
    if not sorted_values:
        return 0.0
    k = (len(sorted_values) - 1) * p
    f = int(k)
    c = min(f + 1, len(sorted_values) - 1)
    if f == c:
        return sorted_values[f]
    return sorted_values[f] + (sorted_values[c] - sorted_values[f]) * (k - f)


def classify_status(status):
    if status in (200, 201):
        return "success"
    if status in (400, 409):
        return "sold_out"
    if status in (423, 429):
        return "conflict"
    return "error"


def main():
    if len(sys.argv) != 2:
        print("usage: analyze-vu-stages.py <raw.json>", file=sys.stderr)
        sys.exit(1)

    by_stage = defaultdict(lambda: {"durations": [], "status": defaultdict(int)})

    with open(sys.argv[1]) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                point = json.loads(line)
            except json.JSONDecodeError:
                continue
            if point.get("type") != "Point" or point.get("metric") != "http_req_duration":
                continue
            tags = point.get("data", {}).get("tags", {})
            if tags.get("name") != "booking":
                continue
            stage = tags.get("vu_stage")
            if stage is None:
                continue
            stage = int(stage)
            status = int(tags.get("status", "0") or "0")
            duration = point.get("data", {}).get("value", 0.0)
            by_stage[stage]["durations"].append(duration)
            by_stage[stage]["status"][classify_status(status)] += 1

    header = f"{'VU':>6} {'reqs':>7} {'req/s~':>8} {'success':>8} {'sold_out':>9} {'conflict':>9} {'error':>7} {'p50ms':>8} {'p95ms':>8} {'p99ms':>8}"
    print(header)
    print("-" * len(header))
    for stage in STAGE_ORDER:
        bucket = by_stage.get(stage)
        if not bucket or not bucket["durations"]:
            print(f"{stage:>6} {'(no data)':>7}")
            continue
        durations = sorted(bucket["durations"])
        n = len(durations)
        throughput = n / STAGE_HOLD_SECONDS
        s = bucket["status"]
        print(
            f"{stage:>6} {n:>7} {throughput:>8.1f} {s['success']:>8} {s['sold_out']:>9} "
            f"{s['conflict']:>9} {s['error']:>7} {percentile(durations, 0.5):>8.0f} "
            f"{percentile(durations, 0.95):>8.0f} {percentile(durations, 0.99):>8.0f}"
        )


if __name__ == "__main__":
    main()
