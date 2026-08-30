#!/usr/bin/env python3
"""S3 — Blast Radius 집계.

raw/hNNNNN-iMM.json 을 핫스팟 도착률로 묶어 회차 중앙값 표를 만든다.
워밍업 런(warmup- 접두)은 파일명으로 걸러진다.

이 시나리오의 결론은 단 하나의 숫자다:
    배경 조회 p95 증폭 = (스파이크 구간 p95) / (평상 구간 p95)
1에 가까우면 핫스팟이 격리된 것이고, 크면 무관한 트래픽까지 같이 죽는 것이다.

사용법: python3 aggregate.py <s3-blast-radius/before 같은 디렉터리>
"""
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

RUN_RE = re.compile(r"^h(\d+)-i(\d+)\.json$")


def med(v):
    vals = [x for x in v if x is not None]
    return statistics.median(vals) if vals else 0.0


def med_opt(v):
    """None이 섞인 계열의 중앙값. 전부 None이면 None을 유지한다.

    성공 표본이 없어 지연을 못 잰 구간을 0으로 뭉개면 "빠르다"로 읽힌다.
    """
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
        if not rep.get("valid", False):
            print(f"WARN: {f.name} 측정 실패 — 제외", file=sys.stderr)
            continue
        stem = str(f.with_suffix(""))
        rep["_integrity"] = load_json(Path(f"{stem}-integrity.json"))
        by_rate[int(m.group(1))].append(rep)
    return by_rate


def summarize(rate, runs):
    bg = lambda k: [r["background"].get(k) for r in runs]
    hot = lambda k: [r["hotspot"][k] for r in runs]
    over = sum(1 for r in runs if (r["_integrity"] or {}).get("overbookedSlots", 0) > 0)

    return {
        "hotRate": rate,
        "runs": len(runs),
        "control": bool(runs[0].get("control")),
        "bgRequests": med(bg("requests")),
        "bgP95Base": med_opt(bg("p95Baseline")),
        "bgP95Spike": med_opt(bg("p95Spike")),
        "bgP95Post": med_opt(bg("p95Post")),
        "bgAmp": med_opt(bg("p95Amplification")),
        "bgRecovery": med_opt(bg("p95Recovery")),
        "bgOkBase": med(bg("okRateBaseline")),
        "bgOkSpike": med(bg("okRateSpike")),
        "bgOkPost": med(bg("okRatePost")),
        "bgTimeout": med(bg("timeout")),
        "bgError": med(bg("error")),
        "hotRequests": med(hot("requests")),
        "hotSuccess": med(hot("success")),
        "hotTimeout": med(hot("timeout")),
        "hotError5xx": med(hot("error5xx")),
        "hotP95": med(hot("p95")),
        "overbookedRuns": over,
        # 시계열은 대표 런(첫 회차) 하나만 싣는다. 회차별 시계열을 평균하면
        # 스파이크 시작 시점의 미세한 어긋남 때문에 뾰족한 봉우리가 뭉개진다.
        "series": runs[0]["series"],
    }


def msnum(v, fmt="{:.0f}ms"):
    """None은 '표본없음'으로. 성공이 0건인 구간을 0ms로 쓰면 '빨랐다'로 읽힌다."""
    return "표본없음" if v is None else fmt.format(v)


