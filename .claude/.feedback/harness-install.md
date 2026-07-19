# Harness Install 피드백

## 1. `user-rules-init.py`에 Kotlin 포인터 플래그 미지원

- **상황**: `--pointer-python`, `--pointer-js`만 존재. `--pointer-kotlin` 없음
- **증상**: `--pointer-kotlin "detekt.yml"` 실행 시 `unrecognized arguments` 에러
- **우회**: `user-rules-init.py add`로 별도 룰 수동 추가
- **제안**: `--pointer-kotlin`, `--pointer-java` 등 JVM 계열 플래그 추가 또는 범용 `--pointer <name> <path>` 형태 지원

## 2. L1 user-rules 저장 경로(`~/.harness/`)가 직관적이지 않음

- **상황**: 프로젝트 디렉토리에서 `harness:install` 실행했는데 `~/.harness/user-rules.md`에 생성됨
- **혼란**: 프로젝트 내부에 생길 것으로 기대했으나 홈 디렉토리에 생성
- **설계 의도**: L1 = 사용자 전역 룰, L2 = 프로젝트별 룰 (cycle 시 생성)
- **제안**: 온보딩 시 "L1은 전역(`~/.harness/`), L2는 프로젝트 내부" 차이를 파일 생성 *전에* 명시적으로 안내할 것
