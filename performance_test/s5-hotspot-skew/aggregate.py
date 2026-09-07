#!/usr/bin/env python3
"""S5 — Hotspot Skew 집계.

raw/kNNNN-iMM.json 을 경합점 수로 묶어 회차 중앙값 표를 만든다.
워밍업 런(warmup- 접두)은 파일명으로 걸러진다.

읽는 법: 총 도착률이 고정이므로 K가 늘 때 goodput이 오르면 병목은 락이다.
K를 늘려도 goodput이 평평하면 병목은 공유 자원(워커/커넥션)이지 락이 아니다.

사용법: python3 aggregate.py <s5-hotspot-skew/before 같은 디렉터리>
"""
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

RUN_RE = re.compile(r"^k(\d+)-i(\d+)\.json$")


def med(v):
    vals = [x for x in v if x is not None]
    return statistics.median(vals) if vals else 0.0


def med_opt(v):
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

    by_k = defaultdict(list)
    for f in sorted(raw.iterdir()):
        m = RUN_RE.match(f.name)
        if not m:
            continue
        rep = load_json(f)
        if rep is None:
            print(f"WARN: {f.name} 파싱 실패 — 건너뜀", file=sys.stderr)
            continue
        if not rep.get("valid", rep.get("requests", 0) > 0):
            print(f"WARN: {f.name} 요청 0건(측정 실패) — 제외", file=sys.stderr)
            continue
        stem = str(f.with_suffix(""))
        rep["_settle"] = (load_json(Path(f"{stem}-settle.json")) or {}).get("settleSeconds")
        rep["_integrity"] = load_json(Path(f"{stem}-integrity.json"))
        by_k[int(m.group(1))].append(rep)
    return by_k


def summarize(k, runs):
    g = lambda fn: [fn(r) for r in runs]
    over = sum(1 for r in runs if (r["_integrity"] or {}).get("overbookedSlots", 0) > 0)
    settles = [r["_settle"] for r in runs if r["_settle"] is not None]

    return {
        "points": k,
        "runs": len(runs),
        "targetRps": runs[0]["targetRps"],
        "offeredRps": med(g(lambda r: r.get("offeredRps", 0))),
        "achievedRps": med(g(lambda r: r["achievedRps"])),
        "allocationRps": med(g(lambda r: r.get("allocationRps", 0))),
        "allocationPerPoint": med(g(lambda r: r.get("allocationPerPoint", 0))),
        "selloutSeconds": med_opt(g(lambda r: r.get("selloutSeconds"))),
        "soldOutRuns": sum(1 for r in runs if r.get("soldOut")),
        "generatorLimitedRuns": sum(1 for r in runs if r.get("generatorLimited")),
        "goodputRps": med(g(lambda r: r["goodputRps"])),
        "goodputPerPoint": med(g(lambda r: r["goodputPerPoint"])),
        "attainment": med(g(lambda r: r["attainment"])),
        "dropped": med(g(lambda r: r["droppedIterations"])),
        "p50": med(g(lambda r: r["latencyMs"]["p50"])),
        "p95": med(g(lambda r: r["latencyMs"]["p95"])),
        "p99": med(g(lambda r: r["latencyMs"]["p99"])),
        "timeout": med(g(lambda r: r["outcome"]["timeout"])),
        "serverError5xx": med(g(lambda r: r["outcome"]["serverError5xx"])),
        "failureRate": med(g(lambda r: r["failureRate"])),
        "settleSeconds": med(settles),
        "overbookedRuns": over,
    }


