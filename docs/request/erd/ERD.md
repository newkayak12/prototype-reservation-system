```mermaid
---
prototype
---
erDiagram
    
    user[user] {
        VARCHAR(128) id "식별키"
        VARCHAR(32) login_id "사용자 아이디"
        
        VARCHAR(256) password "비밀번호"
        VARCHAR(256) old_password "변경 전 비밀번호"
        DATETIME password_changed_datetime "비밀번호 변경 시간"
        
        VARCHAR(32) email "이메일"
        VARCHAR(16) nickname "닉네임"
        VARCHAR(13) mobile "휴대폰 번호"
        ENUM role "역할 (ROOT, SELLER, USER)"
        
        TINYINT fail_count "로그인 실패 카운트"
        DATETIME locked_datetime "접근 잠긴 시간"
        
        
        DATETIME created_datetime "생성일"
        DATETIME updated_datetime "수정일"
    }

    user_change_history[user_change_history] {
        VARCHAR(128) id "식별키"
        VARCHAR(128) user_id "식별키"
        VARCHAR(32) log_id "사용자 아이디"
        VARCHAR(32) email "이메일"
        VARCHAR(16) nickname "닉네임"
        VARCHAR(13) mobile "휴대폰 번호"
        ENUM role "역할 (ROOT, SELLER, USER)"
        TINYINT fail_count "로그인 실패 카운트"
        DATETIME locked_datetime "접근 잠긴 시간"
        DATETIME created_datetime "생성일"
        DATETIME updated_datetime "수정일"
    }
    
    user_access_history[user_access_history] {
        VARCHAR(128) id "식별키"
        VARCHAR(128) user_id "식별키"
        ENUM status "상태(SUCCESS, FAILURE)",
        DATETIME access_datetime
    }
```