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
    request_timeout_seconds: float = float(
        os.getenv("AI_REQUEST_TIMEOUT_SECONDS", "30")
    )


settings = Settings()
