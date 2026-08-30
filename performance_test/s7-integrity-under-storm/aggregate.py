#!/usr/bin/env python3
"""S7 — Integrity Under Storm 집계.

raw/{extreme|storm}-iNNN.json 을 파트로 묶는다. 워밍업 런(warmup- 접두)은 파일명으로 걸러진다.

다른 시나리오와 집계 방식이 다르다. 여기서 중앙값은 위험한 요약이다 —
30회 중 1회에서만 터지는 오버부킹은 중앙값에서 완전히 사라지는데, 그건 여전히 결함이다.
그래서 속도 지표만 중앙값으로 요약하고, **결함은 전부 "발생 회차 수"로 센다.**

핵심 대조: 클라이언트가 받은 200/201 수  vs  정착 후 DB에 살아 있는 점유 행 수.
두 방향의 불일치는 서로 다른 결함이므로 절대 합치지 않는다.

  DB > 200  → 오버부킹.   팔지 않은 좌석이 팔렸다.
  200 > DB  → 유령 성공.  성공이라 답해놓고 그 좌석을 잃었다. 오버부킹보다 나쁘다.

사용법: python3 aggregate.py <s7-integrity-under-storm/before 같은 디렉터리>
"""
import json
import re
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path

RUN_RE = re.compile(r"^(extreme|storm)-i(\d+)\.json$")
PART_ORDER = ["extreme", "storm"]


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
        # 요청 0건은 "성공 0건"이 아니라 측정 실패(setup 예외 등)다. 집계에서 빼야 한다.
        if not rep.get("valid", rep.get("requests", 0) > 0):
            print(f"WARN: {f.name} 요청 0건(측정 실패) — 제외", file=sys.stderr)
            continue
        stem = str(f.with_suffix(""))
        integ = load_json(Path(f"{stem}-integrity.json"))
        if integ is None:
            # 정합성 파일이 없으면 이 런은 정합성에 대해 아무 말도 할 수 없다.
            # 속도만 남기고 판정에 끼워 넣으면 "결함 0회차"를 조용히 부풀린다.
            print(f"WARN: {f.name} integrity 결과 없음 — 제외", file=sys.stderr)
            continue
        rep["_run"] = int(m.group(2))
        rep["_integrity"] = integ
        rep["_settle"] = (load_json(Path(f"{stem}-settle.json")) or {}).get("settleSeconds")
        by_part[m.group(1)].append(rep)
    return by_part


def verdict_of(rep):
    """한 회차의 정합성 판정. 두 방향의 불일치를 각각 따로 낸다."""
    integ = rep["_integrity"]
    seeded = integ.get("seeded", 0)
    db_alive = integ.get("sold", 0)
    ok = rep.get("successResponses", rep.get("outcome", {}).get("success", 0))

    return {
        "run": rep["_run"],
        "seeded": seeded,
        "successResponses": ok,
        "dbAlive": db_alive,
        # 방향 1 — DB가 200보다 많다. 응답으로 약속하지 않은 좌석이 잡혀 있다.
        "overbookedRows": max(0, db_alive - ok),
        # 방향 2 — 200이 DB보다 많다. 성공을 알린 뒤 그 점유를 잃었다.
        "phantomSuccess": max(0, ok - db_alive),
        # 재고 자체를 넘겼는가 (슬롯 총량 기준)
        "oversoldVsSeeded": max(0, db_alive - seeded),
        # 한 슬롯에 유효 점유가 2건 이상 — integrity.sh가 슬롯 단위로 센 값
        "overbookedSlots": integ.get("overbookedSlots", 0),
        # 수요가 재고를 압도하는 실험이므로 미판매는 전부 기회 손실 = 결함이다
        "unsoldSlots": integ.get("unsoldSlots", 0),
        # 응답을 못 받아 DB 대조로 판정할 수 없는 요청 수
        "ambiguousRequests": rep.get("ambiguousRequests", 0),
        "timeout": rep.get("outcome", {}).get("timeout", 0),
        "interrupted": rep.get("interruptedIterations", 0),
        "alivePredicate": integ.get("alivePredicate", "?"),
        "settleSeconds": rep["_settle"],
    }


DEFECTS = [
    ("oversoldVsSeeded", "초과 판매(DB>시드)"),
    ("overbookedSlots", "슬롯 중복"),
    ("overbookedRows", "오버부킹(DB>200)"),
    ("phantomSuccess", "유령 성공(200>DB)"),
    ("unsoldSlots", "미판매"),
]


