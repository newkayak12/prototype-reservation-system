#!/usr/bin/env python3
"""S1 집계 — 인원 레벨마다 회차들의 중앙값을 낸다.

평균이 아니라 중앙값을 쓰는 이유: 회차 하나가 GC나 백그라운드 프로세스로 튀면
평균이 통째로 끌려간다. 10회 중 1회의 이상치가 결론을 바꾸면 안 된다.

★ 유효성 판정이 이 스크립트의 핵심이다.
   일제 발사가 재현되지 않은 회차(fire_skew 초과 또는 늦은 VU)는 "동시 N명"이라는
   전제가 깨진 데이터다. 중앙값에서 제외하되, 몇 개를 왜 뺐는지 반드시 표에 남긴다.
   조용히 버리면 "10회 측정"이라고 써놓고 실제로는 3회인 상황이 된다.

사용: aggregate.py <label_dir>
"""
import json
import re
import statistics
import sys
from pathlib import Path

LABEL_DIR = Path(sys.argv[1])
RAW = LABEL_DIR / "raw"

RUN_RE = re.compile(r"^n(\d+)-i(\d+)\.json$")


def load(path):
    try:
        with open(path) as f:
            return json.load(f)
    except Exception:
        return None


def med(values):
    vals = [v for v in values if v is not None]
    return round(statistics.median(vals), 1) if vals else None


def collect():
    """crowd -> {"usable": [run...], "discarded": [run...]}"""
    levels = {}
    for path in sorted(RAW.glob("n*-i*.json")):
        m = RUN_RE.match(path.name)
        if not m:
            continue
        crowd = int(m.group(1))
        run = load(path)
        if run is None:
            continue

        stem = path.with_suffix("")
        run["_integrity"] = load(f"{stem}-integrity.json") or {}
        run["_settle"] = load(f"{stem}-settle.json") or {}
        run["_infra"] = load(f"{stem}-infra.json") or {}

        bucket = levels.setdefault(crowd, {"usable": [], "discarded": []})
        key = "usable" if run.get("validity", {}).get("usable") else "discarded"
        bucket[key].append(run)
    return levels


def summarize(crowd, bucket):
    runs = bucket["usable"]
    n_ok, n_bad = len(runs), len(bucket["discarded"])

    if not runs:
        return {
            "crowd": crowd, "runs": 0, "discarded": n_bad,
            "note": "유효한 회차 없음 — 일제 발사가 한 번도 재현되지 않았다",
        }

    def g(fn):
        return med([fn(r) for r in runs])

    # 대기열은 after 전용 단계다. before의 회차 파일에는 queue 키 자체가 없으므로
    # 전부 .get 으로 읽는다 — 이 스크립트는 양쪽 결과를 같은 코드로 집계해야 하고,
    # before를 재집계할 때 표가 달라지면 안 된다.
    def q(r, *path):
        node = r.get("queue") or {}
        for k in path:
            if not isinstance(node, dict):
                return None
            node = node.get(k)
        return node

    queue_used = any(q(r, "skipped") is False for r in runs)

    return {
        "crowd": crowd,
        "seats": runs[0].get("seats"),
        "runs": n_ok,
        "discarded": n_bad,

        "fireSkewMaxMs": g(lambda r: r["validity"]["fireSkewMaxMs"]),
        "fireSkewP95Ms": g(lambda r: r["validity"]["fireSkewP95Ms"]),

        "pageOpenFailed": g(lambda r: r["preOpen"]["failed"]),
        "seatWon": g(lambda r: r["outcome"]["seatWon"]),
        "soldOut": g(lambda r: r["outcome"]["soldOut"]),
        "rejected": g(lambda r: r["outcome"]["rejected"]),
        "error5xx": g(lambda r: r["outcome"]["error5xx"]),
        "noAnswer": g(lambda r: r["outcome"]["noAnswer"]),
        "dropped": g(lambda r: r["outcome"].get("dropped", 0)),
        "unreachable": g(lambda r: r["outcome"]["unreachable"]),

        "soldOutSec": g(lambda r: r["timing"]["soldOutSec"]),
        "lastAnswerSec": g(lambda r: r["timing"]["lastAnswerSec"]),
        "waitP50Ms": g(lambda r: r["timing"]["waitMs"]["p50"]),
        "waitP95Ms": g(lambda r: r["timing"]["waitMs"]["p95"]),
        "waitP99Ms": g(lambda r: r["timing"]["waitMs"]["p99"]),

        "queueUsed": queue_used,
        "queueAdmitted": g(lambda r: q(r, "admitted")),
        "queueEnterFailed": g(lambda r: q(r, "enterFailed")),
        "queueGaveUp": g(lambda r: q(r, "gaveUp")),
        "queueWaitP50Ms": g(lambda r: q(r, "waitMs", "p50")),
        "queueWaitP95Ms": g(lambda r: q(r, "waitMs", "p95")),
        "queuePolls": g(lambda r: q(r, "polls")),
        "originRequests": g(lambda r: q(r, "originRequests")),
        "pollSec": q(runs[0], "pollSec"),
        "bookingP50Ms": g(lambda r: (r["timing"].get("bookingMs") or {}).get("p50")),
        "bookingP95Ms": g(lambda r: (r["timing"].get("bookingMs") or {}).get("p95")),

        "settleSec": g(lambda r: r["_settle"].get("settleSeconds")),
        "overbookedSlots": max((r["_integrity"].get("overbookedSlots", 0) for r in runs),
                              default=0),
        "unsoldSlots": g(lambda r: r["_integrity"].get("unsoldSlots")),
        "mysqlCpuPeak": g(lambda r: r["_infra"].get("mysqlCpuPeak")),
        "dbSaturatedAny": any(r["_infra"].get("dbSaturated") for r in runs),
        "portPressureAny": any(r["_infra"].get("generatorPortPressure") for r in runs),
    }