def render(label, rows):
    target = rows[0]["targetRps"] if rows else "?"
    L = [
        f"# S5 Hotspot Skew — {label}",
        "",
        f"총 도착률 **{target} req/s 고정**. 경합점(분산락 키) 수 K만 바꾼다.",
        "경합점당 좌석도 고정이라, 락 하나가 보는 재고는 모든 레벨에서 같다.",
        "값은 회차 중앙값 (워밍업 런 제외).",
        "",
        "- K=1 은 B2C(굿즈 하나에 전원 집중), K=100 은 B2B(여러 지점에 분산)에 대응한다.",
        "",
        "**비교해야 하는 값은 `할당 req/s`다.** K 레벨마다 매진 시점이 달라서,",
        "전체 경과로 나눈 goodput은 매진 후 빠른 실패 구간이 긴 쪽을 부당하게 깎는다.",
        "할당 처리량은 재고가 있는 동안의 성공/초라 K끼리 공정하게 비교된다.",
        "",
        "`점당 할당`이 K가 늘어도 유지되면 락이 병목(분산이 효과 있음),",
        "K에 반비례해 떨어지면 공유 자원(워커·커넥션)이 천장이라는 뜻이다.",
        "",
        "| 경합점 K | 회차 | **할당 req/s** | 점당 할당 | 매진(s) | 제공 req/s | 달성 req/s | 도달률 | p50 | p95 | timeout | 5xx | 실패율 | dropped |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        sell = "미매진" if r["selloutSeconds"] is None else f"{r['selloutSeconds']:.0f}"
        L.append(
            f"| {r['points']} | {r['runs']} | **{r['allocationRps']:.0f}** | "
            f"{r['allocationPerPoint']:.1f} | {sell} | "
            f"{r['offeredRps']:.0f} | {r['achievedRps']:.0f} | {r['attainment'] * 100:.0f}% | "
            f"{r['p50']:.0f} | {r['p95']:.0f} | "
            f"{r['timeout']:.0f} | {r['serverError5xx']:.0f} | {r['failureRate'] * 100:.1f}% | "
            f"{r['dropped']:.0f} |"
        )

    gen_limited = [r for r in rows if r["generatorLimitedRuns"]]
    if gen_limited:
        L += [
            "",
            "> **주의 — 발생기 한계.** 아래 레벨에서 드롭된 요청이 완료 요청의 10%를 넘었다: "
            + ", ".join(f"K={r['points']}" for r in gen_limited)
            + ".",
            "> 서버 지연이 커지면 도착률 × 지연 만큼의 VU가 필요한데 그 상한에 걸린 것이다.",
            "> 이 레벨들은 **목표만큼의 부하를 실제로 받지 못했으므로**, 낮은 처리량을",
            "> \"서버가 못 버텼다\"로 읽으면 안 된다. `제공 req/s` 열을 함께 봐야 한다.",
        ]

    L += [
        "",
        "## 정합성 / 잔여 처리",
        "",
        "| 경합점 K | 오버부킹 발생 회차 | settle(s) |",
        "|---:|---:|---:|",
    ]
    for r in rows:
        flag = f"**{r['overbookedRuns']}/{r['runs']}**" if r["overbookedRuns"] else f"0/{r['runs']}"
        L.append(f"| {r['points']} | {flag} | {r['settleSeconds']:.1f} |")

    if len(rows) >= 2:
        lo, hi = rows[0], rows[-1]
        gain = hi["allocationRps"] / lo["allocationRps"] if lo["allocationRps"] else 0
        kgain = hi["points"] / lo["points"] if lo["points"] else 0

        if gain >= kgain * 0.7:
            verdict = "**락이 병목** — 분산도에 거의 선형으로 처리량이 붙는다"
        elif gain <= 1.2:
            verdict = "**락이 병목이 아님** — 분산해도 처리량이 늘지 않는다 (공유 자원 한계)"
        else:
            verdict = "**혼합** — 분산으로 개선되지만 선형에는 못 미친다 (다른 천장 존재)"

        # 포화점 = 이전 레벨 대비 할당 처리량 개선이 10% 미만인 첫 지점
        plateau = None
        for prev, cur in zip(rows, rows[1:]):
            if prev["allocationRps"] > 0 and cur["allocationRps"] / prev["allocationRps"] < 1.1:
                plateau = cur
                break

        L += [
            "",
            "## 판정",
            "",
            f"- 경합점 {lo['points']} → {hi['points']} ({kgain:.0f}배 분산)에서 "
            f"할당 처리량 {lo['allocationRps']:.0f} → {hi['allocationRps']:.0f} req/s (**{gain:.1f}배**)",
            f"- 점당 할당: {lo['allocationPerPoint']:.1f} → {hi['allocationPerPoint']:.1f} req/s",
            f"- p95: {lo['p95']:.0f}ms → {hi['p95']:.0f}ms",
            f"- {verdict}",
        ]
        if plateau:
            L.append(
                f"- **포화점**: 경합점 {plateau['points']} 부터 개선이 10% 미만 — "
                f"이 지점에서 락이 아닌 다른 자원이 천장이 된다."
            )
        else:
            L.append("- 측정 범위 내에서 개선이 멈추는 지점을 보지 못했다 (더 큰 K가 필요).")

        L.append(
            f"- **해석**: 이 시스템은 경합점 {hi['points']}개로 흩어진 트래픽(B2B형)은 "
            f"{hi['allocationRps']:.0f} req/s 를 할당하지만, 한 점에 몰린 트래픽(B2C형)은 "
            f"{lo['allocationRps']:.0f} req/s 로 떨어진다."
        )
        if gen_limited:
            L.append(
                "- 위 발생기 한계 주의를 함께 읽을 것. 드롭이 큰 레벨은 제공 부하 자체가 목표 미달이다."
            )

    return "\n".join(L) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: aggregate.py <s5-hotspot-skew/<label> 디렉터리>")
    root = Path(sys.argv[1]).resolve()
    by = collect(root)
    if not by:
        sys.exit(f"{root}/raw 에 집계할 결과가 없습니다.")
    rows = [summarize(n, by[n]) for n in sorted(by)]
    md = render(root.name, rows)
    (root / "summary.json").write_text(
        json.dumps({"scenario": "s5-hotspot-skew", "label": root.name, "levels": rows},
                   indent=2, ensure_ascii=False))
    (root / "summary.md").write_text(md)
    print(md)
    print(f"-> {root}/summary.md")


if __name__ == "__main__":
    main()
