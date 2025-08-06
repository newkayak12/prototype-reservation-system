# 🔧 SuperClaude 설정 가이드

CGKR 프로젝트 SuperClaude 프레임워크 설정 파일 (`settings.json`) 상세 설명서

---

## 🚀 기본 플래그 설정 (`default_flags`)

모든 명령어에 자동으로 적용되는 기본 플래그들

```json
"default_flags": {
  "compression": true,        // --uc: 토큰 압축 모드 (30-50% 토큰 절약)
  "ultracompressed": false,   // 극도 압축 모드 (70-95% 토큰 절약)
  "answer_only": false,       // 간단 답변 모드 (TODO 생성 없이 바로 답변)
  "validate": true,           // 실행 전 검증 (위험도 평가 및 확인)
  "safe_mode": false,         // 안전 모드 (프로덕션 환경용, 보수적 실행)
  "verbose": false,           // 상세 설명 모드 (높은 토큰 사용량)
  "plan": false,              // 실행 계획 표시 (계획 먼저 보여줌)
  "introspect": false         // 메타 인지 분석 모드 (추론 과정 분석)
}
```

### 📋 `--plan` 플래그 상세 동작

**실행 계획 표시 모드** - 작업을 바로 실행하지 않고 먼저 계획을 보여줌

```
사용자: "React 컴포넌트를 만들어줘"

--plan 활성화시:
┌─ 실행 계획 ─┐
│ 1. 컴포넌트 구조 분석
│ 2. Props 인터페이스 정의  
│ 3. JSX 마크업 작성
│ 4. 스타일링 적용
│ 5. TypeScript 타입 검증
└─────────────┘
이 계획으로 진행할까요? (y/n)

--plan 비활성화시:
바로 컴포넌트 코드 생성 시작
```

**사용 사례:**
- **복잡한 작업**: 다단계 작업의 전체 흐름 파악
- **리뷰 필요**: 실행 전 접근 방식 검토
- **학습 목적**: 문제 해결 과정 이해
- **위험한 작업**: 시스템 변경 전 확인

### 🧠 `--introspect` 플래그 상세 동작

**메타 인지 분석 모드** - AI의 추론 과정과 의사결정을 투명하게 공개

**분석 마커들:**
```
🧠 추론 분석: "이 문제는 N+1 쿼리 패턴으로 보입니다. 왜냐하면..."
🔄 행동 순서 검토: "Grep → Read → Edit 순서를 선택한 이유는..."
🎯 자기 평가: "제 분석의 신뢰도는 85%입니다. 불확실한 부분은..."
📊 패턴 인식: "이전에 비슷한 문제에서 Sequential MCP가 효과적이었습니다"
🔍 프레임워크 준수: "RULES.md의 'Read before Edit' 규칙을 따랐습니다"
💡 회고 통찰: "예상한 결과와 다른 점은... 다음번엔 이렇게 하겠습니다"
```

**활용 상황:**
- **디버깅**: AI의 판단 과정 이해
- **학습**: 문제 해결 방법론 습득
- **품질 개선**: AI 결정의 타당성 검증
- **프레임워크 분석**: SuperClaude 작동 원리 파악

### ⚡ 압축 모드 상세 비교

| 모드 | 토큰 절약률 | 정보 보존율 | 이해도 | 적용 상황 |
|------|-------------|-------------|---------|-----------|
| **일반 모드** | 0% | 100% | 100% | 학습, 상세 설명 |
| **--uc (compression)** | 30-50% | 95% | 90% | 일반적인 작업 |
| **--ultracompressed** | 70-95% | 85% | 70% | 토큰 절약 필수 |

**Ultra Compressed 예시:**

일반 모드 (100 토큰):
```
이 코드에서 발견된 문제점들을 분석해보겠습니다:

1. N+1 쿼리 문제가 있습니다
   - User 엔티티를 조회할 때마다 개별적으로 Profile을 조회
   - 해결방법: @EntityGraph 또는 Fetch Join 사용

2. 보안 취약점이 있습니다  
   - 패스워드가 평문으로 저장됨
   - 해결방법: BCrypt 해싱 적용
```

Ultra Compressed (15 토큰):
```
Issues:
• N+1: User→Profile (fix: @EntityGraph)
• Security: plaintext pwd (fix: BCrypt)
```

**Ultra Compressed 기술:**
- **기호 활용**: `→` `↔` `∴` `∵` 등
- **축약어**: `pwd`(password), `auth`(authentication), `cfg`(config)
- **구조 최적화**: 불필요한 접속사/관사 제거
- **리스트 형태**: 항목별 핵심만 추출

**추천 설정:**
- `compression: true` - 기본적으로 토큰 효율성 향상
- `validate: true` - 안전한 코드 변경을 위한 검증
- `ultracompressed: false` - 일반적으로는 비활성화, 토큰 절약 필수시에만 활성화
- `plan: false` - 빠른 실행 우선, 복잡한 작업시 수동 활성화
- `introspect: false` - 일반 사용시 비활성화, 디버깅/학습시 수동 활성화

---

## 🧠 사고 모드 설정 (`thinking_modes`)

