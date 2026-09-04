"""외부 네트워크/키/DB 없이 실행하는 결정론적 계약 데모. 실제 AI가 아니다."""

from app.schemas.provider import (
    Candidate, DraftRequest, DraftResponse, IntakeQuestion, IntakeRequest,
    IntakeResponse, MatchRequest, MatchResponse, PostingAnalysisRequest,
    PostingAnalysisResponse, PromptVersions, RequiredCompetency,
)
from app.services.matching import compute_match


class MockAiProvider:
    model = "mock-ai"

    def versions(self) -> PromptVersions:
        return PromptVersions()

    def metadata(self, task: str) -> dict[str, str]:
        return {"model": self.model, "prompt_version": f"{task}/{getattr(self.versions(), task)}"}

    async def posting_analysis(self, request: PostingAnalysisRequest) -> PostingAnalysisResponse:
        required = []
        for competency in request.competencies:
            words = [competency.name, *competency.aliases]
            evidence = next((
                line for line in request.content.splitlines()
                if line.strip() and any(word.casefold() in line.casefold() for word in words)
            ), None)
            if evidence is not None:
                required.append(RequiredCompetency(
                    competency_id=competency.id, weight=0.9, evidence=evidence,
                ))
        return PostingAnalysisResponse(required=required, **self.metadata("posting_analysis"))

    async def experience_intake(self, request: IntakeRequest) -> IntakeResponse:
        # URL을 읽지 않는다. 실제 자료에서 얻은 성과·기간·역량을 지어내지 않는다.
        candidate = Candidate(
            key="mock-intake", title="[Mock] 자료 기반 경험 후보",
            category="PERSONAL_PROJECT", start_date=None, end_date=None,
            situation="Mock 응답입니다. 링크와 파일의 실제 내용은 분석하지 않았습니다.",
            action="", suggested_competency_ids=[], duplicate_of_experience_id=None,
            questions=[
                IntakeQuestion(field=field, q=question, why="실제 분석 전이므로 본인이 내용을 채워야 합니다.")
                for field, question in [
                    ("situation", "어떤 상황에서 시작한 경험인가요?"),
                    ("task", "맡았던 목표는 무엇인가요?"),
                    ("action", "직접 수행한 일은 무엇인가요?"),
                    ("result", "어떤 결과를 얻었나요?"),
                ]
            ],
        )
        return IntakeResponse(candidates=[candidate], **self.metadata("experience_intake"))

    async def match(self, request: MatchRequest) -> MatchResponse:
        overall, verdict, rows = compute_match(request)
        return MatchResponse(overall=overall, verdict=verdict, rows=rows, **self.metadata("match"))

    async def draft(self, request: DraftRequest) -> DraftResponse:
        parts = ["[Mock 초안 — 실제 AI 생성 결과가 아닙니다]"]
        for experience in request.experiences:
            parts.append(" ".join(filter(None, [
                experience.title, experience.situation, experience.task,
                experience.action, experience.result,
            ])))
        draft = "\n\n".join(parts)
        if request.question.length_limit is not None:
            draft = draft[:request.question.length_limit]
        return DraftResponse(draft=draft, char_count=len(draft), **self.metadata("draft"))
