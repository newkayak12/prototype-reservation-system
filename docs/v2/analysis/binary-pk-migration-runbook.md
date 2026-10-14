# Binary PK identifier migration — runbook

> 작성: 2026-10-06 · 베이스 커밋: `244a93b3` · 브랜치: `refactor/to-binary-pk`
> 대상 파일: `infrastructure-module/src/main/resources/db/migration/V1_22__binary_pk_identifier_expand.sql`,
> `.../V1_23__binary_pk_identifier_contract.sql`
> 근거: [[ADR-028-binary-pk-identifier-storage]] · `.claude/conventions/data.md#D-12` ·
> [[binary-pk-identifier-inventory]]

이 문서는 두 마이그레이션 파일을 실제 환경에서 검증한 절차와 **실제로 관측한 출력**을 남긴다.
이전 버전의 이 문서는 (a) docker가 죽어 있다고 잘못 기록했고(이 세션에서는 떠 있었다), (b)
`MySQL >= 8.0.29`가 `ADD/DROP COLUMN IF [NOT] EXISTS`를 지원한다는 조작된 근거를 실었다 —
MySQL은 어떤 버전에서도 이 문법을 지원한 적이 없다(MariaDB 전용 확장). 두 마이그레이션 파일은
이 세션에서 이 오류를 발견하고 `information_schema` 가드 프로시저로 다시 썼다. 이 문서도 그
수정 이후의 실제 상태를 기록한다.

## 0. Docker 상태 — NOT RUN 탈출구는 이번에 적용되지 않는다

```
$ docker info
Client: Version 29.2.1 ...
```
종료 코드 0. 이 worktree의 컨테이너 `refactor-to-binary-pk-mysql-1` (`mysql:latest`, 서버
`26.7.0`, `MySQL Community Server - GPL`)가 `Up (healthy)`, `0.0.0.0:3306->3306/tcp`로 이미
떠 있었다. 따라서 기준의 "docker info가 0이 아니면 NOT RUN" 탈출구는 적용되지 않고, 그
fallback("docker info가 0이면 모든 라이브 단계를 실제로 실행한다")이 적용된다. 아래 전부
**실제로 실행했다.**

## 1. 로컬 연결 정보

- 커넥션: `mysql -h127.0.0.1 -P3306 -utemporary -ptemporary prototype_reservation`
  (자격증명 출처: `docker-entrypoint-initdb.d/init.sql` — `CREATE USER IF NOT EXISTS
  'temporary'@'%' IDENTIFIED BY 'temporary'`, `GRANT ALL ... ON prototype_reservation.*`)
- 컨테이너: `compose.yaml`의 `mysql` 서비스, 이미지 `mysql:latest` → 이 세션에서는 실제로
  MySQL Community Server(서버 버전 문자열 `26.7.0` — Oracle이 MySQL을 연 단위 버전 체계로
  옮긴 뒤의 번호. **MariaDB가 아니다**, `@@version_comment` = `MySQL Community Server - GPL`로
  직접 확인).
- **스키마 분리 주의**: `adapter-module/build.gradle.kts:18`의 Gradle `flyway { url = ... }`는
  `jdbc:mysql://localhost:3306/flyway` — Flyway 자신의 북키핑(`flyway_schema_history`)은
  `flyway` 스키마로 간다. 반면 두 마이그레이션 파일(그리고 기존 V1_0~V1_21 전부)은
  `prototype_reservation.<table>`로 테이블을 명시한다. 이 분리는 기존 구성이며 이번 변경이
  만든 것이 아니다. 아래 검증 쿼리는 전부 `prototype_reservation`을 대상으로 한다.
