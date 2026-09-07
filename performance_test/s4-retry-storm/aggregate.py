#!/usr/bin/env python3
"""S4 — Retry Storm 집계.

raw/pPOLICY-iMM.json 을 재시도 정책으로 묶어 회차 중앙값 표를 만든다.
워밍업 런(warmup- 접두)은 파일명으로 걸러진다.

중앙값을 쓰는 이유: 버스트 측정은 첫 회차에 JIT/커넥션 초기화가 섞여 튄다.

이 시나리오의 결론은 단 하나의 비교다:
    유효 처리량(고유 사용자 기준 성공 수/초)이 NO_RETRY → RETRY_FOREVER 로 갈수록 감소하는가.
부하를 더 넣었는데 처리량이 줄어들면 그게 congestion collapse다.

사용법: python3 aggregate.py <s4-retry-storm/before 같은 디렉터리>
"""
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

RUN_RE = re.compile(r"^p([A-Z_]+)-i(\d+)\.json$")

# 표를 알파벳순으로 두면 NO_RETRY < RETRY_FOREVER < RETRY_ONCE 가 되어 "재시도 강도"라는
# 축이 깨진다. 판정이 인접 행 비교라서 순서 자체가 의미를 갖는다.
POLICY_ORDER = ["NO_RETRY", "RETRY_ONCE", "RETRY_FOREVER"]

# 유효 처리량이 기준선의 이 비율 아래로 떨어지면 "감소"로 본다.
# 회차 중앙값이라도 ±수 % 는 흔들리므로, 그 폭보다 큰 문턱을 둔다.
COLLAPSE_RATIO = 0.90


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

    by_policy = defaultdict(list)
    for f in sorted(raw.iterdir()):
        m = RUN_RE.match(f.name)
        if not m:
            continue
        rep = load_json(f)
        if rep is None:
            print(f"WARN: {f.name} 파싱 실패 — 건너뜀", file=sys.stderr)
            continue
        # 사용자 0명은 "처리량 0"이 아니라 측정 실패(setup 예외 등)다. 집계에서 빼야 한다.
        if not rep.get("valid", False):
            print(f"WARN: {f.name} 사용자 0명(측정 실패) — 제외", file=sys.stderr)
            continue
        stem = str(f.with_suffix(""))
        rep["_settle"] = (load_json(Path(f"{stem}-settle.json")) or {}).get("settleSeconds")
        rep["_integrity"] = load_json(Path(f"{stem}-integrity.json"))
        by_policy[m.group(1)].append(rep)
    return by_policy


def summarize(policy, runs):
    g = lambda fn: [fn(r) for r in runs]
    over = sum(1 for r in runs if (r["_integrity"] or {}).get("overbookedSlots", 0) > 0)
    settles = [r["_settle"] for r in runs if r["_settle"] is not None]

    return {
        "policy": policy,
        "runs": len(runs),
        "maxAttempts": runs[0].get("maxAttempts", 1),
        "targetUserRps": runs[0].get("targetUserRps", 0),
        "usersLaunched": med(g(lambda r: r["users"]["launched"])),
        "usersSuccess": med(g(lambda r: r["users"]["success"])),
        "usersDropped": med(g(lambda r: r["users"]["dropped"])),
        "usersGaveUp": med(g(lambda r: r["users"]["gaveUp"])),
        "usersSoldOut": med(g(lambda r: r["users"]["soldOut"])),
        "userAttainment": med(g(lambda r: r["userAttainment"])),
        "effectiveThroughputRps": med(g(lambda r: r["effectiveThroughputRps"])),
        "userSuccessRate": med(g(lambda r: r["userSuccessRate"])),
        "userCompletionP50": med(g(lambda r: r["userCompletionMs"]["p50"])),
        "userCompletionP95": med(g(lambda r: r["userCompletionMs"]["p95"])),
        "attemptsAvg": med(g(lambda r: r["attemptsPerUser"]["avg"])),
        "requests": med(g(lambda r: r["requests"])),
        "requestRps": med(g(lambda r: r["requestRps"])),
        "amplification": med(g(lambda r: r["amplification"])),
        "abandoned": med(g(lambda r: r["outcome"]["abandoned"])),
        "serverError5xx": med(g(lambda r: r["outcome"]["serverError5xx"])),
        "rejected4xx": med(g(lambda r: r["outcome"]["rejected4xx"])),
        "failureRate": med(g(lambda r: r["failureRate"])),
        "p95": med(g(lambda r: r["latencyMs"]["p95"])),
        "p99": med(g(lambda r: r["latencyMs"]["p99"])),
        "drainSeconds": med(g(lambda r: r["drainSeconds"])),
        "settleSeconds": med(settles),
        "overbookedRuns": over,
    }


