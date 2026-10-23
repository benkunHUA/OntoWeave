"""Java 本体映射内部接口客户端。

只负责 HTTP 契约：路径、owner、幂等头、超时与 camelCase/snake_case 转换。浏览器
使用的业务聚合与错误语义在 `app.services.ontology`，两边不互相替代。
"""

from __future__ import annotations

import json
import uuid
from enum import Enum
from typing import Any

import httpx

from app.errors import (
    DataServiceUnavailableError,
    InvalidRequestError,
    NotFoundError,
    UnprocessableEntityError,
)
from app.services.data_service.client import DataServiceClient, RevisionConflict

ONTOLOGY_PREFIX = "/internal/v1/ontology"


def _snake_keys(value: Any) -> Any:
    """递归把 Java DTO 的 camelCase 键转成 Python 的 snake_case。"""
    if isinstance(value, list):
        return [_snake_keys(item) for item in value]
    if not isinstance(value, dict):
        return value
    return {_snake_key(key): _snake_keys(item) for key, item in value.items()}


def _snake_key(key: str) -> str:
    chars: list[str] = []
    for index, char in enumerate(key):
        if char.isupper() and index and (key[index - 1].islower() or key[index - 1].isdigit()):
            chars.append("_")
        chars.append(char.lower())
    return "".join(chars)


def _camel_keys(value: Any) -> Any:
    if isinstance(value, uuid.UUID):
        return str(value)
    if isinstance(value, Enum):
        return value.value
    if isinstance(value, list):
        return [_camel_keys(item) for item in value]
    if not isinstance(value, dict):
        return value
    return {_camel_key(str(key)): _camel_keys(item) for key, item in value.items()}


def _camel_key(key: str) -> str:
    head, *tail = key.split("_")
    return head + "".join(part[:1].upper() + part[1:] for part in tail)