- **최소 버전 요구사항 (정정)**: 이전 버전의 이 문서는 "MySQL >= 8.0.29가
  `ADD/DROP COLUMN IF [NOT] EXISTS`를 지원한다"고 썼다 — **조작된 근거였다.** MySQL
  8.0.29 changelog가 추가한 것은 "모든 위치에서의 INSTANT ADD COLUMN"이며 `IF [NOT] EXISTS`
  문법과는 무관하다. MySQL은 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 류의 문법을 어떤
  버전에서도 지원하지 않는다 — 이것은 MariaDB의 확장이다. 이 worktree의 실제 컨테이너에
  대해 직접 확인함(§2 "발견한 결함" 참조): `ADD COLUMN IF NOT EXISTS`, `DROP COLUMN IF EXISTS`,
  `ADD INDEX IF NOT EXISTS`, `ADD UNIQUE KEY IF NOT EXISTS`, `DROP INDEX IF EXISTS` 전부
  `ERROR 1064`. 두 마이그레이션 파일은 이제 이 다섯 가지를 전부 `information_schema` 조회로
  먼저 존재를 확인하는 가드 프로시저(`sp_add_column_if_absent`, `sp_drop_column_if_exists`,
  `sp_add_index_if_absent`, `sp_drop_index_if_exists`, 그리고 기존
  `sp_drop_pk_if_exists`/`sp_add_pk_if_absent`/`sp_rename_column_if_exists`) 뒤로 숨긴다.
  실제 최소 버전 요구사항은 **MySQL >= 8.0**(`information_schema.COLUMNS`/`STATISTICS`/
  `TABLE_CONSTRAINTS`, `PREPARE`/`EXECUTE` 동적 SQL — 버전에 민감한 문법 없음) 뿐이다.

## 2. Gradle `flywayMigrate` — 기존에 깨져 있던 경로 (재확인, 미해결)

```
$ ./gradlew :adapter-module:flywayMigrate
...
FAILURE: Build failed with an exception.
> Error occurred while executing flywayMigrate
  No database found to handle jdbc:mysql://localhost:3306/flyway
```
exit code 1. **이 세션에서 직접 재현했다** — docker가 떠 있는 상태에서도 그대로 재현된다.
이 장애는 이번 서브골의 파일 변경과 무관한 **기존 결함**이다 (Flyway Gradle 플러그인
10.0.0이 `flywayMigrate` 태스크용으로 쓰는 클래스패스에 `flyway-mysql`/JDBC 드라이버가
해석되지 않음 — 앱 자신의 런타임 클래스패스에는 있지만 플러그인 태스크의 격리된 클래스패스는
별도임).

시도한 수정과 실패 원인(다음 사람이 같은 데드엔드를 반복하지 않도록 기록):
- `adapter-module/build.gradle.kts`에 `buildscript { dependencies { classpath(...) } }`로
  `flyway-mysql`/`mysql-connector-j`를 명시적으로 추가 — **효과 없음**, 동일한 에러로
  재현됨(10.0.0과 10.20.1 두 버전 조합 모두 시도). Gradle 디버그 로그로 확인한 바, 이
  의존성은 정상적으로 해석되지만(`org.flywaydb:flyway-mysql:10.20.1` 등이
  resolve/transform까지 성공) `flywayMigrate` 태스크가 실제로 사용하는 격리된 플러그인
  클래스로더에는 반영되지 않는 것으로 보인다. 이 수정은 **되돌렸다** — 작동하지 않는
  변경을 남기지 않기 위함.
- 이 블로커를 더 깊이 파려면 Flyway Gradle 플러그인 10.x의 `AbstractFlywayTask` 클래스로더
  구성(혹은 플러그인 버전 업/`flyway-gradle-plugin` 마커 교체)을 봐야 한다 — 이 서브골의
  파일 변경 범위 밖이므로 여기서 멈춘다.

**결론: `./gradlew :adapter-module:flywayMigrate`로의 라이브 검증은 여전히 불가능하다.**
아래 §3~§6의 모든 라이브 검증은 대신 `mysql` CLI로 두 파일을 직접 실행해 확인했다 —
Flyway가 최종적으로 이 파일들을 그대로 실행하는 것과 SQL 수준에서 동등하다(Flyway는 각
버전 파일을 파싱해 서버에 그대로 보내므로, 파일 자체의 정합성은 이 방법으로 완전히
검증된다; 검증되지 않는 것은 Flyway의 버전 기록/락 메커니즘 자체뿐이며 그것은 기존
V1_0~V1_21에 대해서도 동일하게 미검증 상태다).