def verdict(rows):
    base = next((r for r in rows if r["policy"] == "NO_RETRY"), None)
    last = next((r for r in reversed(rows) if r["policy"] != "NO_RETRY"), None)

    L = ["", "## 판정", ""]
    if base is None or last is None:
        L.append("- 기준선(NO_RETRY)과 비교 대상 정책이 모두 있어야 판정할 수 있다.")
        return L

    b, c = base["effectiveThroughputRps"], last["effectiveThroughputRps"]
    ratio = c / b if b > 0 else 0.0

    L.append(
        f"- **유효 처리량**: NO_RETRY {b:.1f} user/s → {last['policy']} {c:.1f} user/s "
        f"(**{ratio * 100:.0f}%**, 문턱 {COLLAPSE_RATIO * 100:.0f}%)"
    )
    L.append(
        f"- **부하는 늘었다**: 증폭 ×{base['amplification']:.2f} → ×{last['amplification']:.2f}, "
        f"총 {base['requestRps']:.0f} → {last['requestRps']:.0f} req/s, "
        f"abandon {base['abandoned']:.0f} → {last['abandoned']:.0f}건"
    )

    # 유효 처리량 하락의 원인이 서버가 아니라 발생기일 수 있다. 이걸 먼저 배제해야
    # "나선"이라고 부를 수 있다 — 사용자를 덜 쏘고서 "덜 처리됐다"고 말하면 안 된다.
    starved = base["userAttainment"] > 0 and last["userAttainment"] < 0.9 * base["userAttainment"]
    if starved:
        L.append(
            f"- ⚠️ **발생기 포화 의심**: 유저 도달률 {base['userAttainment'] * 100:.0f}% → "
            f"{last['userAttainment'] * 100:.0f}%. {last['policy']}에서 사용자를 목표만큼 "
            f"발사하지 못했다(dropped {last['usersDropped']:.0f}). 유효 처리량 하락분 중 "
            f"얼마가 서버 탓인지 분리되지 않는다 — MAX_VUS를 올리거나 RATE를 낮춰 재측정할 것."
        )

    if ratio < COLLAPSE_RATIO and last["abandoned"] > base["abandoned"]:
        L.append(
            "- **H3 지지**: 재시도를 켜자 발사 요청은 늘었는데 실제로 좌석을 딴 고유 사용자는 "
            "줄었다. 부하를 더 넣을수록 처리량이 감소하는 congestion collapse다."
            + (" 단, 위 발생기 포화 경고가 해소되기 전까지는 잠정." if starved else "")
        )
    elif ratio >= COLLAPSE_RATIO:
        L.append(
            f"- **H3 반증**: 유효 처리량이 A ≈ C ({ratio * 100:.0f}%)로 유지된다. "
            f"이 라벨은 재시도에 강건하며, B2C 재시도 증폭을 장애 논거로 쓸 수 없다."
        )
    else:
        L.append(
            "- **판정 보류**: 유효 처리량은 떨어졌지만 abandon이 늘지 않았다. "
            "재시도 증폭이 아니라 다른 원인(재고 소진, 발생기 한계)을 먼저 배제할 것."
        )

    L.append(
        f"- **서버 지표와 체감의 괴리**: {last['policy']}에서 서버 p95 {last['p95']:.0f}ms인데 "
        f"사용자 체감 완료 p95는 {last['userCompletionP95'] / 1000:.1f}s "
        f"(평균 {last['attemptsAvg']:.1f}회 시도). 서버 지연 분포만 보면 안 보이는 값이다."
    )
    L.append(
        f"- **사용자 관점 성공률**: {base['userSuccessRate'] * 100:.0f}% → "
        f"{last['userSuccessRate'] * 100:.0f}% (포기 {last['usersGaveUp']:.0f}명)"
    )

    over_any = [r for r in rows if r["overbookedRuns"]]
    detail = ", ".join(r["policy"] for r in over_any)
    L.append(
        f"- **정합성**: 오버부킹 발생 정책 {len(over_any)}개"
        + (f" ({detail})" if over_any else " — 전 구간 무결")
    )
    L.append(
        "- **after 우위 여부**는 이 파일 하나로 판정할 수 없다. before/after 두 summary.md의 "
        "유효 처리량 유지율과 사용자 체감 완료 시간을 나란히 놓고 볼 것."
    )
    return L


