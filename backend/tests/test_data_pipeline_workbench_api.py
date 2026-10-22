import uuid

from pipeline_test_fakes import API, BUILD_ID, DATASET_ID, NOW, PIPELINE_ID, TURN_ID

from app.errors import UnprocessableEntityError


def test_pipeline_crud_and_bff_workbench_routes(pipeline_client, fake_pipeline_client):
    assert (
        pipeline_client.get(API, params={"limit": 10, "offset": 2}).json()["items"][0]["name"]
        == "A"
    )
    assert pipeline_client.get(f"{API}/{PIPELINE_ID}").json()["id"] == str(PIPELINE_ID)
    detail = pipeline_client.get(f"{API}/{PIPELINE_ID}").json()
    assert detail["current_version_number"] is None
    assert detail["current_version_draft_revision"] is None
    assert (
        pipeline_client.patch(f"{API}/{PIPELINE_ID}", json={"name": "改名"}).json()["name"]
        == "改名"
    )

    draft = pipeline_client.get(f"{API}/{PIPELINE_ID}/draft").json()
    assert draft["pipeline_id"] == str(PIPELINE_ID)
    assert "pipelineId" not in str(draft)

    command = pipeline_client.post(
        f"{API}/{PIPELINE_ID}/draft/commands",
        json={
            "type": "add_node",
            "base_revision": 3,
            "operation_id": str(uuid.uuid4()),
            "turn_id": str(TURN_ID),
            "node": {"id": str(uuid.uuid4()), "type": "filter", "config": {}},
        },
    )
    assert command.status_code == 200
    forwarded = [item for item in fake_pipeline_client.calls if item[0] == "command"][0][1]
    assert forwarded["payload"]["baseRevision"] == 3
    assert forwarded["payload"]["turnId"] == str(TURN_ID)
    assert "base_revision" not in forwarded["payload"]

    moved = pipeline_client.post(
        f"{API}/{PIPELINE_ID}/draft/positions",
        json={"positions": [{"node_id": str(uuid.uuid4()), "x": 10, "y": 20}]},
    )
    assert moved.status_code == 200
    assert fake_pipeline_client.calls[-1][1]["payload"]["position"] == {"x": 10.0, "y": 20.0}

    check = pipeline_client.get(f"{API}/{PIPELINE_ID}/check", params={"revision": 3}).json()
    assert check["blocking_issues"][0]["message"] == "缺少名称"
    assert check["next_actions"][0]["action"] == "CONFIGURE_NODE"

    undo = pipeline_client.post(f"{API}/{PIPELINE_ID}/turns/{TURN_ID}/undo", json={"revision": 3})
    assert undo.status_code == 200

    preview = pipeline_client.post(
        f"{API}/{PIPELINE_ID}/previews",
        json={"draft_revision": 3, "node_id": str(uuid.uuid4()), "request_id": str(uuid.uuid4())},
    ).json()
    assert preview["returned_rows"] == 1
    assert preview["input_versions"][0]["dataset_version_id"]
    assert "inputVersions" not in str(preview)
    assert (
        next(call[1]["payload"] for call in fake_pipeline_client.calls if call[0] == "preview")[
            "rowLimit"
        ]
        == 100
    )
    assert pipeline_client.post(f"{API}/{PIPELINE_ID}/previews/cancel").status_code == 204
    assert any(call[0] == "cancel-preview" for call in fake_pipeline_client.calls)

    build = pipeline_client.post(
        f"{API}/{PIPELINE_ID}/builds",
        json={"draft_revision": 3, "operation_id": str(uuid.uuid4())},
    ).json()
    assert build["status"] == "queued"
    assert build["outputs"][0]["engine_job_id"] is None
    assert "pipelineVersionId" not in str(build)

    assert pipeline_client.get(f"{API}/{PIPELINE_ID}/builds/{BUILD_ID}").status_code == 200
    assert pipeline_client.post(f"{API}/{PIPELINE_ID}/builds/{BUILD_ID}/cancel").status_code == 200

    sources = pipeline_client.get(f"{API}/{PIPELINE_ID}/datasets/{DATASET_ID}/sources").json()
    assert sources["pipeline_version"]["version_number"] == 1
    assert sources["inputs"][0]["source_table_name"] == "orders"
    assert "pipelineVersion" not in str(sources)


