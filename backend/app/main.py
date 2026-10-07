import logging
from collections.abc import Callable

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app.api.router import api_router
from app.config import get_settings
from app.db import build_engine, build_session_factory
from app.errors import InvalidTransitionError, InvariantViolationError, NotFoundError
from app.logging_config import configure_logging
from app.middleware import RequestContextMiddleware

SessionFactory = Callable[[], Session]

logger = logging.getLogger("app")


def create_app(session_factory: SessionFactory | None = None) -> FastAPI:
    """装配应用。session_factory 可注入，测试与生产用同一套装配路径。"""
    return _create_app(session_factory, enable_agent_checkpoints=True)


def _create_app(
    session_factory: SessionFactory | None, *, enable_agent_checkpoints: bool
) -> FastAPI:
    if session_factory is None:
        session_factory = build_session_factory(build_engine(get_settings().database_url))

    configure_logging()

    app = FastAPI(title="OntoWeave", version="0.1.0")
    app.state.session_factory = session_factory
    # 检查点懒建：第一次真的跑智能体时才连库，避免导入期就建立连接。
    app.state.checkpointer = None
    app.state.enable_agent_checkpoints = enable_agent_checkpoints
    app.add_middleware(RequestContextMiddleware)
    app.include_router(api_router)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.exception_handler(NotFoundError)
    async def _not_found(_: Request, exc: NotFoundError) -> JSONResponse:
        logger.warning("not found: %s", exc)
        return JSONResponse(status_code=404, content={"error": "not_found", "message": str(exc)})

    @app.exception_handler(InvalidTransitionError)
    async def _invalid(_: Request, exc: InvalidTransitionError) -> JSONResponse:
        logger.warning("invalid transition: %s", exc)
        return JSONResponse(
            status_code=409,
            content={"error": "invalid_transition", "message": str(exc)},
        )

    @app.exception_handler(InvariantViolationError)
    async def _invariant(_: Request, exc: InvariantViolationError) -> JSONResponse:
        logger.warning("invariant violation: %s", exc)
        return JSONResponse(
            status_code=409,
            content={"error": "invariant_violation", "message": str(exc)},
        )

    return app


app = create_app()
