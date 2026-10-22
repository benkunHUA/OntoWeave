"""Java 数据服务内部 REST 客户端。

只做 HTTP 边界：token、ownerId、路径、超时和错误码转换。业务编排在
`app.services.data_connection`，响应字段仍保持 Java 的 camelCase，由 Pydantic
schema 统一转成前端使用的 snake_case；`config` 等用户自定义 JSON 不会被改写。
"""

from __future__ import annotations

import uuid
from typing import Any
from urllib.parse import quote

import httpx

from app.config import Settings
from app.errors import (
    DataServiceUnavailableError,
    InvalidRequestError,
    InvariantViolationError,
    NotFoundError,
    UnprocessableEntityError,
)
from app.services.data_service.pipeline_client import PipelineDataServiceMixin


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


class DataServiceClient(PipelineDataServiceMixin):
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

    def create_connection(self, *, owner_id: uuid.UUID, payload: dict) -> dict:
        body = {**payload, "ownerId": str(owner_id)}
        return self._request("POST", "/internal/v1/data-connections", json=body)

    def list_connections(self, *, owner_id: uuid.UUID, limit: int, offset: int) -> dict:
        return self._request(
            "GET",
            "/internal/v1/data-connections",
            params={"ownerId": str(owner_id), "limit": limit, "offset": offset},
        )

    def get_connection(self, connection_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/data-connections/{connection_id}",
            params={"ownerId": str(owner_id)},
        )

    def update_connection(
        self,
        connection_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._request(
            "PATCH",
            f"/internal/v1/data-connections/{connection_id}",
            params={"ownerId": str(owner_id)},
            json=payload,
            operation_id=operation_id,
        )

    def test_connection(
        self,
        connection_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/data-connections/{connection_id}/test",
            params={"ownerId": str(owner_id)},
            operation_id=operation_id,
        )

    def list_tables(self, connection_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/data-connections/{connection_id}/tables",
            params={"ownerId": str(owner_id)},
        )

    def describe_table(
        self,
        connection_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        schema: str,
        table: str,
    ) -> dict:
        return self._request(
            "GET",
            (
                f"/internal/v1/data-connections/{connection_id}/tables/"
                f"{quote(schema, safe='')}/{quote(table, safe='')}"
            ),
            params={"ownerId": str(owner_id)},
        )

    def get_state(self, connection_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/data-connections/{connection_id}/state",
            params={"ownerId": str(owner_id)},
        )

    def create_sync_tasks(
        self,
        connection_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        tables: list[dict],
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/data-connections/{connection_id}/sync-tasks",
            params={"ownerId": str(owner_id)},
            json={"tables": tables},
            operation_id=operation_id,
        )

    def list_sync_tasks(self, connection_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/data-connections/{connection_id}/sync-tasks",
            params={"ownerId": str(owner_id)},
        )

    def trigger_sync(
        self,
        connection_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        operation_id: uuid.UUID,
        tables: list[dict],
    ) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/data-connections/{connection_id}/sync-runs",
            params={"ownerId": str(owner_id)},
            json={"operationId": str(operation_id), "tables": tables},
        )

    def get_sync_run(self, run_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/sync-runs/{run_id}",
            params={"ownerId": str(owner_id)},
        )

    def stop_sync_run(
        self,
        run_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/sync-runs/{run_id}/stop",
            params={"ownerId": str(owner_id)},
            operation_id=operation_id,
        )

    def get_dataset(self, dataset_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/datasets/{dataset_id}",
            params={"ownerId": str(owner_id)},
        )

    def get_dataset_versions(
        self,
        dataset_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        limit: int,
        offset: int,
    ) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/datasets/{dataset_id}/versions",
            params={"ownerId": str(owner_id), "limit": limit, "offset": offset},
        )

    def get_dataset_version(
        self,
        dataset_id: uuid.UUID,
        version_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
    ) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/datasets/{dataset_id}/versions/{version_id}",
            params={"ownerId": str(owner_id)},
        )

    def get_dataset_preview(
        self,
        dataset_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        version_id: uuid.UUID | None,
        limit: int,
        live: bool = False,
    ) -> dict:
        params: dict[str, Any] = {
            "ownerId": str(owner_id),
            "limit": limit,
            "live": "true" if live else "false",
        }
        if version_id is not None:
            params["versionId"] = str(version_id)
        return self._request(
            "GET",
            f"/internal/v1/datasets/{dataset_id}/preview",
            params=params,
        )

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