def test_schedule_routes_map_camel_case(pipeline_client, fake_pipeline_client):
    assert (
        pipeline_client.get(f"{API}/{PIPELINE_ID}/schedule").json()["cron_expression"]
        == "0 2 * * *"
    )

    put = pipeline_client.put(
        f"{API}/{PIPELINE_ID}/schedule",
        json={"name": "每日", "cron_expression": "0 3 * * *", "timezone": "Asia/Shanghai"},
        headers={"X-Operation-Id": str(uuid.uuid4())},
    )
    assert put.status_code == 200
    forwarded = [item for item in fake_pipeline_client.calls if item[0] == "schedule-put"][-1][1]
    assert forwarded["payload"] == {
        "name": "每日",
        "cronExpression": "0 3 * * *",
        "timezone": "Asia/Shanghai",
    }

    assert pipeline_client.post(f"{API}/{PIPELINE_ID}/schedule/pause").json()["status"] == "paused"
    assert pipeline_client.post(f"{API}/{PIPELINE_ID}/schedule/resume").json()["status"] == "active"
    runs = pipeline_client.get(
        f"{API}/{PIPELINE_ID}/schedule/runs", params={"limit": 10, "offset": 0}
    ).json()
    assert runs[0]["scheduled_for"] == NOW
    assert runs[0]["trigger_type"] == "scheduled"
    assert runs[0]["started_at"] == NOW
    assert runs[0]["finished_at"] == NOW


def test_java_422_keeps_blocking_issues_for_preview_and_build(
    pipeline_client, fake_pipeline_client, monkeypatch
):
    details = {
        "blockingIssues": [
            {
                "code": "NODE_CONFIG_INCOMPLETE",
                "phase": "config",
                "blocking": True,
                "message": "缺少名称",
            }
        ]
    }

    def reject(*args, **kwargs):  # noqa: ANN002, ANN003, ARG001
        raise UnprocessableEntityError(
            "流程未就绪（PIPELINE_NOT_READY）",
            code="PIPELINE_NOT_READY",
            details=details,
        )

    monkeypatch.setattr(fake_pipeline_client, "preview_node", reject)
    monkeypatch.setattr(fake_pipeline_client, "create_build", reject)

    preview = pipeline_client.post(
        f"{API}/{PIPELINE_ID}/previews",
        json={"draft_revision": 3, "node_id": str(uuid.uuid4())},
    )
    assert preview.status_code == 422
    assert preview.json()["details"] == details
    assert preview.json()["code"] == "PIPELINE_NOT_READY"

    build = pipeline_client.post(
        f"{API}/{PIPELINE_ID}/builds",
        json={"draft_revision": 3, "operation_id": str(uuid.uuid4())},
    )
    assert build.status_code == 422
    assert build.json()["details"]["blockingIssues"][0]["code"] == "NODE_CONFIG_INCOMPLETE"


def test_java_422_single_field_preview_error_preserves_details(
    pipeline_client, fake_pipeline_client, monkeypatch
):
    details = {
        "nodeId": "node-1",
        "portId": "in",
        "field": "amount",
        "message": "金额无法解析",
    }

    def reject(*args, **kwargs):  # noqa: ANN002, ANN003, ARG001
        raise UnprocessableEntityError(
            "预览执行失败（PREVIEW_EXECUTION_FAILED）",
            code="PREVIEW_EXECUTION_FAILED",
            details=details,
        )

    monkeypatch.setattr(fake_pipeline_client, "preview_node", reject)
    response = pipeline_client.post(
        f"{API}/{PIPELINE_ID}/previews",
        json={"draft_revision": 3, "node_id": str(uuid.uuid4())},
    )

    assert response.status_code == 422
    assert response.json()["details"] == details


def test_preview_rejects_invalid_row_limit(pipeline_client, fake_pipeline_client):
    response = pipeline_client.post(
        f"{API}/{PIPELINE_ID}/previews",
        json={"draft_revision": 3, "node_id": str(uuid.uuid4()), "row_limit": 0},
    )

    assert response.status_code == 422
    assert not [call for call in fake_pipeline_client.calls if call[0] == "preview"]
