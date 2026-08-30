#!/usr/bin/env python3
"""S2 집계 — 인원 레벨마다 회차들의 중앙값.

S1과 같은 구조이되, 중심 지표가 다르다.
S1은 "얼마나 기다렸나"를 묻고, S2는 **"3건을 원했는데 몇 건을 받았나"** 를 묻는다.
부분 성공자 수가 이 시나리오의 결론이다.

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
    levels = {}
    for path in sorted(RAW.glob("n*-i*.json")):
        m = RUN_RE.match(path.name)
        if not m:
            continue
        run = load(path)
        if run is None:
            continue
        stem = path.with_suffix("")
        run["_integrity"] = load(f"{stem}-integrity.json") or {}
        run["_settle"] = load(f"{stem}-settle.json") or {}
        run["_infra"] = load(f"{stem}-infra.json") or {}
        bucket = levels.setdefault(int(m.group(1)), {"usable": [], "discarded": []})
        bucket["usable" if run.get("validity", {}).get("usable") else "discarded"].append(run)
    return levels


def summarize(crowd, bucket):
    runs = bucket["usable"]
    n_bad = len(bucket["discarded"])
    if not runs:
        return {"crowd": crowd, "runs": 0, "discarded": n_bad,
                "note": "유효한 회차 없음 — 일제 발사가 한 번도 재현되지 않았다"}

    def g(fn):
        return med([fn(r) for r in runs])

    # 대기열은 after 전용 단계다. before의 회차 파일에는 queue 키 자체가 없으므로
    # 전부 .get 으로 읽는다 — 같은 코드가 양쪽을 집계해야 하고, before를 재집계할 때
    # 표가 달라지면 안 된다.
    def q(r, *path):
        node = r.get("queue") or {}
        for k in path:
            if not isinstance(node, dict):
                return None
            node = node.get(k)
        return node

    return {
        "crowd": crowd,
        "queueUsed": any(q(r, "skipped") is False for r in runs),
        "queueAdmittedSlots": g(lambda r: q(r, "admittedSlots")),
        "queueEnterFailedSlots": g(lambda r: q(r, "enterFailedSlots")),
        "queueNotAdmittedSlots": g(lambda r: q(r, "notAdmittedSlots")),
        "queueGaveUpAll": g(lambda r: q(r, "gaveUpAll")),
        "queuePartialAdmit": g(lambda r: q(r, "partialAdmit")),
        "queueWaitP50Ms": g(lambda r: q(r, "waitMs", "p50")),
        "queueWaitP95Ms": g(lambda r: q(r, "waitMs", "p95")),
        "queuePolls": g(lambda r: q(r, "polls")),
        "originRequests": g(lambda r: q(r, "originRequests")),
        "pollSec": q(runs[0], "pollSec"),
        "bookingP50Ms": g(lambda r: (r["timing"].get("bookingMs") or {}).get("p50")),
        "bookingP95Ms": g(lambda r: (r["timing"].get("bookingMs") or {}).get("p95")),
        "runs": len(runs),
        "discarded": n_bad,
        "fireSkewMaxMs": g(lambda r: r["validity"]["fireSkewMaxMs"]),

        "gotAll": g(lambda r: r["perPerson"]["gotAll"]),
        "gotPartial": g(lambda r: r["perPerson"]["gotPartial"]),
        "gotNone": g(lambda r: r["perPerson"]["gotNone"]),
        "partialSeatsHeld": g(lambda r: r["perPerson"]["partialSeatsHeld"]),

        "reqSuccess": g(lambda r: r["perRequest"]["success"]),
        "reqSoldOut": g(lambda r: r["perRequest"]["soldOut"]),
        "reqNoAnswer": g(lambda r: r["perRequest"]["noAnswer"]),
        "reqDropped": g(lambda r: r["perRequest"]["dropped"]),
        "reqUnreachable": g(lambda r: r["perRequest"]["unreachable"]),
        "req5xx": g(lambda r: r["perRequest"]["error5xx"]),

        "lastAnswerSec": g(lambda r: r["timing"]["lastAnswerSec"]),
        "waitP50Ms": g(lambda r: r["timing"]["waitMs"]["p50"]),
        "waitP95Ms": g(lambda r: r["timing"]["waitMs"]["p95"]),

        "settleSec": g(lambda r: r["_settle"].get("settleSeconds")),
        "seeded": g(lambda r: r["_integrity"].get("seeded")),
        "sold": g(lambda r: r["_integrity"].get("sold")),
        "overbookedSlots": max((r["_integrity"].get("overbookedSlots", 0) for r in runs), default=0),
        "unsoldSlots": g(lambda r: r["_integrity"].get("unsoldSlots")),
        "dbSaturatedAny": any(r["_infra"].get("dbSaturated") for r in runs),
        "portPressureAny": any(r["_infra"].get("generatorPortPressure") for r in runs),
    }


def fmt(v, suffix=""):
    return "—" if v is None else f"{v}{suffix}"


def render(rows, label):
    out = [
        f"# S2 다건 오픈런 — {label}",
        "",
        "정각에 N명이 동시에 **슬롯 3개씩** 예약을 시도한다. 슬롯당 30석, 총 90석.",
        "부하 모형은 S1과 같고, 다른 건 한 사람이 3건을 한꺼번에 요구한다는 점뿐이다.",
        "",
        "## 유효성",
        "",
        "| 인원 | 채택 | 폐기 | 발사 스큐 max |",
        "|---:|---:|---:|---:|",
    ]
    for r in rows:
        out.append(f"| {r['crowd']:,} | {r['runs']} | {r['discarded']} | "
                   f"{fmt(r.get('fireSkewMaxMs'), 'ms')} |")

    out += [
        "",
        "## 사람 단위 — 3건을 원했는데 몇 건을 받았나",
        "",
        "**이 표가 이 시나리오의 전부다.**",
        "",
        "`부분 성공`은 3건 중 1~2건만 된 사람이다. 사용자에게 이건 예약이 된 것도 아니고",
        "안 된 것도 아니다 — \"18시 19시는 됐고 20시는 실패\"다. 그리고 현재 코드에는",
        "이걸 되돌리는 경로가 없다. `execute()`가 슬롯 단위라 **여러 슬롯에 걸친 트랜잭션",
        "경계가 애초에 존재하지 않는다.** 그래서 부분 성공자가 붙잡은 좌석은 그대로 묶인다.",
        "",
        "| 인원 | 3건 전부 | **부분 성공** | 전부 실패 | 부분성공자가 점유한 좌석 |",
        "|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        if not r["runs"]:
            out.append(f"| {r['crowd']:,} | — | — | — | — |")
            continue
        out.append(f"| {r['crowd']:,} | {fmt(r['gotAll'])} | **{fmt(r['gotPartial'])}** | "
                   f"{fmt(r['gotNone'])} | {fmt(r['partialSeatsHeld'])} |")

    out += [
        "",
        "## 요청 단위",
        "",
        "요청 수 = 인원 × 3. 분류는 S1과 같다 —",
        "`접속 불가`(dial 실패)와 `무응답`(응답 타임아웃)을 합치면 안 된다.",
        "",
        "| 인원 | 요청 수 | 성공 | 매진 거절 | 무응답 | 튕김 | 접속 불가 | 5xx |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        if not r["runs"]:
            out.append(f"| {r['crowd']:,} | — | — | — | — | — | — | — |")
            continue
        # 대기열을 통과하지 못한 슬롯은 예약을 시도조차 하지 않았으므로 요청 수에서 뺀다.
        # 인원×3으로 고정해 두면 after에서 "성공+거절이 요청 수에 한참 못 미치는" 표가 되고,
        # 원인이 미입장인데 유실로 읽힌다. before는 미입장이 없어 인원×3 그대로다.
        attempted = r["crowd"] * 3 - (r.get("queueNotAdmittedSlots") or 0)
        out.append(f"| {r['crowd']:,} | {attempted:,} | {fmt(r['reqSuccess'])} | "
                   f"{fmt(r['reqSoldOut'])} | {fmt(r['reqNoAnswer'])} | {fmt(r['reqDropped'])} | "
                   f"{fmt(r['reqUnreachable'])} | {fmt(r['req5xx'])} |")

    # 대기열이 없는 라벨(before)에서는 이 절 자체를 내지 않는다. 빈 표를 붙이면
    # before 요약이 "대기열이 있었는데 0이었다"로 읽힌다 — 그런 단계는 없었다.
    if any(r.get("queueUsed") for r in rows):
        poll = next((r["pollSec"] for r in rows if r.get("pollSec") is not None), None)
        out += [
            "",
            "## 대기열 (after 전용)",
            "",
            "대기열 키가 **슬롯 단위**라 3슬롯을 잡으려면 줄을 3개 서야 한다. 다건에 걸친",
            "경계가 없다는 이 시나리오의 지적이 대기열에서도 그대로 반복된다.",
            "",
            "`전부 포기`·`일부만 입장`은 0이어야 정상이다. 0이 아니면 대기 예산이 짧은 게",
            "아니라 승격이 막힌 것이고, 그 회차는 원인을 찾기 전까지 결과로 읽으면 안 된다.",
            "특히 `일부만 입장`은 부분 성공 수치를 직접 흔든다.",
            "",
            f"`폴링`은 before에 없는 부하다. 실제 배포에서는 폴링 간격(={poll}s)이 CDN TTL이라",
            "이 몫이 엣지로 넘어간다.",
            "",
            "| 인원 | 입장 슬롯 | 진입 실패 | 미입장 | 전부 포기 | 일부만 입장 | 대기 p50 | p95 | 예약 p95 | 폴링 | 앱이 받은 요청 |",
            "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
        ]
        for r in rows:
            if not r["runs"]:
                out.append(f"| {r['crowd']:,} |" + " — |" * 10)
                continue
            gave, part = r["queueGaveUpAll"], r["queuePartialAdmit"]
            out.append(
                f"| {r['crowd']:,} | {fmt(r['queueAdmittedSlots'])} | "
                f"{fmt(r['queueEnterFailedSlots'])} | {fmt(r['queueNotAdmittedSlots'])} | "
                f"{fmt(gave)}{' ★' if gave else ''} | {fmt(part)}{' ★' if part else ''} | "
                f"{fmt(r['queueWaitP50Ms'], 'ms')} | {fmt(r['queueWaitP95Ms'], 'ms')} | "
                f"{fmt(r['bookingP95Ms'], 'ms')} | {fmt(r['queuePolls'])} | "
                f"{fmt(r['originRequests'])} |"
            )

    out += [
        "",
        "## 시간 / 정합성",
        "",
        "`판매`가 90에 못 미치면 미판매 좌석이 남은 것이다 — 수요는 넘치는데 못 판 경우다.",
        "",
        "| 인원 | 마지막 응답 | 대기 p50 | p95 | 정착 | 판매/시딩 | 오버부킹 | 미판매 |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        if not r["runs"]:
            out.append(f"| {r['crowd']:,} | — | — | — | — | — | — | — |")
            continue
        out.append(f"| {r['crowd']:,} | {fmt(r['lastAnswerSec'], 's')} | "
                   f"{fmt(r['waitP50Ms'], 'ms')} | {fmt(r['waitP95Ms'], 'ms')} | "
                   f"{fmt(r['settleSec'], 's')} | {fmt(r['sold'])}/{fmt(r['seeded'])} | "
                   f"{r['overbookedSlots']} | {fmt(r['unsoldSlots'])} |")

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
        print(f"\n★ 유효성 미달로 제외된 회차: {total_bad}개.")


if __name__ == "__main__":
    main()