def fmt(v, suffix=""):
    return "—" if v is None else f"{v}{suffix}"


def render(rows, label):
    out = [
        f"# S1 단건 오픈런 — {label}",
        "",
        "정각에 N명이 동시에 예약 버튼을 누른다. 좌석 30석 고정.",
        "**부하 천장이 아니라 그날 그 사람들이 겪은 것을 잰다.**",
        "",
        "## 유효성",
        "",
        "일제 도착이 재현되지 않은 회차는 집계에서 제외했다. 발사 스큐가 크면",
        "\"동시 N명\"이라는 전제 자체가 깨진 데이터라, 결과로 읽으면 안 된다.",
        "",
        "| 인원 | 채택 | 폐기 | 발사 스큐 max | 스큐 p95 |",
        "|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        out.append(
            f"| {r['crowd']:,} | {r['runs']} | {r['discarded']} | "
            f"{fmt(r.get('fireSkewMaxMs'), 'ms')} | {fmt(r.get('fireSkewP95Ms'), 'ms')} |"
        )

    out += [
        "",
        "## 사용자가 겪은 것",
        "",
        "`페이지 못 엶`은 오픈 *이전*의 실패다 — 커넥션조차 맺지 못한 사람 수이고,",
        "`tomcat.max-connections=8192` 벽이 여기로 나타난다.",
        "오픈 *이후*의 실패는 세 가지이고, 사용자에게 보이는 모습이 전혀 다르다.",
        "",
        "| 분류 | k6 에러 | 무슨 일인가 |",
        "|---|---|---|",
        "| 접속 불가 | `dial: i/o timeout` | **줄에 서지도 못했다.** TCP 연결 수립 실패 |",
        "| 튕김 | `EOF` / `reset` | 서버가 응답 없이 커넥션을 끊었다 |",
        "| 무응답 | `deadline exceeded` | 줄에 섰고 기다렸는데 답이 안 왔다 |",
        "",
        "**셋을 합치면 안 된다.** 앞의 둘은 `max-connections` 벽이고 마지막은 처리 지연이다.",
        "`dial` 실패는 문자열에 `timeout`이 들어있어서 응답 타임아웃과 합쳐지기 쉬운데,",
        "그러면 접속 거부가 \"서버가 느리다\"로 둔갑한다 — 실제로 두 번 그렇게 잘못 읽었다.",
        "",
        "| 인원 | 페이지 못 엶 | 좌석 획득 | 매진 거절 | 락 거절 | 무응답 | 튕김 | 접속 불가 | 5xx |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        if not r["runs"]:
            out.append(f"| {r['crowd']:,} | — | — | — | — | — | — | — | — |")
            continue
        out.append(
            f"| {r['crowd']:,} | {fmt(r['pageOpenFailed'])} | {fmt(r['seatWon'])} | "
            f"{fmt(r['soldOut'])} | {fmt(r['rejected'])} | {fmt(r['noAnswer'])} | "
            f"{fmt(r['dropped'])} | {fmt(r['unreachable'])} | {fmt(r['error5xx'])} |"
        )

    out += [
        "",
        "## 시간",
        "",
        "| 인원 | 매진까지 | 마지막 응답 | 대기 p50 | p95 | p99 | 정착 |",
        "|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        if not r["runs"]:
            out.append(f"| {r['crowd']:,} | — | — | — | — | — | — |")
            continue
        out.append(
            f"| {r['crowd']:,} | {fmt(r['soldOutSec'], 's')} | {fmt(r['lastAnswerSec'], 's')} | "
            f"{fmt(r['waitP50Ms'], 'ms')} | {fmt(r['waitP95Ms'], 'ms')} | "
            f"{fmt(r['waitP99Ms'], 'ms')} | {fmt(r['settleSec'], 's')} |"
        )

    # 대기열이 없는 라벨(before)에서는 이 절 자체를 내지 않는다. 빈 표를 붙이면
    # before 요약이 "대기열이 있었는데 0이었다"로 읽힌다 — 그런 단계는 없었다.
    if any(r.get("queueUsed") for r in rows):
        poll = next((r["pollSec"] for r in rows if r.get("pollSec") is not None), None)
        out += [
            "",
            "## 대기열 (after 전용)",
            "",
            "위 `대기 p50/p95`는 **줄 서는 시간을 포함한** 값이다 — 사용자가 버튼을 누르고",
            "답을 받기까지고, before와 같은 잣대다. `예약 p95`는 대기를 뺀 예약 호출 자체의",
            "지연이라, 둘을 같이 봐야 느림의 출처가 갈린다.",
            "",
            f"`포기`는 0이어야 정상이다. 0이 아니면 대기 예산이 짧은 게 아니라 승격이 막힌 것이므로,",
            "그 회차는 원인을 찾기 전까지 결과로 읽으면 안 된다.",
            "",
            f"`폴링`은 before에 존재하지 않는 부하다. 이걸 세지 않으면 after의 실부하가 통계에서",
            f"사라진다. 실제 배포에서는 폴링 간격(={poll}s)이 CDN TTL이라 이 몫이 엣지로 넘어간다.",
            "",
            "| 인원 | 입장 | 진입 실패 | 포기 | 대기 p50 | p95 | 예약 p50 | 예약 p95 | 폴링 | 앱이 받은 요청 | 폴링 비중 |",
            "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
        ]
        for r in rows:
            if not r["runs"]:
                out.append(f"| {r['crowd']:,} |" + " — |" * 10)
                continue
            polls, origin = r["queuePolls"], r["originRequests"]
            share = f"{polls / origin * 100:.0f}%" if polls and origin else "—"
            gave = r["queueGaveUp"]
            out.append(
                f"| {r['crowd']:,} | {fmt(r['queueAdmitted'])} | {fmt(r['queueEnterFailed'])} | "
                f"{fmt(gave)}{' ★' if gave else ''} | "
                f"{fmt(r['queueWaitP50Ms'], 'ms')} | {fmt(r['queueWaitP95Ms'], 'ms')} | "
                f"{fmt(r['bookingP50Ms'], 'ms')} | {fmt(r['bookingP95Ms'], 'ms')} | "
                f"{fmt(polls)} | {fmt(origin)} | {share} |"
            )

    out += [
        "",
        "## 정합성 / 인프라",
        "",
        "`오버부킹`은 회차 최대값이다 — 10회 중 1회라도 깨지면 깨진 것이다.",
        "`DB 포화`나 `포트 압박`이 켜지면 관측된 한계는 앱이 아니라 환경 한계다.",
        "",
        "| 인원 | 오버부킹 | 미판매 | MySQL CPU peak | DB 포화 | 포트 압박 |",
        "|---:|---:|---:|---:|:--:|:--:|",
    ]
    for r in rows:
        if not r["runs"]:
            out.append(f"| {r['crowd']:,} | — | — | — | — | — |")
            continue
        out.append(
            f"| {r['crowd']:,} | {r['overbookedSlots']} | {fmt(r['unsoldSlots'])} | "
            f"{fmt(r['mysqlCpuPeak'], '%')} | "
            f"{'★' if r['dbSaturatedAny'] else '·'} | "
            f"{'★' if r['portPressureAny'] else '·'} |"
        )

    out.append("")
    return "\n".join(out)


def main():
    levels = collect()
    if not levels:
        print(f"집계할 결과 없음: {RAW}")
        return

    rows = [summarize(c, levels[c]) for c in sorted(levels)]
    label = LABEL_DIR.name

    (LABEL_DIR / "summary.json").write_text(
        json.dumps({"label": label, "levels": rows}, indent=2, ensure_ascii=False))
    md = render(rows, label)
    (LABEL_DIR / "summary.md").write_text(md)

    print(md)
    total_bad = sum(r["discarded"] for r in rows)
    if total_bad:
        print(f"\n★ 유효성 미달로 제외된 회차: {total_bad}개. "
              f"SPREAD_SEC/SETTLE_SEC를 늘리거나 인원을 낮춰야 한다.")


if __name__ == "__main__":
    main()
