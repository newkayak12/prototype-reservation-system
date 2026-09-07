#!/usr/bin/env python3
"""S8 — Inventory Scale 집계.

raw/nNNNNNN-rMM.json 을 좌석 수로 묶어 회차 중앙값 표를 만든다.
워밍업 런(warmup- 접두)은 파일명으로 걸러진다.

중앙값을 쓰는 이유: 버스트 측정은 첫 회차에 JIT/커넥션 풀 초기화가 섞여 튄다.

사용법: python3 aggregate.py <s8-inventory-scale/before 같은 디렉터리>
"""
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

RUN_RE = re.compile(r"^n(\d+)-r(\d+)\.json$")


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

    by_seats = defaultdict(list)
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
        by_seats[int(m.group(1))].append(rep)
    return by_seats


def summarize(seats, runs):
    g = lambda fn: [fn(r) for r in runs]

    over = sum(1 for r in runs if (r["_integrity"] or {}).get("overbookedSlots", 0) > 0)
    unsold = med([(r["_integrity"] or {}).get("unsoldSlots", 0) for r in runs])
    settles = [r["_settle"] for r in runs if r["_settle"] is not None]

    return {
        "seats": seats,
        "runs": len(runs),
        "vus": runs[0]["vus"],
        "success": med(g(lambda r: r["outcome"]["success"])),
        "criticalSectionMs": med(g(lambda r: r["criticalSectionMs"])),
        "serialCeilingRps": med(g(lambda r: r["serialCeilingRps"])),
        "selloutSeconds": med(g(lambda r: r["selloutSeconds"])),
        "resolveSeconds": med(g(lambda r: r["resolveSeconds"])),
        "throughputRps": med(g(lambda r: r["throughputRps"])),
        "p50": med(g(lambda r: r["latencyMs"]["p50"])),
        "p95": med(g(lambda r: r["latencyMs"]["p95"])),
        "p99": med(g(lambda r: r["latencyMs"]["p99"])),
        "maxLatency": max(g(lambda r: r["latencyMs"]["max"])),
        "timeout": sum(g(lambda r: r["outcome"]["timeout"])),
        "serverError5xx": sum(g(lambda r: r["outcome"]["serverError5xx"])),
        "settleSeconds": med(settles),
        "overbookedRuns": over,
        "unsoldSlots": unsold,
    }


def render(label, rows):
    base = rows[0] if rows else None
    L = [
        f"# S8 Inventory Scale — {label}",
        "",
        f"경합점 1개, VU {base['vus'] if base else '?'} 고정. 좌석 수 N만 바꾼다.",
        "값은 회차 중앙값 (워밍업 런 제외).",
        "",
        "**임계구간 = 매진시간 / 판매좌석수.** 분산락이 완전 직렬이므로 이 값이",
        "성공 경로 한 건의 직렬 처리 시간이고, 그 역수가 직렬 처리 상한이다.",
        "",
        "| 좌석 N | 회차 | 성공 | **임계구간(ms)** | **직렬상한(req/s)** | 매진(s) | 해소(s) | p50 | p95 | p99 | timeout | 5xx |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        L.append(
            f"| {r['seats']:,} | {r['runs']} | {r['success']:.0f} | "
            f"**{r['criticalSectionMs']:.2f}** | **{r['serialCeilingRps']:.0f}** | "
            f"{r['selloutSeconds']:.3f} | {r['resolveSeconds']:.2f} | "
            f"{r['p50']:.0f} | {r['p95']:.0f} | {r['p99']:.0f} | "
            f"{r['timeout']} | {r['serverError5xx']} |"
        )


    L += [
        "",
        "## 정합성",
        "",
        "| 좌석 N | 오버부킹 발생 회차 | 미판매 슬롯(중앙값) |",
        "|---:|---:|---:|",
    ]
    for r in rows:
        flag = f"**{r['overbookedRuns']}/{r['runs']}**" if r["overbookedRuns"] else f"0/{r['runs']}"
        L.append(f"| {r['seats']:,} | {flag} | {r['unsoldSlots']:.0f} |")

    # H6 판정
    if len(rows) >= 2:
        lo, hi = rows[0], rows[-1]
        ratio = hi["criticalSectionMs"] / lo["criticalSectionMs"] if lo["criticalSectionMs"] else 0
        verdict = (
            "**H6 지지** — 임계구간이 재고에 따라 증가"
            if ratio >= 5
            else ("**H6 반증** — 임계구간이 재고에 거의 무관 (±20% 이내)"
                  if 0.8 <= ratio <= 1.2
                  else "**부분 지지** — 증가하나 5배 미만")
        )
        L += [
            "",
            "## 판정",
            "",
            f"- 좌석 {lo['seats']:,} → {hi['seats']:,} 에서 임계구간 "
            f"{lo['criticalSectionMs']:.2f}ms → {hi['criticalSectionMs']:.2f}ms (**{ratio:.1f}배**)",
            f"- 직렬 상한 {lo['serialCeilingRps']:.0f} → {hi['serialCeilingRps']:.0f} req/s",
            f"- {verdict}",
        ]
        crossed = [r for r in rows if r["serialCeilingRps"] < 200]
        if crossed:
            L.append(
                f"- **교차점**: 좌석 {crossed[0]['seats']:,} 부터 before의 직렬 상한이 "
                f"after의 대기열 상한(200 req/s) 아래로 내려간다."
            )
        else:
            L.append("- 측정 범위 내에서 before 직렬 상한이 200 req/s 아래로 내려가지 않았다.")

    return "\n".join(L) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: aggregate.py <s8-inventory-scale/<label> 디렉터리>")
    root = Path(sys.argv[1]).resolve()
    by = collect(root)
    if not by:
        sys.exit(f"{root}/raw 에 집계할 결과가 없습니다.")
    rows = [summarize(n, by[n]) for n in sorted(by)]
    md = render(root.name, rows)
    (root / "summary.json").write_text(
        json.dumps({"scenario": "s8-inventory-scale", "label": root.name, "levels": rows},
                   indent=2, ensure_ascii=False))
    (root / "summary.md").write_text(md)
    print(md)
    print(f"-> {root}/summary.md")


if __name__ == "__main__":
    main()
