#!/usr/bin/env python3
"""경합 지점 수를 축으로 한 버스트 결과 집계.

run-multi.sh가 performance_test/<label>/raw/ 아래 남긴 회차별 산출물
(ptNNNN-rMM.json / -settle.json / -integrity.txt)을 지점 수로 묶어 중앙값 표를 만든다.

단일 슬롯 집계(aggregate.py)와 나눠 둔 이유: 축이 VU가 아니라 지점 수이고,
정합성 판정도 "슬롯 단위 초과"라는 항목이 하나 더 붙는다.

사용법: python3 aggregate-multi.py <performance_test/after 같은 디렉터리>
"""
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

RUN_RE = re.compile(r"^pt(\d+)-r(\d+)\.json$")


def median(values):
    return statistics.median(values) if values else 0.0


def read_settle(path):
    try:
        return json.loads(path.read_text()).get("settleSeconds", 0.0)
    except Exception:
        return None


def read_integrity(path):
    """'<시드좌석> <살아있는점유> <중복점유> <초과슬롯수>'"""
    try:
        p = path.read_text().split()
        return int(p[0]), int(p[1]), int(p[2]), int(p[3])
    except Exception:
        return None


def collect(root: Path):
    raw = root / "raw"
    if not raw.is_dir():
        sys.exit(f"no raw/ directory under {root}")

    by_points = defaultdict(list)
    for f in sorted(raw.iterdir()):
        m = RUN_RE.match(f.name)
        if not m:
            continue
        try:
            report = json.loads(f.read_text())
        except Exception as e:
            print(f"WARN: {f.name} 파싱 실패 ({e}) - 건너뜀", file=sys.stderr)
            continue
        if not report.get("valid", report.get("requests", 0) > 0):
            print(f"WARN: {f.name} 요청 0건(측정 실패) - 제외", file=sys.stderr)
            continue
        stem = f.with_suffix("")
        report["_settleSeconds"] = read_settle(Path(f"{stem}-settle.json"))
        report["_integrity"] = read_integrity(Path(f"{stem}-integrity.txt"))
        by_points[int(m.group(1))].append(report)
    return by_points


def summarize(points, runs):
    def field(fn):
        return [fn(r) for r in runs]

    successes = field(lambda r: r["outcome"]["success"])
    settles = [r["_settleSeconds"] for r in runs if r["_settleSeconds"] is not None]

    over_runs, dup_runs, checked = 0, 0, 0
    for r in runs:
        if r["_integrity"] is None:
            continue
        checked += 1
        _seeded, _occupied, dups, over_slots = r["_integrity"]
        if over_slots > 0:
            over_runs += 1
        if dups > 0:
            dup_runs += 1

    return {
        "contentionPoints": points,
        "runs": len(runs),
        "vus": runs[0].get("vus", 0),
        "vusPerPoint": runs[0].get("vusPerPoint", 0),
        "totalSeats": runs[0].get("totalSeats", 0),
        "skipQueue": runs[0].get("skipQueue", False),
        "requests": median(field(lambda r: r["requests"])),
        "success": median(successes),
        "successMin": min(successes),
        "successMax": max(successes),
        "selloutSeconds": median(field(lambda r: r["selloutSeconds"])),
        "tps": median(field(lambda r: r["tps"])),
        "resolveSeconds": median(field(lambda r: r["resolveSeconds"])),
        "throughput": median(field(lambda r: r["throughput"])),
        "p50": median(field(lambda r: r["latencyMs"]["p50"])),
        "p95": median(field(lambda r: r["latencyMs"]["p95"])),
        "p99": median(field(lambda r: r["latencyMs"]["p99"])),
        "maxLatency": max(field(lambda r: r["latencyMs"]["max"])),
        "rejected400": median(field(lambda r: r["outcome"]["rejected400"])),
        "unauthorized401": sum(field(lambda r: r["outcome"]["unauthorized401"])),
        "serverError5xx": sum(field(lambda r: r["outcome"]["serverError5xx"])),
        "timeout": sum(field(lambda r: r["outcome"]["timeout"])),
        "queueTimeout": median(field(lambda r: r["outcome"].get("queueTimeout", 0))),
        "queueWaitP95": median(
            field(lambda r: r.get("queue", {}).get("waitMs", {}).get("p95", 0))
        ),
        "settleSeconds": median(settles),
        "integrityChecked": checked,
        "overbookedRuns": over_runs,
        "duplicateOccupancyRuns": dup_runs,
    }


