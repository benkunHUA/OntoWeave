"""数据整理测试假客户端的数据集、预览与构建部分。"""

import uuid

from tests.support.pipeline_constants import BUILD_ID, DATASET_ID, NOW, PIPELINE_ID


class PipelineDatasetFake:
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

    def get_latest_build(self, pipeline_id, *, owner_id):  # noqa: ANN001
        self._record("get-latest-build", pipeline_id=pipeline_id)
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

    def get_pipeline_dataset_preview(  # noqa: ANN001
        self,
        pipeline_id,
        dataset_id,
        *,
        owner_id,
        version_id=None,
        limit=100,
    ):
        self._record(
            "dataset-preview",
            pipeline_id=pipeline_id,
            dataset_id=dataset_id,
            version_id=version_id,
            limit=limit,
        )
        return {
            "datasetId": str(dataset_id),
            "versionId": str(version_id or uuid.uuid4()),
            "versionNumber": 3,
            "snapshotId": 901,
            "live": False,
            "columns": [
                {"name": "id", "type": "long", "nullable": False},
                {"name": "customer_name", "type": "string", "nullable": True},
            ],
            "rowCount": 2,
            "rows": [{"id": 1, "customerName": "Ada"}][:limit],
            "truncated": False,
        }
