# Git Convention

## 1. 기본 규칙

- `main` 브랜치에 직접 push하지 않습니다.
- 작업 전 `main` 브랜치의 최신 내용을 받습니다.
- 각자 작업 브랜치를 만들어 작업합니다.
- 작업 완료 후 Pull Request(PR)를 생성합니다.
- `.env`, API Key, 비밀번호는 커밋하지 않습니다.

## 2. 작업 순서

### 1) 최신 코드 받기

```bash
git checkout main
git pull origin main
```

### 2) 작업 브랜치 생성하기

```bash
git checkout -b 브랜치종류/작업이름
```

예시:

```bash
git checkout -b feature/fe-coverletter-form
git checkout -b feature/be-coverletter-api
git checkout -b feature/be-database
git checkout -b docs/api-spec
```

### 3) 작업 내용 올리기

```bash
git add .
git commit -m "feat: 작업 내용"
git push -u origin 브랜치이름
```

push 후 GitHub에서 `main` 브랜치를 대상으로 PR을 생성합니다.

## 3. 브랜치 이름

| 종류 | 용도 | 예시 |
| --- | --- | --- |
| `feature` | 기능 개발 | `feature/be-coverletter-api` |
| `fix` | 오류 수정 | `fix/fe-api-error` |
| `docs` | 문서 작업 | `docs/api-spec` |
| `chore` | 설정 및 기타 작업 | `chore/project-setting` |

브랜치 이름에는 영문 소문자와 하이픈을 사용합니다.

## 4. 커밋 메시지

커밋 메시지는 `종류: 작업 내용` 형식으로 작성합니다.

| 종류 | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 오류 수정 |
| `docs` | 문서 작성 및 수정 |
| `refactor` | 코드 구조 개선 |
| `test` | 테스트 추가 및 수정 |
| `chore` | 환경 설정 및 기타 작업 |

예시:

```text
feat: 자기소개서 생성 API 구현
fix: API 응답 오류 수정
docs: ERD 작성
chore: 프로젝트 설정 추가
```

## 5. Pull Request 규칙

- PR 제목은 커밋 메시지와 동일한 형식을 사용합니다.
- 작업한 내용을 간단히 작성합니다.
- 화면 변경이 있으면 스크린샷을 첨부합니다.
- 최소 팀원 1명의 확인을 받은 후 merge합니다.
- API 요청·응답 규격 변경은 Data/API 담당자의 확인을 받습니다.
- merge가 완료되면 작업 브랜치를 삭제합니다.

### PR 작성 예시

```markdown
## 작업 내용

- 자기소개서 생성 API를 구현했습니다.
- Mock 응답 데이터를 추가했습니다.

## 확인 사항

- [ ] 로컬 실행 확인
- [ ] 기존 기능 정상 동작 확인
- [ ] API Key 및 `.env` 미포함 확인
```

## 6. Merge 규칙

- PR의 충돌 여부와 변경 내용을 확인한 후 merge합니다.
- 병합 방식은 `Squash and merge`를 사용합니다.
- 충돌이 발생하면 담당자와 함께 해결한 후 merge합니다.
