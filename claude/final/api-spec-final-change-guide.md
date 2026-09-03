# Career Lab 최종 화면 기준 API 명세 수정안

검토 기준일: 2026-09-03  
기준 화면: `http://172.16.10.135:5173`

## 1. 확정한 제품 규칙

1. 사용자는 경험을 등록하고 각 경험에 역량과 강도(`strength`)를 저장한다.
2. AI 매칭은 사용자의 경험 역량과 공고 요구 역량을 비교한다.
3. 매칭 단위는 `(로그인 사용자, 공고)`이며 자소서별 매칭이 아니다.
4. 공고 자기소개서 문항은 지원서 생성 전부터 공고 상세에서 조회한다.
5. `이 공고로 지원서 만들기` 단계와 API는 없다.
6. 문항 답변은 사용자별 현재 내용 한 건만 저장한다. 버전 이력과 되돌리기는 제공하지 않는다.
7. AI 초안 생성은 비동기이며 `202 Accepted + taskId + 폴링`을 사용한다.
8. Kafka/Outbox는 현재 MVP에서 사용하지 않는다.
9. Supabase Auth가 인증을 담당하고 Career DB에는 비밀번호를 저장하지 않는다.
10. 화면의 `작성 전/작성 중/작성 완료`는 저장된 답변 수로 계산하며 별도 application 상태로 저장하지 않는다.

## 2. 기존 Notion API에서 삭제할 API

| 기존 API | 삭제 사유 |
|---|---|
| `POST /api/applications` | 최종 화면에 지원서 생성 단계가 없음 |
| `POST /api/applications/{applicationId}/questions` | 문항은 application이 아니라 공고에 소속됨 |
| `GET /api/applications/{applicationId}/assessment` | 매칭은 application이 아니라 사용자+공고 기준 |
| `POST /api/answers/{answerId}/drafts` | 초안 생성 전에는 answerId가 존재하지 않을 수 있음 |
| `GET /api/answers/{answerId}/versions` | 버전 기능 제거 |
| `POST /api/answers/{answerId}/versions` | 버전 기능 제거 |

## 3. 변경하거나 새로 추가할 API

| 구분 | Method | 최종 URI | 주요 변경 |
|---|---|---|---|
| 내 정보 | GET | `/api/me` | Supabase JWT 기준 사용자 조회 |
| 역량 | GET | `/api/competencies` | category는 `TECH/SOFT/DOMAIN/VALUE`만 사용 |
| 공고 목록 | GET | `/api/postings` | applicationId 제거, 답변 진행률 포함 |
| 공고 상세 | GET | `/api/postings/{postingId}` | applicationId 제거 |
| 공고 문항 | GET | `/api/postings/{postingId}/application-questions` | `job_posting_questions`에서 조회 |
| 공고 매칭 | GET | `/api/postings/{postingId}/match` | 신규. 로그인 사용자+공고 기준 |
| 즐겨찾기 | PUT | `/api/postings/{postingId}/bookmark` | 기존 멱등 PUT 유지 |
| 경험 목록 | GET | `/api/experiences` | STAR, 기간, 역량 strength까지 반환 |
| 경험 등록 | POST | `/api/experiences` | competencies에 strength 포함 |
| 경험 수정 | PUT | `/api/experiences/{experienceId}` | competencies에 strength 포함 |
| 경험 인테이크 | POST | `/api/experience-intakes` | 202 + 숫자형 taskId |
| AI 초안 | POST | `/api/posting-questions/{questionId}/drafts` | answerId 대신 questionId 사용 |
| 현재 답변 | GET | `/api/posting-questions/{questionId}/answer` | 버전 목록 대신 현재 한 건 조회 |
| 답변 저장 | PUT | `/api/posting-questions/{questionId}/answer` | 최초 INSERT, 이후 UPDATE(upsert) |
| AI 작업 | GET | `/api/ai-tasks/{taskId}` | 상태명 통일 및 폴링 |
| 역량 추출 목업 | POST | `/internal/mock/competency-extractions` | 개발/테스트 환경 전용 |
| 공고 역량 확정 | PUT | `/api/postings/{postingId}/competencies` | 운영 시 관리자/내부 권한 필요 |

최종 공개·내부 API 수는 위 기준 17개다. README에는 숫자를 하드코딩하기보다 API 명세를 단일 원본으로 사용하는 것을 권장한다.

## 4. 공통 인증 규칙

모든 `/api/**` 요청은 Supabase Access Token을 사용한다.

```http
Authorization: Bearer {supabaseAccessToken}
```

백엔드는 JWT의 `sub`를 `users.supabase_user_id`와 매칭한다. 요청 Body나 Query로 `userId`를 받지 않는다.

공통 인증 오류:

- `401 AUTH_TOKEN_MISSING`
- `401 AUTH_TOKEN_INVALID`
- `404 USER_NOT_REGISTERED`: Supabase 사용자는 유효하지만 Career DB 프로필이 없음

## 5. 공고 목록

```http
GET /api/postings?bookmarked=false&sort=match&cursor={cursor}&size=20
```

Query:

- `bookmarked?: boolean`
- `sort?: latest | match | deadline` (기본 `latest`)
- `cursor?: string`
- `size?: number` (기본 20, 최대 100)

Response `200 OK`:

```json
{
  "postings": [
    {
      "postingId": 11,
      "companyName": "한빛시스템",
      "positionName": "플랫폼 엔지니어 (신입)",
      "deadline": "2026-09-21",
      "bookmarked": false,
      "matchScore": 85,
      "answerStatus": "NOT_STARTED",
      "answeredQuestionCount": 0,
      "totalQuestionCount": 2
    }
  ],
  "nextCursor": null,
  "hasNext": false
}
```

규칙:

- `applicationId`를 반환하지 않는다.
- `matchScore`는 아직 계산되지 않았으면 `null`이다.
- `answerStatus`는 저장 컬럼이 아니라 문항 수와 현재 답변 수로 계산한다.
- `0/N=NOT_STARTED`, `1~N-1/N=IN_PROGRESS`, `N/N=COMPLETED`이다.
- `status=ACTIVE`인 공고만 기본 노출하며 `deadline=null`은 상시채용이다.

오류:

- `400 INVALID_QUERY_PARAMETER`
- `401 AUTH_TOKEN_INVALID`
- `500 INTERNAL_SERVER_ERROR`

## 6. 공고 상세

```http
GET /api/postings/{postingId}
```

Response `200 OK`:

```json
{
  "postingId": 11,
  "companyName": "한빛시스템",
  "positionName": "플랫폼 엔지니어 (신입)",
  "content": "공고 원문",
  "deadline": "2026-09-21",
  "bookmarked": false,
  "competencies": [
    {
      "competencyId": 3,
      "name": "클라우드 인프라 운영·IaC",
      "weight": 0.9,
      "evidenceLine": "쿠버네티스 클러스터 운영과 장애 대응"
    }
  ]
}
```

오류:

- `401 AUTH_TOKEN_INVALID`
- `404 POSTING_NOT_FOUND`
- `500 INTERNAL_SERVER_ERROR`

## 7. 공고 자기소개서 문항 조회

```http
GET /api/postings/{postingId}/application-questions
```

Response `200 OK`:

```json
{
  "postingId": 11,
  "collectionStatus": "AVAILABLE",
  "questions": [
    {
      "questionId": 501,
      "content": "인프라나 배포 환경을 직접 다뤄 본 경험을 작성해 주세요.",
      "maxLength": 800,
      "lengthUnit": "CHARACTER",
      "displayOrder": 1,
      "answer": {
        "answerId": 701,
        "content": "현재 저장된 내용",
        "source": "AI_ASSISTED",
        "usedExperienceIds": [1, 4],
        "updatedAt": "2026-09-03T17:00:00+09:00"
      }
    }
  ]
}
```

규칙:

- 답변이 없으면 `answer: null`이다.
- 문항이 확인됐지만 없으면 `collectionStatus=NOT_AVAILABLE`, `questions=[]`이다.
- 아직 수집하지 않았으면 `NOT_CHECKED`, 수집 실패면 `FAILED`다.
- 로그인 사용자의 답변만 조인해 반환한다.

오류:

- `401 AUTH_TOKEN_INVALID`
- `404 POSTING_NOT_FOUND`
- `500 INTERNAL_SERVER_ERROR`

## 8. 매칭 결과 조회

```http
GET /api/postings/{postingId}/match
```

Response `200 OK`:

```json
{
  "postingId": 11,
  "status": "COMPLETED",
  "matchScore": 85,
  "verdict": "RECOMMEND",
  "headline": "요구 역량 11개를 경험으로 덮고 있습니다.",
  "summary": "요약",
  "coverage": [
    {
      "competencyId": 3,
      "competencyName": "클라우드 인프라 운영·IaC",
      "coverageScore": 80,
      "experienceIds": [4]
    }
  ],
  "actions": [],
  "computedAt": "2026-09-03T16:30:00+09:00"
}
```

아직 결과가 없고 계산 중이면 `200 OK`로 다음처럼 반환한다.

```json
{
  "postingId": 11,
  "status": "RUNNING",
  "matchScore": null,
  "verdict": null,
  "headline": null,
  "summary": null,
  "coverage": [],
  "actions": [],
  "computedAt": null
}
```

오류:

- `401 AUTH_TOKEN_INVALID`
- `404 POSTING_NOT_FOUND`
- `500 INTERNAL_SERVER_ERROR`

## 9. 경험 등록 및 수정

```http
POST /api/experiences
PUT /api/experiences/{experienceId}
```

Request:

```json
{
  "title": "EKS 무중단 배포 파이프라인",
  "category": "실습 프로젝트",
  "periodText": "2026.08",
  "sortDate": "2026-08-01",
  "situation": "로컬에서만 돌던 서비스를 공용 클러스터에 올려야 했다.",
  "task": "배포 중 중단을 없애야 했다.",
  "action": "readinessProbe와 롤링 업데이트를 설정했다.",
  "result": "다운타임을 4분에서 0초로 줄였다.",
  "competencies": [
    { "competencyId": 3, "strength": 0.9 },
    { "competencyId": 12, "strength": 0.7 }
  ]
}
```

규칙:

- `competencyId` 중복을 금지한다.
- `strength`는 0 이상 1 이하이다.
- 수정 API는 본인의 경험만 수정할 수 있다.
- 경험 저장 성공 후 매칭 재계산을 비동기로 요청한다.
- Kafka 없이 트랜잭션 커밋 후 애플리케이션 비동기 실행기로 AI Service HTTP API를 호출한다.
- 매칭 재계산 실패가 경험 저장 자체를 롤백시키면 안 된다.

오류:

- `400 INVALID_EXPERIENCE`
- `400 INVALID_STRENGTH`
- `401 AUTH_TOKEN_INVALID`
- `403 EXPERIENCE_ACCESS_DENIED`
- `404 EXPERIENCE_NOT_FOUND`
- `404 COMPETENCY_NOT_FOUND`
- `500 INTERNAL_SERVER_ERROR`

## 10. AI 자소서 초안 생성

```http
POST /api/posting-questions/{questionId}/drafts
```

Request:

```json
{
  "experienceIds": [1, 4]
}
```

Response `202 Accepted`:

```json
{
  "taskId": 151,
  "status": "PENDING",
  "pollAfterMs": 1000
}
```

규칙:

- 질문은 반드시 활성 상태여야 한다.
- `experienceIds`는 모두 로그인 사용자의 경험이어야 한다.
- 초안 작업 완료만으로 `cover_letter_answers`를 저장하거나 덮어쓰지 않는다.
- 완료 결과를 에디터에 표시한 후 사용자가 저장 버튼을 눌러 확정한다.

오류:

- `400 EXPERIENCE_REQUIRED`
- `401 AUTH_TOKEN_INVALID`
- `403 EXPERIENCE_ACCESS_DENIED`
- `404 QUESTION_NOT_FOUND`
- `409 DRAFT_ALREADY_RUNNING`
- `500 INTERNAL_SERVER_ERROR`

## 11. AI 작업 폴링

```http
GET /api/ai-tasks/{taskId}
```

진행 중 `200 OK`:

```json
{
  "taskId": 151,
  "type": "DRAFT",
  "status": "RUNNING",
  "result": null,
  "error": null
}
```

완료 `200 OK`:

```json
{
  "taskId": 151,
  "type": "DRAFT",
  "status": "COMPLETED",
  "result": {
    "questionId": 501,
    "content": "생성된 초안",
    "charCount": 642,
    "usedExperienceIds": [1, 4],
    "cautions": []
  },
  "error": null
}
```

실패한 AI 작업도 조회 요청 자체는 성공했으므로 HTTP `200 OK`를 사용한다.

```json
{
  "taskId": 151,
  "type": "DRAFT",
  "status": "FAILED",
  "result": null,
  "error": {
    "code": "AI_PROVIDER_ERROR",
    "message": "초안 생성에 실패했습니다. 다시 시도해 주세요."
  }
}
```

상태 값은 모든 API와 DB에서 다음 네 개만 사용한다.

- `PENDING`
- `RUNNING`
- `COMPLETED`
- `FAILED`

`jobId`, `taskId`, `SUCCEEDED`, `COMPLETED`를 혼용하지 않는다. 최종 명칭은 숫자형 `taskId`와 `COMPLETED`다.

## 12. 현재 답변 저장

```http
PUT /api/posting-questions/{questionId}/answer
```

Request:

```json
{
  "content": "AI 초안을 사용자가 수정한 현재 내용",
  "usedExperienceIds": [1, 4],
  "draftTaskId": 151
}
```

Response `200 OK`:

```json
{
  "answerId": 701,
  "questionId": 501,
  "content": "AI 초안을 사용자가 수정한 현재 내용",
  "charCount": 642,
  "byteCount": 1510,
  "source": "AI_ASSISTED",
  "usedExperienceIds": [1, 4],
  "updatedAt": "2026-09-03T17:00:00+09:00"
}
```