def summarize(part, runs):
    v = [verdict_of(r) for r in runs]
    settles = [x["settleSeconds"] for x in v if x["settleSeconds"] is not None]

    row = {
        "part": part,
        "runs": len(v),
        "seeded": med([x["seeded"] for x in v]),
        "requests": med([r["requests"] for r in runs]),
        "successResponses": med([x["successResponses"] for x in v]),
        "dbAlive": med([x["dbAlive"] for x in v]),
        "p95": med([r["latencyMs"]["p95"] for r in runs]),
        "p99": med([r["latencyMs"]["p99"] for r in runs]),
        "serverError5xx": med([r["outcome"]["serverError5xx"] for r in runs]),
        "settleSeconds": med(settles),
        "maxSettleSeconds": max(settles) if settles else 0.0,
        # 결함은 중앙값이 아니라 회차 수와 최댓값으로 센다.
        "ambiguousRuns": sum(1 for x in v if x["ambiguousRequests"] > 0),
        "maxAmbiguous": max([x["ambiguousRequests"] for x in v] or [0]),
        # 1석 경합에서 정상은 정확히 1. 분포를 그대로 남긴다.
        "successDistribution": dict(sorted(Counter(x["successResponses"] for x in v).items())),
        "_verdicts": v,
    }
    for key, _ in DEFECTS:
        row[key + "Runs"] = sum(1 for x in v if x[key] > 0)
        row["max" + key[0].upper() + key[1:]] = max([x[key] for x in v] or [0])
    return row


