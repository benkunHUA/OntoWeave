"""对象、关系查询与运行记录接口。"""

from __future__ import annotations

import json
import uuid
from collections.abc import Callable
from typing import Any

from app.services.data_service.ontology_client.codec import list_from


class QueryClientMixin:
    _ontology_request: Callable[..., dict]

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
        return list_from(data, "groups")

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


__all__ = ["QueryClientMixin"]
