# SBI Analysis (Situation-Behavior-Impact)

## 1. 아키텍처 설계 역량

### Situation (상황)
- 복잡한 예약 시스템을 확장 가능하고 유지보수 가능한 구조로 설계 필요
- 비즈니스 로직과 인프라 계층의 명확한 분리 요구사항  
- "공부한 내용을 실제로 구현해보는" 실험적 프로젝트 특성
- Over-Engineering을 통한 엄격하고 풍부한 애플리케이션 개발 목표

### Behavior (행동)
- Hexagonal Architecture + DDD + Clean Architecture 조합 적용
- 5개 모듈(shared, core, application, adapter, test) 완전 분리 설계
- Core 모듈 외부 의존성 제로 달성으로 순수 비즈니스 로직 보장
- CQRS 패턴으로 읽기/쓰기 작업 분리 및 UseCase 세분화
- 103개 GitHub 이슈를 통한 체계적인 개발 프로세스 관리

### Impact (결과)
- 445개 파일 규모에서 Zero-tolerance 코드 품질 정책 달성
- 모듈 간 단방향 의존성으로 변경 영향 최소화 및 테스트 독립성 확보
- JPA와 Domain Entity 분리로 개념적 모델링과 테이블 설계 독립성 달성
- 최신 기술 스택(Spring Boot 3.4.5, Kotlin 2.0.10) 완전 활용

## 2. 코드 품질 관리 역량

### Situation (상황)
- 445개 파일 규모의 대규모 프로젝트에서 일관된 품질 기준 필요
- 실험적 프로젝트 특성상 다양한 패턴 시도로 인한 코드 복잡도 증가
- 포트폴리오 목적으로 엔터프라이즈급 품질 기준 적용 요구
- Main branch 직접 push 금지 정책으로 엄격한 코드 리뷰 프로세스 운영

### Behavior (행동)
- Detekt maxIssues: 0 정책으로 Zero-tolerance 품질 기준 설정
- Spotless + Ktlint 1.2.1로 코드 포매팅 완전 자동화
- Pre-commit hook으로 spotlessApply → detekt → git add 파이프라인 구축
- Slice Test 전략으로 계층별 독립 테스트 환경 구축
- Kotest(Adapter), JUnit(Application), MockK(전 계층) 테스트 프레임워크 분리

### Impact (결과)
- 445개 파일에서 정적 분석 오류 0개 달성 및 지속 유지
- 103개 GitHub 이슈 중 95% 완료로 체계적 개발 프로세스 증명
- Layer별 차별화된 테스트 전략으로 각 계층의 순수성 검증
- EdgeCase 검증을 위한 Fixture Monkey 도입으로 테스트 품질 향상

## 3. 보안 구현 역량

### Situation (상황)
- 사용자 인증/인가가 필요한 예약 시스템 개발
- 개인정보 보호와 XSS 공격 방어 필요
- 엔터프라이즈급 보안 기준 적용 요구

### Behavior (행동)
- Custom JWT + Refresh Token 인증 시스템 직접 구현
- AES-256-CTR 모드 양방향 암호화 유틸리티 개발
- XSS 방어를 위한 RequestWrapper 필터 구현
- Role-based 권한 체계(USER, SELLER, ADMIN) 설계

### Impact (결과)
- Spring Security 기본 구현 대비 요구사항 맞춤형 보안 달성
- 민감 정보 암호화로 데이터 유출 위험 최소화
- XSS 공격 벡터 원천 차단으로 보안 등급 향상
- 확장 가능한 권한 체계로 비즈니스 성장 대응

## 4. 성능 최적화 역량

### Situation (상황)
- 예약 시스템의 높은 동시 접속과 빈번한 DB 조회 예상
- 대용량 데이터 처리와 응답 시간 최적화 필요
- 분산 시스템 환경에서의 확장성 고려 필요

### Behavior (행동)
- QueryDSL을 통한 타입 안전 쿼리 최적화
- Redis 기반 캐싱 전략으로 DB 부하 분산
- Time-based UUID 생성으로 분산 환경 ID 충돌 방지
- Database 인덱스 전략적 설계 및 논리적 삭제 패턴 적용

### Impact (결과)
- 컴파일 타임 쿼리 검증으로 런타임 오류 제거
- 캐싱을 통한 응답 시간 개선 및 서버 리소스 절약
- 분산 환경에서 ID 유일성 보장으로 데이터 무결성 확보
- 대용량 데이터 환경에서도 안정적인 성능 유지 가능

## 5. 개발 프로세스 개선 역량

### Situation (상황)
- 학습 목적의 실험적 프로젝트로 다양한 기술 스택 통합 필요
- 이벤트 스토밍부터 실제 구현까지 완전한 DDD 개발 프로세스 적용
- GitHub 기반 이슈 관리와 PR 템플릿을 통한 체계적 협업 환경 구축
- 12단계 Flyway 마이그레이션으로 복잡한 데이터베이스 스키마 진화 관리

### Behavior (행동)
- 이벤트 스토밍 → 요구사항 정리 → ERD 설계 → 구현 순서로 체계적 접근
- GitHub 이슈 템플릿(feat, chore, refactor, fix)으로 작업 분류 체계화
- PR 템플릿과 리뷰 프로세스로 코드 품질 관리 강화
- Docker Compose + Testcontainers로 개발/테스트 환경 완전 격리
- Custom Gradle Task로 gitPreCommitHook 자동화 파이프라인 구축

### Impact (결과)
- 103개 이슈 중 95% 완료로 프로젝트 관리 역량 입증
- V1.0~V1.11 Flyway 마이그레이션으로 안전한 스키마 진화 관리
- Docker 기반 환경 통합으로 로컬/테스트 환경 일관성 100% 달성
- 사용자, 매장, 카테고리, 회사, 레스토랑 도메인 완전 구현
- Event-driven architecture 기반으로 확장 가능한 시스템 설계

## 6. 기술 리더십 및 문서화 역량

### Situation (상황)
- 복잡한 아키텍처 패턴을 체계적으로 학습하고 기록해야 하는 필요성
- 포트폴리오 목적으로 기술적 의사결정 과정과 트레이드오프 문서화 요구
- 팀 협업을 위한 코딩 컨벤션과 개발 가이드라인 정립 필요

### Behavior (행동)
- .github/technical/ 디렉토리에 Port & Adapter, JPA, Flyway, Law of Demeter 상세 문서 작성
- 테스트 전략, 코드 스타일, 품질 도구별 가이드 문서 체계화
- 아키텍처 적용 과정에서 발생한 8가지 주요 고민사항 분석 및 해결책 문서화
- Mermaid 다이어그램을 활용한 시각적 아키텍처 설명

### Impact (결과)
- 15개 이상의 상세 기술 문서로 프로젝트 지식 베이스 구축
- 복잡한 매핑 과정과 보일러플레이트 코드에 대한 트레이드오프 분석 완료
- Domain Repository vs Output Port, UseCase 크기 등 아키텍처 의사결정 근거 명문화
- 후속 개발자가 프로젝트 구조와 설계 의도를 즉시 이해할 수 있는 문서 체계 완성