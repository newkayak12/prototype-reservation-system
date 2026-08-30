#!/usr/bin/env python3
"""S2 — Arrival Storm 집계.

raw/rNNNNN-iMM.json 을 도착률로 묶어 회차 중앙값 표를 만든다.
워밍업 런(warmup- 접두)은 파일명으로 걸러진다.

중앙값을 쓰는 이유: 버스트 측정은 첫 회차에 JIT/커넥션 초기화가 섞여 튄다.

읽는 법 — 처리량은 두 개다:
  할당(allocation) = 재고가 있는 동안 실제로 좌석을 나눠준 속도. 이게 진짜 처리량.
  거절(rejection)  = 매진 후 부하를 털어내는 속도. 높다고 좋은 게 아니다.
"달성 req/s"만 보면 매진 후 빠른 실패가 섞여 시스템이 실제보다 유능해 보인다.

사용법: python3 aggregate.py <s2-arrival-storm/before 같은 디렉터리>
"""
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

RUN_RE = re.compile(r"^r(\d+)-i(\d+)\.json$")


def med(v):
    return statistics.median(v) if v else 0.0


def med_opt(v):
    """None이 섞인 계열의 중앙값. 전부 None이면 None."""
    vals = [x for x in v if x is not None]
    return statistics.median(vals) if vals else None


def load_json(p):
    try:
        return json.loads(p.read_text())
    except Exception:
        return None


def collect(root: Path):
    raw = root / "raw"
    if not raw.is_dir():
        sys.exit(f"raw/ 없음: {root}")

    by_rate = defaultdict(list)
    for f in sorted(raw.iterdir()):
        m = RUN_RE.match(f.name)
        if not m:
            continue
        rep = load_json(f)
        if rep is None:
            print(f"WARN: {f.name} 파싱 실패 — 건너뜀", file=sys.stderr)
            continue
        # 요청 0건은 "처리량 0"이 아니라 측정 실패(setup 예외 등)다. 집계에서 빼야 한다.
        if not rep.get("valid", rep.get("requests", 0) > 0):
            print(f"WARN: {f.name} 요청 0건(측정 실패) — 제외", file=sys.stderr)
            continue
        stem = str(f.with_suffix(""))
        rep["_settle"] = (load_json(Path(f"{stem}-settle.json")) or {}).get("settleSeconds")
        rep["_integrity"] = load_json(Path(f"{stem}-integrity.json"))
        by_rate[int(m.group(1))].append(rep)
    return by_rate


def summarize(rate, runs):
    g = lambda fn: [fn(r) for r in runs]
    over = sum(1 for r in runs if (r["_integrity"] or {}).get("overbookedSlots", 0) > 0)
    settles = [r["_settle"] for r in runs if r["_settle"] is not None]

    return {
        "targetRps": rate,
        "runs": len(runs),
        "offeredRps": med(g(lambda r: r["offeredRps"])),
        "achievedRps": med(g(lambda r: r["achievedRps"])),
        "attainment": med(g(lambda r: r["attainment"])),
        "allocationRps": med(g(lambda r: r["allocationRps"])),
        "rejectionRps": med_opt(g(lambda r: r.get("rejectionRps"))),
        "selloutSeconds": med_opt(g(lambda r: r.get("selloutSeconds"))),
        "soldOutRuns": sum(1 for r in runs if r.get("soldOut")),
        "preSelloutP95": med(g(lambda r: r.get("preSelloutP95", 0))),
        "postSelloutP95": med_opt(g(lambda r: r.get("postSelloutP95"))),
        "dropped": med(g(lambda r: r["droppedIterations"])),
        "requests": med(g(lambda r: r["requests"])),
        "success": med(g(lambda r: r["outcome"]["success"])),
        "p50": med(g(lambda r: r["latencyMs"]["p50"])),
        "p95": med(g(lambda r: r["latencyMs"]["p95"])),
        "p99": med(g(lambda r: r["latencyMs"]["p99"])),
        "maxLatency": max(g(lambda r: r["latencyMs"]["max"])),
        "timeout": med(g(lambda r: r["outcome"]["timeout"])),
        "serverError5xx": med(g(lambda r: r["outcome"]["serverError5xx"])),
        "failureRate": med(g(lambda r: r["failureRate"])),
        "settleSeconds": med(settles),
        "overbookedRuns": over,
        # 시계열은 대표 런(첫 회차) 하나만 싣는다. 회차별로 매진 시점이 조금씩 달라서
        # 평균을 내면 국면 전환의 계단이 뭉개진다.
        "series": runs[0].get("series", []),
    }


def num(x, fmt="{:.0f}", dash="—"):
    return dash if x is None else fmt.format(x)


