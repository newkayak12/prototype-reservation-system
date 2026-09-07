#!/usr/bin/env python3
"""S6 — Sustained Storm 집계.

raw/sustained-rNNNNN-iMM.json 과 raw/waves-wNNNNN-iMM.json 을 부분(part)별로 묶어
회차 중앙값 시계열을 만든다. 워밍업 런(warmup- 접두)은 파일명으로 걸러진다.

이 시나리오의 결론은 총합이 아니라 추세다. 그래서 표의 단위가 런이 아니라 버킷/웨이브다.
    Part A  마지막 버킷 p99 / 첫 버킷 p99      → 지속 부하에서 나빠지는가
    Part B  웨이브 5 p95 / 웨이브 1 p95        → 웨이브가 갈수록 나빠지는가
            휴지 구간 잔여 완료 건수            → 부하를 껐는데도 서버가 일하고 있는가

버킷은 회차 간에 정확히 같은 시각 격자를 쓰므로(시나리오가 t0 기준으로 자른다)
버킷 번호로 맞춰 중앙값을 낼 수 있다.

사용법: python3 aggregate.py <s6-sustained-storm/before 같은 디렉터리>
"""
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

RUN_RE = re.compile(r"^(sustained|waves)-[rw](\d+)-i(\d+)\.json$")


def med(v):
    return statistics.median(v) if v else 0.0


def load_json(p):
    try:
        return json.loads(p.read_text())
    except Exception:
        return None


def collect(root: Path):
    raw = root / "raw"
    if not raw.is_dir():
        sys.exit(f"raw/ 없음: {root}")

    by_part = defaultdict(list)
    for f in sorted(raw.iterdir()):
        m = RUN_RE.match(f.name)
        if not m:
            continue
        rep = load_json(f)
        if rep is None:
            print(f"WARN: {f.name} 파싱 실패 — 건너뜀", file=sys.stderr)
            continue
        # 요청 0건은 "처리량 0"이 아니라 측정 실패(setup 예외 등)다.
        if not rep.get("valid", False):
            print(f"WARN: {f.name} 측정 실패 — 제외", file=sys.stderr)
            continue
        stem = str(f.with_suffix(""))
        rep["_settle"] = (load_json(Path(f"{stem}-settle.json")) or {}).get("settleSeconds")
        rep["_integrity"] = load_json(Path(f"{stem}-integrity.json"))
        by_part[(m.group(1), int(m.group(2)))].append(rep)
    return by_part


def median_series(runs):
    """버킷 번호를 맞춰 회차 중앙값 시계열을 만든다.

    회차를 평균이 아니라 중앙값으로 합치는 이유는 S2와 같다 — 한 회차의 튐이
    추세선을 통째로 기울이는 걸 막는다.
    """
    n = min(len(r["series"]) for r in runs)
    out = []
    for i in range(n):
        cells = [r["series"][i] for r in runs]
        base = cells[0]
        out.append({
            "bucket": base["bucket"],
            "fromSec": base["fromSec"],
            "toSec": base["toSec"],
            "phase": base["phase"],
            "wave": base["wave"],
            "requests": med([c["requests"] for c in cells]),
            "success": med([c["success"] for c in cells]),
            "timeout": med([c["timeout"] for c in cells]),
            "completedRps": med([c["completedRps"] for c in cells]),
            "successRps": med([c["successRps"] for c in cells]),
            "p50": med([c["p50"] for c in cells]),
            "p95": med([c["p95"] for c in cells]),
            "p99": med([c["p99"] for c in cells]),
            "max": max(c["max"] for c in cells),
        })
    return out


def median_waves(runs):
    n = min(len(r["waves"]) for r in runs)
    out = []
    for i in range(n):
        cells = [r["waves"][i] for r in runs]
        out.append({
            "wave": cells[0]["wave"],
            "requests": med([c["requests"] for c in cells]),
            "success": med([c["success"] for c in cells]),
            "completionRate": med([c["completionRate"] for c in cells]),
            "p50": med([c["p50"] for c in cells]),
            "p95": med([c["p95"] for c in cells]),
            "p99": med([c["p99"] for c in cells]),
            "idleResidualRequests": med([c["idleResidualRequests"] for c in cells]),
            "idleResidualRps": med([c["idleResidualRps"] for c in cells]),
            "carryIntoNextWave": med([c["carryIntoNextWave"] for c in cells]),
        })
    return out


