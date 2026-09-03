# ddl-auto 및 Flyway 정정 결과

수정일: 2026-09-03 13:40 KST

## 결론

공유 PostgreSQL에 `ddl-auto:update`를 쓰던 결정을 철회했다. `update`는 Java 필드에서 제거되거나
이름이 바뀐 컬럼을 삭제하지 않으므로, 현재처럼 ERD가 계속 바뀌는 단계에서 오히려 낡은 스키마를 누적한다.

최종 기준은 다음과 같다.

- 스키마 생성·변경: Flyway
- JPA 설정: `ddl-auto:validate`
- DBML: 관계 검토용 문서
- 실제 DB 변경 이력: `backend/src/main/resources/db/migration/V...sql`
- 공유 DB 자동 삭제: 금지 (`clean-disabled:true`)
- 기존 테이블 자동 baseline: 금지 (`baseline-on-migrate:false`)

## 반영 파일

- `backend/build.gradle`: Flyway starter와 PostgreSQL 모듈 추가
- `backend/src/main/resources/application.yml`: PostgreSQL 직접 연결, `validate` 및 Flyway 안전 설정
- `backend/src/main/resources/db/migration/V1__create_career_fit_schema.sql`: 최초 Career DB 스키마
- `docs/backend-convention.md`: `update` 규칙을 Flyway 규칙으로 교체
- `codex/20260903-1333/api-url-and-ddl-auto-final-review.md`: 이전 잘못된 권고 정정
- `codex/20260903-1333/career_fit_final_slack.dbml`: 구현 방식 설명 정정

## 기존 공유 DB 처리

현재 설정은 테이블이 이미 있으면서 Flyway 이력이 없는 DB를 만나면 실패하게 되어 있다. 이것은 데이터 손실을
막기 위한 의도된 동작이다. 기존 데이터가 테스트 데이터뿐이면 팀 승인 후 스키마를 한 번 초기화하고 `V1`부터
적용한다. 보존할 데이터가 있으면 현재 스키마를 조사한 뒤 별도의 전환 마이그레이션을 작성해야 한다.

`baseline-on-migrate:true`로 오류만 우회하면 V1이 실행되지 않아 제약과 테이블 일부가 빠질 수 있으므로 사용하지 않는다.

## 이후 변경 예시

`experiences.category`를 없애기로 결정해도 적용된 `V1`을 수정하지 않는다.

```sql
-- V2__drop_experience_category.sql
ALTER TABLE experiences DROP COLUMN category;
```

이 방식이면 어떤 DB에서 어떤 변경이 적용됐는지 `flyway_schema_history`로 동일하게 추적할 수 있다.

## 검증 결과

- Gradle 전체 테스트 통과
- PostgreSQL 17 임시 DB에서 Flyway V1 적용 성공
- Flyway 적용 후 Hibernate `ddl-auto:validate` 성공
- 검증용 임시 DB는 테스트 종료 후 삭제했으며 공유 DB는 변경하지 않음
