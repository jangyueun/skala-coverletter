-- 내 샘플 경험 6건 — frontend/src/api/mock/data.js 의 experiences 와 같은 것.
-- 생성: node scripts/seed/generate.mjs (손으로 고치지 말 것)
-- 사용법: Slack 로그인을 한 번 해서 users 에 내 행이 생긴 뒤, 그 이메일로 —
--   docker compose -f compose.yaml -f compose.localdb.yaml exec -T db psql -U postgres -v email=simonjiho@gmail.com < scripts/seed/my-experiences.sql
-- 먼저 competencies.sql 을 넣어야 태그가 다 붙는다(사전에 없는 이름은 태그만 조용히 빠진다).
-- 여러 번 돌려도 안전하다 — 같은 제목이 이미 있으면 건너뛴다. 이메일이 없으면 아무것도 넣지 않는다.
\set ON_ERROR_STOP on
begin;

with me as (
    select id from users where email = :'email'
),
rows(title, category, start_date, end_date, situation, task, action, result) as (values
    ('MSA 주문·결제 서비스 구축', 'TEAM_PROJECT', '2026-08-01', null, '5인 팀으로 모놀리식 주문 서비스를 마이크로서비스로 분리하는 과제를 맡았다.', '서비스 간 결제 상태 불일치를 없애고, 한 서비스가 죽어도 주문이 유실되지 않게 만드는 것이 목표였다.', 'Eureka·API Gateway로 서비스를 분리하고, 결제 이벤트를 Kafka로 비동기 발행했다. 이중 발행 문제는 Transactional Outbox 패턴으로 해결했다.', '결제 상태 불일치 건수를 테스트 100건 중 12건에서 0건으로 줄였다.'),
    ('주식 거래 REST API 개발', 'PERSONAL_PROJECT', '2026-08-01', null, 'Spring Boot로 주문·체결·잔고 조회 API를 처음부터 설계했다.', '예외가 컨트롤러마다 흩어져 응답 형식이 제각각인 문제를 잡아야 했다.', '@ControllerAdvice로 전역 예외 처리를 통일하고, DTO 검증과 표준 에러 응답 스키마를 정의했다. Swagger로 명세를 자동화했다.', '엔드포인트 18개의 에러 응답 형식을 1종으로 통일하고, 프론트 연동 시 문의를 9건에서 1건으로 줄였다.'),
    ('Vue 3 관리자 대시보드 SPA', 'TEAM_PROJECT', '2026-08-01', null, '백엔드만 하던 상태에서 프론트엔드 화면까지 직접 붙여야 하는 상황이 됐다.', '화면 7개가 각자 API를 중복 호출해 로딩이 느린 문제를 해결해야 했다.', 'Pinia로 전역 상태를 통합하고 Axios 인터셉터로 인증·에러를 한 곳에 모았다. 라우터 단위로 코드 스플리팅을 적용했다.', '중복 API 호출을 24회에서 7회로 줄여 초기 렌더 시간을 2.4초에서 0.9초로 단축했다.'),
    ('EKS 무중단 배포 파이프라인', 'PRACTICE_PROJECT', '2026-08-01', null, '로컬에서만 돌던 서비스를 팀 공용 쿠버네티스 클러스터에 올려야 했다.', '배포할 때마다 수 분간 서비스가 끊기는 것을 없애는 게 목표였다.', '멀티 스테이지 빌드로 이미지를 줄이고, Deployment의 롤링 업데이트와 readinessProbe를 설정했다. Harbor에 태그를 불변으로 관리하고 ArgoCD로 선언형 배포를 붙였다.', '이미지 크기를 780MB에서 210MB로, 배포 중 다운타임을 약 4분에서 0초로 줄였다.'),
    ('사내 문서 RAG 질의응답 시스템', 'PERSONAL_PROJECT', '2026-09-01', null, '규정 문서 200여 쪽을 매번 검색해야 하는 불편을 없애 보고 싶었다.', '키워드가 안 겹치면 못 찾는 기존 검색의 한계를 넘는 것이 목표였다.', '문서를 청킹해 임베딩하고 벡터 스토어에 적재한 뒤, 검색 결과를 근거로 붙여 답하게 했다. "모르면 모른다고 답한다"를 시스템 프롬프트로 강제해 환각을 줄였다.', '테스트 질의 40건 중 정답 문단을 찾아낸 비율이 키워드 검색 55%에서 88%로 올랐다.'),
    ('학과 학회 운영진 일정 갈등 조율', 'EXTERNAL_ACTIVITY', '2025-03-01', '2025-11-01', '학회 정기 세미나 일정을 두고 운영진 8명이 두 편으로 갈려 3주간 결론이 나지 않았다.', '감정 대립으로 번지기 전에 결정을 내려야 했다.', '양쪽 주장을 참석 가능 인원수로 환산해 표로 만들고, 두 안을 4주간 시범 운영한 뒤 출석률로 결정하자고 제안했다.', '시범 운영 결과 출석률이 62%에서 84%로 오른 안을 만장일치로 채택했고, 이후 이 방식이 학회 규칙이 됐다.')
),
ins as (
    insert into experiences (user_id, title, category, start_date, end_date, situation, task, action, result,
                             created_at, updated_at)
    select me.id, r.title, r.category, r.start_date::date, r.end_date::date, r.situation, r.task, r.action, r.result,
           now(), now()
    from me, rows r
    where not exists (select 1 from experiences e where e.user_id = me.id and e.title = r.title)
    returning id, title
),
tags(title, competency, strength) as (values
    ('MSA 주문·결제 서비스 구축', '대용량 트래픽·분산 처리', 0.8),
    ('MSA 주문·결제 서비스 구축', 'API 설계·연동', 0.8),
    ('MSA 주문·결제 서비스 구축', '문제 정의·해결', 0.8),
    ('MSA 주문·결제 서비스 구축', 'Spring Boot', 0.7),
    ('주식 거래 REST API 개발', 'API 설계·연동', 0.8),
    ('주식 거래 REST API 개발', '대용량 트래픽·분산 처리', 0.5),
    ('주식 거래 REST API 개발', 'Spring Boot', 0.8),
    ('주식 거래 REST API 개발', 'Java·Kotlin', 0.7),
    ('Vue 3 관리자 대시보드 SPA', 'UI·디자인 시스템', 0.6),
    ('Vue 3 관리자 대시보드 SPA', '성능 최적화', 0.7),
    ('Vue 3 관리자 대시보드 SPA', '자기주도 학습', 0.7),
    ('Vue 3 관리자 대시보드 SPA', 'Vue.js', 0.7),
    ('Vue 3 관리자 대시보드 SPA', 'TypeScript', 0.5),
    ('EKS 무중단 배포 파이프라인', '인프라 운영·IaC', 0.8),
    ('EKS 무중단 배포 파이프라인', 'CI/CD', 0.8),
    ('EKS 무중단 배포 파이프라인', '장애 대응·모니터링', 0.6),
    ('EKS 무중단 배포 파이프라인', 'Kubernetes', 0.9),
    ('EKS 무중단 배포 파이프라인', 'Docker', 0.9),
    ('EKS 무중단 배포 파이프라인', 'AWS·클라우드', 0.7),
    ('사내 문서 RAG 질의응답 시스템', 'API 설계·연동', 0.7),
    ('사내 문서 RAG 질의응답 시스템', '데이터 모델링·쿼리', 0.6),
    ('사내 문서 RAG 질의응답 시스템', '책임감·오너십', 0.5),
    ('사내 문서 RAG 질의응답 시스템', 'Redis·NoSQL', 0.8),
    ('사내 문서 RAG 질의응답 시스템', 'Python', 0.6),
    ('학과 학회 운영진 일정 갈등 조율', '협업·커뮤니케이션', 0.9),
    ('학과 학회 운영진 일정 갈등 조율', '책임감·오너십', 0.6),
    ('학과 학회 운영진 일정 갈등 조율', '문제 정의·해결', 0.5)
)
insert into experience_competencies (experience_id, competency_id, strength, created_at)
select ins.id, c.id, t.strength, now()
from ins
join tags t on t.title = ins.title
join competencies c on c.name = t.competency;

commit;
