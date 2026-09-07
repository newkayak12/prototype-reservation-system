#!/usr/bin/env python3
"""VU 레벨별 버스트 결과 집계.

run.sh가 performance_test/<label>/raw/ 아래 남긴 회차별 산출물
(vuNNNN-rMM.json / -settle.json / -integrity.txt)을 VU 레벨로 묶어
반복 회차에 걸친 중앙값 표를 만들고 summary.json + summary.md로 쓴다.

단일 회차 값이 아니라 중앙값을 쓰는 이유: 버스트 측정은 첫 회차에 JIT 워밍업/커넥션 풀
초기화가 섞여 튀기 때문에 한 회차만 보면 결론이 바뀐다.

사용법: python3 aggregate.py <performance_test/before 같은 디렉터리>
"""
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

RUN_RE = re.compile(r"^vu(\d+)-r(\d+)\.json$")


def median(values):
    return statistics.median(values) if values else 0.0


def read_settle(path):
    try:
        return json.loads(path.read_text()).get("settleSeconds", 0.0)
    except Exception:
        return None


def read_integrity(path):
    """-integrity.txt: '<시드좌석>\t<OCCUPIED수>\t<중복점유 timetable수>'"""
    try:
        parts = path.read_text().split()
        return int(parts[0]), int(parts[1]), int(parts[2])
    except Exception:
        return None


def collect(root: Path):
    raw = root / "raw"
    if not raw.is_dir():
        sys.exit(f"no raw/ directory under {root}")

    by_vu = defaultdict(list)
    for f in sorted(raw.iterdir()):
        m = RUN_RE.match(f.name)
        if not m:
            continue
        try:
            report = json.loads(f.read_text())
        except Exception as e:
            print(f"WARN: {f.name} 파싱 실패 ({e}) - 건너뜀", file=sys.stderr)
            continue
        # 요청 0건인 런은 "성공 0건"이 아니라 측정 실패(setup 실패 등)다. 섞으면 중앙값이 오염된다.
        if not report.get("valid", report.get("requests", 0) > 0):
            print(f"WARN: {f.name} 요청 0건(측정 실패) - 집계에서 제외", file=sys.stderr)
            continue
        stem = f.with_suffix("")
        report["_repeat"] = int(m.group(2))
        report["_settleSeconds"] = read_settle(Path(f"{stem}-settle.json"))
        report["_integrity"] = read_integrity(Path(f"{stem}-integrity.txt"))
        by_vu[int(m.group(1))].append(report)
    return by_vu


def summarize(vu, runs):
    def field(fn):
        return [fn(r) for r in runs]

    seats = runs[0].get("seatCount", 0)
    successes = field(lambda r: r["outcome"]["success"])
    settles = [r["_settleSeconds"] for r in runs if r["_settleSeconds"] is not None]

    # 정합성: OCCUPIED가 시드 좌석 수를 넘었거나, 한 timetable에 OCCUPIED가 2건 이상 붙은 회차 수.
    overbooked_runs, dup_runs, integrity_checked = 0, 0, 0
    for r in runs:
        if r["_integrity"] is None:
            continue
        integrity_checked += 1
        seeded, occupied, dups = r["_integrity"]
        if occupied > seeded:
            overbooked_runs += 1
        if dups > 0:
            dup_runs += 1

    return {
        "vus": vu,
        "runs": len(runs),
        "seatCount": seats,
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
        "soldOut409": median(field(lambda r: r["outcome"]["soldOut409"])),
        "semaphore423": median(field(lambda r: r["outcome"]["semaphoreBlocked423"])),
        "rateLimited429": median(field(lambda r: r["outcome"]["rateLimited429"])),
        "rejected400": median(field(lambda r: r["outcome"]["rejected400"])),
        "unauthorized401": sum(field(lambda r: r["outcome"]["unauthorized401"])),
        "serverError5xx": sum(field(lambda r: r["outcome"]["serverError5xx"])),
        "timeout": sum(field(lambda r: r["outcome"]["timeout"])),
        # 재설계 후에만 존재하는 버킷. before 산출물에는 키가 없으므로 0으로 떨어진다.
        "queueTimeout": median(field(lambda r: r["outcome"].get("queueTimeout", 0))),
        "queueEnterFailed": sum(field(lambda r: r["outcome"].get("queueEnterFailed", 0))),
        "queueWaitP95": median(
            field(lambda r: r.get("queue", {}).get("waitMs", {}).get("p95", 0))
        ),
        "settleSeconds": median(settles),
        "integrityChecked": integrity_checked,
        "overbookedRuns": overbooked_runs,
        "duplicateOccupancyRuns": dup_runs,
    }


