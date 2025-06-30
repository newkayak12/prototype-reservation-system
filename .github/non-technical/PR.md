# PR에 대해서

## 형태적

- 기존의 PR 포맷에서 아래와 같이 변경했다.
```yaml
## 변경 전
## 🎫 Context

- github:
- jira:

## 📄 Summary
- ~한 작업 진행

## 🔥 Impact
- ~한 변경점이 생김

## 🚦 How to Test (검증방법)
- ~를 테스트

###################################################################
## 변경 후 
## 🎫 Context

- github:
- jira:

## 📄 Summary
- ~한 작업 진행

## ✨ What’s Changed
- ~를 수정

## 🧩 Motivation & Background
- ~해서 필요

## 🔥 Impact
- ~한 변경점이 생김

## 🚦 How to Test (검증방법)
- ~를 테스트

## 📝 Notes
  <!-- 기타, 추가 설명, 리뷰 포인트 등 -->
- 참고/리뷰 요청/논의 사항 등
```
- 이런 변경에는 조그마한 고민이 있었다.
  1. 보통 티켓은 어떻게 발급되는가?
  2. 문서화를 왜 하는가?
  3. PullRequest는 적당히 작성해도 되는건가?
  4. PullRequest의 횟수는 어느정도가 적당한가?




## 양태적