복잡한 문제 해결을 위한 사고 깊이 제어

```json
"thinking_modes": {
  "default_think": "standard",    // 기본 사고 모드: "none", "standard", "hard", "ultra"
  "auto_think": true,             // 복잡도에 따른 자동 --think 활성화
  "think_threshold": 0.6,         // 자동 --think 활성화 임계값 (0.0-1.0)
  "think_hard_threshold": 0.8,    // 자동 --think-hard 활성화 임계값
  "ultrathink_threshold": 0.9     // 자동 --ultrathink 활성화 임계값
}
```

**사고 모드별 특징:**
- **standard**: 4K 토큰, 모듈 수준 분석
- **hard**: 10K 토큰, 시스템 전체 분석  
- **ultra**: 32K 토큰, 아키텍처 수준 분석

**조정 가이드:**
- 단순 작업 위주: `think_threshold: 0.8`
- 복잡한 분석 작업: `think_threshold: 0.4`

---

## 🤖 페르소나 자동 활성화 설정 (`persona_settings`)

도메인별 전문가 AI 페르소나 자동 선택

```json
"persona_settings": {
  "default_persona": "mentor",    // 기본 페르소나 (null이면 자동 감지)
  "auto_activation": true,        // 컨텍스트 기반 자동 페르소나 활성화
  "confidence_threshold": 0.75,   // 자동 활성화 신뢰도 임계값
  "persona_preferences": {
    "architecture": "architect",  // 아키텍처 작업 → architect 페르소나
    "frontend": "frontend",       // 프론트엔드 → frontend 페르소나
    "backend": "backend",         // 백엔드 → backend 페르소나
    "analysis": "analyzer",       // 분석 작업 → analyzer 페르소나
    "security": "security",       // 보안 → security 페르소나
    "documentation": "scribe",    // 문서화 → scribe 페르소나
    "refactoring": "refactorer",  // 리팩토링 → refactorer 페르소나
    "performance": "performance", // 성능 최적화 → performance 페르소나
    "testing": "qa",             // 테스팅 → qa 페르소나
    "devops": "devops"           // 인프라/배포 → devops 페르소나
  }
}
```

**페르소나별 특징:**
- **mentor**: 교육적 설명, 중급 개발자 대상
- **architect**: 시스템 설계, 장기적 관점
- **analyzer**: 근본 원인 분석, 체계적 조사
- **security**: 위협 모델링, 취약점 분석

---

## 🌐 MCP 서버 설정 (`mcp_settings`)

외부 AI 서버와의 연동 설정

```json
"mcp_settings": {
  "auto_activation": true,      // MCP 서버 자동 활성화
  "context7_auto": true,        // Context7: 라이브러리 문서/패턴
  "sequential_auto": true,      // Sequential: 복잡한 다단계 분석
  "magic_auto": true,          // Magic: UI 컴포넌트 생성
  "playwright_auto": true,     // Playwright: 브라우저 자동화/테스팅
  "fallback_enabled": true,    // MCP 실패 시 WebSearch 폴백
  "timeout_ms": 30000,         // MCP 서버 타임아웃 (30초)
  "max_retries": 2             // 최대 재시도 횟수
}
```

**서버별 용도:**
- **Context7**: Spring Boot, React 등 프레임워크 문서
- **Sequential**: 복잡한 버그 분석, 아키텍처 리뷰
- **Magic**: React/Vue 컴포넌트, 디자인 시스템
- **Playwright**: E2E 테스트, 성능 측정

---

## ⚡ 성능 및 리소스 관리 (`performance`)

토큰 사용량과 처리 성능 최적화

```json
"performance": {
  "token_management": {
    "auto_compress_threshold": 0.75,  // 토큰 75% 사용시 자동 압축
    "emergency_threshold": 0.90,      // 90% 사용시 비상 모드
    "reserve_percentage": 0.10        // 예약 토큰 10%
  },
  "parallel_operations": {
    "enabled": true,                  // 병렬 작업 활성화
    "max_concurrent": 3,              // 최대 동시 작업 3개
    "batch_operations": true          // 배치 작업 최적화
  },
  "caching": {
    "enabled": true,                  // 캐싱 활성화
    "ttl_seconds": 3600,             // 캐시 유효시간 1시간
    "max_cache_size_mb": 100         // 최대 캐시 크기 100MB
  }
}
```

**조정 팁:**
- 토큰 절약 우선: `auto_compress_threshold: 0.6`
- 성능 우선: `auto_compress_threshold: 0.9`

---

## 🔄 Sub-Agent 위임 설정 (`delegation`)

### 🤖 Sub-Agent 기본 개념

**Sub-Agent란?** Claude Code의 Task 도구를 활용해 독립적인 AI 에이전트를 생성하고 병렬로 작업을 처리하는 시스템

**Sub-Agent 구조:**
```
Claude Code (Main)
├── Task 도구 호출
├── Sub-Agent 1 생성 (독립적인 Claude 인스턴스)
├── Sub-Agent 2 생성 (독립적인 Claude 인스턴스)
├── Sub-Agent 3 생성 (독립적인 Claude 인스턴스)
└── 결과 통합 및 분석
```

