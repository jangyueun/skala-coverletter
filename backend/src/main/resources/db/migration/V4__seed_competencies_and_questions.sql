-- 역량 사전(관리자가 채우는 값, 쓰기 API 없음)과 카카오페이 공고(job_postings.id 참조는 source_url로
-- 다시 찾는다 — id는 환경마다 달라질 수 있어 하드코딩하지 않는다)의 요구 역량·자소서 문항 테스트 시드.
-- 기존 행은 지우거나 덮어쓰지 않는다(V3와 같은 원칙).

insert into competencies (name, category, created_at, updated_at)
values
    ('API 설계·연동', 'ROLE', now(), now()),
    ('대용량 트래픽·분산 처리', 'ROLE', now(), now()),
    ('장애 대응·모니터링', 'ROLE', now(), now()),
    ('Java·Kotlin', 'TECH', now(), now()),
    ('Spring Boot', 'TECH', now(), now()),
    ('협업·커뮤니케이션', 'SOFT', now(), now()),
    ('핀테크·결제 도메인', 'DOMAIN', now(), now()),
    ('책임감·오너십', 'VALUE', now(), now())
on conflict (name) do nothing;

with target_posting as (
    select id from job_postings where source_url = 'https://jasoseol.com/companies/5463/careers'
),
competency_weight(name, weight, evidence_line) as (
    values
        ('API 설계·연동', 0.90, 'REST API 설계 및 운영 경험'),
        ('Spring Boot', 0.85, 'Spring Boot 기반 서버 개발'),
        ('대용량 트래픽·분산 처리', 0.75, '대용량 데이터 플랫폼 처리 경험')
)
insert into posting_competencies (job_posting_id, competency_id, weight, evidence_line, created_at, updated_at)
select target_posting.id, competencies.id, competency_weight.weight, competency_weight.evidence_line, now(), now()
from competency_weight
join competencies on competencies.name = competency_weight.name
cross join target_posting
on conflict (job_posting_id, competency_id) do nothing;

update job_postings
set analyzed_at = now(), updated_at = now()
where source_url = 'https://jasoseol.com/companies/5463/careers' and analyzed_at is null;

with target_posting as (
    select id from job_postings where source_url = 'https://jasoseol.com/companies/5463/careers'
),
question_seed(sequence, prompt_text, length_limit) as (
    values
        (1, '지원 직무와 관련하여 본인의 강점을 구체적인 경험을 바탕으로 서술해 주세요.', 700),
        (2, '협업 과정에서 갈등을 해결했던 경험과 그로부터 배운 점을 서술해 주세요.', 700)
)
insert into job_posting_questions (job_posting_id, sequence, prompt_text, length_limit, created_at, updated_at)
select target_posting.id, question_seed.sequence, question_seed.prompt_text, question_seed.length_limit, now(), now()
from question_seed
cross join target_posting
on conflict (job_posting_id, sequence) do nothing;
