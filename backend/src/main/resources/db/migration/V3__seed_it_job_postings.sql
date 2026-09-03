-- 2026-09-03 자소설닷컴 공개 채용 페이지에서 확인한 IT·AI/AX·백엔드 계열 테스트 공고.
-- 상세 요강이 이미지 또는 로그인 뒤에 있는 공고는 공개 화면에서 확인 가능한 공고명·직무·기간만 저장한다.
-- 원문 출처는 source_url로 남기며, 기존 행은 지우거나 덮어쓰지 않는다.

insert into companies (name, normalized_name, career_url, created_at, updated_at)
values
    ('다우기술', '다우기술', 'https://jasoseol.com/recruit/105868', now(), now()),
    ('HD한국조선해양', 'hd한국조선해양', 'https://jasoseol.com/recruit/105986', now(), now()),
    ('한국가스공사', '한국가스공사', 'https://jasoseol.com/recruit/105932', now(), now()),
    ('포스코DX', '포스코dx', 'https://jasoseol.com/recruit/105955', now(), now()),
    ('현대글로비스', '현대글로비스', 'https://jasoseol.com/recruit/105947', now(), now()),
    ('DB Inc.', 'dbinc', 'https://jasoseol.com/recruit/105772', now(), now()),
    ('한국항공우주산업', '한국항공우주산업', 'https://jasoseol.com/recruit/105887', now(), now()),
    ('두산그룹', '두산그룹', 'https://jasoseol.com/recruit/105959', now(), now()),
    ('에코마케팅', '에코마케팅', 'https://jasoseol.com/companies/6091/careers', now(), now()),
    ('카카오페이', '카카오페이', 'https://jasoseol.com/companies/5463/careers', now(), now())
on conflict (normalized_name) do nothing;

with posting_seed(normalized_name, position, content, source_url, deadline) as (
    values
        (
            '다우기술',
            'AI 개발 (신입)',
            $$[다우기술] 경력·신입 대규모 인재영입

채용 형태: 신입
선택 직무: AI 개발
함께 모집하는 IT 직무: 금융/증권 IT 개발, 데이터센터 네트워크 엔지니어,
데이터센터 시스템 엔지니어

수집 기준일: 2026-09-03
원문: https://jasoseol.com/recruit/105868$$,
            'https://jasoseol.com/recruit/105868',
            timestamptz '2026-09-10 18:00:00+09:00'
        ),
        (
            'hd한국조선해양',
            'AI/DX전략 (신입)',
            $$[HD한국조선해양] 2026년 하반기 신입사원 모집

채용 형태: 신입
선택 직무: AI/DX전략
함께 모집하는 데이터 직무: 데이터 사이언티스트/데이터 엔지니어

수집 기준일: 2026-09-03
원문: https://jasoseol.com/recruit/105986$$,
            'https://jasoseol.com/recruit/105986',
            timestamptz '2026-09-27 23:59:00+09:00'
        ),
        (
            '한국가스공사',
            'AI·정보기술_전산 (신입)',
            $$[한국가스공사] 2026년 하반기 채용

채용 형태: 일반직 6급 신입
선택 직무: AI·정보기술_전산

수집 기준일: 2026-09-03
원문: https://jasoseol.com/recruit/105932$$,
            'https://jasoseol.com/recruit/105932',
            timestamptz '2026-09-15 23:59:00+09:00'
        ),
        (
            '포스코dx',
            'SW개발 (신입)',
            $$[포스코DX] 2026년 하반기 신입사원 채용

채용 형태: 신입
선택 직무: SW개발
함께 모집하는 AI 직무: AI Operator, 최적화, Vision AI, 자율주행

수집 기준일: 2026-09-03
원문: https://jasoseol.com/recruit/105955$$,
            'https://jasoseol.com/recruit/105955',
            timestamptz '2026-09-16 15:00:00+09:00'
        ),
        (
            '현대글로비스',
            'AI Application Development (신입)',
            $$[현대글로비스] 2026년 하반기 신입사원 채용

채용 형태: 신입
선택 직무: AI Application Development
함께 모집하는 IT 직무: IT Strategy&Management, Applied AI Engineering,
Cyber Security(관리보안/기술보안)

수집 기준일: 2026-09-03
원문: https://jasoseol.com/recruit/105947$$,
            'https://jasoseol.com/recruit/105947',
            timestamptz '2026-09-13 23:59:00+09:00'
        ),
        (
            'dbinc',
            'S/W엔지니어(AX) (신입)',
            $$[DB Inc.] 2026년 신입사원 공개채용

채용 형태: 신입
선택 직무: S/W엔지니어(AX)
함께 모집하는 IT 직무: S/W엔지니어(보험·생명·금융·제조·대외프로젝트),
Infra엔지니어(보험계열사·DBA), 정보보호

수집 기준일: 2026-09-03
원문: https://jasoseol.com/recruit/105772$$,
            'https://jasoseol.com/recruit/105772',
            timestamptz '2026-10-02 17:00:00+09:00'
        ),
        (
            '한국항공우주산업',
            'AI/AX 개발 (신입)',
            $$[한국항공우주산업] 2026년 하반기 신입사원 채용

채용 형태: 신입
선택 직무: AI/AX 개발
함께 모집하는 IT 직무: SW, ICT, M&S, 항공전자, 비행제어

수집 기준일: 2026-09-03
원문: https://jasoseol.com/recruit/105887$$,
            'https://jasoseol.com/recruit/105887',
            timestamptz '2026-09-21 17:00:00+09:00'
        ),
        (
            '두산그룹',
            '디지털이노베이션BU_AI 솔루션 개발 (신입)',
            $$[두산그룹] 2026년 신입사원 채용

채용 형태: 신입
선택 직무: 디지털이노베이션BU_AI 솔루션 개발
함께 모집하는 IT 직무: 방산·산업보안, 디지털플랜트 R&D,
로봇 모션플래닝, 로봇 컨트롤

수집 기준일: 2026-09-03
원문: https://jasoseol.com/recruit/105959$$,
            'https://jasoseol.com/recruit/105959',
            timestamptz '2026-09-21 18:00:00+09:00'
        ),
        (
            '에코마케팅',
            'Forward Deployed Engineer(FDE) (신입)',
            $$[에코마케팅] Forward Deployed Engineer(FDE) 신입사원 채용

채용 형태: 신입
선택 직무: Forward Deployed Engineer(FDE)

수집 기준일: 2026-09-03
원문: https://jasoseol.com/companies/6091/careers$$,
            'https://jasoseol.com/companies/6091/careers',
            timestamptz '2026-09-06 23:59:00+09:00'
        ),
        (
            '카카오페이',
            '서버 개발자 - 데이터 플랫폼 (신입)',
            $$[카카오페이] 2026년 크루 상시 채용

채용 형태: 신입
선택 직무: 서버 개발자 - 데이터 플랫폼
함께 모집하는 데이터 직무: 데이터 엔지니어 - 데이터 플랫폼

수집 기준일: 2026-09-03
원문: https://jasoseol.com/companies/5463/careers$$,
            'https://jasoseol.com/companies/5463/careers',
            timestamptz '2026-09-20 23:59:00+09:00'
        )
)
insert into job_postings (
    company_id,
    position,
    content,
    source_url,
    deadline,
    status,
    analyzed_at,
    created_at,
    updated_at
)
select
    company.id,
    posting_seed.position,
    posting_seed.content,
    posting_seed.source_url,
    posting_seed.deadline,
    'ACTIVE',
    null,
    now(),
    now()
from posting_seed
join companies company on company.normalized_name = posting_seed.normalized_name
on conflict (source_url) do nothing;