def render(label, rows):
    L = [
        f"# S3 Blast Radius — {label}",
        "",
        "배경 조회(GET, 락 없음)를 일정하게 흘리면서 다른 식당 하나에만 예약 폭격을 넣는다.",
        "배경 조회는 핫슬롯과 도메인상 아무 관계가 없다. 공유하는 건 Tomcat 워커 풀과 DB 커넥션뿐.",
        "값은 회차 중앙값 (워밍업 런 제외).",
        "",
        "**증폭 = 스파이크 구간 배경 p95 / 스파이크 _이전_ 배경 p95.**",
        "1이면 격리, 크면 핫스팟이 무관한 트래픽까지 끌고 들어간 것이다.",
        "",
        "국면을 셋으로 나눈다. 스파이크가 끝났다고 바로 평상으로 돌아가지 않기 때문이다.",
        "`평상`(스파이크 이전) / `스파이크` / `회복`(스파이크 이후). ",
        "\"스파이크가 아닌 구간\"을 통째로 기준선으로 쓰면 아직 망가져 있는 회복 구간이 섞여",
        "기준선이 부풀고, 피해를 안 본 것처럼 보인다.",
        "",
        "성공 표본이 없는 구간의 지연은 `표본없음`으로 둔다. 0으로 두면 \"빨랐다\"로 읽히는데,",
        "실제로는 **아무도 응답을 못 받았다**는 뜻이라 정반대다. 그 구간은 성공률로 읽어야 한다.",
        "",
        "| 핫스팟 req/s | 회차 | p95 평상 | p95 스파이크 | **증폭** | p95 회복 | 회복 배수 | 성공률 평상→스파이크→회복 | 배경 timeout | 핫스팟 p95 | 핫스팟 timeout |",
        "|---:|---:|---:|---:|---:|---:|---:|:--:|---:|---:|---:|",
    ]
    for r in rows:
        name = f"{r['hotRate']:,}" + (" (대조군)" if r["control"] else "")
        amp = "—" if r["control"] else f"**{msnum(r['bgAmp'], '{:.1f}배')}**"
        L.append(
            f"| {name} | {r['runs']} | {msnum(r['bgP95Base'])} | {msnum(r['bgP95Spike'])} | {amp} | "
            f"{msnum(r['bgP95Post'])} | {msnum(r['bgRecovery'], '{:.1f}배')} | "
            f"{r['bgOkBase'] * 100:.1f}% → {r['bgOkSpike'] * 100:.1f}% → {r['bgOkPost'] * 100:.1f}% | "
            f"{r['bgTimeout']:.0f} | {r['hotP95']:.0f}ms | {r['hotTimeout']:.0f} |"
        )

    L += [
        "",
        "## 배경 조회 p95 시계열 (대표 런)",
        "",
        "스파이크 구간은 `*` 로 표시. 봉우리가 스파이크 구간에 정확히 겹치는지가 인과의 근거다.",
    ]
    for r in rows:
        if r["control"]:
            continue
        L += [
            "",
            f"### 핫스팟 {r['hotRate']:,} req/s",
            "",
            "| 구간(s) | 국면 | 요청 | 성공 | 성공률 | p50 | p95 | p99 |",
            "|---:|:--|---:|---:|---:|---:|---:|---:|",
        ]
        phase_ko = {"pre": "평상", "spike": "**스파이크**", "post": "회복"}
        for s in r["series"]:
            ok = "—" if s["okRate"] is None else f"{s['okRate'] * 100:.1f}%"
            L.append(
                f"| {s['fromSec']}–{s['toSec']} | {phase_ko.get(s.get('phase'), '')} | "
                f"{s['requests']} | {s['ok']} | {ok} | "
                f"{msnum(s['p50'])} | **{msnum(s['p95'])}** | {msnum(s['p99'])} |"
            )

    # 판정
    real = [r for r in rows if not r["control"]]
    if real:
        # 판정은 지연 배수와 성공률 하락 중 "더 나쁜 쪽"으로 한다.
        # 성공률이 0이 되면 지연 배수는 아예 계산되지 않으므로(표본없음),
        # 지연만 보면 최악의 구간을 통째로 놓친다.
        def severity(r):
            amp = r["bgAmp"] or 0
            drop = r["bgOkBase"] - r["bgOkSpike"]
            return max(amp, 1 + drop * 20)

        worst = max(real, key=severity)
        amp = worst["bgAmp"]
        drop = worst["bgOkBase"] - worst["bgOkSpike"]

        if drop >= 0.5 or (amp is not None and amp >= 3):
            verdict = "**폭발 반경 넓음** — 핫스팟이 무관한 조회 트래픽까지 끌고 들어간다"
        elif drop <= 0.05 and (amp is None or amp <= 1.5):
            verdict = "**격리됨** — 핫스팟이 배경 조회에 거의 영향을 주지 않는다"
        else:
            verdict = "**부분 전파** — 영향은 있으나 치명적이지는 않다"

        L += ["", "## 판정", ""]
        L.append(
            f"- 최악 구간: 핫스팟 {worst['hotRate']:,} req/s"
        )
        L.append(
            f"  - 배경 p95 {msnum(worst['bgP95Base'])} → {msnum(worst['bgP95Spike'])} "
            f"(**{msnum(amp, '{:.1f}배')}**)"
        )
        L.append(
            f"  - 배경 성공률 {worst['bgOkBase'] * 100:.1f}% → **{worst['bgOkSpike'] * 100:.1f}%** "
            f"(**{drop * 100:.1f}%p 하락**)"
            + (f", timeout {worst['bgTimeout']:.0f}건" if worst["bgTimeout"] else "")
        )
        if amp is None:
            L.append(
                "  - 스파이크 구간 지연이 `표본없음`인 것은 빨라서가 아니라 "
                "**성공한 요청이 하나도 없어서**다. 이 경우 성공률이 유일한 지표다."
            )
        L.append(f"- {verdict}")

        rec = worst["bgRecovery"]
        if rec is not None and rec > 1.5:
            L.append(
                f"- **회복 지연**: 스파이크가 끝난 뒤에도 배경 p95가 {msnum(worst['bgP95Post'])} "
                f"(평상의 {rec:.1f}배)로 남아 있다. 피해는 스파이크보다 오래 간다."
            )
        elif rec is not None:
            L.append(
                f"- 회복: 스파이크 종료 후 배경 p95가 {msnum(worst['bgP95Post'])} "
                f"(평상의 {rec:.1f}배)로 돌아왔다."
            )

        ctrl = next((r for r in rows if r["control"]), None)
        if ctrl:
            L.append(
                f"- 대조군(핫스팟 없음) 배경 p95 = {msnum(ctrl['bgP95Base'])} — "
                "각 런의 평상 구간 p95가 이 값과 비슷해야 증폭 배수를 신뢰할 수 있다."
            )
        over_any = [r for r in rows if r["overbookedRuns"]]
        L.append(
            f"- 정합성: 오버부킹 발생 칸 {len(over_any)}개"
            + ("" if over_any else " — 전 구간 무결. 느려질 뿐 틀리지는 않는다.")
        )

    return "\n".join(L) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: aggregate.py <s3-blast-radius/<label> 디렉터리>")
    root = Path(sys.argv[1]).resolve()
    by = collect(root)
    if not by:
        sys.exit(f"{root}/raw 에 집계할 결과가 없습니다.")
    rows = [summarize(n, by[n]) for n in sorted(by)]
    md = render(root.name, rows)
    (root / "summary.json").write_text(
        json.dumps({"scenario": "s3-blast-radius", "label": root.name, "steps": rows},
                   indent=2, ensure_ascii=False))
    (root / "summary.md").write_text(md)
    print(md)
    print(f"-> {root}/summary.md")


if __name__ == "__main__":
    main()