def render(label, rows):
    L = [
        f"# S7 Integrity Under Storm — {label}",
        "",
        "속도가 아니라 정확성을 재는 실험이다. 결론을 바꾸는 대조는 하나뿐이다:",
        "**클라이언트가 받은 200/201 수** vs **정착 후 DB에 살아 있는 점유 행 수.**",
        "",
        "- `DB > 200` → **오버부킹**. 응답으로 약속하지 않은 좌석이 잡혀 있다.",
        "- `200 > DB` → **유령 성공**. 성공이라 답한 뒤 그 좌석을 잃었다. 오버부킹보다 나쁘다.",
        "",
        "결함은 중앙값으로 요약하지 않는다 — 30회 중 1회만 터져도 결함이다. **발생 회차 수**로 센다.",
        "속도 지표(중앙값)는 어떤 상태에서 정합성을 봤는지 알기 위한 맥락일 뿐이다.",
        "",
        "| 파트 | 회차 | 시드 좌석 | 요청 | 성공 응답 | DB 유효 점유 | p95 | p99 | 5xx | settle(s) |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        L.append(
            f"| {r['part']} | {r['runs']} | {r['seeded']:.0f} | {r['requests']:.0f} | "
            f"**{r['successResponses']:.0f}** | **{r['dbAlive']:.0f}** | "
            f"{r['p95']:.0f} | {r['p99']:.0f} | {r['serverError5xx']:.0f} | "
            f"{r['settleSeconds']:.1f} |"
        )

    L += [
        "",
        "## 결함 발생 회차 (핵심)",
        "",
        "`0/N` 이 아닌 칸이 하나라도 있으면 그 파트는 정합성을 지키지 못한 것이다.",
        "",
        "| 파트 | " + " | ".join(name for _, name in DEFECTS) + " | 판정 불가 회차 |",
        "|---|" + "---:|" * (len(DEFECTS) + 1),
    ]
    for r in rows:
        cells = []
        for key, _ in DEFECTS:
            n = r[key + "Runs"]
            mx = r["max" + key[0].upper() + key[1:]]
            cells.append(f"**{n}/{r['runs']}** (최대 {mx})" if n else f"0/{r['runs']}")
        amb = (
            f"**{r['ambiguousRuns']}/{r['runs']}** (최대 {r['maxAmbiguous']}건)"
            if r["ambiguousRuns"]
            else f"0/{r['runs']}"
        )
        L.append(f"| {r['part']} | " + " | ".join(cells) + f" | {amb} |")

    L += [
        "",
        "판정 불가 = 응답을 받지 못한 요청(timeout / k6 중단)이 있던 회차. 그 요청이 DB에",
        "반영됐는지 클라이언트가 알 수 없으므로, 그 회차의 200 대 DB 불일치는 결함으로",
        "단정할 수 없다. 0이어야 판정이 깨끗하다.",
        "",
    ]

    # 결함이 있는 회차만 원본 그대로 펼친다. 요약에 가려지면 안 되는 정보다.
    bad = [
        (r["part"], x)
        for r in rows
        for x in r["_verdicts"]
        if any(x[key] > 0 for key, _ in DEFECTS)
    ]
    L += ["## 결함 회차 상세", ""]
    if not bad:
        L += ["결함이 발생한 회차 없음.", ""]
    else:
        L += [
            "| 파트 | 회차 | 시드 | 성공 응답 | DB | DB-200 | 200-DB | 슬롯 중복 | 미판매 | timeout | 중단 |",
            "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
        ]
        for part, x in bad:
            L.append(
                f"| {part} | {x['run']} | {x['seeded']} | {x['successResponses']} | "
                f"{x['dbAlive']} | {x['overbookedRows']} | {x['phantomSuccess']} | "
                f"{x['overbookedSlots']} | {x['unsoldSlots']} | {x['timeout']} | "
                f"{x['interrupted']} |"
            )
        L.append("")

    ext = next((r for r in rows if r["part"] == "extreme"), None)
    if ext:
        L += [
            "## extreme — 성공 응답 수 분포",
            "",
            "좌석 1석에 VU 3000. 정상적인 결과는 **정확히 1**이다. 2 이상이면 오버부킹,",
            "0이면 아무도 못 산 것(과소 판매)이다.",
            "",
            "| 성공 응답 수 | 회차 |",
            "|---:|---:|",
        ]
        for k, n in ext["successDistribution"].items():
            mark = "" if k == 1 else "  ← 비정상"
            L.append(f"| {k}{mark} | {n} |")
        L.append("")

    # --- 판정 -----------------------------------------------------------------
    L += ["## 판정", ""]
    defect_name = dict(DEFECTS)
    over_keys = ["oversoldVsSeeded", "overbookedSlots", "overbookedRows"]
    over_runs = sum(r[k + "Runs"] for r in rows for k in over_keys)
    phantom_runs = sum(r["phantomSuccessRuns"] for r in rows)
    unsold_runs = sum(r["unsoldSlotsRuns"] for r in rows)
    amb_runs = sum(r["ambiguousRuns"] for r in rows)
    total_runs = sum(r["runs"] for r in rows)

    if over_runs == 0:
        L.append(
            f"- **H5 지지 — 오버부킹 0.** 총 {total_runs}회차 전부에서 "
            "초과 판매·슬롯 중복·`DB>200` 모두 0이었다. "
            "이 구성은 극한 경합에서 좌석을 두 번 팔지 않는다."
        )
    else:
        detail = ", ".join(
            f"{r['part']} {defect_name[k]} {r[k + 'Runs']}/{r['runs']}회차"
            for r in rows
            for k in over_keys
            if r[k + "Runs"]
        )
        L.append(
            f"- **H5 반증 — 오버부킹 관측.** ({detail}) "
            "정합성이 재설계 명분에 포함된다. 결함 회차 상세를 근거로 쓸 것."
        )

    if phantom_runs == 0:
        L.append(
            f"- **응답/상태 일치.** 성공 응답 수와 정착 후 DB 점유 수가 {total_runs}회차 "
            "전부에서 어긋나지 않았다. 사용자에게 성공을 알리고 좌석을 잃은 사례는 없다."
        )
    else:
        L.append(
            f"- **유령 성공 관측 — {phantom_runs}회차.** 성공 응답을 보낸 뒤 DB에 그 점유가 "
            "없었다. 오버부킹보다 심각하다: 사용자는 예약이 된 줄 안다."
        )

    if unsold_runs:
        L.append(
            f"- **과소 판매 — {unsold_runs}회차.** 수요가 재고를 압도하는 조건인데 미판매 "
            "슬롯이 남았다. 오버부킹은 아니지만 기회 손실이고, 결함으로 기록해야 한다."
        )

    if amb_runs:
        L.append(
            f"- ⚠ **판정 불가 요청이 있는 회차 {amb_runs}개.** 응답을 못 받은 요청은 DB "
            "반영 여부를 알 수 없다. 위 불일치 판정을 그 폭만큼 보수적으로 읽어야 한다."
        )

    preds = sorted({x["alivePredicate"] for r in rows for x in r["_verdicts"]})
    L.append(f"- 유효 점유 판정식: `{', '.join(preds)}`")
    max_settle = max((r["maxSettleSeconds"] for r in rows), default=0.0)
    L.append(
        f"- 정착 대기 최대 {max_settle:.1f}s — 정합성 검사는 전부 이 대기 **후**에 했다. "
        "대기 전에 셌다면 비동기 지연이 오버부킹/유령 성공으로 잘못 잡혔을 것이다."
    )

    return "\n".join(L) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: aggregate.py <s7-integrity-under-storm/<label> 디렉터리>")
    root = Path(sys.argv[1]).resolve()
    by = collect(root)
    if not by:
        sys.exit(f"{root}/raw 에 집계할 결과가 없습니다.")
    rows = [summarize(p, by[p]) for p in PART_ORDER if p in by]
    md = render(root.name, rows)

    payload = {
        "scenario": "s7-integrity-under-storm",
        "label": root.name,
        "parts": [
            {**{k: v for k, v in r.items() if k != "_verdicts"}, "perRun": r["_verdicts"]}
            for r in rows
        ],
    }
    (root / "summary.json").write_text(json.dumps(payload, indent=2, ensure_ascii=False))
    (root / "summary.md").write_text(md)
    print(md)
    print(f"-> {root}/summary.md")


if __name__ == "__main__":
    main()
