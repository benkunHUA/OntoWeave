"""本体 Java 客户端的公共请求与错误转换。"""

from __future__ import annotations

import uuid
from typing import Any

import httpx

from app.clients.data_service.client import DataServiceClient, RevisionConflict
from app.clients.data_service.ontology_client.codec import camel_keys, list_from, snake_keys
from app.errors import DataServiceUnavailableError

ONTOLOGY_PREFIX = "/internal/v1/ontology"


class OntologyClientBase(DataServiceClient):
    def _ontology_request(
        self,
        method: str,
        path: str,
        *,
        params: dict | None = None,
        json: dict | None = None,
        operation_id: uuid.UUID | None = None,
        timeout: float | None = None,
    ) -> dict:
        data = super()._request(
            method,
            f"{ONTOLOGY_PREFIX}{path}",
            params=params,
            json=camel_keys(json) if json is not None else None,
            operation_id=operation_id,
            timeout=timeout,
        )
        converted = snake_keys(data)
        if not isinstance(converted, dict):
            raise DataServiceUnavailableError("数据服务返回了非对象响应")
        return converted

    def _ontology_list(self, path: str, *, params: dict) -> list[dict]:
        data = super()._request_json(
            "GET",
            f"{ONTOLOGY_PREFIX}{path}",
            params=params,
            operation_id=None,
            empty=[],
        )
        if isinstance(data, dict):
            data = list_from(snake_keys(data), "items", "columns")
        converted = snake_keys(data)
        if not isinstance(converted, list) or not all(isinstance(item, dict) for item in converted):
            raise DataServiceUnavailableError("数据服务返回了非对象数组")
        return converted

    @staticmethod
    def _raise_error(response: httpx.Response) -> None:
        if response.status_code != 409:
            DataServiceClient._raise_error(response)
            return
        try:
            body: Any = response.json()
        except ValueError:
            body = {}
        code = body.get("code") if isinstance(body, dict) else None
        message = body.get("message") if isinstance(body, dict) else None
        details = body.get("details") if isinstance(body, dict) else None
        if not isinstance(details, dict):
            details = {}
        details = dict(details)
        for key in ("currentRevision", "revision", "draft", "currentMapping"):
            if isinstance(body, dict) and key in body and key not in details:
                details[key] = body[key]
        if "draft" in details and "currentMapping" not in details:
            details["currentMapping"] = details["draft"]
        if "revision" in details and "currentRevision" not in details:
            details["currentRevision"] = details["revision"]
        raise RevisionConflict(
            message or response.text or "本体映射冲突",
            code=code or "ONTOLOGY_CONFLICT",
            details=details,
        )


__all__ = ["ONTOLOGY_PREFIX", "OntologyClientBase"]