**기술적 구현:**
- **Task 도구**: Claude Code의 내장 도구로 sub-agent 생성
- **독립 실행**: 각 sub-agent는 별도의 Claude 인스턴스
- **병렬 처리**: 동시에 여러 작업 수행
- **결과 통합**: 메인 Claude가 모든 결과를 수집하여 통합

**Sub-Agent 설정:**
```json
"delegation": {
  "auto_delegate": true,        // 자동 위임 활성화/비활성화
  "file_threshold": 50,         // 파일 개수 임계값
  "directory_threshold": 7,     // 디렉토리 개수 임계값  
  "complexity_threshold": 0.8,  // 작업 복잡도 임계값
  "max_agents": 5,             // 최대 생성 가능 sub-agent 수
  "default_strategy": "auto",   // 위임 전략 선택
  "concurrency_limit": 3       // 동시 실행 sub-agent 수
}
```

### 🛠️ Sub-Agent 수동 설정

**수동 활성화 방법:**
```bash
# 특정 전략으로 sub-agent 활성화
/sc:analyze --delegate files    # 파일별 분산
/sc:analyze --delegate folders  # 폴더별 분산  
/sc:analyze --delegate tasks    # 작업별 분산
/sc:analyze --delegate auto     # 자동 최적 선택

# 동시 실행 수 제어
/sc:improve --concurrency 5    # 5개까지 동시 실행

# sub-agent 비활성화
/sc:analyze --no-delegate       # 단일 Claude로 실행
```

**Sub-Agent 작업 흐름:**
1. **작업 분석**: 메인 Claude가 작업을 분석하여 분할 가능성 판단
2. **전략 선택**: 파일/폴더/작업 유형 중 최적 전략 선택
3. **Sub-Agent 생성**: Task 도구로 독립적인 AI 에이전트들 생성
4. **작업 할당**: 각 sub-agent에게 특정 작업 영역 할당
5. **병렬 실행**: 모든 sub-agent가 동시에 작업 수행
6. **결과 수집**: 각 sub-agent의 결과를 메인 Claude가 수집
7. **통합 분석**: 전체 결과를 종합하여 최종 보고서 생성

### 🤖 Sub-Agent 동작 원리

```
메인 Claude (조율자)
    ├── Sub-Agent 1: api/ 모듈 분석 (backend 페르소나)
    ├── Sub-Agent 2: domain/ 모듈 분석 (architect 페르소나)  
    ├── Sub-Agent 3: usecase/ 모듈 분석 (analyzer 페르소나)
    └── Sub-Agent 4: legacy/ 모듈 분석 (refactorer 페르소나)
    
각 Sub-Agent가 독립적으로 작업 → 결과를 메인 Claude가 통합
```

### ⚡ 실제 동작 예시

**일반 모드 (순차 처리):**
```
사용자: "전체 프로젝트를 분석해줘"

1. api/ 분석 (5분)
2. domain/ 분석 (5분)  
3. usecase/ 분석 (5분)
4. legacy/ 분석 (5분)
총 20분 소요
```

**Sub-Agent 모드 (병렬 처리):**
```
사용자: "전체 프로젝트를 분석해줘"

Sub-Agent 1: api/ 분석    ████████ (5분)
Sub-Agent 2: domain/ 분석 ████████ (5분)
Sub-Agent 3: usecase/ 분석████████ (5분)  
Sub-Agent 4: legacy/ 분석 ████████ (5분)
                          └─ 모든 작업이 동시 진행
통합 및 보고 (1분)
총 6분 소요 (70% 시간 단축!)
```

### 🎯 자동 위임 조건

```json
"delegation": {
  "auto_delegate": true,        // 자동 위임 활성화
  "file_threshold": 50,         // 파일 50개 이상시 위임
  "directory_threshold": 7,     // 디렉토리 7개 이상시 위임
  "complexity_threshold": 0.8,  // 복잡도 0.8 이상시 위임
  "max_agents": 5,             // 최대 sub-agent 5개
  "default_strategy": "auto",   // 위임 전략
  "concurrency_limit": 3       // 동시 실행 sub-agent 3개
}
```

**자동 위임 트리거:**
- **파일 기준**: 50개 이상 파일 분석 필요시
- **디렉토리 기준**: 7개 이상 디렉토리 분석 필요시  
- **복잡도 기준**: 작업 복잡도가 0.8 이상일 때
- **도메인 기준**: 3개 이상 다른 도메인 작업시

### 🔧 위임 전략 상세

**1. `"files"` 전략 - 파일별 분산**
```
Task: "모든 Java 파일의 성능 이슈 찾기"

Sub-Agent A: UserController.java, AccountController.java 분석
Sub-Agent B: ItemService.java, StockService.java 분석  
Sub-Agent C: OutboundRepository.java, InboundRepository.java 분석

각자 N+1 쿼리, 메모리 누수 등을 독립적으로 찾음
```

