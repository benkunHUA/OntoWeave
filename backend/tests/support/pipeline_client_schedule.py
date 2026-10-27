"""数据整理测试假客户端的调度部分。"""

import uuid

from tests.support.pipeline_constants import BUILD_ID, NOW, PIPELINE_ID


class PipelineScheduleFake:
    def get_schedule(self, pipeline_id, *, owner_id):  # noqa: ANN001
        self._record("schedule-get", pipeline_id=pipeline_id)
        return self._schedule()

    def put_schedule(self, pipeline_id, *, owner_id, payload, operation_id=None):  # noqa: ANN001
        self._record("schedule-put", pipeline_id=pipeline_id, payload=payload)
        return self._schedule()

    def pause_schedule(self, pipeline_id, *, owner_id, operation_id=None):  # noqa: ANN001
        self._record("schedule-pause", pipeline_id=pipeline_id)
        return {**self._schedule(), "status": "paused", "nextRunAt": None}

    def resume_schedule(self, pipeline_id, *, owner_id, operation_id=None):  # noqa: ANN001
        self._record("schedule-resume", pipeline_id=pipeline_id)
        return self._schedule()

    def list_schedule_runs(self, pipeline_id, *, owner_id, limit, offset):  # noqa: ANN001
        self._record("schedule-runs", pipeline_id=pipeline_id, limit=limit, offset=offset)
        return [
            {
                "id": str(uuid.uuid4()),
                "scheduleId": str(uuid.uuid4()),
                "pipelineId": str(pipeline_id),
                "status": "dispatched",
                "triggerType": "scheduled",
                "scheduledFor": NOW,
                "buildId": str(BUILD_ID),
                "logicHash": "hash",
                "reasonCode": None,
                "message": None,
                "startedAt": NOW,
                "finishedAt": NOW,
            }
        ]

    def _schedule(self) -> dict:
        return {
            "id": str(uuid.uuid4()),
            "pipelineId": str(PIPELINE_ID),
            "name": "每日",
            "status": "active",
            "triggerType": "scheduled",
            "cronExpression": "0 2 * * *",
            "timezone": "Asia/Shanghai",
            "nextRunAt": NOW,
            "lastRunAt": None,
            "pausedAt": None,
        }