규칙:

- 최초 저장은 INSERT, 이후 저장은 `(user_id, job_posting_question_id)` 기준 UPDATE다.
- 버전 행을 추가하지 않는다.
- `source`는 클라이언트가 임의로 보내지 않고 서버가 판단한다.
- `draftTaskId`가 없으면 `MANUAL`, AI 결과를 그대로 저장하면 `AI_GENERATED`, 수정 후 저장하면 `AI_ASSISTED`다.
- `charCount`, `byteCount`는 서버가 계산한다.
- `usedExperienceIds` 연결은 저장 시 현재 목록으로 교체한다.
- `draftTaskId`는 로그인 사용자와 해당 문항의 완료된 DRAFT 작업이어야 한다.

오류:

- `400 CONTENT_REQUIRED`
- `401 AUTH_TOKEN_INVALID`
- `403 EXPERIENCE_ACCESS_DENIED`
- `404 QUESTION_NOT_FOUND`
- `404 DRAFT_TASK_NOT_FOUND`
- `409 DRAFT_TASK_NOT_COMPLETED`
- `422 CHARACTER_LIMIT_EXCEEDED`
- `500 INTERNAL_SERVER_ERROR`

## 13. 현재 답변 조회

```http
GET /api/posting-questions/{questionId}/answer
```

답변 존재 `200 OK`:

```json
{
  "answer": {
    "answerId": 701,
    "questionId": 501,
    "content": "현재 저장된 내용",
    "source": "AI_ASSISTED",
    "usedExperienceIds": [1, 4],
    "updatedAt": "2026-09-03T17:00:00+09:00"
  }
}
```

답변 없음 `200 OK`:

```json
{
  "answer": null
}
```

## 14. 화면 저장 UX 계약

자소서 에디터에는 다음 상태를 반드시 표시한다.

- `저장`: 저장 가능한 변경 사항이 있음
- `저장 중...`: PUT 요청 진행 중
- `저장됨 · HH:mm`: 마지막 PUT 성공
- `저장 실패 · 다시 시도`: PUT 실패

명시적 저장 버튼을 MVP 기본으로 사용한다. 저장되지 않은 변경이 있을 때 문항 이동 또는 페이지 이탈 시 경고한다.

## 15. 구현 담당별 수정 범위

### ERD 담당

- `applications`, `application_status`, application FK 제거
- `job_posting_questions` 추가
- `cover_letter_answers`를 사용자+공고문항당 현재 한 건 구조로 변경
- `version`, `is_final` 및 버전 관련 인덱스 제거
- `job_matches`는 `(user_id, job_posting_id)` 유지
- `outbox_events` 제거
- `users.supabase_user_id UUID UNIQUE NOT NULL` 추가
- `user_auth_providers/password_hash` 제거

### API 담당

- application 기반 API 3개 제거
- answer version API 2개 제거
- assessment를 posting match API로 변경
- draft 시작 식별자를 answerId에서 questionId로 변경
- 현재 답변 GET/PUT 추가
- 경험 요청에 `competencies[].strength` 추가
- `taskId`와 `COMPLETED` 상태명 통일
- 응답에서 `applicationId` 제거

### 목업 담당

- application 존재 여부 분기와 지원서 생성 버튼 제거
- 공고 문항을 postingId 기준으로 구성
- `versions`, `versionsSeed`, 버전 목록/복원/개수 제거
- `새 버전 저장`을 `저장`으로 변경하고 저장 상태 표시
- 매칭 설명을 사용자 경험+공고 요구 역량 기준으로 유지
- API 경로, 상태 코드, 네트워크 로그, README 개수 수정
- 개발 참고 시드 SQL을 현재 답변 1행 upsert 구조로 수정

## 16. 승인 조건

다음 조건을 모두 만족하면 ERD와 API가 최종 화면과 일치한다고 판단한다.

- 지원서 생성 없이 공고 문항이 조회된다.
- 지원서 생성 없이 매칭 점수가 조회된다.
- 사용자마다 같은 공고 문항에 답변은 최대 한 행이다.
- 답변을 여러 번 저장해도 버전 행이 증가하지 않는다.
- AI 초안 생성 전 answerId가 필요하지 않다.
- AI 초안 완료 후 사용자가 저장하기 전에는 현재 답변이 변경되지 않는다.
- 경험 strength가 저장되고 매칭 입력에 포함된다.
- 공고 카드 작성 상태가 문항/답변 개수와 일치한다.
- 다른 사용자의 경험·답변·AI 작업을 조회하거나 사용할 수 없다.
- AI DB와 Career DB 사이에 물리 FK가 없다.