def render_md(label, rows):
    vus = rows[0]["vus"] if rows else 0
    skip = rows[0]["skipQueue"] if rows else False
    lines = [
        f"# 다중 경합 지점 결과 - {label}",
        "",
        f"VU {vus}명을 **고정**하고 경합 지점(음식점 x 슬롯) 수만 올렸다. 지점마다 좌석 30석을",
        "리셋하고, VU는 지점에 균등 분산돼 동시에 1번씩 예약을 시도한다. 값은 회차 중앙값이다.",
        "",
        f"- 대기열 단계: {'건너뜀 (before 아키텍처)' if skip else '포함 (재설계 후)'}",
        "- **지점당 VU**: VU / 경합 지점 = 지점 하나에 몰리는 인원",
        "- **해소(s)**: 마지막 요청이 종착점에 닿을 때까지",
        "- **처리율**: 전체 요청 / 해소 시간",
        "- **오버부킹**: 한 슬롯의 점유 건수가 그 슬롯 좌석 수를 넘은 회차",
        "",
        "| 경합지점 | 회차 | 지점당VU | 총좌석 | 성공 | 매진(s) | 해소(s) | 처리율(req/s) | p50(ms) | p95(ms) | p99(ms) | 5xx | 정착(s) | 오버부킹 |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|:--|",
    ]
    for r in rows:
        if r["integrityChecked"] == 0:
            integrity = "미검증"
        elif r["overbookedRuns"] or r["duplicateOccupancyRuns"]:
            integrity = f"**발생** ({r['overbookedRuns']}/{r['integrityChecked']}회)"
        else:
            integrity = f"없음 ({r['integrityChecked']}회)"
        success = f"{r['success']:.0f}"
        if r["successMin"] != r["successMax"]:
            success += f" ({r['successMin']}~{r['successMax']})"
        lines.append(
            f"| {r['contentionPoints']} | {r['runs']} | {r['vusPerPoint']:.0f} | "
            f"{r['totalSeats']} | {success} | {r['selloutSeconds']:.2f} | "
            f"{r['resolveSeconds']:.2f} | {r['throughput']:.1f} | {r['p50']:.0f} | "
            f"{r['p95']:.0f} | {r['p99']:.0f} | {r['serverError5xx']} | "
            f"{r['settleSeconds']:.2f} | {integrity} |"
        )

    if any(r["queueTimeout"] for r in rows):
        lines += [
            "",
            "## 대기열",
            "",
            "| 경합지점 | 요청 | 대기포기 | 대기 p95(ms) |",
            "|---:|---:|---:|---:|",
        ]
        for r in rows:
            lines.append(
                f"| {r['contentionPoints']} | {r['requests']:.0f} | "
                f"{r['queueTimeout']:.0f} | {r['queueWaitP95']:.0f} |"
            )

    return "\n".join(lines) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: aggregate-multi.py <performance_test/<label> 디렉터리>")
    root = Path(sys.argv[1]).resolve()
    by_points = collect(root)
    if not by_points:
        sys.exit(f"{root}/raw 에 집계할 결과가 없습니다.")

    rows = [summarize(p, by_points[p]) for p in sorted(by_points)]
    label = root.name

    (root / "summary.json").write_text(
        json.dumps({"label": label, "levels": rows}, indent=2, ensure_ascii=False)
    )
    md = render_md(label, rows)
    (root / "summary.md").write_text(md)
    print(md)
    print(f"-> {root}/summary.md, {root}/summary.json")


if __name__ == "__main__":
    main()
