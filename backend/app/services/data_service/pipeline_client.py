"""数据流程 Java 内部接口的客户端扩展。"""

from __future__ import annotations

import uuid
from typing import Any


class PipelineDataServiceMixin:
    def _request(
        self,
        method: str,
        path: str,
        *,
        params: dict | None = None,
        json: dict | None = None,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        raise NotImplementedError

    # ---- data pipeline -------------------------------------------------
    def create_pipeline(
        self,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        body = {**payload, "ownerId": str(owner_id)}
        return self._request(
            "POST",
            "/internal/v1/pipelines",
            json=body,
            operation_id=operation_id,
        )

    def list_pipelines(self, *, owner_id: uuid.UUID, limit: int, offset: int) -> dict:
        return self._request(
            "GET",
            "/internal/v1/pipelines",
            params={"ownerId": str(owner_id), "limit": limit, "offset": offset},
        )

    def get_pipeline(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/pipelines/{pipeline_id}",
            params={"ownerId": str(owner_id)},
        )

    def rename_pipeline(
        self,
        pipeline_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        name: str,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._request(
            "PATCH",
            f"/internal/v1/pipelines/{pipeline_id}",
            params={"ownerId": str(owner_id)},
            json={"name": name},
            operation_id=operation_id,
        )

    def archive_pipeline(
        self,
        pipeline_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/pipelines/{pipeline_id}/archive",
            params={"ownerId": str(owner_id)},
            operation_id=operation_id,
        )

    def copy_pipeline(
        self,
        pipeline_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        body = {**payload, "ownerId": str(owner_id)}
        return self._request(
            "POST",
            f"/internal/v1/pipelines/{pipeline_id}/copy",
            json=body,
            operation_id=operation_id,
        )

    def get_draft(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/pipelines/{pipeline_id}/draft",
            params={"ownerId": str(owner_id)},
        )

    def apply_draft_command(
        self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, payload: dict
    ) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/pipelines/{pipeline_id}/draft/commands",
            params={"ownerId": str(owner_id)},
            json=payload,
        )

    def move_node(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, payload: dict) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/pipelines/{pipeline_id}/draft/positions",
            params={"ownerId": str(owner_id)},
            json=payload,
        )

    def check_pipeline(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, revision: int) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/pipelines/{pipeline_id}/check",
            params={"ownerId": str(owner_id), "revision": revision},
        )

    def undo_turn(
        self,
        pipeline_id: uuid.UUID,
        turn_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        revision: int,
    ) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/pipelines/{pipeline_id}/turns/{turn_id}/undo",
            params={"ownerId": str(owner_id)},
            json={"currentRevision": revision},
        )

    def get_node_catalog(self) -> dict:
        return self._request("GET", "/internal/v1/pipeline-node-catalog")

    def list_input_datasets(self, *, owner_id: uuid.UUID, limit: int, offset: int) -> dict:
        return self._request(
            "GET",
            "/internal/v1/datasets",
            params={"ownerId": str(owner_id), "limit": limit, "offset": offset},
        )

    def get_dataset_schema(self, dataset_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/datasets/{dataset_id}",
            params={"ownerId": str(owner_id)},
        )

    def preview_node(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, payload: dict) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/pipelines/{pipeline_id}/previews",
            params={"ownerId": str(owner_id)},
            json=payload,
        )

    def create_build(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, payload: dict) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/pipelines/{pipeline_id}/builds",
            params={"ownerId": str(owner_id)},
            json=payload,
        )

    def get_build(
        self, pipeline_id: uuid.UUID, build_id: uuid.UUID, *, owner_id: uuid.UUID
    ) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/pipelines/{pipeline_id}/builds/{build_id}",
            params={"ownerId": str(owner_id)},
        )

    def cancel_build(
        self,
        pipeline_id: uuid.UUID,
        build_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/pipelines/{pipeline_id}/builds/{build_id}/cancel",
            params={"ownerId": str(owner_id)},
            operation_id=operation_id,
        )

    def get_output_sources(
        self,
        pipeline_id: uuid.UUID,
        dataset_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        version_id: uuid.UUID | None = None,
    ) -> dict:
        params: dict[str, Any] = {"ownerId": str(owner_id)}
        if version_id is not None:
            params["versionId"] = str(version_id)
        return self._request(
            "GET",
            f"/internal/v1/pipelines/{pipeline_id}/datasets/{dataset_id}/sources",
            params=params,
        )

    def get_schedule(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/pipelines/{pipeline_id}/schedule",
            params={"ownerId": str(owner_id)},
        )

    def put_schedule(
        self,
        pipeline_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        payload: dict,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._request(
            "PUT",
            f"/internal/v1/pipelines/{pipeline_id}/schedule",
            params={"ownerId": str(owner_id)},
            json=payload,
            operation_id=operation_id,
        )

    def pause_schedule(
        self,
        pipeline_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/pipelines/{pipeline_id}/schedule/pause",
            params={"ownerId": str(owner_id)},
            operation_id=operation_id,
        )

    def resume_schedule(
        self,
        pipeline_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        operation_id: uuid.UUID | None = None,
    ) -> dict:
        return self._request(
            "POST",
            f"/internal/v1/pipelines/{pipeline_id}/schedule/resume",
            params={"ownerId": str(owner_id)},
            operation_id=operation_id,
        )

    def list_schedule_runs(
        self,
        pipeline_id: uuid.UUID,
        *,
        owner_id: uuid.UUID,
        limit: int,
        offset: int,
    ) -> dict:
        return self._request(
            "GET",
            f"/internal/v1/pipelines/{pipeline_id}/schedule/runs",
            params={"ownerId": str(owner_id), "limit": limit, "offset": offset},
        )