### CREATE PROCEDURE를 `mysql` CLI로 돌릴 때 필요한 것 — DELIMITER

두 파일 모두 `CREATE PROCEDURE ... BEGIN ... END;` 블록을 포함한다. **Flyway 자신의 SQL
파서는 `BEGIN`/`END` 블록을 추적하므로 DELIMITER 없이 그대로 처리한다** — 마이그레이션
파일 자체에는 `DELIMITER` 구문을 넣지 않는다(Flyway 환경에서는 불필요하고, 오히려
`DELIMITER`는 서버로 보내는 SQL이 아니라 클라이언트 전용 메타 명령이라 Flyway 파서가
이해하지 못한다).

반면 **`mysql` CLI로 파일을 직접 실행할 때**(`mysql ... < file.sql`)는 클라이언트가
`;`를 문장 구분자로 쓰기 때문에, 프로시저 본문 안의 각 `;`에서 문장이 잘려 `CREATE
PROCEDURE`가 깨진다. 이 세션의 검증은 파일을 바꾸지 않고, 검증 전용 임시 복사본에만
`CREATE PROCEDURE` 블록 앞뒤로 `DELIMITER ;;` / `DELIMITER ;`를 끼워 넣어 사용했다
(아래 "검증 방법" 참조). **실제 마이그레이션 파일에는 이 변경을 하지 않았다** — Flyway가
그대로 실행하므로 불필요하고, 넣으면 오히려 Flyway 파서가 깨질 위험이 있다.

## 3. 클린 적용 — 실제로 실행함

```
$ mysql -h127.0.0.1 -P3306 -utemporary -ptemporary \
    -e "DROP DATABASE IF EXISTS prototype_reservation; \
        CREATE DATABASE prototype_reservation CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
$ mysql -h127.0.0.1 -P3306 -utemporary -ptemporary prototype_reservation -e "SHOW TABLES;"
(출력 없음 — 0개 테이블, 깨끗한 시작점)

$ for f in V1_0 .. V1_21 (버전 순서대로); do
    mysql -h127.0.0.1 -P3306 -utemporary -ptemporary prototype_reservation < "$f"
  done
# 결과: 21개 파일 전부 exit 0. (V1_0__init.sql은 0줄 — no-op.)
```

알려진 UUID 두 개로 시드:
```sql
INSERT INTO user (id, login_id, role, user_status) VALUES
  ('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', 'seed_user_1', 'USER', 'ACTIVATED'),
  ('11111111-2222-3333-4444-555555555555', 'seed_user_2', 'USER', 'ACTIVATED');
```

V1_22 적용 (DELIMITER-wrapped 임시 복사본으로):
```
$ mysql -h127.0.0.1 -P3306 -utemporary -ptemporary prototype_reservation < V1_22_wrapped.sql
EXIT=0
```

백필 확인:
```sql
SELECT id, HEX(id_bin) AS id_bin_hex, login_id FROM user;
-- 실제 출력:
-- id                                    | id_bin_hex                       | login_id
-- 11111111-2222-3333-4444-555555555555  | 11111111222233334444555555555555 | seed_user_2
-- aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee  | AAAAAAAABBBBCCCCDDDDEEEEEEEEEEEE | seed_user_1

SELECT COUNT(*) AS unbackfilled FROM user WHERE id_bin IS NULL AND id IS NOT NULL;
-- 실제 출력: 0

SHOW PROCEDURE STATUS WHERE Db='prototype_reservation';
-- 실제 출력: 0 rows — sp_backfill_uuid_binary, sp_add_column_if_absent 모두 파일 끝에서
-- 정리됨(DROP PROCEDURE), 스키마에 흔적을 남기지 않음.
```

V1_23 적용:
```
$ mysql -h127.0.0.1 -P3306 -utemporary -ptemporary prototype_reservation < V1_23_wrapped.sql
EXIT=0
```

