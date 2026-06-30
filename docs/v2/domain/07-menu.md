# menu 컨텍스트

> 쓰기 모델: **현행 (상태 테이블)** · 구독 필요 시 Outbox 추가

---

## 1. V1 현행 분석

### 애그리거트: Menu

```
Menu
├── id: String?
├── restaurantId: String
├── information: MenuDescription(title, description)
├── menuPhotoBook: MenuPhotoBook → List<MenuPhoto(url)>  (max 10)
├── attributes: MenuAttributes(isRepresentative, isRecommended, isVisible)
└── price: MenuPrice(amount: BigDecimal)
```

- **행위**: `changeInformation`, `changeAttributes`, `changePrice`, `manipulatePhoto`, `snapshot()`
- **도메인 서비스**: `CreateMenuDomainService`, `ChangeMenuDomainService` — 검증+생성/수정
- **정책**: 제목 빈값·길이, 설명 빈값·길이, 가격 범위, restaurantId 포맷

### V1 한계

| 한계 | 설명 |
|------|------|
| 검증 서비스 외부 | 11개 검증 Policy가 서비스에서 실행 |
| 이벤트 없음 | 메뉴 등록/수정/삭제 이벤트 없음 |

---

## 2. V2 이벤트 스토밍

### 액터 → 커맨드 → 이벤트

| 액터 | 커맨드 | 애그리거트 | 도메인 이벤트 | 비고 |
|------|--------|-----------|-------------|------|
| 매장 점주 | `CreateMenu` | Menu | `MenuCreated` | |
| 매장 점주 | `UpdateMenu` | Menu | `MenuUpdated` | 제목·설명·가격·속성 |
| 매장 점주 | `HideMenu` | Menu | `MenuHidden` | 비공개 |
| 매장 점주 | `DeleteMenu` | Menu | `MenuDeleted` | 논리 삭제 |

### 불변식

| # | 불변식 |
|---|--------|
| 1 | 메뉴명: 필수, 최대 30자 |
| 2 | 설명: 필수, 최대 255자 |
| 3 | 사진: 최대 10장 |
| 4 | 가격: 0 이상 |

---

## 3. V1→V2 변경 요약

현행/lookup 컨텍스트라 변경 최소. 검증 로직만 애그리거트로 이전. 다른 컨텍스트가 메뉴 변경을 구독해야 할 때 Outbox 이벤트 추가.
