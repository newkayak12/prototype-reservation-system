# company 컨텍스트

> 쓰기 모델: **현행 (상태 테이블)** · 업체/브랜드 정보

---

## 1. V1 현행 분석

### 애그리거트: Company

```
Company
├── id: String
├── brand: Brand(name, url)
├── business: Business(businessNumber, corporateRegistrationNumber)  — 불변
├── companyContact: CompanyContact(phone, email, url)
├── companyAddress: CompanyAddress(zipCode, address, detail)
└── representative: Representative(representativeName, representativeMobile)
```

- **행위**: `changeBrand()` — 브랜드 변경 뿐
- **도메인 서비스**: 없음
- **이벤트**: 없음
- **특징**: `Business` VO는 변경 메서드가 없어 구조적으로 불변(사업자번호·법인등록번호는 변경 불가)이지만, 값 자체에 대한 포맷/길이 검증은 없다 (모든 VO에 `require`/정책 클래스 없음)

---

## 2. V2 이벤트 스토밍

저빈도 lookup. 관리자/매장 점주가 업체 정보를 관리하는 단순 흐름.

| 액터 | 커맨드 | 이벤트 | 비고 |
|------|--------|--------|------|
| 관리자 | `RegisterCompany` | `CompanyRegistered` | |
| 매장 점주 | `UpdateCompany` | `CompanyUpdated` | 브랜드·연락처·대표자 |

restaurant가 companyId를 참조.

---

## 3. V1→V2 변경 요약

변경 거의 없음. 현행 유지.
