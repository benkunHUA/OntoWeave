import logging
from collections.abc import Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app.api.router import api_router
from app.config import get_settings
from app.db import build_engine, build_session_factory
from app.errors import (
    DataServiceUnavailableError,
    InvalidRequestError,
    InvalidTransitionError,
    InvariantViolationError,
    NotFoundError,
    UnprocessableEntityError,
)
from app.logging_config import configure_logging
from app.middleware import RequestContextMiddleware
from app.services.event_bus import ConversationEventBus

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

    @asynccontextmanager
    async def lifespan(application: FastAPI):
        settings = get_settings()
        consumer = None
        if settings.event_consumer_enabled:
            from app.messaging import DataEventConsumer

            consumer = DataEventConsumer(
                settings, application.state.session_factory, application.state.event_bus
            )
            consumer.start()
        application.state.event_consumer = consumer
        yield
        if consumer is not None:
            consumer.stop()
        # 检查点的连接池是懒建的，关的时候也要顺手关掉，不然进程退出会漏连接。
        pool = application.state.checkpointer_pool
        if pool is not None:
            pool.close()
            application.state.checkpointer_pool = None
        # Langfuse 是后台上报，退出前 flush 一次，别丢掉最后几条 trace。
        if get_settings().langfuse_configured:
            try:
                from langfuse import get_client

                get_client().flush()
            except Exception:  # pragma: no cover - 追踪失败不影响关停
                logger.warning("langfuse flush on shutdown failed", exc_info=True)

    app = FastAPI(title="OntoWeave", version="0.1.0", lifespan=lifespan)
    app.state.session_factory = session_factory
    # 检查点懒建：第一次真的跑智能体时才连库，避免导入期就建立连接。
    app.state.checkpointer = None
    app.state.checkpointer_pool = None
    app.state.enable_agent_checkpoints = enable_agent_checkpoints
    app.state.event_bus = ConversationEventBus()
    app.add_middleware(RequestContextMiddleware)
    app.include_router(api_router)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.exception_handler(NotFoundError)
    async def _not_found(_: Request, exc: NotFoundError) -> JSONResponse:
        logger.warning("not found: %s", exc)
        return JSONResponse(status_code=404, content={"error": "not_found", "message": str(exc)})

    @app.exception_handler(InvalidRequestError)
    async def _bad_request(_: Request, exc: InvalidRequestError) -> JSONResponse:
        logger.warning("invalid request: %s", exc)
        return JSONResponse(
            status_code=400,
            content={"error": "invalid_request", "message": str(exc)},
        )

    @app.exception_handler(DataServiceUnavailableError)
    async def _data_service_unavailable(
        _: Request, exc: DataServiceUnavailableError
    ) -> JSONResponse:
        logger.warning("data service unavailable: %s", exc)
        return JSONResponse(
            status_code=503,
            content={"error": "data_service_unavailable", "message": str(exc)},
        )

    @app.exception_handler(UnprocessableEntityError)
    async def _unprocessable(_: Request, exc: UnprocessableEntityError) -> JSONResponse:
        logger.warning("data service rejected request: %s", exc)
        content: dict[str, object] = {
            "error": "unprocessable_entity",
            "message": str(exc),
        }
        if exc.code is not None:
            content["code"] = exc.code
        if exc.details:
            content["details"] = exc.details
        return JSONResponse(status_code=422, content=content)

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
