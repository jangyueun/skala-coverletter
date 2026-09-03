/* 목업 mock/src/data.js 를 그대로 옮긴 것. 백엔드가 생기면 이 파일이 사라지고
   stores/ 가 API 를 호출하게 된다. 그때까지 화면을 만들 수 있게 하는 발판이다. */
export const DATA = {

  /* --- competency 마스터 20개 -------------------------------
     2026년 하반기 실제 채용공고 72건(백엔드/프론트/풀스택/인프라/AI/신입공채)의
     자격요건·우대사항·인재상 문장에서 반복 어휘를 뽑아 도출했다.
     이름은 공고가 쓰는 말 그대로 잡았다 — "책임감"이 아니라 "주도성·오너십",
     "아키텍처 설계"가 아니라 "확장 가능한 설계". 자소서에 그대로 써야 걸리기 때문.
     제품명(Spring·React·Kubernetes·Terraform)은 전부 aliases 로 내렸다. */
  competencies: [
    { id: 1,  name:'백엔드 서버 개발', category:'ROLE', aliases:['백엔드', '서버 개발', '서버 사이드', '웹 애플리케이션 개발'] },
    { id: 2,  name:'프론트엔드 개발', category:'ROLE', aliases:['프론트엔드', 'UI 구현', 'SPA', 'SSR', '반응형 웹', '웹 표준', '웹 접근성'] },
    { id: 3,  name:'API 설계·연동', category:'ROLE', aliases:['REST API', 'RESTful API', 'API 설계', '엔드포인트', '외부 API 연동', 'OpenAPI'] },
    { id: 4,  name:'데이터 설계·처리', category:'ROLE', aliases:['스키마 설계', '테이블 설계', '데이터 모델링', '쿼리 튜닝', '인덱싱', '데이터 파이프라인', '전처리'] },
    { id: 5,  name:'배포 자동화·CI/CD', category:'ROLE', aliases:['CI/CD', '파이프라인', '배포 자동화', '빌드 자동화', 'IaC', 'GitOps', '형상 관리', '브랜치 전략'] },
    { id: 6,  name:'클라우드 인프라 운영', category:'ROLE', aliases:['클라우드', '인프라 구축', '컨테이너', '오케스트레이션', '온프레미스', '서버 운영', '네트워크', '가상화'] },
    { id: 7,  name:'운영·장애 대응', category:'ROLE', aliases:['서비스 운영', '장애 대응', '트러블슈팅', '모니터링', '로깅', '알람', '가용성', 'SLO', '대규모 트래픽', '사후 분석'] },
    { id: 8,  name:'LLM 서비스 개발', category:'ROLE', aliases:['LLM', 'RAG', '검색 증강 생성', 'AI Agent', '에이전트', '프롬프트 엔지니어링', '모델 서빙', 'LLMOps', 'AI 기능 개발'] },
    { id: 9,  name:'확장 가능한 설계', category:'ROLE', aliases:['아키텍처 설계', '유지보수성', '리팩토링', '객체지향', 'OOP', 'SOLID', 'DDD', '클린 아키텍처', 'MSA', '마이크로서비스', '모듈화'] },
    { id: 10, name:'테스트·품질', category:'ROLE', aliases:['테스트 코드', '단위 테스트', '통합 테스트', 'E2E 테스트', 'TDD', '테스트 자동화', '정적 분석', '코드 품질'] },
    { id: 11, name:'성능 최적화', category:'ROLE', aliases:['성능 최적화', '렌더링 최적화', 'Web Vitals', '응답 속도', '병목', '부하 테스트', '캐싱', '동시성', '비동기 처리', '대용량 처리'] },
    { id: 12, name:'CS 기초 지식', category:'ROLE', aliases:['자료구조', '알고리즘', '운영체제', '네트워크', '컴퓨터공학', '디자인 패턴', '컴퓨터 구조'] },

    { id: 13, name:'Java', category:'TECH', aliases:['Java', '자바', 'JVM', 'Kotlin'] },
    { id: 14, name:'Spring', category:'TECH', aliases:['Spring', 'SpringBoot', 'Spring Boot', 'Spring Framework', 'Spring Cloud', 'JPA', 'Hibernate'] },
    { id: 15, name:'Python', category:'TECH', aliases:['Python', '파이썬'] },
    { id: 16, name:'FastAPI', category:'TECH', aliases:['FastAPI', 'Flask', 'Django'] },
    { id: 17, name:'TypeScript', category:'TECH', aliases:['TypeScript', '타입스크립트', 'JavaScript', 'ES6'] },
    { id: 18, name:'React', category:'TECH', aliases:['React', '리액트', 'Next.js'] },
    { id: 19, name:'Vue', category:'TECH', aliases:['Vue', '뷰', 'Nuxt', 'Pinia', 'Vuex'] },
    { id: 20, name:'RDBMS', category:'TECH', aliases:['PostgreSQL', 'MySQL', 'MariaDB', 'Oracle', 'RDBMS', 'SQL', '관계형 데이터베이스'] },
    { id: 21, name:'Kafka', category:'TECH', aliases:['Kafka', '카프카', 'RabbitMQ', '메시지 큐', '이벤트 스트리밍'] },
    { id: 22, name:'Docker · Kubernetes', category:'TECH', aliases:['Docker', '도커', 'Kubernetes', '쿠버네티스', 'K8s', 'EKS', 'Helm', 'ArgoCD', '컨테이너 이미지'] },
    { id: 23, name:'AWS', category:'TECH', aliases:['AWS', 'GCP', 'Azure', 'EC2', 'S3', 'Lambda', 'Lightsail'] },
    { id: 24, name:'Git', category:'TECH', aliases:['Git', 'GitHub', 'GitLab', 'GitHub Actions', 'Jenkins', 'Pull Request'] },
    { id: 25, name:'벡터DB · 임베딩', category:'TECH', aliases:['벡터DB', '벡터 검색', '임베딩', 'LangChain', 'LlamaIndex', 'pgvector', 'Elasticsearch'] },

    { id: 26, name:'협업·소통', category:'SOFT', aliases:['협업', '커뮤니케이션', '의사소통', '타 직군', '기획자', '디자이너', 'PO', 'PM', '이해관계자', '요구사항 협의', '팀워크'] },
    { id: 27, name:'코드리뷰·피드백', category:'SOFT', aliases:['코드 리뷰', 'PR 리뷰', '페어 프로그래밍', '피드백', '리뷰 문화', '기술 문서 작성', '문서화', '지식 공유'] },
    { id: 28, name:'문제 정의·해결', category:'SOFT', aliases:['문제 정의', '문제 해결', '원인 분석', '근본 원인', '디버깅', '가설 검증', '분석적 사고', '논리적 사고'] },

    { id: 29, name:'물류·유통', category:'DOMAIN', aliases:['물류', '유통', '풀필먼트', '배송', '창고', '정산', 'SCM', 'WMS'] },
    { id: 30, name:'금융·보험', category:'DOMAIN', aliases:['금융', '보험', '핀테크', '결제', '증권', '여신', '정산', 'PG'] },
    { id: 31, name:'이커머스', category:'DOMAIN', aliases:['이커머스', '커머스', '쇼핑', '주문', '장바구니', '상품', '리뷰'] },
    { id: 32, name:'반도체·제조', category:'DOMAIN', aliases:['반도체', '제조', '생산', '설비', '공정', 'MES', '스마트팩토리', '센서'] },
    { id: 33, name:'모빌리티', category:'DOMAIN', aliases:['모빌리티', '자동차', '차량', '내비게이션', '자율주행', '전장'] },
    { id: 34, name:'헬스케어·바이오', category:'DOMAIN', aliases:['헬스케어', '의료', '바이오', '병원', '임상', 'EMR'] },
    { id: 35, name:'미디어·콘텐츠', category:'DOMAIN', aliases:['미디어', '콘텐츠', '웹툰', '게임', '스트리밍', '커뮤니티'] },

    { id: 36, name:'자기주도 학습', category:'VALUE', aliases:['자기주도적', '새로운 기술 학습', '빠른 습득력', '학습 의지', '성장', '기술 트렌드', '스터디', '독학'] },
    { id: 37, name:'주도성·오너십', category:'VALUE', aliases:['주도적', '오너십', '책임감', '실행력', '스스로 문제를 발견', '끝까지 책임', '집요함', 'POC'] },
    { id: 38, name:'사용자 중심 사고', category:'VALUE', aliases:['고객 중심', '사용자 중심', '사용자 문제 해결', 'UX', '제품 관점', '비즈니스 이해', '서비스 개선'] },
    { id: 39, name:'AI 도구 활용', category:'VALUE', aliases:['AI 도구', '생성형 AI', '코딩 에이전트', 'Claude Code', 'Copilot', 'AI 활용 생산성', '개발 생산성'] },
  ],

  /* --- 내 경험 6건 (STAR) ---------------------------------- */
  experiences: [
    {
      id: 1, title:'MSA 주문·결제 서비스 구축', period:'2026.08', category:'팀 프로젝트',
      situation:'5인 팀으로 모놀리식 주문 서비스를 마이크로서비스로 분리하는 과제를 맡았다.',
      task:'서비스 간 결제 상태 불일치를 없애고, 한 서비스가 죽어도 주문이 유실되지 않게 만드는 것이 목표였다.',
      action:'Eureka·API Gateway로 서비스를 분리하고, 결제 이벤트를 Kafka로 비동기 발행했다. 이중 발행 문제는 Transactional Outbox 패턴으로 해결했다.',
      result:'결제 상태 불일치 건수를 테스트 100건 중 12건에서 0건으로 줄였다.',
      competencyIds:[9, 1, 28, 3, 21, 14], strength:{9:0.8, 1:0.8, 28:0.8, 3:0.6, 21:0.8, 14:0.7}, usedInAnswers:3,
    },
    {
      id: 2, title:'주식 거래 REST API 개발', period:'2026.08', category:'개인 프로젝트',
      situation:'Spring Boot로 주문·체결·잔고 조회 API를 처음부터 설계했다.',
      task:'예외가 컨트롤러마다 흩어져 응답 형식이 제각각인 문제를 잡아야 했다.',
      action:'@ControllerAdvice로 전역 예외 처리를 통일하고, DTO 검증과 표준 에러 응답 스키마를 정의했다. Swagger로 명세를 자동화했다.',
      result:'엔드포인트 18개의 에러 응답 형식을 1종으로 통일하고, 프론트 연동 시 문의를 9건에서 1건으로 줄였다.',
      competencyIds:[3, 1, 9, 14, 13], strength:{3:0.8, 1:0.7, 9:0.5, 14:0.8, 13:0.7}, usedInAnswers:2,
    },
    {
      id: 3, title:'Vue 3 관리자 대시보드 SPA', period:'2026.08', category:'팀 프로젝트',
      situation:'백엔드만 하던 상태에서 프론트엔드 화면까지 직접 붙여야 하는 상황이 됐다.',
      task:'화면 7개가 각자 API를 중복 호출해 로딩이 느린 문제를 해결해야 했다.',
      action:'Pinia로 전역 상태를 통합하고 Axios 인터셉터로 인증·에러를 한 곳에 모았다. 라우터 단위로 코드 스플리팅을 적용했다.',
      result:'중복 API 호출을 24회에서 7회로 줄여 초기 렌더 시간을 2.4초에서 0.9초로 단축했다.',
      competencyIds:[2, 11, 36, 19, 17], strength:{2:0.6, 11:0.7, 36:0.7, 19:0.7, 17:0.5}, usedInAnswers:1,
    },
    {
      id: 4, title:'EKS 무중단 배포 파이프라인', period:'2026.08', category:'실습 프로젝트',
      situation:'로컬에서만 돌던 서비스를 팀 공용 쿠버네티스 클러스터에 올려야 했다.',
      task:'배포할 때마다 수 분간 서비스가 끊기는 것을 없애는 게 목표였다.',
      action:'멀티 스테이지 빌드로 이미지를 줄이고, Deployment의 롤링 업데이트와 readinessProbe를 설정했다. Harbor에 태그를 불변으로 관리하고 ArgoCD로 선언형 배포를 붙였다.',
      result:'이미지 크기를 780MB에서 210MB로, 배포 중 다운타임을 약 4분에서 0초로 줄였다.',
      competencyIds:[6, 5, 7, 22, 23], strength:{6:0.8, 5:0.8, 7:0.6, 22:0.9, 23:0.7}, usedInAnswers:2,
    },
    {
      id: 5, title:'사내 문서 RAG 질의응답 시스템', period:'2026.09', category:'개인 프로젝트',
      situation:'규정 문서 200여 쪽을 매번 검색해야 하는 불편을 없애 보고 싶었다.',
      task:'키워드가 안 겹치면 못 찾는 기존 검색의 한계를 넘는 것이 목표였다.',
      action:'문서를 청킹해 임베딩하고 벡터 스토어에 적재한 뒤, 검색 결과를 근거로 붙여 답하게 했다. "모르면 모른다고 답한다"를 시스템 프롬프트로 강제해 환각을 줄였다.',
      result:'테스트 질의 40건 중 정답 문단을 찾아낸 비율이 키워드 검색 55%에서 88%로 올랐다.',
      competencyIds:[8, 4, 37, 25, 15], strength:{8:0.7, 4:0.6, 37:0.5, 25:0.8, 15:0.6}, usedInAnswers:1,
    },
    {
      id: 6, title:'학과 학회 운영진 일정 갈등 조율', period:'2025.03 – 2025.11', category:'대외활동',
      situation:'학회 정기 세미나 일정을 두고 운영진 8명이 두 편으로 갈려 3주간 결론이 나지 않았다.',
      task:'감정 대립으로 번지기 전에 결정을 내려야 했다.',
      action:'양쪽 주장을 참석 가능 인원수로 환산해 표로 만들고, 두 안을 4주간 시범 운영한 뒤 출석률로 결정하자고 제안했다.',
      result:'시범 운영 결과 출석률이 62%에서 84%로 오른 안을 만장일치로 채택했고, 이후 이 방식이 학회 규칙이 됐다.',
      competencyIds:[26, 37, 28], strength:{26:0.9, 37:0.6, 28:0.5}, usedInAnswers:2,
    },
  ],

  /* --- 채용공고 --------------------------------------------
     개인이 URL 을 넣어 크롤링하는 구조가 아니다.
     백엔드가 기업별 채용 페이지(company.career_url)를 주기적으로 수집해
     공고 풀에 적재하고, 사용자는 거기서 고르거나 원문을 붙여넣는다.
     기업·공고는 전부 가상이다. */
  activePostingId: 9,
  postings: [
    {
      id: 9, role:'BACKEND', questionsFromServer: true, source:'CRAWLED', sourceUrl:'https://careers.seumtech.example/jobs/2026-be',
      collectedAt:'2026-09-01 04:12',
      company:'세움테크', position:'백엔드 엔지니어 (신입)', deadline:'2026-09-12',
    rawText:
`[세움테크] 2026 하반기 신입 백엔드 엔지니어 채용

■ 담당 업무
· Spring Boot · JPA 기반 REST API 설계 및 운영
· 사내 물류 플랫폼의 주문·정산 도메인 백엔드 개발
· 서비스 안정성 개선 및 장애 대응

■ 자격 요건
· Java 또는 Kotlin으로 웹 애플리케이션을 만들어 본 경험
· 관계형 데이터베이스 설계와 SQL 작성 능력
· 문제가 생겼을 때 원인을 끝까지 추적해 본 경험

■ 우대 사항
· Docker · Kubernetes 기반 배포 환경을 다뤄 본 경험
· Kafka 운영 또는 MSA 전환 경험
· 생성형 AI를 실제 업무나 프로젝트에 활용해 본 경험
· 대용량 트래픽 환경에서 안정성을 고민해 본 경험

■ 우리가 함께 일하고 싶은 사람
· 정해진 답이 없는 문제에 스스로 뛰어드는 분
· 현재에 안주하지 않고 더 높은 기준을 세우는 분
· 프론트엔드·기획과 적극적으로 소통하며 서비스를 완성하는 분`,
    required: [
      { competencyId: 1,  weight:0.9, evidence:'Spring Boot · JPA 기반 서버 개발' },
      { competencyId: 3,  weight:0.9, evidence:'REST API 설계 및 운영 경험' },
      { competencyId: 28, weight:0.9, evidence:'문제가 생겼을 때 원인을 끝까지 추적해 본 경험' },
      { competencyId: 6,  weight:0.8, evidence:'Docker · Kubernetes 기반 배포 환경을 다뤄 본 경험' },
      { competencyId: 26, weight:0.8, evidence:'프론트엔드·기획과 적극적으로 소통하며 서비스를 완성하는 분' },
      { competencyId: 36, weight:0.8, evidence:'정해진 답이 없는 문제에 스스로 뛰어드는 분' },
      { competencyId: 4,  weight:0.7, evidence:'관계형 데이터베이스 설계와 SQL 작성 능력' },
      { competencyId: 39, weight:0.7, evidence:'생성형 AI 를 실제 업무나 프로젝트에 활용해 본 경험' },
      { competencyId: 37, weight:0.7, evidence:'현재에 안주하지 않고 스스로 기준을 세우는 분' },
      { competencyId: 13, weight:0.9, evidence:'Java · Spring Boot 기반 서버 개발' },
      { competencyId: 14, weight:0.9, evidence:'Spring Boot · JPA' },
      { competencyId: 21, weight:0.7, evidence:'Kafka 운영 경험' },
      { competencyId: 22, weight:0.8, evidence:'Docker · Kubernetes 기반 배포 환경' },
      { competencyId: 29, weight:0.7, evidence:'사내 물류 플랫폼의 주문·정산 도메인' },
    ],
    newCompetencies: ['Kafka 운영'],
    },

    {
      id: 10, role:'BACKEND', questionsFromServer: true, source:'CRAWLED', sourceUrl:'https://recruit.daonsoft.example/2026h2/server',
      collectedAt:'2026-09-01 04:12',
      company:'다온소프트', position:'서버 개발 (신입)', deadline:'2026-09-15',
      rawText:
`[다온소프트] 2026 하반기 서버 개발 신입 채용

■ 담당 업무
· 보험 청구 정산 시스템의 서버 로직 개발 및 운영
· 대량 배치 처리와 데이터 정합성 관리
· 레거시 프로시저의 애플리케이션 이관

■ 자격 요건
· Java 기반 웹 애플리케이션 개발 경험
· 관계형 데이터베이스 설계와 복잡한 SQL 작성 능력
· 장애 발생 시 원인을 끝까지 추적해 본 경험
· 맡은 일을 끝까지 마무리하는 책임감

■ 우대 사항
· 보험 · 금융 도메인 업무 이해
· 대량 배치 튜닝 경험
· 동료와 근거를 놓고 토론할 수 있는 분`,
      required: [
        { competencyId: 1,  weight:0.9, evidence:'Java 기반 웹 애플리케이션 개발 경험' },
        { competencyId: 4,  weight:0.9, evidence:'관계형 데이터베이스 설계와 복잡한 SQL 작성 능력' },
        { competencyId: 28, weight:0.8, evidence:'장애 발생 시 원인을 끝까지 추적해 본 경험' },
        { competencyId: 37, weight:0.7, evidence:'맡은 일을 끝까지 마무리하는 책임감' },
        { competencyId: 26, weight:0.6, evidence:'동료와 근거를 놓고 토론할 수 있는 분' },
        { competencyId: 12, weight:0.6, evidence:'자료구조 · 알고리즘 등 전공 기초 지식' },
        { competencyId: 13, weight:0.9, evidence:'Java 기반 웹 애플리케이션 개발 경험' },
        { competencyId: 20, weight:0.9, evidence:'관계형 데이터베이스 설계와 복잡한 SQL' },
        { competencyId: 30, weight:0.8, evidence:'보험 · 금융 도메인 업무 이해' },
      ],
      newCompetencies: ['배치 튜닝'],
    },

    {
      id: 11, role:'PLATFORM', questionsFromServer: true, source:'CRAWLED', sourceUrl:'https://hanbit-sys.example/careers/platform',
      collectedAt:'2026-09-02 04:12',
      company:'한빛시스템', position:'플랫폼 엔지니어 (신입)', deadline:'2026-09-21',
      rawText:
`[한빛시스템] 2026 하반기 플랫폼 엔지니어 채용

■ 담당 업무
· 사내 서비스의 컨테이너 기반 배포 파이프라인 구축 및 운영
· 쿠버네티스 클러스터 운영과 장애 대응
· 배포 지표 수집 및 개선

■ 자격 요건
· Docker · Kubernetes 를 직접 다뤄 본 경험
· 시스템 구조를 그려 놓고 설명할 수 있는 분
· 장애가 났을 때 로그를 따라 원인을 좁혀 본 경험

■ 우대 사항
· 개선 결과를 지표로 증명해 본 경험
· 시키지 않은 개선을 스스로 제안하고 실행해 본 경험
· 개발팀과 함께 배포 방식을 합의해 본 경험`,
      required: [
        { competencyId: 6,  weight:0.9, evidence:'Docker · Kubernetes 를 직접 다뤄 본 경험' },
        { competencyId: 5,  weight:0.8, evidence:'IaC · GitOps 기반 배포 자동화 경험' },
        { competencyId: 9,  weight:0.8, evidence:'시스템 구조를 그려 놓고 설명할 수 있는 분' },
        { competencyId: 28, weight:0.8, evidence:'장애가 났을 때 원인을 좁혀 본 경험' },
        { competencyId: 7,  weight:0.7, evidence:'로그와 지표로 장애 원인을 추적해 본 경험' },
        { competencyId: 37, weight:0.7, evidence:'시키지 않은 개선을 스스로 제안하고 실행해 본 경험' },
        { competencyId: 26, weight:0.6, evidence:'개발팀과 함께 배포 방식을 합의해 본 경험' },
        { competencyId: 36, weight:0.6, evidence:'새로운 인프라 기술을 스스로 학습해 적용해 본 경험' },
        { competencyId: 12, weight:0.7, evidence:'네트워크 · 운영체제 등 시스템 기초 지식' },
        { competencyId: 22, weight:0.9, evidence:'Docker · Kubernetes 를 직접 다뤄 본 경험' },
        { competencyId: 23, weight:0.7, evidence:'클라우드 환경 운영 경험' },
      ],
      newCompetencies: [],
    },

    {
      id: 12, role:'BACKEND', questionsFromServer: true, source:'CRAWLED', sourceUrl:'https://corelink.example/jobs/be-ai', collectedAt:'2026-09-02 04:12',
      company:'코어링크', position:'백엔드 엔지니어 · AI 프로덕트', deadline:'2026-09-28',
      rawText:
`[코어링크] AI 프로덕트 백엔드 엔지니어 (신입)

■ 담당 업무
· LLM 기반 사내 지식 검색 서비스의 백엔드 개발
· 문서 수집 · 임베딩 · 검색 파이프라인 운영

■ 자격 요건
· Spring Boot 또는 FastAPI 로 API 를 만들어 본 경험
· 생성형 AI 를 실제 서비스에 붙여 본 경험
· 검색 품질을 지표로 측정하고 개선해 본 경험

■ 우대 사항
· RAG 파이프라인 구축 경험
· 프롬프트를 설계하고 결과를 검증해 본 경험`,
      required: [
        { competencyId: 8,  weight:0.9, evidence:'RAG 파이프라인 구축 및 LLM 을 실제 서비스에 붙여 본 경험' },
        { competencyId: 4,  weight:0.8, evidence:'벡터DB · 임베딩 파이프라인 구성 경험' },
        { competencyId: 3,  weight:0.8, evidence:'Spring Boot 또는 FastAPI 로 API 를 만들어 본 경험' },
        { competencyId: 1,  weight:0.8, evidence:'Python 또는 Java 로 서버를 만들어 본 경험' },
        { competencyId: 28, weight:0.7, evidence:'검색 품질을 가설·검증으로 개선해 본 경험' },
        { competencyId: 39, weight:0.6, evidence:'AI 코딩 에이전트를 개발 과정에 활용해 본 경험' },
        { competencyId: 38, weight:0.7, evidence:'사용자 문제에서 출발해 AI 기능을 정의하는 분' },
        { competencyId: 14, weight:0.7, evidence:'Spring Boot 로 API 를 만들어 본 경험' },
        { competencyId: 16, weight:0.8, evidence:'FastAPI 로 AI 서비스를 붙여 본 경험' },
        { competencyId: 15, weight:0.8, evidence:'Python 기반 AI 서비스 개발' },
        { competencyId: 25, weight:0.8, evidence:'벡터DB · 임베딩 파이프라인 구성' },
      ],
      newCompetencies: [],
    },

    {
      id: 13, role:'FULLSTACK', questionsFromServer: true, source:'CRAWLED', sourceUrl:'https://rivertree.example/careers/fullstack', collectedAt:'2026-09-02 04:12',
      company:'리버트리', position:'풀스택 엔지니어 (신입)', deadline:'2026-09-08',
      rawText:
`[리버트리] 풀스택 엔지니어 신입 채용

■ 담당 업무
· 사내 관리자 도구의 화면과 API 를 함께 개발
· 프론트엔드 상태 관리 구조 개선

■ 자격 요건
· Vue 또는 React 로 화면을 만들어 본 경험
· 백엔드 API 를 직접 설계해 본 경험
· 화면과 API 를 오가며 일정을 스스로 관리해 본 경험

■ 우대 사항
· 성능 개선을 수치로 증명해 본 경험
· 기획·디자인과 합의하며 범위를 조정해 본 경험`,
      required: [
        { competencyId: 3,  weight:0.8, evidence:'백엔드 API 를 직접 설계해 본 경험' },
        { competencyId: 2,  weight:0.8, evidence:'Vue 또는 React 로 화면을 만들어 본 경험' },
        { competencyId: 1,  weight:0.7, evidence:'Node.js 또는 Spring 으로 서버를 만들어 본 경험' },
        { competencyId: 37, weight:0.7, evidence:'일정과 우선순위를 스스로 관리해 본 경험' },
        { competencyId: 11, weight:0.7, evidence:'성능 개선을 수치로 증명해 본 경험' },
        { competencyId: 26, weight:0.7, evidence:'기획·디자인과 합의하며 범위를 조정해 본 경험' },
        { competencyId: 10, weight:0.7, evidence:'테스트 코드를 작성하며 개발해 본 경험' },
        { competencyId: 38, weight:0.7, evidence:'사용자 관점에서 우선순위를 판단하는 분' },
        { competencyId: 18, weight:0.7, evidence:'React 로 화면을 만들어 본 경험' },
        { competencyId: 19, weight:0.7, evidence:'Vue 로 화면을 만들어 본 경험' },
        { competencyId: 17, weight:0.7, evidence:'TypeScript 사용 경험' },
      ],
      newCompetencies: [],
    },

    {
      id: 14, role:'FRONTEND', questionsFromServer: true, source:'CRAWLED',
      sourceUrl:'https://careers.seumtech.example/jobs/2026-fe', collectedAt:'2026-09-01 04:12',
      company:'세움테크', position:'프론트엔드 엔지니어 (신입)', deadline:'2026-09-12',
      rawText:
`[세움테크] 2026 하반기 신입 프론트엔드 엔지니어 채용

■ 담당 업무
· 물류 관제 대시보드의 화면 설계 및 개발
· 실시간 배송 현황 시각화
· 사내 디자인 시스템 컴포넌트 유지보수

■ 자격 요건
· Vue 또는 React 로 실제 서비스를 만들어 본 경험
· 상태 관리 구조를 직접 설계해 본 경험
· 화면 성능을 수치로 개선해 본 경험

■ 우대 사항
· 백엔드 API 를 함께 설계해 본 경험
· 기획·디자인과 합의하며 범위를 조정해 본 경험

■ 우리가 함께 일하고 싶은 사람
· 정해진 답이 없는 문제에 스스로 뛰어드는 분`,
      required: [
        { competencyId: 2,  weight:0.9, evidence:'Vue 또는 React 로 실제 서비스를 만들어 본 경험' },
        { competencyId: 11, weight:0.8, evidence:'화면 성능을 수치로 개선해 본 경험' },
        { competencyId: 26, weight:0.7, evidence:'기획·디자인과 합의하며 범위를 조정해 본 경험' },
        { competencyId: 9,  weight:0.7, evidence:'상태 관리 구조를 직접 설계해 본 경험' },
        { competencyId: 36, weight:0.6, evidence:'정해진 답이 없는 문제에 스스로 뛰어드는 분' },
        { competencyId: 10, weight:0.6, evidence:'Jest · Playwright 로 테스트를 작성해 본 경험' },
        { competencyId: 3,  weight:0.5, evidence:'백엔드 API 를 함께 설계해 본 경험' },
        { competencyId: 27, weight:0.6, evidence:'코드 리뷰를 주고받으며 품질을 맞춰 본 경험' },
        { competencyId: 18, weight:0.8, evidence:'React 로 실제 서비스를 만들어 본 경험' },
        { competencyId: 19, weight:0.8, evidence:'Vue 로 실제 서비스를 만들어 본 경험' },
        { competencyId: 17, weight:0.8, evidence:'TypeScript 사용 경험' },
        { competencyId: 29, weight:0.6, evidence:'물류 플랫폼 화면 개발' },
      ],
      newCompetencies: [],
    },

    {
      id: 15, role:'PLATFORM', questionsFromServer: true, source:'CRAWLED',
      sourceUrl:'https://hanbit-sys.example/careers/sre', collectedAt:'2026-09-02 04:12',
      company:'한빛시스템', position:'SRE (신입)', deadline:'2026-09-25',
      rawText:
`[한빛시스템] 2026 하반기 SRE 채용

■ 담당 업무
· 서비스 가용성 지표(SLI/SLO) 정의와 관측 체계 운영
· 장애 대응 및 사후 분석 리포트 작성

■ 자격 요건
· 리눅스 서버와 컨테이너 환경을 다뤄 본 경험
· 장애 원인을 로그와 지표로 추적해 본 경험
· 개선 결과를 수치로 증명해 본 경험

■ 우대 사항
· 온콜 대응 경험
· SLO 를 스스로 높여 잡고 달성해 본 경험`,
      required: [
        { competencyId: 6,  weight:0.9, evidence:'리눅스 서버와 컨테이너 환경을 다뤄 본 경험' },
        { competencyId: 7,  weight:0.9, evidence:'장애 원인을 로그와 지표로 추적해 본 경험' },
        { competencyId: 5,  weight:0.8, evidence:'Terraform · Ansible 등 IaC 로 인프라를 코드화해 본 경험' },
        { competencyId: 11, weight:0.8, evidence:'병목을 찾아 응답 속도를 개선해 본 경험' },
        { competencyId: 28, weight:0.8, evidence:'장애 사후 분석으로 근본 원인을 규명해 본 경험' },
        { competencyId: 37, weight:0.6, evidence:'온콜 대응 경험' },
        { competencyId: 12, weight:0.8, evidence:'네트워크 · 운영체제 등 시스템 기초 지식' },
        { competencyId: 22, weight:0.9, evidence:'리눅스 서버와 컨테이너 환경' },
        { competencyId: 23, weight:0.8, evidence:'클라우드 인프라 운영 경험' },
      ],
      newCompetencies: ['카오스 엔지니어링'],
    },

    {
      id: 16, role:'BACKEND', questionsFromServer: true, source:'CRAWLED',
      company:'넥스트레이어', position:'백엔드 엔지니어 (신입)', deadline:'2026-08-25',
      rawText:`[넥스트레이어] 2026 상반기 백엔드 엔지니어 채용

■ 담당 업무
· 이커머스 주문·결제 API 개발과 운영
· 트래픽 증가에 대응하는 서버 구조 개선

■ 자격 요건
· Java · Spring Boot 로 서비스를 만들어 본 경험
· 관계형 데이터베이스 설계와 SQL 작성 능력
· 문제가 생겼을 때 원인을 끝까지 추적해 본 경험

■ 우대 사항
· 이커머스 도메인 경험`,
      required: [
        { competencyId: 1,  weight:0.9, evidence:'Java · Spring Boot 로 서비스를 만들어 본 경험' },
        { competencyId: 13, weight:0.9, evidence:'Java 로 서비스를 만들어 본 경험' },
        { competencyId: 14, weight:0.9, evidence:'Spring Boot 로 서비스를 만들어 본 경험' },
        { competencyId: 3,  weight:0.8, evidence:'주문·결제 API 개발과 운영' },
        { competencyId: 20, weight:0.8, evidence:'관계형 데이터베이스 설계와 SQL 작성 능력' },
        { competencyId: 28, weight:0.8, evidence:'원인을 끝까지 추적해 본 경험' },
        { competencyId: 31, weight:0.7, evidence:'이커머스 도메인 경험' },
        { competencyId: 11, weight:0.6, evidence:'트래픽 증가에 대응하는 서버 구조 개선' },
      ],
      newCompetencies: [],
    },
    {
      id: 17, role:'FRONTEND', questionsFromServer: true, source:'CRAWLED',
      company:'하람랩스', position:'프론트엔드 엔지니어 (신입)', deadline:'2026-08-30',
      rawText:`[하람랩스] 2026 상반기 프론트엔드 엔지니어 채용

■ 담당 업무
· 헬스케어 예약·문진 서비스의 화면 개발
· 디자인 시스템 컴포넌트 개선

■ 자격 요건
· React 또는 Vue 로 서비스를 만들어 본 경험
· TypeScript 사용 경험
· 화면 성능을 수치로 개선해 본 경험`,
      required: [
        { competencyId: 2,  weight:0.9, evidence:'React 또는 Vue 로 서비스를 만들어 본 경험' },
        { competencyId: 18, weight:0.9, evidence:'React 로 서비스를 만들어 본 경험' },
        { competencyId: 17, weight:0.8, evidence:'TypeScript 사용 경험' },
        { competencyId: 11, weight:0.8, evidence:'화면 성능을 수치로 개선해 본 경험' },
        { competencyId: 26, weight:0.7, evidence:'디자이너와 협업하며 컴포넌트를 개선' },
        { competencyId: 34, weight:0.7, evidence:'헬스케어 예약·문진 서비스' },
      ],
      newCompetencies: [],
    },
  ],

  /* --- 자소서 문항 4개 ------------------------------------
     SKALA 서류 전형 문항 구조를 가상 공고에 맞춰 각색했다. */
  questions: [
    {
      id: 31, applicationId: 101, charLimit: 700,
      prompt:'현재 본인이 어떤 갈림길에 서 있다고 느끼는지 구체적으로 설명하고, 당사 지원이 그 선택에 어떤 영향을 주는지 기술해 주십시오.',
      intent:'로열티와 산업 관심도. 회사 이름만 바꾸면 어디든 낼 수 있는 글인지 본다. 진짜로 방향이 갈리는 두 경로여야 한다 — "운영이냐 개발이냐"는 갈림길이 아니다.',
      asks:['지금 서 있는 갈림길', '당사 지원이 그 선택에 주는 영향'],
      usedExperienceIds: [1],
      draft:'저는 데이터 분석 직무와 백엔드 개발 사이에서 고민했습니다. 학부에서는 통계를 전공했고 졸업 프로젝트도 분석이었기 때문에 자연스럽게 분석 직무를 준비하고 있었습니다. 그런데 부트캠프에서 MSA 프로젝트를 하면서 생각이 바뀌었습니다. 다온소프트의 물류 플랫폼을 보며 이런 시스템을 직접 만들어 보고 싶다고 생각했습니다. 다온소프트에서 그 일을 하고 싶습니다. 열심히 하겠습니다.',
      aiDraft:'정해진 답이 없는 문제를 시스템 구조로 해결하는 백엔드 엔지니어가 되기 위해 세움테크에 지원합니다. 저는 통계를 전공해 데이터 분석 직무를 준비하던 중, 부트캠프에서 MSA 주문·결제 서비스를 구축하며 방향을 바꾸었습니다. 모놀리식 서비스를 5개 마이크로서비스로 분리하는 과정에서 결제 상태 불일치가 테스트 100건 중 12건 발생했습니다. 로그를 거슬러 올라가며 원인을 추적한 끝에 결제 이벤트가 이중 발행되는 문제임을 확인했고, Transactional Outbox 패턴을 적용해 불일치를 0건으로 만들었습니다. 이 과정에서 분석 결과를 해석하는 일보다, 문제의 원인을 구조에서 찾아 다시 일어나지 않게 만드는 일이 제게 맞는다는 것을 알았습니다. 대학원에 진학해 분석을 더 깊이 파고들 것인가, 서비스를 직접 만드는 개발자가 될 것인가 사이에서 고민하던 갈림길은 이때 정리되었습니다. 세움테크의 물류 플랫폼 주문·정산 도메인은 숫자 하나가 틀리면 고객의 신뢰가 무너지는 영역이고, 정해진 답이 없는 문제에 스스로 뛰어드는 사람을 찾는다고 이해했습니다. 제가 겪은 정합성 문제와 그것을 구조로 해결한 경험을 주문·정산 도메인에서 이어가겠습니다. 입사 후 1년은 도메인 용어와 정산 로직을 정확히 익히는 데 쓰고, 3년 안에는 정합성 검증을 자동화해 장애를 사전에 잡아내는 사람이 되겠습니다.',
    },
    {
      id: 32, applicationId: 101, charLimit: 700,
      prompt:'본인을 가장 크게 성장시킨 경험은 무엇이며, 그 과정에서 본인의 태도에 어떤 변화가 있었는지 기술해 주십시오.',
      intent:'직무 경험은 자소서 평가 요소 1위(34%)다. STAR로 쓰되 결과에 반드시 수치가 있어야 한다.',
      asks:['가장 크게 성장시킨 경험', '그 과정에서 태도에 생긴 변화'],
      usedExperienceIds: [4],
      draft:'',
      aiDraft:'코드가 돌아가는 것과 서비스가 되는 것은 다르다는 것을 EKS 배포 파이프라인을 만들며 배웠습니다. 로컬에서만 돌던 서비스를 팀 공용 쿠버네티스 클러스터에 올리는 과제를 맡았을 때, 배포할 때마다 약 4분간 서비스가 끊겼습니다. 원인은 새 파드가 요청을 받을 준비를 마치기 전에 기존 파드가 종료되는 것이었습니다. readinessProbe와 롤링 업데이트 전략을 설정해 다운타임을 0초로 줄였고, 멀티 스테이지 빌드로 이미지 크기를 780MB에서 210MB로 줄여 배포 시간 자체도 단축했습니다. 같은 조의 다른 팀들이 평균 2분대 다운타임에 머물렀던 것과 비교하면, 무중단에 도달한 것은 저희 조가 유일했습니다. 이 과정에서 이미지 태그를 불변으로 관리하지 않으면 지금 어떤 버전이 떠 있는지 아무도 모른다는 것을 직접 겪었고, 이후 Harbor에 고유 태그 규칙을 세워 팀 전체가 따르도록 제안했습니다. 그전까지 저는 기능이 동작하면 제 몫이 끝났다고 생각했습니다. 지금은 배포와 운영까지가 설계의 일부라고 생각하며, 기능을 만들 때부터 이것이 어떻게 배포되고 어떻게 롤백될지를 먼저 확인하는 습관이 생겼습니다.',
    },
    {
      id: 33, applicationId: 101, charLimit: 700,
      prompt:'낯선 환경에서 자발적으로 최고 수준의 성과를 만들어 낸 경험을 서술하고, 그 과정에서 세운 목표와 전략을 기술해 주십시오.',
      intent:'인재상 "주도성·오너십"을 직접 검증하는 문항이다. 목표를 스스로 정의했는지, 비교 가능한 수치로 증명했는지를 본다.',
      asks:['낯선 환경에서의 자발적 성과', '세운 목표', '세운 전략'],
      usedExperienceIds: [4],
      draft:'',
      aiDraft:'남들이 통과 기준으로 삼는 지점을 시작점으로 잡는 것이 제 방식입니다. 쿠버네티스를 처음 접한 실습에서 과제 기준은 “파드가 정상적으로 뜰 것” 하나였습니다. 저는 여기에 “배포 중 요청이 단 한 건도 실패하지 않을 것”이라는 기준을 스스로 더했습니다. 낯선 환경이었고 참고할 팀도 없었기 때문에, 먼저 무엇이 다운타임을 만드는지부터 측정했습니다. 배포 중 1초 간격으로 헬스 체크를 걸어 로그를 남겼더니 평균 4분 12초 동안 요청이 실패하고 있었습니다. 원인을 파드 교체 순서로 좁힌 뒤 readinessProbe와 maxUnavailable 설정을 바꿔 가며 여덟 번을 다시 배포했고, 마지막에는 실패 요청 0건으로 무중단 배포에 도달했습니다. 덤으로 멀티 스테이지 빌드를 적용해 이미지를 780MB에서 210MB로 줄여 배포 자체를 3분에서 50초로 단축했습니다. 과제 기준만 맞췄다면 30분이면 끝났을 일이었지만, 기준을 올려 잡은 덕분에 지금은 어떤 서비스를 봐도 “이건 배포 중에 안 끊기나”를 먼저 묻게 되었습니다. 세움테크에서도 주어진 요구사항을 완성이 아니라 시작점으로 삼고, 그 위에 제가 세운 기준을 하나씩 더해 가겠습니다.',
    },
    {
      id: 34, applicationId: 101, charLimit: 1000,
      prompt:'수행한 프로젝트에서 본인이 맡은 역할과, 프로젝트를 하며 아쉬웠던 점 및 그 개선 방향을 기술해 주십시오.',
      intent:'성공 결과만 나열하면 안 되는 문항. 내 역할의 구체성과, 아쉬움을 직면하고 개선한 태도를 본다.',
      asks:['내가 맡은 역할', '아쉬웠던 점', '개선 방향'],
      usedExperienceIds: [1, 3],
      draft:'',
      aiDraft:'MSA 주문·결제 서비스에서 저는 결제 도메인과 서비스 간 통신 설계를 맡았습니다. 5인 팀이 모놀리식 주문 서비스를 5개 마이크로서비스로 분리하는 과제였고, 저는 주문과 결제 사이의 정합성을 책임졌습니다. 초기 구현에서는 결제 완료 이벤트를 서비스가 직접 호출로 전달했는데, 결제 서비스가 잠시 죽으면 주문이 통째로 유실됐습니다. Kafka로 이벤트를 비동기 발행하도록 바꾸었지만 이번에는 이중 발행으로 결제 상태 불일치가 테스트 100건 중 12건 발생했습니다. DB 커밋과 메시지 발행이 하나의 트랜잭션이 아니라는 것이 원인이었고, Transactional Outbox 패턴을 적용해 불일치를 0건으로 만들었습니다. 아쉬웠던 점은 이 문제를 설계 단계가 아니라 통합 테스트에서야 발견했다는 것입니다. 저는 “서비스를 나눈다”는 결정에만 집중했고, 나눈 뒤에 트랜잭션이 어디서 끊기는지는 코드를 다 짜고 나서 확인했습니다. 그 결과 3일치 작업을 되돌려야 했습니다. 이후 프론트엔드 대시보드 프로젝트에서는 화면을 그리기 전에 API 계약과 상태 흐름을 먼저 문서로 고정했고, 그 덕분에 중복 API 호출을 24회에서 7회로 줄이며 초기 렌더 시간을 2.4초에서 0.9초로 단축할 수 있었습니다. 경계를 나누는 일은 나누는 순간이 아니라 나눈 뒤 무엇이 끊기는지를 먼저 그려 봐야 한다는 것이 이 프로젝트에서 얻은 교훈입니다. 지금은 새로운 기능을 설계할 때 가장 먼저 트랜잭션 경계와 실패 지점을 그림으로 그린 뒤 코드를 시작합니다. 세움테크의 주문·정산 도메인처럼 여러 시스템이 하나의 숫자를 함께 책임지는 환경에서는, 이 습관이 장애를 사후에 수습하는 대신 사전에 없애는 데 쓰일 수 있다고 생각합니다.',
    },
    {
      id: 51, applicationId: 102, charLimit: 800,
      prompt:'데이터 정합성이 중요한 시스템을 다뤄 본 경험과, 정합성을 어떻게 보장했는지 기술해 주십시오.',
      intent:'이 회사의 핵심 업무가 청구 정산이다. 정합성을 “신경 썼다”가 아니라 어떤 장치로 보장했는지를 본다.',
      asks:['정합성이 중요했던 상황', '정합성을 보장한 방법'],
      usedExperienceIds: [1, 2],
      draft:'',
      aiDraft:'정합성은 주의 깊게 짜는 것이 아니라 구조로 보장해야 한다는 것을 MSA 주문·결제 서비스에서 배웠습니다. 모놀리식 주문 서비스를 5개 마이크로서비스로 분리하자 결제 상태 불일치가 테스트 100건 중 12건 발생했습니다. 원인은 DB 커밋과 메시지 발행이 하나의 트랜잭션이 아니라는 데 있었습니다. 애플리케이션 코드에서 순서를 조정하는 방식으로는 재현 조건을 줄일 수 있을 뿐 없앨 수 없다고 판단해, Transactional Outbox 패턴을 도입했습니다. 이벤트를 같은 트랜잭션 안에서 Outbox 테이블에 적재하고 별도 릴레이가 발행하도록 바꾸자 불일치가 0건이 되었습니다. 주식 거래 API 에서도 같은 관점으로 접근해, 엔드포인트 18개의 에러 응답 형식을 1종으로 통일하고 DTO 검증을 표준화해 연동 시 문의를 9건에서 1건으로 줄였습니다. 청구 정산처럼 숫자 하나가 곧 신뢰인 도메인에서는, 사람이 조심하는 대신 구조가 막아 주도록 설계하겠습니다.',
    },
    {
      id: 52, applicationId: 102, charLimit: 600,
      prompt:'맡은 일을 끝까지 마무리한 경험을 기술해 주십시오.',
      intent:'책임감 문항. 끝까지 했다는 서술이 아니라, 중간에 그만둘 이유가 있었는데도 끝낸 근거를 본다.',
      asks:['끝까지 마무리한 경험'],
      usedExperienceIds: [4],
      draft:'',
      aiDraft:'',
    },
    {
      id: 41, applicationId: 105, charLimit: 800,
      prompt:'화면과 API 를 함께 개발한 경험과, 그 과정에서 두 쪽을 어떻게 맞췄는지 기술해 주십시오.',
      intent:'풀스택 지원자에게 가장 중요한 문항. 두 영역을 다 만졌다는 나열이 아니라, 경계에서 무엇을 합의했는지를 본다.',
      asks:['화면과 API 를 함께 개발한 경험', '두 쪽을 맞춘 방법'],
      usedExperienceIds: [3],
      draft:'화면을 그리기 전에 API 계약을 먼저 고정하는 것이 두 영역을 맞추는 가장 빠른 길이었습니다. Vue 3 관리자 대시보드를 맡았을 때, 화면 7개가 각자 필요한 데이터를 그때그때 호출하면서 같은 API 를 중복으로 부르고 있었습니다. 초기 렌더에만 24회의 요청이 나갔고 첫 화면이 뜨는 데 2.4초가 걸렸습니다. 저는 화면별 요구 데이터를 표로 정리해 백엔드와 함께 응답 스키마를 하나로 합의한 뒤, Pinia 로 전역 상태를 통합하고 Axios 인터셉터에서 인증과 에러를 한 곳으로 모았습니다. 그 결과 요청을 7회로 줄이고 초기 렌더 시간을 0.9초로 단축했습니다. 같은 기간 다른 조들이 평균 1.8초에 머물렀던 것과 비교하면 눈에 띄는 차이였습니다. 이 경험 이후로는 화면과 API 중 어느 쪽을 먼저 잡느냐가 아니라, 둘 사이의 계약을 언제 고정하느냐가 일정을 결정한다고 생각하게 되었습니다.',
      aiDraft:'',
    },
    {
      id: 42, applicationId: 105, charLimit: 600,
      prompt:'기획·디자인과 의견이 달랐을 때 범위를 조정한 경험을 기술해 주십시오.',
      intent:'협업·소통 문항. 감정 대립을 피했는지, 근거를 놓고 좁혔는지를 본다.',
      asks:['의견이 달랐던 상황', '범위를 조정한 방법'],
      usedExperienceIds: [6],
      draft:'이견은 주장으로 좁히는 것이 아니라 측정 가능한 기준으로 좁혔습니다. 학과 학회 운영진 8명이 정기 세미나 일정을 두고 두 편으로 갈려 3주간 결론을 내지 못했습니다. 양쪽 모두 참석률이 오를 것이라 주장했지만 근거는 각자의 경험뿐이었습니다. 저는 두 안을 각각 참석 가능 인원수로 환산해 표로 만든 뒤, 어느 쪽이 맞는지 말로 정하지 말고 4주간 시범 운영한 다음 출석률로 결정하자고 제안했습니다. 시범 운영 결과 한 안의 출석률이 62%에서 84%로 올랐고, 그 안이 만장일치로 채택되어 이후 학회 규칙이 되었습니다. 의견이 다를 때 필요한 것은 설득이 아니라 양쪽이 함께 인정할 수 있는 측정 기준이라는 것을 이때 배웠습니다.',
      aiDraft:'',
    },
    {
      id: 61, applicationId: 109, charLimit: 700,
      prompt:'서비스가 멈췄을 때 원인을 끝까지 추적해 본 경험을 기술해 주십시오.',
      intent:'SRE 의 핵심 문항. 복구했다가 아니라 원인을 어디까지 좁혔는지를 본다.',
      asks:['장애 상황', '원인을 추적한 방법'],
      usedExperienceIds: [4],
      draft:'배포할 때마다 약 4분간 서비스가 끊기는 문제를 만났습니다. 1초 간격 헬스 체크를 걸어 로그를 남겼더니 평균 4분 12초 동안 요청이 실패하고 있었고, 원인을 파드 교체 순서로 좁혔습니다. readinessProbe 와 maxUnavailable 설정을 바꿔 가며 여덟 번을 다시 배포한 끝에 실패 요청 0건으로 무중단 배포에 도달했습니다. 이 과정에서 배운 것은 장애를 눈으로 확인하지 말고 먼저 측정 가능한 형태로 만들어야 한다는 것이었습니다.',
      aiDraft:'',
    },
    {
      id: 62, applicationId: 109, charLimit: 600,
      prompt:'개선 결과를 수치로 증명해 본 경험을 기술해 주십시오.',
      intent:'비교 대상 없는 단독 수치는 감점이다. 무엇 대비 얼마나인지를 본다.',
      asks:['개선한 것', '수치로 증명한 방법'],
      usedExperienceIds: [],
      draft:'',
      aiDraft:'',
    },
    {
      id: 71, applicationId: 110, charLimit: 800,
      prompt:'지원 직무와 관련해 가장 깊이 파고들었던 경험을 하나 골라, 무엇을 목표로 삼았고 어떻게 해결했는지 써 주십시오.',
      intent:'', asks:[], usedExperienceIds:[1, 2],
      draft:'모놀리식 주문 서비스를 마이크로서비스로 분리하는 과제를 5인 팀으로 맡았습니다. 서비스가 나뉘면서 결제 상태가 주문 쪽과 어긋나는 문제가 생겼고, 테스트 100건 중 12건에서 불일치가 났습니다. 원인은 결제 이벤트가 이중 발행되는 것이었습니다. Transactional Outbox 패턴을 도입해 이벤트 발행을 DB 트랜잭션에 묶었고 불일치를 0건으로 줄였습니다. Spring Boot 와 Kafka 를 다루며 분산 환경에서 정합성을 지키는 방법을 배웠습니다.',
      aiDraft:'',
    },
    {
      id: 72, applicationId: 110, charLimit: 600,
      prompt:'당사에 지원한 이유와 입사 후 하고 싶은 일을 써 주십시오.',
      intent:'', asks:[], usedExperienceIds:[2],
      draft:'넥스트레이어의 이커머스 주문·결제 도메인은 제가 다뤄 본 문제와 가장 가깝습니다. 주식 거래 REST API 를 만들면서 엔드포인트 18개의 에러 응답 형식을 1종으로 통일했고, 프론트 연동 문의를 9건에서 1건으로 줄인 경험이 있습니다. 주문·결제처럼 실패가 곧 돈이 되는 도메인에서는 이 정합성이 더 중요하다고 생각합니다. 입사 후에는 결제 API 의 실패 처리와 재시도 구조를 맡아 보고 싶습니다.',
      aiDraft:'',
    },
    {
      id: 81, applicationId: 103, charLimit: 800,
      prompt:'인프라나 배포 환경을 직접 다뤄 본 경험을 하나 골라, 무엇을 개선했고 그 결과를 어떻게 확인했는지 써 주십시오.',
      intent:'', asks:[], usedExperienceIds:[], draft:'', aiDraft:'',
    },
    {
      id: 82, applicationId: 103, charLimit: 700,
      prompt:'문제가 생겼을 때 원인을 끝까지 추적해 본 경험을 써 주십시오. 어떤 근거로 범위를 좁혔는지 함께 적어 주십시오.',
      intent:'', asks:[], usedExperienceIds:[], draft:'', aiDraft:'',
    },
    {
      id: 83, applicationId: 104, charLimit: 800,
      prompt:'AI 또는 LLM 을 실제 서비스나 프로젝트에 붙여 본 경험을 써 주십시오. 무엇을 검증했고 한계는 무엇이었습니까?',
      intent:'', asks:[], usedExperienceIds:[], draft:'', aiDraft:'',
    },
    {
      id: 84, applicationId: 104, charLimit: 600,
      prompt:'사용자의 문제에서 출발해 기능을 정의해 본 경험을 써 주십시오.',
      intent:'', asks:[], usedExperienceIds:[], draft:'', aiDraft:'',
    },
    {
      id: 85, applicationId: 106, charLimit: 800,
      prompt:'화면을 직접 만들어 본 경험을 하나 골라, 사용자가 체감한 문제를 어떻게 개선했는지 수치와 함께 써 주십시오.',
      intent:'', asks:[], usedExperienceIds:[], draft:'', aiDraft:'',
    },
    {
      id: 86, applicationId: 106, charLimit: 600,
      prompt:'기획·디자인과 의견이 갈렸을 때 범위를 어떻게 조정했는지 써 주십시오.',
      intent:'', asks:[], usedExperienceIds:[], draft:'', aiDraft:'',
    },
    {
      id: 87, applicationId: 107, charLimit: 700,
      prompt:'화면을 만들면서 사용자가 체감한 문제를 개선한 경험을 써 주십시오.',
      intent:'', asks:[], usedExperienceIds:[3],
      draft:'화면 7개가 각자 API 를 중복 호출해 초기 로딩이 느렸습니다. Pinia 로 전역 상태를 통합하고 Axios 인터셉터로 인증·에러를 한 곳에 모았습니다. 라우터 단위 코드 스플리팅까지 적용해 중복 호출을 24회에서 7회로 줄였고, 초기 렌더 시간이 2.4초에서',
      aiDraft:'',
    },
    {
      id: 88, applicationId: 107, charLimit: 600,
      prompt:'당사에 지원한 이유를 써 주십시오.',
      intent:'', asks:[], usedExperienceIds:[], draft:'', aiDraft:'',
    },
  ],

  /* --- 지원 현황 ------------------------------------------- */
  /* --- 지원서 -------------------------------------------------
     status·제출 여부·면접 결과를 두지 않는다. 우리는 알 수 없다.
     진행 상태는 questions 의 draft 유무에서 파생한다. */
  applications: [
    { id:101, postingId:9,  company:'세움테크',   position:'백엔드 엔지니어' },
    { id:102, postingId:10, company:'다온소프트', position:'서버 개발' },
    { id:103, postingId:11, company:'한빛시스템', position:'플랫폼 엔지니어' },
    { id:104, postingId:12, company:'코어링크',   position:'백엔드 · 신입' },
    { id:105, postingId:13, company:'리버트리',   position:'풀스택 엔지니어' },
    { id:109, postingId:15, company:'한빛시스템', position:'SRE' },
    { id: 110, postingId: 16, company:'넥스트레이어', position:'백엔드 엔지니어 (신입)' },
    { id: 106, postingId: 14, company:'세움테크', position:'프론트엔드 엔지니어 (신입)' },
    { id: 107, postingId: 17, company:'하람랩스', position:'프론트엔드 엔지니어 (신입)' },
  ],


  /* --- AX-4 포트폴리오 인테이크 목업 결과 ------------------
     코드·문서에서 확인되는 것만 채우고, 본인만 아는 것은 질문으로 되묻는다. */
  intakeCandidates: [
    {
      key:'msa', title:'MSA 주문·결제 서비스 구축', period:'2026.08', category:'팀 프로젝트',
      duplicateOfExperienceId: 1,
      situation:'order-service · payment-service 등 5개 모듈과 docker-compose, Eureka 설정이 있습니다.',
      action:'Kafka 프로듀서와 OutboxEvent 엔티티, 스케줄러 릴레이 구현이 확인됩니다.',
      evidence:[{ type:'REPO', ref:'msa-order-service', quote:'@Scheduled(fixedDelay = 500) public void relay()' }],
      questions:[], suggestedCompetencyIds:[5,2,1,14],
    },
    {
      key:'oss', title:'오픈소스 라이브러리 버그 수정 기여', period:'2026.06', category:'개인 프로젝트',
      duplicateOfExperienceId: null,
      situation:'JSON 직렬화 라이브러리 저장소에 본인 계정으로 머지된 PR 두 건이 있습니다. 이슈에 재현 코드를 먼저 올린 이력이 남아 있습니다.',
      action:'중첩 제네릭 타입에서 타입 정보가 지워지는 문제를 재현 테스트로 좁히고, TypeReference 처리 분기를 수정했습니다. 리뷰 코멘트 11개를 반영해 세 차례 다시 올렸습니다.',
      evidence:[
        { type:'PR',   ref:'PR #412 · merged', quote:'fix: preserve nested generic type info' },
        { type:'REPO', ref:'test/NestedGenericTest.java', quote:'assertThat(result).isInstanceOf(Map.class)' },
      ],
      questions:[
        { field:'task', q:'이 버그를 고치기로 한 계기는 무엇이었고, 어디까지 고치는 것을 목표로 잡았나요?',
          why:'PR 에는 무엇을 고쳤는지는 있지만 왜 이 문제를 골랐는지는 없습니다. 남의 코드에 손대기로 한 판단이 이 경험의 핵심입니다.' },
        { field:'result', q:'머지까지 얼마나 걸렸고, 리뷰에서 몇 번 수정 요청을 받았나요? 이 라이브러리를 쓰는 사용자 규모를 아신다면 함께 적어 주세요.',
          why:'오픈소스 기여는 규모가 곧 임팩트입니다. 저장소 스타 수나 다운로드 수는 읽었지만 본인이 체감한 것은 다를 수 있습니다.' },
      ],
      suggestedCompetencyIds:[14,6,13,17],
    },
    {
      key:'algo', title:'알고리즘 스터디 12주 운영', period:'2025.09 – 2025.12', category:'대외활동',
      duplicateOfExperienceId: null,
      situation:'주차별 문제 풀이 저장소에 본인 포함 7명의 커밋이 12주간 이어져 있습니다. 첫 주 참여자는 12명, 마지막 주는 7명입니다.',
      action:'매주 문제를 선정하고 풀이를 코드 리뷰 형식으로 남겼습니다. 6주차부터 난이도 투표와 짝 리뷰 방식을 도입한 커밋 메시지가 있습니다.',
      evidence:[
        { type:'REPO', ref:'algo-study-2025/week06/README.md', quote:'이번 주부터 난이도 투표로 문제를 고릅니다' },
        { type:'REPO', ref:'커밋 이력', quote:'12주 연속 · 주 1회 이상' },
      ],
      questions:[
        { field:'task', q:'스터디를 시작할 때 무엇을 목표로 삼으셨나요? 중도 이탈을 줄이는 것이 목표였다면 어느 정도를 기대했나요?',
          why:'참여자 수 변화는 저장소에서 읽히지만, 그것이 목표였는지 결과였는지는 알 수 없습니다.' },
        { field:'result', q:'6주차에 방식을 바꾼 뒤 이탈률이나 참여율이 어떻게 달라졌나요? 숫자로 답해 주세요.',
          why:'방식을 바꾼 커밋은 있지만 그 효과를 측정한 기록이 없습니다. 이 숫자가 있어야 “운영했다”가 “개선했다”가 됩니다.' },
      ],
      suggestedCompetencyIds:[11,13,18,12],
    },
    {
      key:'hack', title:'교내 해커톤 실시간 투표 서비스', period:'2025.05', category:'수상·자격',
      duplicateOfExperienceId: null,
      situation:'48시간 해커톤 저장소입니다. WebSocket 기반 실시간 집계와 Redis 캐시 설정이 있고, 발표 자료에 “최우수상”이 적혀 있습니다.',
      action:'투표 폭주 시 집계가 밀리는 문제를 Redis 카운터와 주기적 flush 로 나눠 처리했습니다. 프론트는 낙관적 업데이트로 먼저 반영했습니다.',
      evidence:[
        { type:'REPO', ref:'vote-live/src/VoteAggregator.java', quote:'redisTemplate.opsForValue().increment(key)' },
        { type:'DOC',  ref:'발표자료.pdf', quote:'교내 해커톤 최우수상' },
      ],
      questions:[
        { field:'task', q:'48시간 안에 반드시 되게 만들려고 정한 우선순위가 있었나요? 무엇을 포기하기로 했나요?',
          why:'무엇을 만들었는지는 코드에 있지만, 시간이 없을 때 무엇을 버렸는지는 본인만 압니다. 이게 판단력을 보여주는 부분입니다.' },
        { field:'result', q:'동시 투표를 몇 건까지 처리했고, 개선 전에는 얼마나 밀렸나요?',
          why:'Redis 를 쓴 것은 확인되지만 그것이 얼마나 효과가 있었는지는 측정 기록이 없습니다.' },
      ],
      suggestedCompetencyIds:[9,4,2,3],
    },
    {
      key:'capstone', title:'학부 캡스톤 · 설비 센서 이상 탐지', period:'2025.03 – 2025.06', category:'팀 프로젝트',
      duplicateOfExperienceId: null,
      situation:'센서 시계열 데이터 전처리와 이상 탐지 모델 학습 노트북이 있습니다. 4인 팀 저장소이며 본인 커밋은 전처리와 평가 부분에 몰려 있습니다.',
      action:'결측 구간을 보간하고 이동 표준편차로 특징을 만들었습니다. 임계값을 고정하지 않고 재현율 기준으로 조정한 평가 코드가 있습니다.',
      evidence:[
        { type:'REPO', ref:'capstone/preprocess.ipynb', quote:'df.interpolate(method="time")' },
        { type:'REPO', ref:'capstone/eval.py', quote:'threshold tuned for recall >= 0.9' },
      ],
      questions:[
        { field:'task', q:'이 프로젝트에서 본인이 맡은 범위는 어디까지였고, 팀에서 정한 성공 기준은 무엇이었나요?',
          why:'4인 팀 저장소라 커밋만으로는 기여 범위가 모호합니다. 자소서에서 가장 자주 감점되는 지점입니다.' },
        { field:'result', q:'최종 재현율과 오탐률이 얼마였나요? 기존 방식이나 베이스라인과 비교할 수 있다면 함께 적어 주세요.',
          why:'평가 코드는 있지만 최종 수치가 저장소에 없습니다. 비교 대상 없는 단독 수치는 감점 사유입니다.' },
      ],
      suggestedCompetencyIds:[4,14,15,12],
    },
  ],


  /* --- 금지 표현 -------------------------------------------
     앞 8종은 강의정리_260828 부록 D 원문 그대로.
     뒤 2종은 채용 트렌드 자료의 BAD 이력서 예시에서 가져왔다. */
  bannedPhrases: [
    { phrase:'열심히 하겠습니다',           instead:'구체적 기여 포인트로 바꾸세요. 회사는 "열심히(hard)"가 아니라 "잘(well)"을 봅니다.' },
    { phrase:'잘합니다',                     instead:'성과와 근거를 쓰세요. 근거 없는 역량 나열은 감점 3위입니다.' },
    { phrase:'성장하겠습니다',               instead:'1년 / 3년 / 5년 목표와 그때의 기여를 쓰세요.' },
    { phrase:'부족하지만',                   instead:'"제 강점 ○○를 통해 잘 적응하겠습니다". 회사는 교육기관이 아닙니다.' },
    { phrase:'시켜주시면',                   instead:'어떤 성과를 낼 수 있고 어떤 전문성을 가질지 쓰세요.' },
    { phrase:'저는 원래',                    instead:'"~한 경향이 있었지만 보완하기 위해 ~했습니다"로 바꾸세요.' },
    { phrase:'아직은 잘 모르겠지만',         instead:'결론부터 쓰세요.' },
    { phrase:'AI 에이전트 개발자가 되겠습니다', instead:'10명 중 9명이 씁니다. 구체적 분야와 기여로 바꾸세요.' },
    { phrase:'정보 검색 우수',               instead:'도구 나열은 스킬 기반 채용에서 감점입니다. 그 도구로 무엇을 얼마나 바꿨는지 쓰세요.' },
    { phrase:'활용 능숙',                    instead:'“능숙”은 근거가 아닙니다. 사용한 스킬로 만든 정량 성과로 바꾸세요.' },
  ],
};