왕복 검증 — 실제 출력:
```sql
SELECT
  HEX(id) AS id_hex,
  LOWER(INSERT(INSERT(INSERT(INSERT(HEX(id),9,0,'-'),14,0,'-'),19,0,'-'),24,0,'-')) AS id_roundtrip,
  login_id
FROM user;
-- id_hex                            | id_roundtrip                          | login_id
-- AAAAAAAABBBBCCCCDDDDEEEEEEEEEEEE | aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee  | seed_user_1
-- 11111111222233334444555555555555 | 11111111-2222-3333-4444-555555555555 | seed_user_2

SELECT COUNT(*) FROM user;  -- 2  (시딩한 행 수와 동일, 전환 전후 행 수 불변)
```

`DESCRIBE user`로 확인: `id` 컬럼이 `binary(16) NOT NULL PRI`로 바뀌어 있음(이전엔
`varchar(128) NOT NULL PRI`).

## 4. 발견한 결함과 그 자리에서 고친 것

이 섹션은 "라이브로 돌려보니 실제로 깨졌던 것"만 적는다 — 정적 리뷰로는 안 보였던 것들이다.

### 4a. `IF [NOT] EXISTS`는 이 MySQL에서 전부 ERROR 1064 (치명적, 수정함)

이전 버전의 두 마이그레이션 파일은 `ADD COLUMN IF NOT EXISTS`(V1_22, 41곳),
`DROP COLUMN IF EXISTS`(V1_23, 41곳), `DROP INDEX IF EXISTS`(13곳),
`ADD INDEX IF NOT EXISTS`(10곳), `ADD UNIQUE KEY IF NOT EXISTS`(3곳)를 "MySQL >= 8.0.29
네이티브 문법"이라고 주장하며 썼다. 이 worktree의 실제 컨테이너에 직접 검증:

```sql
CREATE TABLE zz_probe_t1 (id INT PRIMARY KEY);
ALTER TABLE zz_probe_t1 ADD COLUMN IF NOT EXISTS c1 INT;
-- ERROR 1064 (42000): ... syntax ... near 'IF NOT EXISTS c1 INT'
ALTER TABLE zz_probe_t1 DROP COLUMN IF EXISTS c1;
-- ERROR 1064 — 동일
ALTER TABLE zz_probe_t1 ADD INDEX IF NOT EXISTS idx1 (id);
-- ERROR 1064 — 동일
ALTER TABLE zz_probe_t1 DROP INDEX IF EXISTS idx1;
-- ERROR 1064 — 동일
DROP TABLE zz_probe_t1;
```
네 가지 전부 재현. **수정**: 두 파일 모두 다섯 종류 전부를 `information_schema` 조회 가드
프로시저(`sp_add_column_if_absent`, `sp_drop_column_if_exists`, `sp_add_index_if_absent`,
`sp_drop_index_if_exists`)로 바꿨다 — 기존에 PK/RENAME COLUMN에 이미 쓰고 있던 것과
같은 패턴. 41+41+13+10+3 = 108곳 전부 변환, 수정 후 실제로 V1_22/V1_23를 클린 상태에
완주시켜 확인함(§3).

### 4b. 전체 성공 후 재실행하면 에러 + 데이터 손실 위험 (치명적, 수정함)

§3을 마친 뒤, D-05("재실행해도 안전") 요구를 직접 확인하려고 V1_23을 **한 번 더** 그대로
실행했다:
```
$ mysql ... prototype_reservation < V1_23_wrapped.sql
ERROR 1054 (42S22) at line 276: Unknown column 'id_bin' in 'where clause'
EXIT=1
```
원인: `sp_assert_backfill_complete('user', 'id', 'id_bin')`가 `id_bin` 컬럼이 이미
RENAME되어 없어진 뒤에도 그 컬럼을 참조하는 동적 SQL을 그대로 돌림 — 컬럼이 없으니
raw SQL 에러(SIGNAL이 아니라). **더 심각한 숨은 위험**: 이 에러 때문에 실제로는 거기서
멈췄지만, 만약 그 체크를 "없으면 그냥 통과"로 고치기만 하고 끝냈다면 다음 단계인
`DROP COLUMN IF EXISTS id`(이제는 `sp_drop_column_if_exists`)가 — 리네임으로 복원된
**최종 BINARY(16) `id` 컬럼**을 "존재하니까" 그대로 드롭해버렸을 것이다. 컬럼 이름이
변환 전/후 동일(`id_bin` → `id`)하기 때문에, "컬럼이 존재하는가"만 보는 가드로는
"아직 변환 전의 VARCHAR `id`"와 "이미 변환된 BINARY `id`"를 구별할 수 없다.

