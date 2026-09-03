-- V4가 카카오페이 공고(자소서 진행률·매칭 테스트에서 팀원 테스트가 가장 많이 쓰는 공고)에
-- 요구 역량 3개·문항 2개를 심어서, "빈 상태에서 몇 개 넣었다"고 가정하는 테스트들과 계속 부딪혔다
-- (V5로 문항 시퀀스는 피했지만 posting_competencies 개수는 여전히 어긋난다).
-- 카카오페이 몫은 지우고, 어떤 테스트도 데이터를 넣지 않는 한국가스공사 공고로 옮긴다.
-- 역량 사전 자체(competencies 8개)는 이 문제와 무관해 그대로 둔다.

with kakaopay as (
    select id from job_postings where source_url = 'https://jasoseol.com/companies/5463/careers'
)
delete from posting_competencies
where job_posting_id in (select id from kakaopay);

with kakaopay as (
    select id from job_postings where source_url = 'https://jasoseol.com/companies/5463/careers'
)
delete from job_posting_questions
where job_posting_id in (select id from kakaopay) and sequence in (3, 4);

update job_postings
set analyzed_at = null, updated_at = now()
where source_url = 'https://jasoseol.com/companies/5463/careers';

with target_posting as (
    select id from job_postings where source_url = 'https://jasoseol.com/recruit/105932' -- 한국가스공사
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
where source_url = 'https://jasoseol.com/recruit/105932' and analyzed_at is null;

with target_posting as (
    select id from job_postings where source_url = 'https://jasoseol.com/recruit/105932'
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
