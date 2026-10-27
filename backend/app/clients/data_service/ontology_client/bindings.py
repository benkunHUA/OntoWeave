"""对象/关系绑定、校验、预览、生成与运行列表接口。"""

from __future__ import annotations

import uuid
from collections.abc import Callable
from typing import Any

from app.clients.data_service.ontology_client.base import ONTOLOGY_PREFIX
from app.clients.data_service.ontology_client.codec import camel_keys, list_from, snake_keys


class BindingClientMixin:
    _preview_timeout: float
    _ontology_request: Callable[..., dict]
    _request_json: Callable[..., Any]

    def put_relation_definition(
        self,
        relation_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        body = {**camel_keys(payload), "ownerId": str(owner_id)}
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

    def _put_binding(
        self,
        resource: str,
        resource_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None,
    ) -> dict:
        body = {**camel_keys(payload), "ownerId": str(owner_id)}
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
        body = {**camel_keys(payload or {}), "ownerId": str(owner_id)}
        return self._ontology_request(
            "POST",
            f"/{resource}/{resource_id}/binding/{action}",
            params={"ownerId": str(owner_id)},
            json=body,
            operation_id=operation_id,
            timeout=timeout,
        )

    def _run_list(self, path: str, owner_id: uuid.UUID, limit: int) -> list[dict]:
        data = self._request_json(
            "GET",
            f"{ONTOLOGY_PREFIX}{path}",
            params={"ownerId": str(owner_id), "limit": limit},
            operation_id=None,
            empty=[],
        )
        return list_from(snake_keys(data), "items", "runs")


__all__ = ["BindingClientMixin"]
