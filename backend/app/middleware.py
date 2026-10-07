import logging
import time
import uuid

from starlette.types import ASGIApp, Message, Receive, Scope, Send

from app.logging_config import request_id_var

logger = logging.getLogger("app.request")


class RequestContextMiddleware:
    """给每个请求分配 request id、写一条访问日志，并把 id 放进日志上下文。

    用纯 ASGI 中间件而不是 BaseHTTPMiddleware，是为了让 contextvar 的传递
    行为保持可预期。
    """

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        incoming = dict(scope["headers"]).get(b"x-request-id")
        request_id = (incoming or b"").decode() or uuid.uuid4().hex
        token = request_id_var.set(request_id)
        status = {"code": 500}
        started = time.perf_counter()

        async def send_with_request_id(message: Message) -> None:
            if message["type"] == "http.response.start":
                status["code"] = message["status"]
                headers = message.setdefault("headers", [])
                headers.append((b"x-request-id", request_id.encode()))
            await send(message)

        try:
            await self.app(scope, receive, send_with_request_id)
        finally:
            duration_ms = (time.perf_counter() - started) * 1000
            logger.info(
                "%s %s -> %s (%.1f ms)",
                scope.get("method"),
                scope.get("path"),
                status["code"],
                duration_ms,
            )
            request_id_var.reset(token)