**2. `"folders"` 전략 - 디렉토리별 분산**
```
Task: "전체 아키텍처 개선점 찾기"

Sub-Agent A: api/ 디렉토리 전체 (REST API 설계)
Sub-Agent B: domain/ 디렉토리 전체 (도메인 모델링)
Sub-Agent C: usecase/ 디렉토리 전체 (비즈니스 로직)
Sub-Agent D: legacy/ 디렉토리 전체 (기술 부채)

각자 해당 레이어의 문제점과 개선안 제시
```

**3. `"tasks"` 전략 - 작업 유형별 분산**
```
Task: "코드 품질 종합 개선"

Sub-Agent A (security): 보안 취약점 스캔
Sub-Agent B (performance): 성능 병목 분석  
Sub-Agent C (quality): 코드 품질 검사
Sub-Agent D (architecture): 설계 개선점 분석

각자 전문 영역에 집중하여 깊이 있는 분석
```

**4. `"auto"` 전략 - 상황에 맞는 최적 선택**
```
AI가 작업 특성을 분석하여 자동 선택:
- 단일 도메인 + 많은 파일 → "files" 전략
- 다중 도메인 + 모듈 구조 → "folders" 전략  
- 다양한 전문 영역 → "tasks" 전략
```

### 📊 성능 향상 메트릭

| 프로젝트 규모 | 일반 모드 | Sub-Agent 모드 | 시간 단축 |
|--------------|----------|----------------|-----------|
| **소규모** (10-30 파일) | 10분 | 8분 | 20% |
| **중규모** (50-100 파일) | 30분 | 12분 | 60% |
| **대규모** (200+ 파일) | 90분 | 25분 | 72% |
| **엔터프라이즈** (500+ 파일) | 240분 | 60분 | 75% |

### 🎭 Sub-Agent 페르소나 할당

**자동 페르소나 매칭:**
```
분석 대상에 따른 최적 페르소나 자동 할당:

• Controller/API → backend 페르소나
• Entity/Domain → architect 페르소나  
• Service/Usecase → analyzer 페르소나
• Security 관련 → security 페르소나
• 성능 이슈 → performance 페르소나
• 테스트 코드 → qa 페르소나
• 문서화 → scribe 페르소나
```

### ⚠️ 주의사항 및 제약

**리소스 제약:**
- 동시 실행: 최대 3개 (concurrency_limit)
- 메모리 사용량: 일반 모드의 2-3배
- 토큰 사용량: 병렬 처리로 인한 약간의 증가 (10-15%)

**적용 제외 상황:**
- 순차적 의존성이 있는 작업
- 단일 파일 편집 작업
- 실시간 상호작용이 필요한 작업
- 토큰 사용량이 매우 중요한 경우

### 🔧 설정 최적화 가이드

**개발 환경별 권장 설정:**

```json
// 개인 프로젝트 (리소스 절약)
"file_threshold": 100,
"directory_threshold": 10,
"max_agents": 3,
"concurrency_limit": 2

// 팀 프로젝트 (균형)  
"file_threshold": 50,
"directory_threshold": 7,
"max_agents": 5,
"concurrency_limit": 3

// 대규모 프로젝트 (성능 우선)
"file_threshold": 30,
"directory_threshold": 5, 
"max_agents": 7,
"concurrency_limit": 5
```

---

## 🌊 Wave 오케스트레이션 설정 (`wave_orchestration`)

**Wave 오케스트레이션이란?** 복잡하고 큰 작업을 여러 단계(Wave)로 나누어 체계적으로 실행하는 다단계 워크플로우 시스템

### 🌊 Wave 동작 원리

**일반 모드 vs Wave 모드 비교:**

```
일반 모드 (한번에 모든 것):
사용자: "전체 시스템을 개선해줘"
→ 모든 분석, 계획, 실행을 한 번에 처리 (overwhelm 위험)

Wave 모드 (단계적 접근):
사용자: "전체 시스템을 개선해줘"
┌─ Wave 1: 현황 분석 ─┐     ┌─ Wave 2: 개선 계획 ─┐
│ • 코드 품질 분석    │ ──→ │ • 우선순위 설정     │
│ • 성능 병목 식별    │     │ • 리팩토링 계획     │
│ • 보안 취약점 스캔  │     │ • 아키텍처 개선안   │
└─────────────────────┘     └─────────────────────┘
                                       │
┌─ Wave 3: 단계적 실행 ─┐     ┌─ Wave 4: 검증 및 최적화 ─┐
│ • 중요도 순 리팩토링 │ ←── │ • 변경사항 검증         │
│ • 성능 최적화       │     │ • 테스트 실행           │
│ • 보안 강화         │     │ • 문서 업데이트         │
└─────────────────────┘     └─────────────────────────┘
```

### ⚡ 실제 Wave 시나리오

**예시: CGKR 프로젝트 성능 최적화**

