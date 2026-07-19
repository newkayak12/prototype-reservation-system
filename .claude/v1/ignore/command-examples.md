# 🚀 SuperClaude 명령어 예시 가이드

CGKR 프로젝트에서 실제로 사용할 수 있는 SuperClaude 명령어 예시 모음

---

## 📋 분석 명령어 (`/analyze`)

### 🔍 기본 분석
```bash
# 전체 프로젝트 분석
/sc:analyze

# 특정 모듈 분석
/sc:analyze @api/

# 성능 분석에 집중
/sc:analyze --focus performance

# 보안 취약점 분석
/sc:analyze --focus security --persona-security
```

### 🧠 깊이 있는 분석
```bash
# 복잡한 시스템 분석 (자동 --think 활성화)
/sc:analyze --think-hard @legacy/

# 아키텍처 전체 분석
/sc:analyze --ultrathink --persona-architect

# Sub-Agent로 대규모 분석 (자동 위임)
/sc:analyze --delegate auto @domain/ @usecase/ @api/

# Wave 모드로 체계적 분석
/sc:analyze --wave-mode systematic
```

### 📊 특정 문제 분석
```bash
# N+1 쿼리 문제 찾기
/sc:analyze "N+1 쿼리 패턴을 찾아줘" --focus performance

# 메모리 누수 분석
/sc:analyze "메모리 누수 가능성 분석" --persona-performance

# 순환 참조 문제
/sc:analyze "순환 참조 의존성 검사" --think
```

---

## 🏗️ 구현 명령어 (`/implement`)

### ⚡ 기본 구현
```bash
# UI 컴포넌트 구현 (자동으로 Magic MCP 활성화)
/sc:implement "재고 현황 대시보드 컴포넌트"

# API 엔드포인트 구현 (자동으로 Context7 활성화)
/sc:implement "상품 검색 API" --persona-backend

# 도메인 서비스 구현
/sc:implement "출고 처리 비즈니스 로직" --persona-architect
```

### 🔧 고급 구현
```bash
# 성능 최적화 포함 구현
/sc:implement "대용량 데이터 처리 API" --focus performance --validate

# 보안 강화 포함 구현
/sc:implement "사용자 인증 시스템" --persona-security --safe-mode

# 테스트 포함 구현
/sc:implement "주문 처리 서비스" --persona-qa --auto-test
```

### 🌊 대규모 구현
```bash
# Wave 모드로 단계적 구현
/sc:implement "레거시 모듈 현대화" --wave-mode enterprise

# Sub-Agent로 병렬 구현
/sc:implement "마이크로서비스 분할" --delegate tasks --concurrency 5
```

---

## 🔄 개선 명령어 (`/improve`)

### 🎯 기본 개선
```bash
# 코드 품질 개선 (자동 루프 모드)
/sc:improve @domain/

# 성능 최적화
/sc:improve --focus performance --persona-performance

# 보안 강화
/sc:improve --focus security --validate
```

### 🔁 반복 개선
```bash
# 여러 차례 반복 개선
/sc:improve --loop --iterations 5 "OutboundService 리팩토링"

# 품질 목표치까지 개선
/sc:improve --loop --quality-threshold 0.9 @legacy/

# 대화형 개선 (단계별 확인)
/sc:improve --loop --interactive "Stock 엔티티 최적화"
```

### 🌊 체계적 개선
```bash
# Wave 모드로 전체 시스템 개선
/sc:improve --wave-mode systematic "전체 아키텍처 개선"

# 점진적 개선 전략
/sc:improve --wave-mode progressive @api/ @domain/ @usecase/
```

---

## 🏗️ 빌드 명령어 (`/build`)

### 📦 기본 빌드
```bash
# 전체 프로젝트 빌드
/sc:build

# 특정 모듈 빌드
/sc:build @api/

# 검증 포함 빌드
/sc:build --validate --auto-test
```

### 🔧 고급 빌드
```bash
# 성능 최적화 빌드
/sc:build --focus performance "번들 크기 최적화"

# 보안 스캔 포함 빌드
/sc:build --persona-security --security-scan

# Docker 컨테이너 빌드
/sc:build "Docker 이미지 생성 및 최적화" --persona-devops
```

---

## 📝 문서화 명령어 (`/document`)

