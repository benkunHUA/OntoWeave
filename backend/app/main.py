from collections.abc import Callable

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app.api.routes import router
from app.config import get_settings
from app.db import build_engine, build_session_factory
from app.errors import InvariantViolationError, InvalidTransitionError, NotFoundError

SessionFactory = Callable[[], Session]


def create_app(session_factory: SessionFactory | None = None) -> FastAPI:
    """装配应用。session_factory 可注入，测试与生产用同一套装配路径。"""
    if session_factory is None:
        session_factory = build_session_factory(
            build_engine(get_settings().database_url)
        )

    app = FastAPI(title="OntoWeave", version="0.1.0")
    app.state.session_factory = session_factory
    app.include_router(router)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.exception_handler(NotFoundError)
    async def _not_found(_: Request, exc: NotFoundError) -> JSONResponse:
        return JSONResponse(
            status_code=404, content={"error": "not_found", "message": str(exc)}
        )

    @app.exception_handler(InvalidTransitionError)
    async def _invalid(_: Request, exc: InvalidTransitionError) -> JSONResponse:
        return JSONResponse(
            status_code=409,
            content={"error": "invalid_transition", "message": str(exc)},
        )

    @app.exception_handler(InvariantViolationError)
    async def _invariant(_: Request, exc: InvariantViolationError) -> JSONResponse:
        return JSONResponse(
            status_code=409,
            content={"error": "invariant_violation", "message": str(exc)},
        )

    return app


app = create_app()
