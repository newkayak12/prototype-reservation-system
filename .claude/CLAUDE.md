# Project — Claude Instructions

<!-- harness:begin -->
## 이 프로젝트는 harness 사이클 규율 아래서 작업한다

`.claude/harness/` 가 설치돼 있다. 기본 계약 —

- **작업 = 사이클.** 새 기능·구조 변경은 사이클로 연다: `python3 .claude/harness/scripts/cycle-init.py "<name>" --type dev-tool`. 이후 절차·register 스크립트는 `harness:cycle` 스킬이 운반.
- **WIP=1.** 열린 사이클은 하나. 새로 열기 전에 진행 중인 것을 닫는다.
- **게이트.** 가설/품질-바를 *먼저* 잠그고 → 독립 리뷰어(doer≠reviewer)가 채점 → 게이트로 닫는다. cross-cycle ratchet 이 사이클을 넘어 바가 낮아지는 걸 막는다.
- **plan-before-code.** 코드는 phase 가 implementation/validation 일 때만. PreToolUse phase-guard 가 자동 강제(미충족 시 편집 차단); 전진은 `phase-advance.py`(절차는 `harness:cycle`).

> 단순 버그픽스·유지보수는 사이클을 강제하지 않는다(GOAL: 솔로 dev 제품 *한 사이클*).
<!-- harness:end -->