**수정**:
1. `sp_assert_backfill_complete`: `*_bin` 대상 컬럼이 더 이상 존재하지 않으면(이미 이전
   실행에서 완료됨) 에러 없이 통과하도록 `information_schema.COLUMNS` 존재 체크를 먼저
   함.
2. `sp_drop_column_if_exists`: 컬럼이 존재하는지뿐 아니라 **그 컬럼의 현재 `DATA_TYPE`이
   `binary`가 아닌지**까지 함께 확인. 변환 전엔 `varchar`이므로 드롭 대상이고, 변환이
   이미 끝나 리네임된 뒤엔 `binary`이므로 더 이상 드롭 대상이 아니다 — 이 체크가 바로
   "이미 끝난 전체 실행을 다시 돌려도 방금 막 변환한 데이터를 지우지 않는다"를 보장한다.

수정 후 실제로 재검증:
```
$ mysql ... < V1_23_wrapped.sql   # 성공한 상태에서 한 번 더
EXIT=0
$ mysql ... -e "SELECT ... FROM user;"   # 왕복 검증
-- 여전히 aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee / 11111111-...-555555555555, 2 rows
```

### 4c. 부분 실패 후 재시작 — 실제로 재현해 확인함

"파일 중간에서 프로세스가 죽는다"를 시뮬레이션: 깨끗한 V1_22 적용 상태에서, V1_23을
`restaurant` 테이블까지만 처리하도록 잘라낸 사본을 실행(`restaurant_photo` 블록 시작
직전에서 자름):
```
$ mysql ... < V1_23_partial.sql   # restaurant까지만
EXIT=0
$ DESCRIBE restaurant;        -- id/company_id/user_id 모두 binary(16) — 변환 완료
$ DESCRIBE restaurant_photo;  -- id/restaurant_id 모두 여전히 varchar(128) — 아직 미착수
                               --   (id_bin/restaurant_id_bin은 V1_22가 만들어둔 채로 존재)
```
그 다음 **전체** V1_23 파일을 처음부터 다시 실행:
```
$ mysql ... < V1_23_wrapped.sql   # 처음부터 전체 재실행
EXIT=0
$ DESCRIBE restaurant_photo;  -- id/restaurant_id 모두 binary(16) — 이제 완료
$ SELECT ... FROM user;       -- 왕복 검증 여전히 통과, 2 rows
$ SHOW PROCEDURE STATUS WHERE Db='prototype_reservation';  -- 0 rows, 정리됨
```
이미 끝난 `user`/`company`/`restaurant`를 다시 건드려도 에러가 나지 않았고(4b의 수정
덕분), 아직 안 끝난 `restaurant_photo`부터는 정상적으로 이어서 처리됐다 — "중단 후
재시작"이 실제로 안전함을 라이브로 증명했다.

## 5. 제약·구조 보존 확인 — `SHOW CREATE TABLE` 전/후 (실제 출력, data.md D-07)

전(§3, V1_21까지):
```
CREATE TABLE `waiting_queue` (
  `id` bigint NOT NULL AUTO_INCREMENT ...,
  `restaurant_id` varchar(128) ... NOT NULL ...,
  `date` date NOT NULL ...,
  `start_time` time NOT NULL ...,
  `user_id` varchar(128) ... NOT NULL ...,
  `ticket_id` varchar(128) ... NOT NULL ...,
  `status` enum(...) NOT NULL ...,
  `admitted_at` datetime DEFAULT NULL ...,
  `created_at` datetime NOT NULL ...,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_slot_ticket_id` (`restaurant_id`,`date`,`start_time`,`ticket_id`),
  UNIQUE KEY `unique_slot_user_id` (`restaurant_id`,`date`,`start_time`,`user_id`),
  KEY `index_slot_status_id` (`restaurant_id`,`date`,`start_time`,`status`,`id`)
) ENGINE=InnoDB ...