```
Wave 1: 성능 분석 (Analysis Phase)
├── Sub-Agent A: N+1 쿼리 패턴 분석
├── Sub-Agent B: 메모리 사용량 분석  
├── Sub-Agent C: API 응답시간 분석
└── 통합 보고서: "3개 주요 병목 발견"

Wave 2: 최적화 계획 (Planning Phase)  
├── 우선순위: 1) N+1 쿼리 2) 메모리 누수 3) API 캐싱
├── 영향도 분석: 각 개선의 예상 성능 향상
├── 위험도 평가: 변경으로 인한 부작용 가능성
└── 실행 계획: 단계별 상세 로드맵

Wave 3: 핵심 최적화 (Implementation Phase)
├── 1단계: @EntityGraph로 N+1 쿼리 해결
├── 2단계: 메모리 누수 코드 수정
├── 3단계: Redis 캐싱 레이어 추가
└── 각 단계마다 테스트 및 검증

Wave 4: 검증 및 모니터링 (Validation Phase)
├── 성능 테스트: 개선 전후 비교
├── 부작용 체크: 기능 동작 확인
├── 모니터링 설정: 지속적 성능 추적
└── 문서화: 변경사항 및 운영 가이드
```

### 🎯 자동 Wave 활성화 조건

```json
"wave_orchestration": {
  "enabled": true,              // Wave 모드 활성화
  "auto_detection": true,       // 자동 Wave 감지
  "wave_threshold": 0.7,        // Wave 활성화 임계값
  "default_strategy": "adaptive", // Wave 전략
  "max_waves": 5,               // 최대 Wave 수
  "validation_required": true,   // Wave 간 검증 필수
  "checkpoint_enabled": true    // 체크포인트 저장
}
```

**자동 Wave 트리거 조건:**
- **복잡도 ≥ 0.7** AND **파일 수 > 20** AND **작업 유형 > 2개**
- 키워드: "comprehensive", "systematic", "entire", "complete"
- 대규모 변경: 리팩토링, 마이그레이션, 아키텍처 변경
- 다단계 작업: 분석 → 계획 → 실행 → 검증이 필요한 경우

### 🔧 Wave 전략 상세

**1. `"progressive"` - 점진적 개선**
```
특징: 작은 개선을 반복적으로 적용
사용 사례: 코드 품질 개선, 성능 최적화

Wave 1: 가장 영향도 높은 20% 개선
Wave 2: 다음 30% 개선  
Wave 3: 나머지 50% 개선
각 Wave마다 검증 후 다음 단계 진행
```

**2. `"systematic"` - 체계적 분석**
```
특징: 철저한 분석 기반의 체계적 접근
사용 사례: 보안 감사, 아키텍처 리뷰

Wave 1: 전체 시스템 분석 및 현황 파악
Wave 2: 문제점 분류 및 우선순위 설정
Wave 3: 해결책 설계 및 계획 수립
Wave 4: 단계적 실행 및 검증
```

**3. `"adaptive"` - 적응적 실행**
```
특징: 상황에 따라 전략을 동적으로 조정
사용 사례: 복잡한 문제, 불확실성이 높은 작업

Wave 1: 초기 분석 및 상황 파악
Wave 2: 첫 번째 해결 시도 및 결과 평가
Wave 3: 결과에 따른 전략 조정 및 재실행
Wave 4: 최종 최적화 및 안정화
```

**4. `"enterprise"` - 대규모 시스템용**
```
특징: 대기업 수준의 대규모 변경 관리
사용 사례: 레거시 마이그레이션, 대규모 리팩토링

Wave 1: 전사적 영향도 분석 (비즈니스 연속성)
Wave 2: 단계별 마이그레이션 계획 (롤백 계획 포함)
Wave 3: 파일럿 실행 및 검증 (작은 범위 테스트)
Wave 4: 전체 실행 (단계적 확산)
Wave 5: 안정화 및 모니터링 (운영 이관)
```

### 📊 Wave vs Sub-Agent 비교

| 특징 | Sub-Agent | Wave |
|------|-----------|------|
| **목적** | 병렬 처리로 속도 향상 | 단계적 접근으로 품질 향상 |
| **방식** | 동시 실행 (horizontal) | 순차 실행 (vertical) |
| **적용** | 대량 분석, 독립적 작업 | 복잡한 워크플로우 |
| **장점** | 70% 시간 단축 | 90% 품질 향상 |
| **사용** | 파일 50개+ 또는 복잡도 0.8+ | 복잡도 0.7+ AND 다단계 작업 |

### 🔄 Wave + Sub-Agent 결합

**최강의 조합: Wave 내에서 Sub-Agent 활용**

```
Wave 1: 분석 단계
├── Sub-Agent 1: api/ 모듈 분석 (병렬)
├── Sub-Agent 2: domain/ 모듈 분석 (병렬)
└── Sub-Agent 3: legacy/ 모듈 분석 (병렬)
    └── 통합 분석 보고서

Wave 2: 계획 단계  
├── 분석 결과를 바탕으로 개선 계획 수립
└── 단일 Claude가 전체적 관점에서 계획

Wave 3: 실행 단계
├── Sub-Agent 1: 고우선순위 이슈 해결 (병렬)
├── Sub-Agent 2: 중우선순위 이슈 해결 (병렬)  
└── Sub-Agent 3: 저우선순위 이슈 해결 (병렬)
    └── 통합 검증

결과: 70% 속도 향상 + 90% 품질 향상
```

### ✅ Wave 체크포인트 시스템

