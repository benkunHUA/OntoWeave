"""数据整理测试假客户端的流程 CRUD 部分。"""

import uuid

from tests.support.pipeline_constants import _pipeline


class PipelineCrudFake:
    def __init__(self) -> None:
        self.calls: list[tuple[str, dict]] = []

    def _record(self, call_name: str, **kwargs):  # noqa: ANN003
        self.calls.append((call_name, kwargs))
        return kwargs

    def create_pipeline(self, *, owner_id, payload, operation_id=None):  # noqa: ANN001
        self._record("create", owner_id=owner_id, payload=payload, operation_id=operation_id)
        return _pipeline(payload.get("name", "订单清洗"))

    def list_pipelines(self, *, owner_id, limit, offset):  # noqa: ANN001
        self._record("list", owner_id=owner_id, limit=limit, offset=offset)
        return {
            "items": [_pipeline("A"), _pipeline("B")],
            "total": 2,
            "limit": limit,
            "offset": offset,
        }

    def get_pipeline(self, pipeline_id, *, owner_id):  # noqa: ANN001
        self._record("get", pipeline_id=pipeline_id, owner_id=owner_id)
        return _pipeline()

    def rename_pipeline(self, pipeline_id, *, owner_id, name, operation_id=None):  # noqa: ANN001
        self._record("rename", pipeline_id=pipeline_id, owner_id=owner_id, name=name)
        return _pipeline(name)

    def archive_pipeline(self, pipeline_id, *, owner_id, operation_id=None):  # noqa: ANN001
        self._record("archive", pipeline_id=pipeline_id, owner_id=owner_id)
        return {}

    def copy_pipeline(self, pipeline_id, *, owner_id, payload, operation_id=None):  # noqa: ANN001
        self._record("copy", pipeline_id=pipeline_id, owner_id=owner_id, payload=payload)
        return {**_pipeline(payload.get("name", "副本")), "id": str(uuid.uuid4())}

    def get_draft(self, pipeline_id, *, owner_id):  # noqa: ANN001
        self._record("draft", pipeline_id=pipeline_id, owner_id=owner_id)
        return {"pipelineId": str(pipeline_id), "revision": 3, "definition": {"nodes": []}}

    def apply_draft_command(self, pipeline_id, *, owner_id, payload):  # noqa: ANN001
        self._record("command", pipeline_id=pipeline_id, owner_id=owner_id, payload=payload)
        return {"pipelineId": str(pipeline_id), "revision": 4, "definition": {"nodes": []}}

    def move_node(self, pipeline_id, *, owner_id, payload):  # noqa: ANN001
        self._record("move", pipeline_id=pipeline_id, owner_id=owner_id, payload=payload)
        return {"pipelineId": str(pipeline_id), "revision": 5, "definition": {"nodes": []}}

    def check_pipeline(self, pipeline_id, *, owner_id, revision):  # noqa: ANN001
        self._record("check", pipeline_id=pipeline_id, owner_id=owner_id, revision=revision)
        return {
            "pipelineId": str(pipeline_id),
            "revision": revision,
            "stage": "draft",
            "blockingIssues": [
                {
                    "code": "NODE_CONFIG_INCOMPLETE",
                    "phase": "config",
                    "blocking": True,
                    "nodeId": None,
                    "portId": None,
                    "field": "name",
                    "message": "缺少名称",
                    "hint": None,
                }
            ],
            "warnings": [],
            "nextActions": [{"action": "CONFIGURE_NODE", "nodeId": None}],
        }

    def undo_turn(self, pipeline_id, turn_id, *, owner_id, revision):  # noqa: ANN001
        self._record("undo", pipeline_id=pipeline_id, turn_id=turn_id, revision=revision)
        return {"pipelineId": str(pipeline_id), "revision": 2, "definition": {"nodes": []}}
