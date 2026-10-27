"""Java 数据服务的 HTTP 传输与错误映射。"""

from __future__ import annotations

import uuid
from typing import Any

import httpx

from app.config import Settings
from app.errors import (
    DataServiceUnavailableError,
    InvalidRequestError,
    InvariantViolationError,
    NotFoundError,
    UnprocessableEntityError,
)


class RevisionConflict(InvariantViolationError):
    """草稿 revision 已被其他操作推进；Agent 应重新读取而不是重试旧写入。"""

    def __init__(
        self,
        message: str,
        *,
        code: str = "REVISION_CONFLICT",
        details: dict[str, object] | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.details = details or {}


class DataServiceTransport:
    def __init__(
        self,
        settings: Settings,
        *,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self._base_url = settings.data_service_url.rstrip("/")
        self._token = settings.data_service_token
        self._timeout = settings.data_service_timeout_seconds
        self._preview_timeout = settings.data_service_preview_timeout_seconds
        self._transport = transport

    def _request(
        self,
        method: str,
        path: str,
        *,
        params: dict | None = None,
        json: dict | None = None,
        operation_id: uuid.UUID | None = None,
        timeout: float | None = None,
    ) -> dict:
        data = self._request_json(
            method,
            path,
            params=params,
            json=json,
            operation_id=operation_id,
            timeout=timeout,
            empty={},
        )
        if not isinstance(data, dict):
            raise DataServiceUnavailableError("数据服务返回了非对象响应")
        return data

    def _request_list(
        self,
        method: str,
        path: str,
        *,
        params: dict | None = None,
        operation_id: uuid.UUID | None = None,
        timeout: float | None = None,
    ) -> list[dict]:
        data = self._request_json(
            method,
            path,
            params=params,
            operation_id=operation_id,
            timeout=timeout,
            empty=[],
        )
        if not isinstance(data, list):
            raise DataServiceUnavailableError("数据服务返回了非数组响应")
        if not all(isinstance(item, dict) for item in data):
            raise DataServiceUnavailableError("数据服务返回了非对象数组项")
        return data

    def _request_json(
        self,
        method: str,
        path: str,
        *,
        params: dict | None,
        json: dict | None = None,
        operation_id: uuid.UUID | None,
        timeout: float | None = None,
        empty: Any,
    ) -> Any:
        headers = {"X-Internal-Token": self._token}
        if operation_id is not None:
            headers["X-Operation-Id"] = str(operation_id)
        try:
            response = self._send(
                method,
                f"{self._base_url}{path}",
                params=params,
                json=json,
                headers=headers,
                timeout=self._timeout if timeout is None else timeout,
            )
        except httpx.HTTPError as exc:
            raise DataServiceUnavailableError(f"数据服务不可用：{exc}") from exc

        if response.status_code >= 400:
            self._raise_error(response)
        if not response.content:
            return empty
        try:
            return response.json()
        except ValueError as exc:
            raise DataServiceUnavailableError("数据服务返回了非 JSON 响应") from exc

    def _send(self, method: str, url: str, **kwargs: Any) -> httpx.Response:
        if self._transport is None:
            return httpx.request(method, url, **kwargs)
        with httpx.Client(transport=self._transport, timeout=self._timeout) as client:
            return client.request(method, url, **kwargs)

    @staticmethod
    def _raise_error(response: httpx.Response) -> None:
        try:
            body: Any = response.json()
        except ValueError:
            body = {}
        code = body.get("code") if isinstance(body, dict) else None
        message = body.get("message") if isinstance(body, dict) else None
        details = body.get("details") if isinstance(body, dict) else None
        if not isinstance(details, dict):
            details = {}
        issues = body.get("issues") if isinstance(body, dict) else None
        if issues is not None and "issues" not in details:
            details = {**details, "issues": issues}
        text = message or response.text or "数据服务请求失败"
        suffix = f"（{code}）" if code else ""
        if response.status_code == 404:
            raise NotFoundError(f"{text}{suffix}")
        if response.status_code == 400:
            raise InvalidRequestError(f"{text}{suffix}")
        if response.status_code == 409:
            if code == "REVISION_CONFLICT":
                raise RevisionConflict(f"{text}{suffix}", details=details)
            raise InvariantViolationError(f"{text}{suffix}")
        if response.status_code == 422:
            raise UnprocessableEntityError(
                f"{text}{suffix}",
                code=code,
                details=details,
            )
        raise DataServiceUnavailableError(f"{text}{suffix}")