**체크포인트 기능:**
```json
"checkpoint_enabled": true    // 각 Wave 완료시 저장점 생성
```

**동작 방식:**
1. **Wave 완료시**: 현재 상태를 체크포인트로 저장
2. **문제 발생시**: 이전 체크포인트로 롤백 가능
3. **재시작시**: 마지막 체크포인트부터 이어서 실행
4. **검증 실패시**: 해당 Wave만 재실행

### 🎯 CGKR 프로젝트 적용 예시

**시나리오: 레거시 모듈 현대화**

```
현재 상황: legacy/ 모듈의 80% 기술부채 해결

Wave 1: 레거시 분석 (adaptive 전략)
├── 기술부채 유형 분류 (하드코딩, 중복코드, 복잡한 로직)
├── 비즈니스 영향도 분석 (핵심 기능 vs 보조 기능)
├── 마이그레이션 위험도 평가 (데이터 무결성, 성능 영향)
└── 우선순위 매트릭스 생성

Wave 2: 마이그레이션 설계 (systematic 전략)  
├── 헥사고날 아키텍처 적용 계획
├── 도메인 모델 재설계 (DDD 패턴 적용)
├── API 인터페이스 표준화 계획
└── 단계별 이관 로드맵 (6개월 계획)

Wave 3: 핵심 기능 이관 (progressive 전략)
├── 1차: 사용자 관리 모듈 (위험도 낮음)
├── 2차: 재고 관리 모듈 (비즈니스 핵심)  
├── 3차: 주문 처리 모듈 (복잡도 높음)
└── 각 단계마다 기능 테스트 및 성능 검증

Wave 4: 최적화 및 안정화 (enterprise 전략)
├── 성능 튜닝 (쿼리 최적화, 캐싱)
├── 모니터링 시스템 구축 (알림, 대시보드)
├── 운영 문서화 (장애 대응, 배포 가이드)
└── 개발팀 교육 (새로운 아키텍처 패턴)

결과: 6개월에 걸친 안전한 레거시 현대화
```

### ⚠️ Wave 사용시 고려사항

**Wave 권장 상황:**
- 대규모 변경 작업 (50+ 파일)
- 다단계 워크플로우 필요
- 높은 품질과 안정성 요구
- 복잡한 의존성이 있는 작업

**Wave 비권장 상황:**
- 단순한 버그 수정
- 단일 파일 편집
- 빠른 프로토타입 작성
- 시간이 매우 제한적인 상황

### 🔧 설정 최적화 권장사항

```json
// 품질 우선 (대규모 프로젝트)
"wave_threshold": 0.6,        // 낮은 임계값으로 자주 활성화
"default_strategy": "systematic",
"validation_required": true,
"checkpoint_enabled": true

// 속도 우선 (빠른 개발)  
"wave_threshold": 0.8,        // 높은 임계값으로 필요시만
"default_strategy": "progressive", 
"validation_required": false,
"max_waves": 3

// 균형 잡힌 설정 (현재 권장)
"wave_threshold": 0.7,
"default_strategy": "adaptive",
"validation_required": true,
"max_waves": 5
```

---

## 🔁 반복 개선 설정 (`iterative_improvement`)

코드 품질을 점진적으로 개선하는 루프 시스템

```json
"iterative_improvement": {
  "auto_loop": true,            // 자동 루프 모드 감지
  "default_iterations": 3,      // 기본 반복 횟수
  "max_iterations": 10,         // 최대 반복 횟수
  "quality_threshold": 0.85,    // 품질 목표 임계값
  "interactive_mode": false     // 반복 간 사용자 확인
}
```

**자동 활성화 키워드:**
- "improve", "refine", "enhance", "polish", "fix"
- "iteratively", "step by step", "incrementally"

**사용 사례:**
- 코드 품질 개선
- 성능 최적화
- 문서 개선

---

## 📋 작업 관리 설정 (`task_management`)

TODO 생성 및 진행상황 추적

```json
"task_management": {
  "auto_create_todos": true,    // 자동 TODO 생성
  "min_steps_for_todo": 3,     // TODO 생성 최소 단계 수
  "real_time_updates": true,    // 실시간 진행상황 업데이트
  "progress_notifications": true, // 진행 알림
  "single_focus_mode": true     // 단일 작업 집중 모드
}
```

**TODO 자동 생성 조건:**
- 3단계 이상 작업
- 멀티파일 수정
- 복잡한 기능 구현

---

## 🔍 품질 관리 설정 (`quality_gates`)

코드 품질 보장을 위한 8단계 검증 시스템

```json
"quality_gates": {
  "enabled": true,              // 품질 게이트 활성화
  "validation_steps": 8,        // 검증 단계 수
  "auto_lint": true,           // 자동 린트 검사
  "auto_test": true,           // 자동 테스트 실행
  "security_scan": true,        // 보안 스캔
  "performance_check": true,    // 성능 검사
  "documentation_check": true   // 문서화 검사
}
```

