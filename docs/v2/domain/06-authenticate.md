# authenticate 컨텍스트

> 쓰기 모델: **상태 + Outbox** · 로그인·잠금·접근 이력

---

## 1. V1 현행 분석

### 애그리거트: Authenticate

```
Authenticate
├── id: String
├── loginId: LoginId
├── password: Password (encoded)
├── lockState: LockState
│   ├── failCount: Int
│   ├── lockedDateTime: LocalDateTime?
│   ├── userStatus: UserStatus (ACTIVATED | DEACTIVATED)
│   └── 행위: hasExceededFailCount(), isLockdownTimeOver(), deactivate(), activate(), addFailureCount()
├── role: Role
└── accessLog: List<AccessHistory>
    └── AccessHistory(authenticateId, loginId, accessDetails)
        └── AccessDetails(accessStatus, accessDateTime)
```

- **행위**: `canISignIn(rawPassword, policy)` — 비밀번호 확인 + 잠금 체크 + 접근 이력 기록
- **도메인 서비스**: `AuthenticateSignInDomainService` — `NormalSignInPolicy`(5회, 30분)로 `canISignIn` 호출
- **정책**: `SignInPolicy` 인터페이스 → `NormalSignInPolicy`(limitCount=5, interval=30, unit=MINUTES)

### V1 한계

| 한계 | 설명 |
|------|------|
| 행위 있음 (예외) | `canISignIn`은 V1에서 거의 유일하게 애그리거트 안에 행위가 있는 사례 |
| 부수효과 내재 | `canISignIn` 안에서 lockState 변이 + accessLog 추가가 동시에 일어남 |
| 이벤트 없음 | 로그인 성공/실패/잠금 이벤트가 없다 |

---

## 2. V2 이벤트 스토밍

### 액터 → 커맨드 → 이벤트

| 액터 | 커맨드 | 애그리거트 | 도메인 이벤트 | 정책 / 후속 |
|------|--------|-----------|-------------|-------------|
| 사용자 | `SignIn` | Authenticate | `SignInSucceeded` / `SignInFailed` | 접근 이력 기록 |
| 정책 | (5회 실패) | Authenticate | `AccountLocked` | 30분 잠금 |
| 시스템 | (30분 경과) | Authenticate | `AccountUnlocked` | 자동 해제 |
| 사용자 | `SignOut` | — | `SignedOut` | 세션/토큰 무효화 |
| 사용자 | `FindLoginId` | — (조회) | — | 이메일로 조회 → 60% 마스킹 |
| 사용자 | `ResetPassword` | Authenticate | `TemporaryPasswordIssued` | 이메일 발송 |

### 불변식

| # | 불변식 |
|---|--------|
| 1 | 5회 연속 실패 → 30분 잠금 |
| 2 | 잠금 상태에서 로그인 시도 불가 |
| 3 | 임시 비밀번호 발급 → `isNeedToChangePassword = true` |
| 4 | 로그인 성공/실패 기록은 항상 남긴다 |

---

## 3. V1→V2 변경 요약

| 항목 | V1 | V2 |
|------|----|----|
| 리치 정도 | 비교적 양호 (`canISignIn` 행위 존재) | handle/apply 패턴으로 정규화 |
| 이벤트 | 없음 | 5개 (성공/실패/잠금/해제/임시비밀번호) |
| 부수효과 | `canISignIn` 안에서 상태 변이+이력 동시 | 이벤트 분리 (SignInSucceeded → apply로 이력 추가) |