### 📖 기본 문서화
```bash
# API 문서 생성 (자동으로 Scribe 페르소나)
/sc:document @api/

# 도메인 가이드 작성
/sc:document "도메인 모델 설명서" --persona-scribe=ko

# 아키텍처 문서
/sc:document "시스템 아키텍처" --persona-architect
```

### 🌐 다국어 문서화
```bash
# 한국어 문서
/sc:document --persona-scribe=ko "사용자 가이드"

# 영어 문서
/sc:document --persona-scribe=en "API Reference"

# 개발자 온보딩 가이드
/sc:document "신입 개발자 가이드" --persona-mentor
```

---

## 🧪 테스트 명령어 (`/test`)

### ✅ 기본 테스트
```bash
# 단위 테스트 생성
/sc:test @domain/

# E2E 테스트 (자동으로 Playwright 활성화)
/sc:test "사용자 로그인 플로우" --persona-qa

# 성능 테스트
/sc:test --focus performance "API 응답시간"
```

### 🔍 고급 테스트
```bash
# 크로스 브라우저 테스트
/sc:test --play "전체 UI 컴포넌트" --cross-browser

# 보안 테스트
/sc:test --persona-security "인증 우회 시도"

# 대용량 데이터 테스트
/sc:test "10만건 데이터 처리" --performance-check
```

---

## 🔧 문제해결 명령어 (`/troubleshoot`)

### 🐛 기본 문제해결
```bash
# 버그 원인 분석 (자동으로 Analyzer 페르소나)
/sc:troubleshoot "재고 수량 불일치 문제"

# 성능 문제 해결
/sc:troubleshoot --focus performance "API 응답 지연"

# 깊이 있는 분석
/sc:troubleshoot --think-hard "간헐적 OutOfMemory 에러"
```

### 🔍 체계적 문제해결
```bash
# 근본 원인 분석
/sc:troubleshoot --seq "데이터 무결성 문제"

# 여러 시스템 연관 문제
/sc:troubleshoot --delegate tasks "전사 시스템 장애"

# 보안 사고 대응
/sc:troubleshoot --persona-security --safe-mode "의심스러운 접근 로그"
```

---

## 📐 설계 명령어 (`/design`)

### 🎨 기본 설계
```bash
# UI/UX 설계
/sc:design "재고 관리 대시보드" --persona-frontend

# API 설계
/sc:design "RESTful API 구조" --persona-backend

# 데이터베이스 설계
/sc:design "새로운 테이블 스키마" --persona-architect
```

### 🏗️ 시스템 설계
```bash
# 마이크로서비스 아키텍처
/sc:design --wave-mode systematic "MSA 전환 설계"

# 대규모 시스템 설계
/sc:design --persona-architect --enterprise "글로벌 WMS 아키텍처"

# 성능 중심 설계
/sc:design --focus performance "고성능 데이터 파이프라인"
```

---

## 🔄 Git 명령어 (`/git`)

### 📝 기본 Git 작업
```bash
# 커밋 메시지 작성 (자동으로 DevOps 페르소나)
/sc:git "변경사항 커밋"

# PR 생성
/sc:git "pull request 생성" --persona-scribe

# 브랜치 전략 수립
/sc:git "Git Flow 전략 수립" --persona-devops
```

### 🚀 고급 Git 작업
```bash
# 릴리스 노트 생성
/sc:git "v2.1.0 릴리스 노트" --persona-scribe=ko

# 코드 리뷰 가이드
/sc:git "코드 리뷰 체크리스트" --persona-qa

# 배포 전략
/sc:git "무중단 배포 전략" --persona-devops --safe-mode
```

---

## 🎯 프로젝트별 실제 사용 예시

### 📊 CGKR 프로젝트 특화 명령어

```bash
# 창고 관리 시스템 분석
/sc:analyze "WMS 성능 병목 지점" --focus performance --persona-performance

# 재고 관리 개선
/sc:improve @domain/stock/ --loop --quality-threshold 0.9

# 출고 프로세스 최적화
/sc:implement "출고 워크플로우 개선" --wave-mode progressive

# 레거시 모듈 현대화
/sc:improve @legacy/ --wave-mode enterprise --validate

# API 성능 최적화
/sc:improve "N+1 쿼리 해결" --persona-performance --auto-test

# 보안 감사
/sc:analyze --focus security --persona-security --wave-mode systematic

# 신입 개발자 온보딩 문서
/sc:document "CGKR 시스템 가이드" --persona-mentor --persona-scribe=ko

# E2E 테스트 구축
/sc:test "전체 출고 프로세스" --play --persona-qa
```

