"""数据流程定时调度的 Java 内部接口。"""

from __future__ import annotations

import uuid


class PipelineScheduleDataServiceMixin:
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
        raise NotImplementedError

    def _request_list(
        self,
        method: str,
        path: str,
        *,
        params: dict | None = None,
        operation_id: uuid.UUID | None = None,
    ) -> list[dict]:
        raise NotImplementedError

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
    ) -> list[dict]:
        return self._request_list(
            "GET",
            f"/internal/v1/pipelines/{pipeline_id}/schedule/runs",
            params={"ownerId": str(owner_id), "limit": limit, "offset": offset},
        )
