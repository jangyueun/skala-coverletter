-- V4가 카카오페이 공고에 심은 자소서 문항 2개가 sequence 1·2를 차지하고 있었는데,
-- CareerLabApplicationTest(역량과_북마크를_필터링하고_매칭과_자소서_진행률을_반환한다)가
-- 같은 공고에 자기 픽스처를 sequence 1·2로 넣으려다 uk_job_posting_questions_sequence에 걸렸다
-- (Docker가 없어 그 테스트가 스킵되던 동안은 못 봤다). V4는 이미 실제 Supabase에 적용돼 있어
-- 그 파일을 고치지 않고(체크섬이 깨진다) 여기서 sequence만 3·4로 옮긴다.

update job_posting_questions
set sequence = sequence + 2, updated_at = now()
where job_posting_id = (
    select id from job_postings where source_url = 'https://jasoseol.com/companies/5463/careers'
)
and sequence in (1, 2);