class OntologyDataServiceClient(DataServiceClient):
    """`/internal/v1/ontology/**` 的完整客户端。"""

    def list_bindable_datasets(
        self,
        *,
        owner_id: uuid.UUID,
        keyword: str | None = None,
        page: int = 1,
        size: int = 20,
    ) -> dict:
        params: dict[str, Any] = {"ownerId": str(owner_id), "page": page, "size": size}
        if keyword:
            params["keyword"] = keyword
        return self._ontology_request("GET", "/bindable-datasets", params=params)

    def list_dataset_columns(
        self, *, owner_id: uuid.UUID, dataset_kind: str, dataset_id: uuid.UUID
    ) -> list[dict]:
        path = f"/bindable-datasets/{dataset_kind}/{dataset_id}/columns"
        return self._ontology_list(path, params={"ownerId": str(owner_id)})

    def put_relation_definition(
        self,
        relation_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        body = {**_camel_keys(payload), "ownerId": str(owner_id)}
        return self._ontology_request(
            "PUT",
            f"/relation-definitions/{relation_id}",
            params={"ownerId": str(owner_id)},
            json=body,
            operation_id=operation_id or uuid.uuid4(),
        )

    def get_object_binding(self, object_type_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._ontology_request(
            "GET",
            f"/object-types/{object_type_id}/binding",
            params={"ownerId": str(owner_id)},
        )

    def put_object_binding(
        self,
        object_type_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._put_binding(
            "object-types",
            object_type_id,
            owner_id=owner_id,
            payload=payload,
            operation_id=operation_id,
        )

    def delete_object_binding(
        self,
        object_type_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._delete_binding(
            "object-types", object_type_id, owner_id=owner_id, operation_id=operation_id
        )

    def validate_object_binding(
        self,
        object_type_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict | None = None,
    ) -> dict:
        return self._binding_action(
            "object-types", object_type_id, "validate", owner_id=owner_id, payload=payload
        )

    def preview_object_binding(
        self,
        object_type_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict | None = None,
    ) -> dict:
        return self._binding_action(
            "object-types",
            object_type_id,
            "preview",
            owner_id=owner_id,
            payload=payload,
            timeout=self._preview_timeout,
        )

    def generate_object_binding(
        self,
        object_type_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._binding_action(
            "object-types",
            object_type_id,
            "generate",
            owner_id=owner_id,
            payload=payload,
            operation_id=operation_id or uuid.uuid4(),
        )

    def list_object_runs(
        self, object_type_id: uuid.UUID, *, owner_id: uuid.UUID, limit: int = 20
    ) -> list[dict]:
        return self._run_list(f"/object-types/{object_type_id}/binding/runs", owner_id, limit)

    def get_relation_binding(self, relation_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._ontology_request(
            "GET", f"/relation-types/{relation_id}/binding", params={"ownerId": str(owner_id)}
        )

    def put_relation_binding(
        self,
        relation_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._put_binding(
            "relation-types",
            relation_id,
            owner_id=owner_id,
            payload=payload,
            operation_id=operation_id,
        )

    def delete_relation_binding(
        self,
        relation_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._delete_binding(
            "relation-types", relation_id, owner_id=owner_id, operation_id=operation_id
        )

    def preview_relation_binding(
        self,
        relation_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict | None = None,
    ) -> dict:
        return self._binding_action(
            "relation-types",
            relation_id,
            "preview",
            owner_id=owner_id,
            payload=payload,
            timeout=self._preview_timeout,
        )

    def generate_relation_binding(
        self,
        relation_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._binding_action(
            "relation-types",
            relation_id,
            "generate",
            owner_id=owner_id,
            payload=payload,
            operation_id=operation_id or uuid.uuid4(),
        )

    def list_relation_runs(
        self, relation_id: uuid.UUID, *, owner_id: uuid.UUID, limit: int = 20
    ) -> list[dict]:
        return self._run_list(f"/relation-types/{relation_id}/binding/runs", owner_id, limit)

    def get_relation_overview(self, relation_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._ontology_request(
            "GET", f"/relation-types/{relation_id}/overview", params={"ownerId": str(owner_id)}
        )

    def get_run(self, run_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._ontology_request("GET", f"/runs/{run_id}", params={"ownerId": str(owner_id)})

    def cancel_run(
        self,
        run_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._ontology_request(
            "POST",
            f"/runs/{run_id}/cancel",
            params={"ownerId": str(owner_id)},
            json={"ownerId": str(owner_id)},
            operation_id=operation_id or uuid.uuid4(),
        )

    def list_objects(
        self,
        object_type_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        page: int = 1,
        page_size: int = 20,
        q: str | None = None,
        filters: list[dict] | None = None,
    ) -> dict:
        params: dict[str, Any] = {
            "ownerId": str(owner_id),
            "page": page,
            "pageSize": page_size,
        }
        if q:
            params["q"] = q
        if filters:
            params["filters"] = json.dumps(filters, ensure_ascii=False, default=str)
        return self._ontology_request(
            "GET", f"/object-types/{object_type_id}/objects", params=params
        )

    def get_object(
        self, object_type_id: uuid.UUID, *, owner_id: uuid.UUID, primary_key: str
    ) -> dict:
        return self._ontology_request(
            "GET",
            f"/object-types/{object_type_id}/object",
            params={"ownerId": str(owner_id), "primaryKey": primary_key},
        )

    def list_object_relation_groups(
        self,
        object_type_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        primary_key: str,
        direction: str = "both",
    ) -> list[dict]:
        data = self._ontology_request(
            "GET",
            f"/object-types/{object_type_id}/object-relations",
            params={
                "ownerId": str(owner_id),
                "primaryKey": primary_key,
                "direction": direction,
            },
        )
        return _list_from(data, "groups")

    def list_object_relations(
        self,
        object_type_id: uuid.UUID,
        relation_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        primary_key: str,
        direction: str,
        page: int = 1,
        page_size: int = 20,
    ) -> dict:
        return self._ontology_request(
            "GET",
            f"/object-types/{object_type_id}/object-relations/{relation_id}",
            params={
                "ownerId": str(owner_id),
                "primaryKey": primary_key,
                "direction": direction,
                "page": page,
                "pageSize": page_size,
            },
        )

    def list_relation_instances(
        self,
        relation_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        page: int = 1,
        page_size: int = 20,
        from_primary_key: str | None = None,
        to_primary_key: str | None = None,
    ) -> dict:
        params: dict[str, Any] = {
            "ownerId": str(owner_id),
            "page": page,
            "pageSize": page_size,
        }
        if from_primary_key is not None:
            params["fromPrimaryKey"] = from_primary_key
        if to_primary_key is not None:
            params["toPrimaryKey"] = to_primary_key
        return self._ontology_request(
            "GET", f"/relation-types/{relation_id}/instances", params=params
        )

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
            json=_camel_keys(json) if json is not None else None,
            operation_id=operation_id,
            timeout=timeout,
        )
        converted = _snake_keys(data)
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
            data = _list_from(_snake_keys(data), "items", "columns")
        converted = _snake_keys(data)
        if not isinstance(converted, list) or not all(isinstance(item, dict) for item in converted):
            raise DataServiceUnavailableError("数据服务返回了非对象数组")
        return converted

    def _put_binding(
        self,
        resource: str,
        resource_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None,
    ) -> dict:
        body = {**_camel_keys(payload), "ownerId": str(owner_id)}
        return self._ontology_request(
            "PUT",
            f"/{resource}/{resource_id}/binding",
            params={"ownerId": str(owner_id)},
            json=body,
            operation_id=operation_id or uuid.uuid4(),
        )

    def _delete_binding(
        self,
        resource: str,
        resource_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        operation_id: uuid.UUID | None,
    ) -> dict:
        return self._ontology_request(
            "DELETE",
            f"/{resource}/{resource_id}/binding",
            params={"ownerId": str(owner_id)},
            operation_id=operation_id or uuid.uuid4(),
        )

    def _binding_action(
        self,
        resource: str,
        resource_id: uuid.UUID,
        action: str,
        *,
        owner_id: uuid.UUID,
        payload: dict | None,
        operation_id: uuid.UUID | None = None,
        timeout: float | None = None,
    ) -> dict:
        body = {**_camel_keys(payload or {}), "ownerId": str(owner_id)}
        return self._ontology_request(
            "POST",
            f"/{resource}/{resource_id}/binding/{action}",
            params={"ownerId": str(owner_id)},
            json=body,
            operation_id=operation_id,
            timeout=timeout,
        )

    def _run_list(self, path: str, owner_id: uuid.UUID, limit: int) -> list[dict]:
        data = super()._request_json(
            "GET",
            f"{ONTOLOGY_PREFIX}{path}",
            params={"ownerId": str(owner_id), "limit": limit},
            operation_id=None,
            empty=[],
        )
        return _list_from(_snake_keys(data), "items", "runs")

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
        for key in ("currentRevision", "revision", "currentMapping"):
            if isinstance(body, dict) and key in body and key not in details:
                details[key] = body[key]
        raise RevisionConflict(
            message or response.text or "本体映射冲突",
            code=code or "ONTOLOGY_CONFLICT",
            details=details,
        )


def _list_from(data: Any, *keys: str) -> list[dict]:
    if isinstance(data, list):
        return [item for item in data if isinstance(item, dict)]
    if isinstance(data, dict):
        for key in keys:
            items = data.get(key)
            if isinstance(items, list):
                return [item for item in items if isinstance(item, dict)]
    return []


__all__ = [
    "ONTOLOGY_PREFIX",
    "OntologyDataServiceClient",
    "InvalidRequestError",
    "NotFoundError",
    "UnprocessableEntityError",
]