**8단계 검증 프로세스:**
1. 구문 검사 (Syntax)
2. 타입 검사 (Type)
3. 린트 검사 (Lint)
4. 보안 검사 (Security)
5. 테스트 실행 (Test)
6. 성능 검사 (Performance)
7. 문서화 검사 (Documentation)
8. 통합 검사 (Integration)

---

## 🛡️ 보안 설정 (`security`)

안전한 코드 변경을 위한 보안 정책

```json
"security": {
  "safe_mode_auto": false,         // 자동 안전 모드
  "validation_required": true,      // 위험 작업 검증 필수
  "risk_threshold": 0.7,           // 위험도 임계값
  "backup_before_changes": true,   // 변경 전 백업
  "sensitive_data_protection": true // 민감정보 보호
}
```

**위험도 계산:**
- 복잡도 × 0.3 + 취약점 × 0.25 + 리소스 × 0.2 + 실패확률 × 0.15 + 시간 × 0.1

---

## 📊 프로젝트별 특화 설정 (`project_specific`)

CGKR 프로젝트에 특화된 기술 스택 설정

```json
"project_specific": {
  "framework": "spring_boot",    // Spring Boot 2.7.3
  "architecture": "hexagonal",  // 헥사고날 아키텍처
  "primary_language": "java",   // Java 11+
  "database": "mysql",          // MySQL 데이터베이스
  "build_tool": "gradle",       // Gradle 빌드 도구
  "test_framework": "junit5",   // JUnit 5 테스팅
  "code_style": "google",       // Google Java 스타일
  "documentation_format": "markdown" // 마크다운 문서
}
```

---

## 🎯 명령어별 기본 설정 (`command_defaults`)

특정 명령어에 자동 적용되는 플래그

```json
"command_defaults": {
  "/analyze": ["--think", "--seq"],                    // 분석: 깊이 있는 사고 + Sequential
  "/build": ["--uc", "--validate"],                   // 빌드: 압축 + 검증
  "/implement": ["--c7", "--magic"],                  // 구현: 문서 + UI 생성
  "/improve": ["--loop", "--validate"],               // 개선: 반복 + 검증
  "/document": ["--persona-scribe", "--c7"],          // 문서화: 작가 페르소나 + 문서
  "/test": ["--persona-qa", "--play"],                // 테스트: QA 페르소나 + Playwright
  "/troubleshoot": ["--think", "--seq", "--persona-analyzer"], // 문제해결: 사고 + 분석
  "/git": ["--persona-devops", "--validate"]          // Git: DevOps 페르소나 + 검증
}
```

---

## 🔧 고급 설정 (`advanced`)

실험적 기능 및 디버깅 옵션

```json
"advanced": {
  "experimental_features": false, // 실험적 기능 (베타 테스트용)
  "debug_mode": false,           // 디버그 모드 (상세 로그)
  "verbose_logging": false,      // 상세 로깅
  "telemetry_enabled": true,     // 원격 측정 (성능 분석용)
  "feature_flags": {
    "wave_v2": false,           // Wave 시스템 v2 (실험적)
    "enhanced_delegation": true, // 향상된 위임 시스템
    "smart_caching": true,      // 스마트 캐싱
    "adaptive_compression": true // 적응형 압축
  }
}
```

---

## 📈 모니터링 및 분석 (`monitoring`)

성능 추적 및 최적화 분석

```json
"monitoring": {
  "performance_tracking": true,      // 성능 추적
  "success_rate_tracking": true,     // 성공률 추적
  "token_usage_analytics": true,     // 토큰 사용량 분석
  "command_frequency_analysis": true, // 명령어 빈도 분석
  "optimization_suggestions": true   // 최적화 제안
}
```

---

## 🌍 지역화 설정 (`localization`)

한국 환경에 최적화된 설정

```json
"localization": {
  "default_language": "ko",     // 한국어
  "timezone": "Asia/Seoul",     // 서울 시간대
  "date_format": "YYYY-MM-DD",  // ISO 날짜 형식
  "number_format": "korean"     // 한국 숫자 형식
}
```

---

## 🎨 출력 형식 설정 (`output_formatting`)

마크다운 및 시각적 출력 제어

```json
"output_formatting": {
  "use_markdown": true,              // 마크다운 사용
  "code_syntax_highlighting": true,  // 코드 하이라이팅
  "emoji_enabled": true,             // 이모지 사용
  "table_formatting": "github",     // GitHub 스타일 테이블
  "diagram_format": "mermaid"       // Mermaid 다이어그램
}
```

---

## 🛠️ 설정 조정 가이드

### 토큰 효율성 우선
```json
{
  "compression": true,
  "auto_compress_threshold": 0.6,
  "ultracompressed": true,
  "verbose": false
}
```

### 품질 우선
```json
{
  "validate": true,
  "quality_gates.enabled": true,
  "iterative_improvement.auto_loop": true,
  "wave_orchestration.validation_required": true
}
```

### 성능 우선
```json
{
  "parallel_operations.enabled": true,
  "auto_delegate": true,
  "file_threshold": 30,
  "caching.enabled": true
}
```

### 초보자용
```json
{
  "default_persona": "mentor",
  "verbose": true,
  "plan": true,
  "interactive_mode": true
}
```

