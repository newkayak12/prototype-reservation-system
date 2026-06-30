# category 컨텍스트

> 쓰기 모델: **현행 (상태 테이블)** · 마스터 데이터 관리

---

## 1. V1 현행 분석

### 애그리거트

3종의 카테고리를 공통 VO(`CategoryDetail`)로 관리:

```
Cuisine(id: Long, categoryDetail: CategoryDetail)     — 요리 종류
Nationality(id: Long, categoryDetail: CategoryDetail)  — 국적
Tag(id: Long, categoryDetail: CategoryDetail)          — 태그

CategoryDetail(title: String, categoryType: CategoryType)
```

- **행위**: `changeTitle(newTitle)` — 제목 변경 뿐
- **도메인 서비스**: 없음
- **이벤트**: 없음

### V1 한계

특별한 한계 없음. 저빈도 마스터 데이터라 현행 유지가 적절.

---

## 2. V2 이벤트 스토밍

마스터 데이터 CRUD. 관리자가 카테고리를 추가/수정하는 단순 흐름.

| 액터 | 커맨드 | 이벤트 | 비고 |
|------|--------|--------|------|
| 관리자 | `CreateCategory` | `CategoryCreated` | cuisine/nationality/tag |
| 관리자 | `UpdateCategory` | `CategoryUpdated` | 제목 변경 |

restaurant가 카테고리 ID를 참조하므로, 카테고리 삭제 시 참조 무결성을 검토해야 한다 (soft delete 또는 삭제 금지).

---

## 3. V1→V2 변경 요약

변경 거의 없음. 현행 유지. 필요 시 Outbox 이벤트 추가.
