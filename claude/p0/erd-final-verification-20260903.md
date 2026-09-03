# ERD 최종 검증 (2026-09-03)

## 배경
- 동료 시니어 개발자가 career_fit ERD(DBML, dbdiagram.io)와 mock/ 목업을 검토한 1차 피드백을 받음.
- 이 문서는 그 1차 피드백을 mock/ 폴더 실제 파일(render.js, data.js, DESIGN.md, README.md)과 직접 대조하여 검증하고, 놓친 부분을 추가한 2차(최종) 검증임.

## 1차 피드백 검증 결과
1차 피드백의 모든 기술적 주장은 사실 확인 결과 정확했음. 오류 없음.

| 지적 | 확인 결과 |
|---|---|
| assessment.application_id vs job_matches(user_id, job_posting_id) | render.js TABLES 배열에서 확인 |
| applications postingId:null 3건 (id 106/107/108) | data.js 427~430행 확인 |
| competency.aliases[] vs competency_aliases 테이블 | data.js seed SQL 생성 함수에서 확인 |
| ai_job/crawl_job 분리 vs async_tasks 통합 | render.js TABLES 배열에서 확인 |
| AX-2 상태코드 202 vs 200 불일치 | render.js ENDPOINTS=202, DESIGN.md 331행 TODO=200 (문서 자체 모순) |
| README "API 14개" vs 실제 개수 | 실제 ENDPOINTS 배열 = 18개 |

DECIMAL(3,2) 최댓값 9.99, PostgreSQL NULL != NULL unique 우회, NULLS NOT DISTINCT(PG15+), cross-DB FK 불가 등 기술 근거도 모두 정확.

## 1차 피드백이 놓친 추가 이슈 (직접 발견)

### (1) 순환 FK — DB 미분리 상태에서도 존재
- job_postings.source_task_id → async_tasks.id
- async_tasks.job_posting_id → job_postings.id
- experiences.source_task_id → async_tasks.id (async_tasks.user_id 경유 간접 연결)

같은 DB에 둬도 테이블 생성 순서 문제로 2단계 DDL(FK 없이 생성 후 ALTER TABLE로 추가)이 강제됨. AI DB 분리와 별개의 구조적 문제.

### (2) postingId:null 3건 — NOT NULL 위반보다 근본적
id 106(미르테크)/107(아라시스템)/108(세진데이터)은 postingId만 null이 아니라 job_postings 테이블에 해당 공고 row 자체가 없음(MANUAL 소스로도 생성 안 됨). match 점수(72/79/64)를 저장할 job_matches row 자체가 원천적으로 존재할 수 없는 상태 — 단순 제약 위반이 아니라 "저장할 곳이 없음".

### (3) applications 정규화 설명과 목업 데이터 불일치
1차 피드백은 "company/position/dday/match를 저장 안 한 것은 좋은 정규화"라 평가했으나, 실제 목업 9건 전체가 이 값들을 applications에 리터럴로 저장 중. ERD 방향은 맞지만 화면(render.js) 로직까지 재작성 범위.

### (4) async_tasks 대상 FK 전부 CASCADE — 이력 보존 목적과 충돌
job_posting/question 삭제 시 관련 async_tasks 이력도 함께 삭제됨. AI DB 분리 전까지는 SET NULL + 스냅샷 컬럼 고려 필요.

### (5) requirements.addressed — 실제 미구현 개념
render.js 어디에도 addressed 필드/로직 없음. 순수 설계 문서상의 개념이므로 실사용 쿼리/화면부터 재정의 필요.

### (6) DBML 문법 자체의 한계
dbdiagram.io DBML은 컬럼 CHECK 제약을 표현 불가. strength/weight/match_score 범위 제약은 다이어그램에 못 넣고 별도 마이그레이션 SQL 필수.

### (7) job_postings 상태 컬럼 부재가 기존 API 스펙과 충돌
GET /api/postings?active=true가 이미 존재(render.js ENDPOINTS 1번). deadline만으로 active 판정 불가 — 상시채용(deadline=NULL), 수집 실패 등 표현 불가능. "개선사항"이 아니라 기존 요구사항 미충족.

## 최종 우선순위

**P0 (구현 자체 blocking)**
1. AI DB 분리 확정 → 물리 FK 제거, 논리 참조 전환
2. job_postings↔async_tasks, experiences↔async_tasks 순환 FK 해소
3. applications.job_posting_id NOT NULL 정책과 목업 3건 정합 (MANUAL 공고 자동 생성 규칙 구현)
4. async_tasks 멱등성 NULL 문제 해결

**P1 (무결성)**
5. CHECK 제약 전부 별도 마이그레이션 SQL로 관리
6. cover_letter_answers is_final 부분 유니크 인덱스
7. competency_aliases (alias) 유니크 정책 확정
8. MATCH 작업 단위 확정

**P2 (설계 보완)**
9. job_postings 상태 컬럼 추가
10. application_status 합격/불합격 세분화
11. async_tasks 대상 FK cascade → 이력 보존 정책 재검토
12. questions_from_server → 4상태 enum

**P3 (문서/목업 정합화)**
13. 1차 피드백 5번 항목 전체 (assessment→job_matches, aliases 구조, ai_job/crawl_job 통합, AX-2 상태코드, README API 개수)

## 최종 판단
현재 상태로 DDL을 그대로 뽑아 구현하면 안 됨. 모델링 방향(역량 허브, 버전 관리, AI 분리)은 타당하나 P0 항목 4개가 해결되지 않으면 마이그레이션이 돌지 않거나 실제 목업 데이터(9건 중 3건)가 스키마에 들어갈 자리가 없음.