def common(part, param, runs):
    g = lambda fn: [fn(r) for r in runs]
    integ = [r["_integrity"] for r in runs if r["_integrity"]]
    settles = [r["_settle"] for r in runs if r["_settle"] is not None]
    seeded = med([i.get("seeded", 0) for i in integ])
    sold = med([i.get("sold", 0) for i in integ])

    return {
        "part": part,
        "targetRps": param,
        "runs": len(runs),
        "requests": med(g(lambda r: r["requests"])),
        "dropped": med(g(lambda r: r["droppedIterations"])),
        "loadWindowRps": med(g(lambda r: r["loadWindowRps"])),
        "goodputRps": med(g(lambda r: r["goodputRps"])),
        "attainment": med(g(lambda r: r["attainment"])),
        "success": med(g(lambda r: r["outcome"]["success"])),
        "timeout": med(g(lambda r: r["outcome"]["timeout"])),
        "serverError5xx": med(g(lambda r: r["outcome"]["serverError5xx"])),
        "failureRate": med(g(lambda r: r["failureRate"])),
        "p50": med(g(lambda r: r["latencyMs"]["p50"])),
        "p95": med(g(lambda r: r["latencyMs"]["p95"])),
        "p99": med(g(lambda r: r["latencyMs"]["p99"])),
        "settleSeconds": med(settles),
        "overbookedRuns": sum(1 for i in integ if i.get("overbookedSlots", 0) > 0),
        "seededSeats": seeded,
        "soldSeats": sold,
        # 재고 소진율. 이 값이 크면 런 후반의 요청은 전반의 요청보다 구조적으로 싸다
        # (findBookableTimeTable에 LIMIT이 없어 비용이 잔여 재고에 비례한다).
        "drainRatio": (sold / seeded) if seeded else 0.0,
        "series": median_series(runs),
    }


def summarize(part, param, runs):
    row = common(part, param, runs)
    g = lambda fn: [fn(r) for r in runs]
    if part == "waves":
        row["waves"] = median_waves(runs)
        row["waveSec"] = runs[0].get("waveSec")
        row["idleSec"] = runs[0].get("idleSec")
        row["p95Ratio"] = med(g(lambda r: r["waveDegradation"]["p95Ratio"]))
        row["p95SlopePerWave"] = med(g(lambda r: r["waveDegradation"]["p95SlopePerWave"]))
        row["idleResidualTotal"] = med(g(lambda r: r["waveDegradation"]["idleResidualTotal"]))
        row["carryMax"] = med(g(lambda r: r["waveDegradation"]["carryMax"]))
    else:
        row["totalSec"] = runs[0].get("totalSec")
        row["bucketSec"] = runs[0].get("bucketSec")
        row["p99Ratio"] = med(g(lambda r: r["drift"]["p99Ratio"]))
        row["p99SlopePerBucket"] = med(g(lambda r: r["drift"]["p99SlopePerBucket"]))
        row["rpsRatio"] = med(g(lambda r: r["drift"]["rpsRatio"]))
        row["rpsSlopePerBucket"] = med(g(lambda r: r["drift"]["rpsSlopePerBucket"]))
    return row