### 전문가용
```json
{
  "answer_only": true,
  "auto_think": false,
  "experimental_features": true,
  "debug_mode": true
}
```

---

## 📝 설정 변경 후 확인사항

1. **JSON 유효성**: 문법 오류 확인
2. **임계값 범위**: 0.0 ~ 1.0 사이 값
3. **페르소나 이름**: 정확한 페르소나 명 사용
4. **MCP 서버**: 사용 가능한 서버만 활성화
5. **명령어 플래그**: 유효한 플래그 조합

---

*마지막 업데이트: 2025-08-05*  
*CGKR 프로젝트 전용 SuperClaude 설정 가이드*


### 예시
```json
{
  "default_flags": {
    "compression": true,
    "ultracompressed": true,
    "answer_only": false,
    "validate": true,
    "safe_mode": false,
    "verbose": false,
    "plan": false,
    "introspect": false
  },
  "thinking_modes": {
    "default_think": "standard",
    "auto_think": true,
    "think_threshold": 0.6,
    "think_hard_threshold": 0.8,
    "ultrathink_threshold": 0.9
  },
  "persona_settings": {
    "default_persona": "mentor",
    "auto_activation": true,
    "confidence_threshold": 0.75,
    "persona_preferences": {
      "architecture": "architect",
      "frontend": "frontend",
      "backend": "backend",
      "analysis": "analyzer",
      "security": "security",
      "documentation": "scribe",
      "refactoring": "refactorer",
      "performance": "performance",
      "testing": "qa",
      "devops": "devops"
    }
  },
  "mcp_settings": {
    "auto_activation": true,
    "context7_auto": true,
    "sequential_auto": true,
    "magic_auto": true,
    "playwright_auto": true,
    "fallback_enabled": true,
    "timeout_ms": 30000,
    "max_retries": 2
  },
  "performance": {
    "token_management": {
      "auto_compress_threshold": 0.6,
      "emergency_threshold": 0.80,
      "reserve_percentage": 0.10
    },
    "parallel_operations": {
      "enabled": true,
      "max_concurrent": 3,
      "batch_operations": true
    },
    "caching": {
      "enabled": true,
      "ttl_seconds": 3600,
      "max_cache_size_mb": 100
    }
  },
  "delegation": {
    "auto_delegate": true,
    "file_threshold": 50,
    "directory_threshold": 7,
    "complexity_threshold": 0.8,
    "max_agents": 5,
    "default_strategy": "auto",
    "concurrency_limit": 3
  },
  "wave_orchestration": {
    "enabled": true,
    "auto_detection": true,
    "wave_threshold": 0.7,
    "default_strategy": "adaptive",
    "max_waves": 5,
    "validation_required": true,
    "checkpoint_enabled": true
  },
  "iterative_improvement": {
    "auto_loop": true,
    "default_iterations": 3,
    "max_iterations": 10,
    "quality_threshold": 0.85,
    "interactive_mode": false
  },
  "task_management": {
    "auto_create_todos": true,
    "min_steps_for_todo": 3,
    "real_time_updates": true,
    "progress_notifications": true,
    "single_focus_mode": true
  },
  "quality_gates": {
    "enabled": true,
    "validation_steps": 8,
    "auto_lint": true,
    "auto_test": true,
    "security_scan": true,
    "performance_check": true,
    "documentation_check": true
  },
  "security": {
    "safe_mode_auto": false,
    "validation_required": true,
    "risk_threshold": 0.7,
    "backup_before_changes": true,
    "sensitive_data_protection": true
  },
  "project_specific": {
    "framework": "spring_boot",
    "architecture": "hexagonal",
    "primary_language": "java",
    "database": "mysql",
    "build_tool": "gradle",
    "test_framework": "junit5",
    "code_style": "google",
    "documentation_format": "markdown"
  },
  "command_defaults": {
    "/analyze": ["--think", "--seq"],
    "/build": ["--uc", "--validate"],
    "/implement": ["--c7", "--magic"],
    "/improve": ["--loop", "--validate"],
    "/document": ["--persona-scribe", "--c7"],
    "/test": ["--persona-qa", "--play"],
    "/troubleshoot": ["--think", "--seq", "--persona-analyzer"],
    "/git": ["--persona-devops", "--validate"]
  },
  "advanced": {
    "experimental_features": false,
    "debug_mode": false,
    "verbose_logging": false,
    "telemetry_enabled": true,
    "feature_flags": {
      "wave_v2": false,
      "enhanced_delegation": true,
      "smart_caching": true,
      "adaptive_compression": true
    }
  },
  "monitoring": {
    "performance_tracking": true,
    "success_rate_tracking": true,
    "token_usage_analytics": true,
    "command_frequency_analysis": true,
    "optimization_suggestions": true
  },
  "localization": {
    "default_language": "ko",
    "timezone": "Asia/Seoul",
    "date_format": "YYYY-MM-DD",
    "number_format": "korean"
  },
  "output_formatting": {
    "use_markdown": true,
    "code_syntax_highlighting": true,
    "emoji_enabled": true,
    "table_formatting": "github",
    "diagram_format": "mermaid"
  }
}
```