# user 컨텍스트

> 쓰기 모델: **상태 + Outbox** · 회원 관리 (일반 사용자 · 매장 점주 · 관리자)

---

## 1. V1 현행 분석

### 애그리거트: User 계층

```
ServiceUser (interface)
├── PasswordChangeable · PersonalAttributesChangeable · UserResignable · UserAttributeChangeable
│
├── User (일반 사용자)
│   ├── id, loginId: LoginId, password: Password
│   ├── personalAttributes: PersonalAttributes(email, mobile)
│   ├── userAttributes: UserAttribute(nickname, role=USER)
│   └── 행위: resign(), changePassword(), changePersonalAttributes(), changeUserNickname()
│
├── RestaurantOwner (매장 점주)
│   ├── 동일 구조, role=RESTAURANT_OWNER
│   └── 행위: 동일
│
└── Admin (관리자)
    ├── id, loginId, password, role=ROOT
    └── 행위: resign(), changePassword() only
```

- **VO**: `LoginId`, `Password`(encodedPassword, oldPassword, changedDateTime, isNeedToChange), `PersonalAttributes`(email, mobile), `UserAttribute`(nickname, role)
- **도메인 서비스**: 5개 — `CreateGeneralUserDomainService`, `CreateSellerUserDomainService`, `ChangeGeneralUserPasswordDomainService`, `ChangeUserNicknameDomainService`, `ResignUserDomainService`
- **이벤트**: 없음
- **보조 엔티티**: `ResignedUser`(탈퇴 사용자 — 암호화된 개인정보 보관)

### V1 한계

| 한계 | 설명 |
|------|------|
| 빈약 도메인 | 검증 로직이 5개 DomainService에 분산. 애그리거트는 getter+setter. |
| 이벤트 없음 | 가입·탈퇴·정보변경 이벤트가 없다. |
| 암호화 의존 | `ResignUserDomainService`가 `PasswordEncoderUtility`(인프라)에 의존 |
| User/Owner 중복 | 구조가 거의 동일한데 별도 클래스 |

---

## 2. V2 이벤트 스토밍

### 액터 → 커맨드 → 이벤트

| 액터 | 커맨드 | 애그리거트 | 도메인 이벤트 | 정책 / 후속 |
|------|--------|-----------|-------------|-------------|
| 일반 사용자 | `RegisterUser` | User | `UserRegistered` | |
| 관리자 | `RegisterSeller` | User | `SellerRegistered` | role=RESTAURANT_OWNER |
| 사용자 | `ChangePassword` | User | `PasswordChanged` | 이전 비밀번호와 동일 불가 |
| 사용자 | `ChangeNickname` | User | `NicknameChanged` | |
| 사용자 | `ChangePersonalInfo` | User | `PersonalInfoChanged` | email, mobile |
| 사용자 | `ResignUser` | User | `UserResigned` | PII 암호화 후 보관 |

### 불변식

| # | 불변식 |
|---|--------|
| 1 | 아이디: 영문+숫자, 8~12자 |
| 2 | 비밀번호: 영문+숫자+특수문자, 12~20자 |
| 3 | 이메일: 필수, 유효 형식 |
| 4 | 닉네임: 필수, 2~20자 |
| 5 | 비밀번호 변경 시 이전과 동일 불가 |
| 6 | 임시 비밀번호 로그인 시 변경 강제 |

### 읽기 모델

| 뷰 | 소비자 | 데이터 |
|----|--------|--------|
| 내 정보 | 사용자 | 닉네임, 이메일(마스킹), 모바일 |
| 아이디 찾기 결과 | 사용자 | 아이디 60% 마스킹 |

---

## 3. V1→V2 변경 요약

| 항목 | V1 | V2 |
|------|----|----|
| 도메인 서비스 | 5개 | 검증 → 애그리거트 handle |
| User/Owner | 별도 클래스 | 단일 User + role 구분 검토 |
| 이벤트 | 없음 | 6개 |
| PII 처리 | 서비스에서 암호화 | 포트 경계로 분리 ([[DESIGN-016]]) |