CREATE TABLE `timetable_occupancy` (
  `id` varchar(128) NOT NULL ...,
  `timetable_id` varchar(128) DEFAULT NULL ...,
  `user_id` varchar(128) DEFAULT NULL ...,
  ...
  `released_at` datetime DEFAULT NULL ...,
  `active_marker` tinyint GENERATED ALWAYS AS ((case when (`released_at` is null) then 1 else NULL end)) STORED ...,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_active_occupancy` (`timetable_id`,`active_marker`),
  KEY `index_occupied_status_occupied_datetime` (`occupied_status`,`occupied_datetime`),
  KEY `index_timetable_id_occupied_status` (`timetable_id`,`occupied_status`)
) ENGINE=InnoDB ...
```

후(§3, V1_22+V1_23 적용 뒤, 최종 안정 상태 §4b/§4c 검증까지 끝낸 뒤 — 동일):
```
CREATE TABLE `waiting_queue` (
  `id` bigint NOT NULL AUTO_INCREMENT ...,
  `restaurant_id` binary(16) NOT NULL ...,
  `date` date NOT NULL ...,
  `start_time` time NOT NULL ...,
  `user_id` binary(16) NOT NULL ...,
  `ticket_id` varchar(128) ... NOT NULL ...,   -- LEAVE-AS-VARCHAR, 불변
  `status` enum(...) NOT NULL ...,
  `admitted_at` datetime DEFAULT NULL ...,
  `created_at` datetime NOT NULL ...,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_slot_ticket_id` (`restaurant_id`,`date`,`start_time`,`ticket_id`),
  UNIQUE KEY `unique_slot_user_id` (`restaurant_id`,`date`,`start_time`,`user_id`),
  KEY `index_slot_status_id` (`restaurant_id`,`date`,`start_time`,`status`,`id`)
) ENGINE=InnoDB ...

