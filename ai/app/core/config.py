import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


load_dotenv(Path(__file__).resolve().parents[2] / ".env")


@dataclass(frozen=True)
class Settings:
    anthropic_api_key: str = os.getenv("ANTHROPIC_API_KEY", "")
    # 키가 있어도 Mock 으로 띄운다. 테스트(tests/conftest.py)와 "키는 두되 오늘은 안 쓰겠다" 용.
    force_mock: bool = os.getenv("AI_FORCE_MOCK", "") == "1"
    # 기본 모델. 아래 기능별 값이 비어 있을 때 쓴다.
    model: str = os.getenv("AI_MODEL", "claude-opus-5")
    # 기능별 모델 — 일의 무게가 다르다.
    #   공고 분석: 사전 안에서 고르고 근거 문장을 옮겨 적는 일. 가벼운 모델로 충분하다.
    #   인테이크: web_fetch 로 자료를 읽어 후보를 뽑는다. 토큰 대부분이 읽은 자료라 모델 단가가 그대로 비용이다.
    #   초안: 글 품질이 곧 결과다. 기본 모델(Opus)을 그대로 둔다.
    model_posting_analysis: str = os.getenv("AI_MODEL_POSTING_ANALYSIS") or "claude-sonnet-5"
    model_experience_intake: str = os.getenv("AI_MODEL_EXPERIENCE_INTAKE") or "claude-sonnet-5"
    model_draft: str = os.getenv("AI_MODEL_DRAFT") or os.getenv("AI_MODEL", "claude-opus-5")
    # 기능별 effort(low·medium·high·xhigh·max). 모델을 바꾸기 전에 먼저 만질 손잡이 — 같은 모델에서 생각 깊이만 줄인다.
    effort_posting_analysis: str = os.getenv("AI_EFFORT_POSTING_ANALYSIS") or "medium"
    effort_experience_intake: str = os.getenv("AI_EFFORT_EXPERIENCE_INTAKE") or "high"
    effort_draft: str = os.getenv("AI_EFFORT_DRAFT") or "high"
    # 인테이크 advisor — 실행 모델(Sonnet)이 자료를 읽고, 후보를 나누거나 합칠지·역량 태깅이 애매할 때만 무거운 모델에게
    # 묻는다. advisor 는 실행 모델의 맥락을 그대로 받아 판단만 하고 자료를 직접 읽지 않는다. 짝은 정해져 있다 —
    # Sonnet 5 실행 → advisor 는 Opus 5 이상(아니면 400). max_tokens 는 1024 미만이면 400.
    advisor_experience_intake: bool = os.getenv("AI_ADVISOR_EXPERIENCE_INTAKE", "1") == "1"
    advisor_model: str = os.getenv("AI_ADVISOR_MODEL") or "claude-opus-5"
    advisor_max_uses: int = int(os.getenv("AI_ADVISOR_MAX_USES") or "2")
    advisor_max_tokens: int = int(os.getenv("AI_ADVISOR_MAX_TOKENS") or "4096")
    # 프롬프트 캐시 TTL("5m" 또는 "1h"). 시스템 프롬프트와 역량 사전은 호출마다 같아서 캐시한다.
    # 작업은 사용자가 띄엄띄엄 만든다 — 5분 안에 다음 호출이 온다고 볼 수 없어 1h 가 기본이다.
    # 쓰기 비용은 1h 가 2배(5m 는 1.25배), 읽기는 둘 다 0.1배. 5분 안에 계속 오는 트래픽이면 5m 이 싸다.
    cache_ttl: str = os.getenv("AI_CACHE_TTL") or "1h"
    internal_token: str = os.getenv("AI_INTERNAL_TOKEN", "")
    # 한 호출의 상한. 인테이크는 web_fetch 로 저장소를 여러 번 읽어 몇 분이 걸릴 수 있다 — 30초면 늘 끊긴다.
    # Spring 의 careerlab.ai.read-timeout 도 이보다 길어야 한다.
    request_timeout_seconds: float = float(
        os.getenv("AI_REQUEST_TIMEOUT_SECONDS", "300")
    )


settings = Settings()