def render(label, rows):
    L = [
        f"# S4 Retry Storm — {label}",
        "",
        "경합점 1개, 좌석·도착률 고정. 재시도 정책만 바꾼다(개방형 부하).",
        "값은 회차 중앙값 (워밍업 런 제외).",
        "",
        "이터레이션 1회 = **사용자 1명의 여정**이다(요청 1건이 아니다). 재시도는 여정 안에서",
        "일어나므로, 정책을 바꿔도 사용자 유입은 그대로다.",
        "",
        "- **유효 처리량** = 성공한 **고유 사용자** 수 / 초. 사용자에게 의미 있는 유일한 처리량.",
        "- **총 req/s** = 재시도를 포함해 실제로 서버가 받은 요청/초. 이건 성과가 아니라 **부하**다.",
        "  재시도를 켜면 이 값은 당연히 오른다 — 요청 단위로 처리량을 세면 더 많은 사람이",
        "  처리된 것처럼 보이지만, 그 증가분은 같은 사람의 두 번째 시도일 뿐이다.",
        "- **증폭 계수** = 총 발사 요청 / 고유 사용자. 재시도가 만들어낸 부하 배수.",
        "- **유저 도달률** = 실제 발사된 사용자 / 목표. 100% 미만이면 k6가 VU를 못 구해",
        "  사용자를 발사조차 못한 것이므로, 유효 처리량 하락을 서버 탓으로 돌리기 전에 확인해야 한다.",
        "- **체감 완료 p95** = 첫 시도 시작 → 최종 성공. 성공한 사용자만 들어간다.",
        "",
        "| 정책 | 회차 | 유효 처리량 user/s | 사용자 성공률 | 증폭 | 총 req/s | 유저 도달률 | abandon | 5xx | 서버 p95 | 체감 완료 p95 | 평균 시도 |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        L.append(
            f"| {r['policy']} | {r['runs']} | **{r['effectiveThroughputRps']:.1f}** | "
            f"{r['userSuccessRate'] * 100:.0f}% | ×{r['amplification']:.2f} | "
            f"{r['requestRps']:.0f} | {r['userAttainment'] * 100:.0f}% | "
            f"{r['abandoned']:.0f} | {r['serverError5xx']:.0f} | "
            f"{r['p95']:.0f}ms | **{r['userCompletionP95'] / 1000:.1f}s** | "
            f"{r['attemptsAvg']:.1f} |"
        )

    L += [
        "",
        "## 사용자 결말 분해",
        "",
        "포기(gaveUp)는 재시도 상한까지 갔는데도 좌석을 못 딴 사용자다. 성공률만 보면",
        "가려지는 값이라 따로 센다.",
        "",
        "| 정책 | 발사 | 성공 | 품절 | 포기 | 발사 실패(dropped) |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        L.append(
            f"| {r['policy']} | {r['usersLaunched']:.0f} | {r['usersSuccess']:.0f} | "
            f"{r['usersSoldOut']:.0f} | {r['usersGaveUp']:.0f} | {r['usersDropped']:.0f} |"
        )

    L += [
        "",
        "## 정합성 / 잔여 처리",
        "",
        "| 정책 | 오버부킹 발생 회차 | drain(s) | settle(s) |",
        "|---|---:|---:|---:|",
    ]
    for r in rows:
        flag = f"**{r['overbookedRuns']}/{r['runs']}**" if r["overbookedRuns"] else f"0/{r['runs']}"
        L.append(f"| {r['policy']} | {flag} | {r['drainSeconds']:.1f} | {r['settleSeconds']:.1f} |")

    L += verdict(rows)
    return "\n".join(L) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: aggregate.py <s4-retry-storm/<label> 디렉터리>")
    root = Path(sys.argv[1]).resolve()
    by = collect(root)
    if not by:
        sys.exit(f"{root}/raw 에 집계할 결과가 없습니다.")
    order = {p: i for i, p in enumerate(POLICY_ORDER)}
    rows = [summarize(p, by[p]) for p in sorted(by, key=lambda p: (order.get(p, len(order)), p))]
    md = render(root.name, rows)
    (root / "summary.json").write_text(
        json.dumps({"scenario": "s4-retry-storm", "label": root.name, "policies": rows},
                   indent=2, ensure_ascii=False))
    (root / "summary.md").write_text(md)
    print(md)
    print(f"-> {root}/summary.md")


if __name__ == "__main__":
    main()