### 🔄 일상 업무 명령어

```bash
# 아침 코드 리뷰
/sc:analyze @api/src/main/java/global/colosseum/colo/api/inbound/ --uc

# 기능 개발
/sc:implement "재고 알림 기능" --c7 --magic --validate

# 버그 수정
/sc:troubleshoot "OutboundDetail 매핑 오류" --think --seq

# 코드 정리
/sc:improve @domain/ --loop --iterations 3

# 문서 업데이트
/sc:document @.claude/ --persona-scribe=ko

# 배포 준비
/sc:build --validate --auto-test --persona-devops
```

---

## 🎨 플래그 조합 예시

### 💎 추천 조합

```bash
# 품질 중심 개발
/sc:improve --loop --validate --quality-threshold 0.9

# 성능 최적화 전문
/sc:analyze --focus performance --persona-performance --think

# 보안 강화 작업
/sc:analyze --focus security --persona-security --safe-mode --validate

# 대규모 리팩토링
/sc:improve --wave-mode systematic --delegate auto --validate

# 빠른 프로토타입
/sc:implement --uc --answer-only --no-validate

# 학습 중심 분석
/sc:analyze --verbose --plan --persona-mentor --introspect

# 토큰 절약 모드
/sc:analyze --uc --ultracompressed --no-plan

# 최고 품질 구현
/sc:implement --wave-mode enterprise --validate --auto-test --persona-architect
```

### ⚠️ 주의할 조합

```bash
# 충돌 가능한 조합들
/sc:analyze --uc --verbose           # 압축과 상세 모드 충돌
/sc:implement --answer-only --plan   # 간단 답변과 계획 표시 충돌
/sc:improve --safe-mode --no-validate # 안전 모드인데 검증 안함
/sc:build --ultracompressed --introspect # 극도 압축과 상세 분석 충돌
```

---

## 🛠️ 상황별 명령어 가이드

### 🚨 긴급 상황
```bash
# 프로덕션 장애
/sc:troubleshoot --think-hard --seq --persona-analyzer --safe-mode

# 보안 사고
/sc:analyze --focus security --persona-security --ultrathink --validate

# 성능 급락
/sc:troubleshoot --focus performance --persona-performance --delegate auto
```

### 📚 학습 목적
```bash
# 코드 이해하기
/sc:explain @domain/ --persona-mentor --verbose --plan

# 아키텍처 학습
/sc:analyze --persona-architect --introspect --verbose

# 베스트 프랙티스 학습
/sc:document "Spring Boot 베스트 프랙티스" --persona-mentor --c7
```

### 🏃‍♂️ 빠른 작업
```bash
# 간단한 버그 수정
/sc:troubleshoot --uc --answer-only "NullPointerException"

# 빠른 문서 작성
/sc:document --uc --persona-scribe=ko "API 사용법"

# 간단한 개선
/sc:improve --uc --no-validate @api/util/
```

---

## 💡 실용 팁

### 🎯 효율적인 명령어 사용법

1. **자주 사용하는 조합은 settings.json에 등록**
   ```json
   "command_defaults": {
     "/analyze": ["--think", "--seq", "--uc"],
     "/improve": ["--loop", "--validate", "--uc"]
   }
   ```

2. **프로젝트 특성에 맞는 기본 설정**
   ```json
   "project_specific": {
     "framework": "spring_boot",
     "primary_language": "java"
   }
   ```

3. **상황별 플래그 조합 즐겨찾기**
   - 학습: `--verbose --plan --persona-mentor`
   - 품질: `--validate --loop --quality-threshold 0.9`
   - 속도: `--uc --answer-only --no-plan`
   - 안전: `--safe-mode --validate --backup`

### 📊 성능 최적화 팁

- **대용량 작업**: `--delegate auto --concurrency 5`
- **복잡한 분석**: `--wave-mode systematic`
- **토큰 절약**: `--uc --ultracompressed`
- **품질 우선**: `--wave-mode enterprise --validate`

---

*마지막 업데이트: 2025-08-05*  
*CGKR 프로젝트 SuperClaude 명령어 실용 가이드*