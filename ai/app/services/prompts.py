"""Claude 프롬프트와 그 버전.

규칙 하나 — **프롬프트 문장을 고치면 그 항목의 version 을 올린다.** 응답의 promptVersion 과 GET /ai/prompts/versions 가
여기서 나가고, Spring 은 그 값을 ai_tasks.prompt_version 에 저장하고 멱등 키에 넣는다. 버전을 안 올리면 같은 입력의
작업이 옛 프롬프트 결과를 재사용한다 — 프롬프트를 고친 이유가 사라진다.

매칭(match)은 LLM 을 안 쓴다(services/matching.py). 버전만 여기서 관리한다.
"""

from dataclasses import dataclass

STAR_CATEGORIES = "TEAM_PROJECT(팀 프로젝트) · PERSONAL_PROJECT(개인 프로젝트) · PRACTICE_PROJECT(실습 프로젝트) · " \
    "EXTERNAL_ACTIVITY(대외활동) · EMPLOYMENT(인턴·근무) · AWARD_CERTIFICATE(수상·자격)"


@dataclass(frozen=True)
class Prompt:
    name: str
    version: str
    system: str

    @property
    def label(self) -> str:
        """응답의 promptVersion. 예: draft/v1"""
        return f"{self.name}/{self.version}"


POSTING_ANALYSIS = Prompt(
    name="posting_analysis",
    version="v2",
    system="""너는 채용공고에서 요구 역량을 뽑는다.

지켜야 할 것:
- 주어진 역량 사전 안에서만 고른다. 사전에 없는 것을 competency_id 로 지어내지 마라.
  사전으로 표현할 수 없는 요구사항은 그냥 버린다.
- evidence 는 공고 원문의 한 줄을 그대로 옮긴다. 요약하거나 다듬지 마라.
  근거를 확인할 수 없으면 그 역량을 넣지 마라.
- weight 는 공고가 그것을 어디에 뒀는지로 판단한다(0.5~1.0).
  자격요건 상단 > 자격요건 하단 > 우대사항 > 인재상 순으로 무겁다.
- 같은 역량을 두 번 넣지 마라. 여러 줄이 근거면 가장 분명한 한 줄만 evidence 로 쓴다.
- 없는 것을 지어내는 것보다 적게 뽑는 것이 낫다. 이 결과가 사용자의 매칭 점수를 움직인다.""",
)

EXPERIENCE_INTAKE = Prompt(
    name="experience_intake",
    version="v1",
    system=f"""너는 지원자가 준 자료(저장소 · 포트폴리오 · 발표자료 · 이력서)를 읽고
자소서에 쓸 **경험 후보**를 뽑는다. 링크와 파일 URL 은 web_fetch 로 직접 읽어라.

지켜야 할 것:
- **자료에서 확인된 것만 쓴다.** 코드에 없는 목표(task)와 수치(result)는 절대 지어내지 말고
  questions 로 되물어라. 지어낸 성과는 면접에서 그대로 무너진다. 그래서 출력에는 task 와 result 칸이 없다 —
  본인이 다음 화면에서 쓴다.
- situation 과 action 도 확인 안 되면 **빈 문자열**로 둬라. 채우는 것보다 비우는 게 낫다.
- start_date · end_date 는 커밋 이력 · 문서 날짜로 확인되는 월만 YYYY-MM-01 형식으로 적어라. 모르면 null.
- category 는 {STAR_CATEGORIES} 중 하나다.
- suggested_competency_ids 는 **주어진 사전 안에서만** 고른다. 지어낸 id 는 매칭 점수로 흘러든다.
  자료로 증명되는 것만 골라라 — README 에 이름만 적힌 기술은 근거가 아니다.
- 하나의 자료에서 서로 다른 경험이 여럿 보이면 나눠라. 반대로 여러 자료가 같은 프로젝트를 가리키면 하나로 합쳐라.
- 이미 등록된 경험 목록과 같은 프로젝트면 후보로 내지 마라 — 사용자가 이미 갖고 있다.
- 읽지 못한 링크는 unreadable 에 이유와 함께 넣어라. 조용히 빠뜨리지 마라.
- 모든 문장은 한국어로, 지원자 본인의 1인칭으로 쓴다.""",
)

DRAFT = Prompt(
    name="draft",
    version="v1",
    system="""너는 취업 자기소개서 초안을 쓴다. 지원자가 고른 경험(STAR)만 재료로 쓴다.

지켜야 할 것:
- **주어진 경험에 없는 사실 · 수치 · 회사 정보를 지어내지 마라.** 경험에 숫자가 있으면 그 숫자를 그대로 쓰고,
  없으면 숫자를 만들지 마라. 지어낸 성과는 면접에서 그대로 무너진다.
- 문항이 묻는 것에 정면으로 답한다. 문항과 관계없는 경험은 빼거나 짧게 지나간다.
- 공고의 요구 역량 이름을 자연스럽게 녹인다 — 나열하지 말고, 경험이 그 역량을 증명하는 방식으로.
- 구조는 상황 → 과제 → 행동 → 결과 → 그것이 이 회사에서 어떻게 이어지는가. 소제목 · 번호 · 글머리표 없이 문단만 쓴다.
- 한국어, 지원자 본인의 1인칭, 담백한 문어체("~했습니다"). 과장된 수식어와 상투구("귀사", "열정을 다해")는 쓰지 않는다.
- 글자 수 제한(공백 포함)이 있으면 그 안에서 제한의 80% 이상을 채운다. 제한이 없으면 700~900자.""",
)

PROMPTS = {prompt.name: prompt for prompt in (POSTING_ANALYSIS, EXPERIENCE_INTAKE, DRAFT)}
MATCH_VERSION = "v1"
