import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


load_dotenv(Path(__file__).resolve().parents[2] / ".env")


@dataclass(frozen=True)
class Settings:
    anthropic_api_key: str = os.getenv("ANTHROPIC_API_KEY", "")
    model: str = os.getenv("AI_MODEL", "claude-opus-5")
    internal_token: str = os.getenv("AI_INTERNAL_TOKEN", "")
    # 한 호출의 상한. 인테이크는 web_fetch 로 저장소를 여러 번 읽어 몇 분이 걸릴 수 있다 — 30초면 늘 끊긴다.
    # Spring 의 careerfit.ai.read-timeout 도 이보다 길어야 한다.
    request_timeout_seconds: float = float(
        os.getenv("AI_REQUEST_TIMEOUT_SECONDS", "300")
    )


settings = Settings()
