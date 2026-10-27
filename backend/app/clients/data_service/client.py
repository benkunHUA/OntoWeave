"""Java 数据服务内部 REST 客户端。

只做 HTTP 边界：token、ownerId、路径、超时和错误码转换。业务编排在
`app.services.data_connection`，响应字段仍保持 Java 的 camelCase，由 Pydantic
schema 统一转成前端使用的 snake_case；`config` 等用户自定义 JSON 不会被改写。
"""

from __future__ import annotations

import uuid
from typing import Any
from urllib.parse import quote

from app.clients.data_service.pipeline_client import PipelineDataServiceMixin
from app.clients.data_service.pipeline_schedule_client import PipelineScheduleDataServiceMixin
from app.clients.data_service.transport import DataServiceTransport, RevisionConflict


class DataServiceClient(
    DataServiceTransport,
    PipelineDataServiceMixin,
    PipelineScheduleDataServiceMixin,
):
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


__all__ = ["DataServiceClient", "RevisionConflict"]
