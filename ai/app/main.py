from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.extract import router as extract_router
from app.core.config import settings
from app.services.anthropic_client import AnthropicExtractionClient


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.extraction_client = AnthropicExtractionClient(settings)
    yield
    await app.state.extraction_client.close()


app = FastAPI(
    title="CareerFit AI Server",
    version="0.1.0",
    lifespan=lifespan,
)
app.include_router(extract_router)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