def render_md(label, rows):
    seats = rows[0]["seatCount"] if rows else 0
    lines = [
        f"# 부하테스트 결과 - {label}",
        "",
        f"VU 레벨마다 좌석 {seats}석을 리셋하고, 해당 VU 수만큼의 유저가 **동시에 1번씩** 예약을",
        "시도하는 버스트를 반복 측정했다. 아래 값은 반복 회차의 **중앙값**이다.",
        "",
        "- **매진(s)**: 버스트 발사부터 마지막 좌석이 팔릴 때까지",
        "- **TPS**: 팔린 좌석 / 매진 시간 (= 실제 예약 성공 처리율)",
        "- **해소(s)**: 마지막 요청이 성공/거절 응답을 받을 때까지",
        "- **처리율**: 전체 요청 / 해소 시간 (= 시스템이 소화한 req/s)",
        "- **정착(s)**: HTTP 응답 이후 DB 점유 건수가 더 이상 변하지 않을 때까지",
        "",
        "| VU | 회차 | 성공/좌석 | 매진(s) | TPS | 해소(s) | 처리율(req/s) | p50(ms) | p95(ms) | p99(ms) | timeout | 5xx | 정착(s) | 오버부킹 |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|:--|",
    ]
    for r in rows:
        if r["integrityChecked"] == 0:
            integrity = "미검증"
        elif r["overbookedRuns"] or r["duplicateOccupancyRuns"]:
            integrity = f"**발생** ({r['overbookedRuns']}/{r['integrityChecked']}회)"
        else:
            integrity = f"없음 ({r['integrityChecked']}회)"
        success = f"{r['success']:.0f}/{r['seatCount']}"
        if r["successMin"] != r["successMax"]:
            success += f" ({r['successMin']}~{r['successMax']})"
        lines.append(
            f"| {r['vus']} | {r['runs']} | {success} | {r['selloutSeconds']:.2f} | "
            f"{r['tps']:.1f} | {r['resolveSeconds']:.2f} | {r['throughput']:.1f} | "
            f"{r['p50']:.0f} | {r['p95']:.0f} | {r['p99']:.0f} | {r['timeout']} | "
            f"{r['serverError5xx']} | {r['settleSeconds']:.2f} | {integrity} |"
        )

    has_queue = any(r.get("queueTimeout") or r.get("queueEnterFailed") for r in rows)

    lines += [
        "",
        "## 실패 사유 분포 (중앙값)",
        "",
        "| VU | 요청 | 성공 | 409 품절 | 423 세마포어 | 429 레이트리밋 | 400 기타 | 401 | timeout |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        lines.append(
            f"| {r['vus']} | {r['requests']:.0f} | {r['success']:.0f} | "
            f"{r['soldOut409']:.0f} | {r['semaphore423']:.0f} | {r['rateLimited429']:.0f} | "
            f"{r['rejected400']:.0f} | {r['unauthorized401']} | {r['timeout']} |"
        )

    if has_queue:
        lines += [
            "",
            "## 대기열 (재설계 후에만 존재하는 단계)",
            "",
            "`대기포기`는 입장 정원에 막혀 예약 단계까지 가지 못한 사용자다 - 거절도 실패도 아니고",
            "**아직 줄 서 있는 상태**다. 예약 지연이 좋아 보이는 데에는 이만큼의 부하를 대기열이",
            "막아 준 몫이 섞여 있으므로, 성공/거절 수치와 반드시 같이 읽어야 한다.",
            "",
            "| VU | 요청 | 입장 성공 | 대기포기 | 진입 실패 | 대기 p95(ms) |",
            "|---:|---:|---:|---:|---:|---:|",
        ]
        for r in rows:
            admitted = r["requests"] - r["queueTimeout"] - r["queueEnterFailed"]
            lines.append(
                f"| {r['vus']} | {r['requests']:.0f} | {admitted:.0f} | "
                f"{r['queueTimeout']:.0f} | {r['queueEnterFailed']} | {r['queueWaitP95']:.0f} |"
            )

    if any(r["rejected400"] for r in rows):
        lines += [
            "",
            "> **주의**: `RestControllerExceptionHandler`가 `ClientException` 전체를 400 하나로",
            "> 매핑하고 있어 품절(`AllTheSeatsAreAlreadyOccupied`)과 세마포어 획득 실패",
            "> (`AllTheThingsAreAlreadyOccupied`)가 `400 기타` 하나로 뭉쳐 있다. 예외 핸들러를",
            "> 409/423으로 세분화하면 위 표의 해당 칸이 그대로 채워진다.",
        ]
    return "\n".join(lines) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: aggregate.py <performance_test/<label> 디렉터리>")
    root = Path(sys.argv[1]).resolve()
    by_vu = collect(root)
    if not by_vu:
        sys.exit(f"{root}/raw 에 집계할 결과가 없습니다.")

    rows = [summarize(vu, by_vu[vu]) for vu in sorted(by_vu)]
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
