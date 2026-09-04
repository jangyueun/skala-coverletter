"""매칭 계산 — 결정론 공식. Mock 과 Claude 제공자가 같이 쓴다.

LLM 을 안 쓰는 이유: 점수는 경험 강도와 공고 가중치의 산술이고, 프론트(domain/matching.js)가 카드에서 같은 식을
브라우저로 돌린다. 여기가 다른 답을 내면 목록 카드와 매칭 탭이 서로 다른 숫자를 말한다.

  score   = min(1, Σ strength)                     같은 역량을 여러 경험이 뒷받침하면 오르되 1 을 넘지 않는다
  overall = Σ(weight × score) / Σ weight            무겁게 요구되는 것을 못 채우면 크게 깎인다
  isGap   = 근거 없음 또는 score < 0.45
  verdict = ≥0.85 RECOMMEND · ≥0.62 CONDITIONAL · 그 외 HOLD   (Spring MatchVerdict 와 같은 경계)
"""

from app.schemas.provider import MatchRequest, MatchRow

GAP_BELOW = 0.45
RECOMMEND_FROM = 0.85
CONDITIONAL_FROM = 0.62


def compute_match(request: MatchRequest) -> tuple[float, str, list[MatchRow]]:
    rows = []
    for requirement in request.posting.required:
        evidence = [
            (experience.id, competency.strength)
            for experience in request.experiences
            for competency in experience.competencies
            if competency.competency_id == requirement.competency_id
        ]
        score = min(1.0, sum(strength for _, strength in evidence))
        rows.append(MatchRow(
            competency_id=requirement.competency_id, weight=requirement.weight,
            score=score, is_gap=not evidence or score < GAP_BELOW,
            experience_ids=[experience_id for experience_id, _ in evidence],
        ))
    total_weight = sum(row.weight for row in rows)
    overall = min(1.0, sum(row.weight * row.score for row in rows) / total_weight) if total_weight else 0.0
    verdict = "RECOMMEND" if overall >= RECOMMEND_FROM else "CONDITIONAL" if overall >= CONDITIONAL_FROM else "HOLD"
    return overall, verdict, rows