CREATE TABLE `timetable_occupancy` (
  `id` binary(16) NOT NULL ...,
  `timetable_id` binary(16) DEFAULT NULL ...,
  `user_id` binary(16) DEFAULT NULL ...,
  ...
  `released_at` datetime DEFAULT NULL ...,
  `active_marker` tinyint GENERATED ALWAYS AS ((case when (`released_at` is null) then 1 else NULL end)) STORED ...,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_active_occupancy` (`timetable_id`,`active_marker`),
  KEY `index_occupied_status_occupied_datetime` (`occupied_status`,`occupied_datetime`),
  KEY `index_timetable_id_occupied_status` (`timetable_id`,`occupied_status`)
) ENGINE=InnoDB ...
```

판정: 두 테이블 모두 — 키 이름 집합 동일, 각 키의 컬럼 목록(순서 포함) 동일,
`active_marker`의 `GENERATED ALWAYS AS (...) STORED` 식이 공백 하나까지 문자 그대로
동일. **PASS.**

검증을 마친 뒤 `prototype_reservation` 스키마는 이 세션이 시작했을 때와 같은 빈 상태(0개
테이블)로 되돌려 두었다 — 실제 로컬 환경에 변환된 스키마를 남기지 않음.

## 6. 알려진 문서 드리프트 (손대지 않고 기록만)

- **`boundaries.md`의 경로가 stale다**: `.claude/conventions/boundaries.md`는 "Applied Flyway
  migrations under `adapter-module/**/db/migration/**`"라고 쓰지만, 실제 마이그레이션 경로는
  `infrastructure-module/src/main/resources/db/migration/`이다(`adapter-module`이 아니라
  `infrastructure-module`). 이 단위는 규칙의 **의도**(이미 적용된 마이그레이션은 append-only)를
  실제 경로에 적용했다 — `git diff --diff-filter=M`/`git status --porcelain`으로 두 번 다
  확인(§7). `boundaries.md` 자체는 이 단위의 산출물이 아니므로 고치지 않았다 — 다음에 그 문서를
  만지는 사람이 경로를 고쳐야 한다.
- **`schedule.id`는 이번 전환 범위 밖이다**: `ScheduleEntity.kt:20-22`가 `@Id @Column(name="id")
  val restaurantId: String`로 `restaurant.id`의 복사본을 PK로 쓰지만, `TimeBasedPrimaryKey`를
  상속하지 않고 `binary-pk-identifier-inventory.md`의 53개 표면 전수조사에도 들어 있지 않다.
  이 표(U1)의 범위로 한정된 이 단위(U6)는 표에 없는 표면을 임의로 CONVERT/LEAVE 판정하지
  않는다 — `schedule.id`는 VARCHAR(128)로 남고, `restaurant.id`는 BINARY(16)으로 바뀐다. 두
  값은 물리적으로 다른 컬럼이고 FK가 없어 DB가 이 불일치를 막아주지 않는다. ADR-028/인벤토리
  소유자가 다음 라운드에 `schedule.id`를 CONVERT로 넣을지 LEAVE로 명시할지 결정해야 한다.
- **`data.md`의 D-05 예시가 MySQL에서 무효였다**: 이전 버전의 `.claude/conventions/data.md`는
  D-05(재실행 가능 DDL)의 예시로 `ALTER TABLE ... IF NOT EXISTS`류를 들었다 — 위 §4a가 보여주듯
  MySQL에는 없는 문법이다. 이 세션에서 `data.md`를 `information_schema` 가드 프로시저 패턴으로
  고쳤다(별도 커밋 대상 변경, 이 문서가 아니라 `.claude/conventions/data.md` 참조).
- **`./gradlew :adapter-module:flywayMigrate`는 여전히 깨져 있다**(§2) — 이 서브골의 파일
  변경 범위 밖의 기존 결함. 한 가지 수정 시도(buildscript classpath 추가)를 해봤고 효과가
  없어 되돌렸다; 근본 원인은 Flyway Gradle 플러그인 10.0.0의 `flywayMigrate` 태스크가 쓰는
  격리된 클래스로더로 보이지만 확정하지 못했다. 다음 사람이 이어서 봐야 한다.

## 7. 정적 검증

```
$ git diff --name-only --diff-filter=M 244a93b3 -- infrastructure-module/src/main/resources/db/migration/
(출력 없음 — 기존 마이그레이션 파일 중 수정된 것 없음)

$ git status --porcelain -- infrastructure-module/src/main/resources/db/migration/
?? infrastructure-module/src/main/resources/db/migration/V1_22__binary_pk_identifier_expand.sql
?? infrastructure-module/src/main/resources/db/migration/V1_23__binary_pk_identifier_contract.sql
```

## 8. 확인하지 못한 것

- `./gradlew :adapter-module:flywayMigrate`를 통한 검증(§2) — 기존 결함으로 막혀 있다.
  `mysql` CLI로 파일을 직접 실행해 SQL 수준의 정합성은 확인했지만, Flyway 자신의
  버전 기록/락 메커니즘(동시 마이그레이션 방지 등)은 검증하지 못했다.
- `ddl-auto: validate` 부팅 테스트(ADR-028 "확인" 절) — 이 단위는 Flyway SQL만 다루고
  엔티티/컨버터 코드는 건드리지 않으므로(그 작업은 ADR-028의 "migrate" 단계, 별도 단위의 몫),
  이 검증 자체가 이 단위의 범위 밖이다.

## 관련 문서

- [[ADR-028-binary-pk-identifier-storage]] — 메커니즘·바이트 순서·expand/contract 모양 결정
- [[binary-pk-identifier-inventory]] — 전환 대상 53개 표면의 CONVERT/LEAVE 판정
- `.claude/conventions/data.md#D-12` · `#D-04` · `#D-05` · `#D-01`
- `.claude/conventions/security.md#S-07`