def render_sustained(row, L):
    load = [s for s in row["series"] if s["phase"] == "load"]
    tail = [s for s in row["series"] if s["phase"] == "tail" and s["requests"] > 0]

    L += [
        "",
        f"## Part A — 지속 부하 ({row['targetRps']:,} req/s × {row['totalSec']}s)",
        "",
        f"회차 {row['runs']}회 중앙값. 버킷 {row['bucketSec']}초.",
        "",
        "| 구간(s) | 완료 req/s | 성공 req/s | p50 | p95 | p99 | timeout |",
        "|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for s in load:
        L.append(
            f"| {s['fromSec']}–{s['toSec']} | {s['completedRps']:.0f} | {s['successRps']:.0f} | "
            f"{s['p50']:.0f} | {s['p95']:.0f} | **{s['p99']:.0f}** | {s['timeout']:.0f} |"
        )
    if tail:
        L.append("")
        L.append(
            f"부하 종료 후 잔여 완료: {sum(s['requests'] for s in tail):.0f}건 "
            f"({len(tail)}개 꼬리 버킷에 걸쳐 있음)"
        )

    L += [
        "",
        "### 재고 드리프트 (해석 시 반드시 감안할 것)",
        "",
        f"시딩 {row['seededSeats']:,.0f}석 중 {row['soldSeats']:,.0f}석 판매 — "
        f"소진율 **{row['drainRatio'] * 100:.0f}%**.",
        "",
        "`findBookableTimeTable`에 LIMIT이 없어 임계구간 비용이 잔여 재고에 비례한다(S8).",
        "즉 런이 진행될수록 요청 하나가 **싸진다.** 이 드리프트는 위 표를 아래쪽으로",
        "(= 빨라지는 쪽으로) 끌어당긴다. 따라서:",
        "",
        "- p99가 그럼에도 우상향한다면, 그건 재고 감소분을 이기고 나온 열화다 — 결론이 강해진다.",
        "- p99가 평탄하다면 **\"열화 없음\"과 \"열화가 재고 감소로 상쇄됨\"을 이 실험은 구분하지 못한다.**",
        "  그 경우 반증이라고 쓸 수 없고, 재고를 고정한 재측정이 필요하다.",
        "",
        "매진은 일어나지 않았어야 한다. 소진율 100%면 후반 버킷은 빠른 실패 경로를 잰",
        "것이므로 이 표 전체가 무효다.",
    ]


def render_waves(row, L):
    ws = row["waves"]
    L += [
        "",
        f"## Part B — 반복 웨이브 ({row['targetRps']:,} req/s × {row['waveSec']}s, "
        f"휴지 {row['idleSec']}s, {len(ws)}회)",
        "",
        f"회차 {row['runs']}회 중앙값. 좌석은 웨이브 사이에 리셋하지 않는다(잔여 누적이 관측 대상).",
        "",
        "- **지연은 발사 시각 기준**으로 웨이브에 귀속시킨다.",
        "- **휴지 잔여는 도착 시각 기준**이다 — 부하가 꺼진 구간에 도착한 응답은 전부 밀려 있던 요청이다.",
        "- **다음 웨이브 직전**은 휴지 마지막 버킷의 완료 건수. 0이 아니면 다음 웨이브가",
        "  빈 상태가 아니라 밀린 상태에서 시작한다는 뜻이다.",
        "",
        "| 웨이브 | 완료 | 성공 | 완료율 | p50 | p95 | p99 | 휴지 잔여 완료 | 휴지 잔여 req/s | 다음 웨이브 직전 |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for w in ws:
        L.append(
            f"| {w['wave']} | {w['requests']:.0f} | {w['success']:.0f} | "
            f"{w['completionRate'] * 100:.0f}% | {w['p50']:.0f} | **{w['p95']:.0f}** | "
            f"{w['p99']:.0f} | {w['idleResidualRequests']:.0f} | "
            f"{w['idleResidualRps']:.1f} | {w['carryIntoNextWave']:.0f} |"
        )

    L += [
        "",
        "### 완료 시각 시계열",
        "",
        "부하 구간은 `■`, 휴지 구간은 `·`, 부하 종료 후 꼬리는 `~`.",
        "휴지 구간에 막대가 서 있으면 그게 잔여 큐다.",
        "",
        "| 구간(s) | | 웨이브 | 완료 req/s | 성공 req/s | p95 | p99 |",
        "|---:|:--:|---:|---:|---:|---:|---:|",
    ]
    mark = {"load": "■", "idle": "·", "tail": "~"}
    for s in row["series"]:
        L.append(
            f"| {s['fromSec']}–{s['toSec']} | {mark.get(s['phase'], '')} | "
            f"{s['wave'] if s['wave'] else '—'} | {s['completedRps']:.0f} | "
            f"{s['successRps']:.0f} | {s['p95']:.0f} | {s['p99']:.0f} |"
        )

    L += [
        "",
        f"시딩 {row['seededSeats']:,.0f}석 중 {row['soldSeats']:,.0f}석 판매 "
        f"(소진율 {row['drainRatio'] * 100:.0f}%). Part A와 같은 좌석 수로 시딩하므로",
        "두 부분의 지연을 나란히 놓고 볼 수 있다.",
    ]


def render_verdict(sustained, waves, L):
    L += ["", "## 판정", ""]

    if sustained:
        r = sustained
        ratio = r["p99Ratio"]
        slope = r["p99SlopePerBucket"]
        if ratio >= 2:
            v = f"**지속 열화 확정** — 마지막 버킷 p99가 첫 버킷의 {ratio:.2f}배"
        elif ratio <= 1.2 and slope <= 0:
            v = ("**지속 열화 관측되지 않음** — 다만 재고 소진 "
                 f"{r['drainRatio'] * 100:.0f}%가 요청을 싸게 만든 효과와 구분되지 않는다")
        else:
            v = f"**경미한 상승** — p99 {ratio:.2f}배, 기울기 {slope:+.1f}ms/버킷"
        L += [
            f"- Part A p99: 첫 버킷 {r['series'][0]['p99']:.0f}ms → "
            f"마지막 부하 버킷 {[s for s in r['series'] if s['phase'] == 'load'][-1]['p99']:.0f}ms "
            f"({ratio:.2f}배, 최소제곱 기울기 {slope:+.1f}ms/버킷)",
            f"- Part A 처리량: {r['rpsRatio']:.2f}배 (기울기 {r['rpsSlopePerBucket']:+.1f} req/s/버킷)",
            f"- {v}",
        ]

    if waves:
        r = waves
        ratio = r["p95Ratio"]
        residual = r["idleResidualTotal"]
        carry = r["carryMax"]
        # README의 반증 조건: 웨이브 5 ≈ 웨이브 1 이고 휴지 잔여가 0.
        # 잔여는 "0인가"가 아니라 "다음 웨이브 직전까지 남아 있는가"로 판단한다 —
        # 휴지 시작 직후 몇 건 흘러나오는 건 타임아웃 상한이 30s인 이상 당연하고,
        # 그걸 누적 증거로 쓰면 어떤 런도 반증될 수 없다.
        if ratio >= 2:
            v = f"**누적 확정** — 웨이브 {len(r['waves'])} p95가 웨이브 1의 {ratio:.2f}배"
        elif carry > 0:
            v = (f"**누적 확정** — 다음 웨이브 직전 버킷에서도 응답이 {carry:.0f}건 "
                 "나온다. 웨이브는 밀린 상태에서 시작한다")
        elif ratio <= 1.2:
            v = "**H2 연장 반증** — 웨이브 5가 웨이브 1과 같고 휴지 구간이 비어 있다. 회복력이 있다"
        else:
            v = f"**부분 누적** — p95 {ratio:.2f}배, 웨이브당 기울기 {r['p95SlopePerWave']:+.1f}ms"
        L += [
            f"- Part B p95: 웨이브 1 {r['waves'][0]['p95']:.0f}ms → "
            f"웨이브 {len(r['waves'])} {r['waves'][-1]['p95']:.0f}ms ({ratio:.2f}배)",
            f"- 완료율: 웨이브 1 {r['waves'][0]['completionRate'] * 100:.0f}% → "
            f"웨이브 {len(r['waves'])} {r['waves'][-1]['completionRate'] * 100:.0f}%",
            f"- 휴지 구간 잔여 완료 합계 {residual:.0f}건, 다음 웨이브 직전 최대 {carry:.0f}건",
            f"- {v}",
        ]

    over = [r for r in (sustained, waves) if r and r["overbookedRuns"]]
    L.append(
        "- 정합성: 오버부킹 발생 부분 "
        + (", ".join(f"{r['part']}({r['overbookedRuns']}/{r['runs']}회)" for r in over)
           if over else "없음 — 전 구간 무결")
    )
    L += [
        "",
        "heap / GC pause / hikari active는 **관측하지 않는다**(ENVIRONMENT.md). "
        "따라서 \"자원 누수\" 판정은 이 시나리오가 내리지 않는다.",
        "위 시계열이 말할 수 있는 건 외부에서 보이는 열화와 잔여뿐이다.",
    ]


def render(label, rows):
    sustained = next((r for r in rows if r["part"] == "sustained"), None)
    waves = next((r for r in rows if r["part"] == "waves"), None)

    L = [
        f"# S6 Sustained Storm — {label}",
        "",
        "스파이크를 한 번 견디는 것과, 스파이크가 반복되는 하루를 버티는 것은 다른 문제다.",
        "S2가 \"언제 무너지나\"라면 이건 \"무너진 다음 어떻게 되나\"다.",
        "",
        "값은 회차 중앙값 (워밍업 런 제외).",
    ]
    if not sustained:
        L += ["", "> Part A(지속) 결과 없음 — `PART=sustained ./run.sh <label>` 미실행."]
    else:
        render_sustained(sustained, L)
    if not waves:
        L += ["", "> Part B(웨이브) 결과 없음 — `PART=waves ./run.sh <label>` 미실행."]
    else:
        render_waves(waves, L)

    render_verdict(sustained, waves, L)
    return "\n".join(L) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: aggregate.py <s6-sustained-storm/<label> 디렉터리>")
    root = Path(sys.argv[1]).resolve()
    by = collect(root)
    if not by:
        sys.exit(f"{root}/raw 에 집계할 결과가 없습니다.")
    # sustained를 먼저 싣는다 (Part A → Part B 순서).
    order = {"sustained": 0, "waves": 1}
    rows = [summarize(p, n, by[(p, n)]) for (p, n) in sorted(by, key=lambda k: (order[k[0]], k[1]))]
    md = render(root.name, rows)
    (root / "summary.json").write_text(
        json.dumps({"scenario": "s6-sustained-storm", "label": root.name, "parts": rows},
                   indent=2, ensure_ascii=False))
    (root / "summary.md").write_text(md)
    print(md)
    print(f"-> {root}/summary.md")


if __name__ == "__main__":
    main()
