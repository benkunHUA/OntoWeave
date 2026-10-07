from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api.routes import router
from app.errors import InvariantViolationError, InvalidTransitionError, NotFoundError


def create_app() -> FastAPI:
    app = FastAPI(title="OntoWeave", version="0.1.0")
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
