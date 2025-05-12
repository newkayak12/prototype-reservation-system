# 프로젝트 개요

- 이름: Prototype Reservation System
- 목적: 예약 시스템 기능을 기반으로 공부한 내용 구현


## 🏗 프로젝트 구조

- `core-module`: 도메인 및 서비스 계층
- `adapter-module`: 컨트롤러, API, 외부 연동
- `application-module`: 실행 환경, 설정 파일
- `shared-module`: 공통 유틸 및 설정

## 🧪 품질 정책

- pre-commit hook으로  `spotlessApply` 적용
- commit 시 `./gradlew detekt` 확인
- PR 시 `spotlessKotlinCheck` 확인
- main branch 직접 push 금지