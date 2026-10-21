import uuid

from app.config import DEFAULT_USER_ID

API = "/api/v1/pipelines"
OWNER_ID = uuid.UUID(DEFAULT_USER_ID)
PIPELINE_ID = uuid.UUID("40000000-0000-4000-8000-000000000001")
CONVERSATION_ID = uuid.UUID("40000000-0000-4000-8000-000000000002")
BUILD_ID = uuid.UUID("40000000-0000-4000-8000-000000000003")
DATASET_ID = uuid.UUID("40000000-0000-4000-8000-000000000004")
TURN_ID = uuid.UUID("40000000-0000-4000-8000-000000000005")
NOW = "2026-10-18T00:00:00Z"


def _pipeline(name: str = "订单清洗") -> dict:
    return {
        "id": str(PIPELINE_ID),
        "conversationId": str(CONVERSATION_ID),
        "name": name,
        "status": "draft",
        "draftRevision": 3,
        "currentVersionId": None,
        "currentVersionNumber": None,
        "currentVersionDraftRevision": None,
        "createdAt": NOW,
        "updatedAt": NOW,
    }


class FakePipelineClient:
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

    def get_node_catalog(self) -> dict:
        return {
            "catalogVersion": "v1",
            "nodes": [
                {
                    "type": "input",
                    "version": 1,
                    "label": "输入",
                    "category": "source",
                    "ports": [
                        {
                            "id": "out",
                            "role": "output",
                            "dataType": "table",
                            "required": True,
                            "multiple": False,
                        }
                    ],
                    "configSchema": {"type": "object"},
                    "uiSchema": {},
                }
            ],
        }

    def list_input_datasets(self, *, owner_id, limit, offset):  # noqa: ANN001
        self._record("input-datasets", owner_id=owner_id, limit=limit, offset=offset)
        return {
            "items": [self._dataset()],
            "total": 1,
            "limit": limit,
            "offset": offset,
        }

    def get_dataset_schema(self, dataset_id, *, owner_id):  # noqa: ANN001
        self._record("dataset-schema", dataset_id=dataset_id, owner_id=owner_id)
        return self._dataset()

    def _dataset(self) -> dict:
        return {
            "id": str(DATASET_ID),
            "connectionId": str(uuid.uuid4()),
            "syncTaskId": str(uuid.uuid4()),
            "name": "订单",
            "currentVersionId": str(uuid.uuid4()),
            "currentVersion": {
                "id": str(uuid.uuid4()),
                "versionNumber": 2,
                "rowCount": 12,
                "columnCount": 2,
                "schema": {"fields": [{"name": "id", "type": "long"}]},
            },
            "versionCount": 2,
            "sourceSchema": "sales",
            "sourceTable": "orders",
            "createdAt": NOW,
            "updatedAt": NOW,
        }

    def preview_node(self, pipeline_id, *, owner_id, payload):  # noqa: ANN001
        self._record("preview", pipeline_id=pipeline_id, payload=payload)
        return {
            "previewId": str(uuid.uuid4()),
            "pipelineId": str(pipeline_id),
            "draftRevision": payload["draftRevision"],
            "nodeId": payload["nodeId"],
            "columns": [{"name": "id", "type": "long", "nullable": False}],
            "rows": [{"id": 1}],
            "returnedRows": 1,
            "scannedRows": 1,
            "truncated": False,
            "truncationReason": None,
            "elapsedMs": 12,
            "inputVersions": [
                {
                    "datasetKind": "pipeline_dataset",
                    "datasetId": str(DATASET_ID),
                    "datasetVersionId": str(uuid.uuid4()),
                    "datasetVersionNumber": 1,
                    "schemaHash": "abc",
                    "icebergCatalog": "lake",
                    "icebergNamespace": "ns",
                    "icebergTable": "tbl",
                    "icebergSnapshotId": 10,
                    "displaySnapshot": {"rowCount": 1},
                }
            ],
            "errors": [],
            "warnings": [],
        }

    def cancel_preview(self, pipeline_id, *, owner_id):  # noqa: ANN001
        self._record("cancel-preview", pipeline_id=pipeline_id, owner_id=owner_id)
        return {}

    def create_build(self, pipeline_id, *, owner_id, payload):  # noqa: ANN001
        self._record("build", pipeline_id=pipeline_id, payload=payload)
        return self._build()

    def get_build(self, pipeline_id, build_id, *, owner_id):  # noqa: ANN001
        self._record("get-build", pipeline_id=pipeline_id, build_id=build_id)
        return self._build()

    def cancel_build(self, pipeline_id, build_id, *, owner_id, operation_id=None):  # noqa: ANN001
        self._record("cancel-build", pipeline_id=pipeline_id, build_id=build_id)
        return self._build()

    def _build(self) -> dict:
        return {
            "id": str(BUILD_ID),
            "pipelineId": str(PIPELINE_ID),
            "pipelineVersionId": None,
            "status": "queued",
            "stage": "queued",
            "trigger": "manual",
            "errorCode": None,
            "errorMessage": None,
            "logUri": None,
            "startedAt": None,
            "finishedAt": None,
            "outputs": [
                {
                    "id": str(uuid.uuid4()),
                    "outputId": str(uuid.uuid4()),
                    "outputType": "pipeline_dataset",
                    "status": "queued",
                    "stage": "queued",
                    "engineJobId": None,
                    "logUri": None,
                    "errorCode": None,
                    "errorMessage": None,
                    "result": {"expectations": []},
                    "stats": {"rowCount": 1},
                    "startedAt": None,
                    "finishedAt": None,
                }
            ],
        }

    def get_output_sources(self, pipeline_id, dataset_id, *, owner_id, version_id=None):  # noqa: ANN001
        self._record("sources", pipeline_id=pipeline_id, dataset_id=dataset_id)
        return {
            "pipelineId": str(pipeline_id),
            "datasetId": str(dataset_id),
            "versionId": version_id,
            "output": {
                "outputId": str(uuid.uuid4()),
                "name": "输出",
                "versionNumber": 1,
                "rowCount": 10,
                "publishedAt": NOW,
            },
            "build": {
                "buildId": str(BUILD_ID),
                "status": "succeeded",
                "trigger": "manual",
                "startedAt": NOW,
                "finishedAt": NOW,
            },
            "pipelineVersion": {
                "versionId": str(uuid.uuid4()),
                "versionNumber": 1,
                "createdAt": NOW,
            },
            "inputs": [
                {
                    "datasetKind": "sync_dataset",
                    "datasetId": str(DATASET_ID),
                    "datasetVersionId": str(uuid.uuid4()),
                    "datasetVersionNumber": 2,
                    "schemaHash": "abc",
                    "datasetName": "订单",
                    "connectionName": "ERP",
                    "sourceSchemaName": "sales",
                    "sourceTableName": "orders",
                    "metadataStatus": "available",
                }
            ],
        }

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