def render(label, rows):
    L = [
        f"# S2 Arrival Storm — {label}",
        "",
        "경합점 1개, 좌석 고정. 도착률만 올린다(개방형 부하).",
        "값은 회차 중앙값 (워밍업 런 제외).",
        "",
        "**처리량은 두 개다. 하나로 말하면 거짓말이 된다.**",
        "",
        "- **할당 req/s** — 재고가 있는 동안 실제로 좌석을 나눠준 속도. 사용자에게 의미 있는 유일한 처리량.",
        "- **거절 req/s** — 매진된 뒤 부하를 털어내는 속도. 이 경로는 락을 잡되 읽을 게 없어 훨씬 싸다.",
        "  높다고 좋은 게 아니라, \"이만큼은 최소한 안 죽고 거절은 한다\"는 뜻이다.",
        "- **달성 req/s** — 위 둘이 섞인 값. 매진이 빠를수록 거절이 섞여 시스템이 실제보다 유능해 보인다.",
        "- **dropped** — k6가 VU를 못 구해 발사조차 못한 수. 0이 아니면 그 칸의 실제 제공 부하는 목표보다 낮다.",
        "",
        "| 목표 req/s | 회차 | **할당 req/s** | 매진(s) | 거절 req/s | 달성 req/s | 도달률 | p95 매진전 | p95 매진후 | p99 | timeout | 5xx | 실패율 | dropped |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        L.append(
            f"| {r['targetRps']:,} | {r['runs']} | **{r['allocationRps']:.0f}** | "
            f"{num(r['selloutSeconds'])} | {num(r['rejectionRps'])} | "
            f"{r['achievedRps']:.0f} | {r['attainment'] * 100:.0f}% | "
            f"{r['preSelloutP95']:.0f} | {num(r['postSelloutP95'])} | {r['p99']:.0f} | "
            f"{r['timeout']:.0f} | {r['serverError5xx']:.0f} | "
            f"{r['failureRate'] * 100:.1f}% | {r['dropped']:.0f} |"
        )

    L += [
        "",
        "## 정합성 / 잔여 처리",
        "",
        "| 목표 req/s | 오버부킹 발생 회차 | 매진된 회차 | settle(s) |",
        "|---:|---:|---:|---:|",
    ]
    for r in rows:
        flag = f"**{r['overbookedRuns']}/{r['runs']}**" if r["overbookedRuns"] else f"0/{r['runs']}"
        L.append(f"| {r['targetRps']:,} | {flag} | {r['soldOutRuns']}/{r['runs']} | {r['settleSeconds']:.1f} |")

    # 초 단위 시계열 — 국면 전환이 눈에 보이는 게 이 시나리오의 핵심 산출물이다.
    L += [
        "",
        "## 초 단위 시계열 (대표 런)",
        "",
        "성공이 끊기는 순간이 매진 시점이다. 그 뒤로 요청 수가 뛰면 빠른 실패 경로로 전환된 것이다.",
    ]
    for r in rows:
        if not r["series"]:
            continue
        L += [
            "",
            f"### 목표 {r['targetRps']:,} req/s",
            "",
            "| t(s) | 요청 | 성공 | p50 | p95 |",
            "|---:|---:|---:|---:|---:|",
        ]
        for s in r["series"]:
            mark = "" if s["success"] else " "
            L.append(
                f"| {s['sec']}{mark} | {s['requests']} | {s['success']} | "
                f"{s['p50']:.0f} | {s['p95']:.0f} |"
            )

    # 판정
    if rows:
        peak = max(rows, key=lambda r: r["allocationRps"])
        knee = next((r for r in rows if r["attainment"] < 0.9), None)
        collapse = next((r for r in rows if r["failureRate"] > 0.05), None)

        L += ["", "## 판정", ""]
        L.append(
            f"- **할당 처리량 상한**: 목표 {peak['targetRps']:,} req/s 에서 "
            f"{peak['allocationRps']:.0f} req/s. 도착률을 더 올려도 이 값은 오르지 않는다."
        )

        allocs = [r["allocationRps"] for r in rows]
        if allocs and max(allocs) > 0 and min(allocs) / max(allocs) > 0.8:
            L.append(
                "- **할당 처리량이 도착률과 무관하게 평평하다.** 부하를 아무리 늘려도 "
                "좌석을 나눠주는 속도는 그대로다 — 직렬 임계구간이 천장이라는 뜻이다."
            )

        if knee:
            L.append(
                f"- **무릎(포화 시작)**: {knee['targetRps']:,} req/s — 도달률 "
                f"{knee['attainment'] * 100:.0f}%. 이 지점부터 요청이 쌓인다."
            )
        else:
            L.append("- 측정 범위 내에서 도달률 90% 아래로 떨어지는 칸이 없었다.")

        if collapse:
            L.append(
                f"- **붕괴점**: {collapse['targetRps']:,} req/s — 실패율 "
                f"{collapse['failureRate'] * 100:.1f}% (timeout {collapse['timeout']:.0f}, "
                f"5xx {collapse['serverError5xx']:.0f}), p99 {collapse['p99']:.0f}ms"
            )
        else:
            L.append("- 측정 범위 내에서 실패율 5%를 넘는 칸이 없었다 (붕괴 미관측).")

        over_any = [r for r in rows if r["overbookedRuns"]]
        detail = ", ".join("{:,} req/s".format(r["targetRps"]) for r in over_any)
        L.append(
            f"- **정합성**: 오버부킹 발생 칸 {len(over_any)}개"
            + (f" ({detail})" if over_any else " — 전 구간 무결. 느려질 뿐 틀리지는 않는다.")
        )

    return "\n".join(L) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: aggregate.py <s2-arrival-storm/<label> 디렉터리>")
    root = Path(sys.argv[1]).resolve()
    by = collect(root)
    if not by:
        sys.exit(f"{root}/raw 에 집계할 결과가 없습니다.")
    rows = [summarize(n, by[n]) for n in sorted(by)]
    md = render(root.name, rows)
    (root / "summary.json").write_text(
        json.dumps({"scenario": "s2-arrival-storm", "label": root.name, "steps": rows},
                   indent=2, ensure_ascii=False))
    (root / "summary.md").write_text(md)
    print(md)
    print(f"-> {root}/summary.md")


if __name__ == "__main__":
    main()
