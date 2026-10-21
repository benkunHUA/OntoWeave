import uuid

import httpx
import pytest

from app.config import Settings
from app.errors import (
    DataServiceUnavailableError,
    InvalidRequestError,
    InvariantViolationError,
    NotFoundError,
    UnprocessableEntityError,
)
from app.services.data_service.client import DataServiceClient


def _response(status: int, body: dict) -> httpx.Response:
    return httpx.Response(status, json=body, request=httpx.Request("GET", "http://java"))


def test_client_sends_internal_token_and_owner(monkeypatch):
    captured = {}

    def fake_request(method, url, **kwargs):
        captured.update(method=method, url=url, **kwargs)
        return _response(200, {"id": "x"})

    monkeypatch.setattr("app.services.data_service.client.httpx.request", fake_request)
    client = DataServiceClient(Settings(data_service_url="http://java:18001"))
    owner = uuid.uuid4()
    connection = uuid.uuid4()

    assert client.get_connection(connection, owner_id=owner) == {"id": "x"}
    assert captured["method"] == "GET"
    assert captured["url"] == f"http://java:18001/internal/v1/data-connections/{connection}"
    assert captured["params"] == {"ownerId": str(owner)}
    assert captured["headers"] == {"X-Internal-Token": "dev-internal-token"}


def test_client_sends_create_body(monkeypatch):
    captured = {}

    def fake_request(method, url, **kwargs):
        captured.update(method=method, url=url, **kwargs)
        return _response(201, {"id": "x"})

    monkeypatch.setattr("app.services.data_service.client.httpx.request", fake_request)
    client = DataServiceClient(Settings())
    owner = uuid.uuid4()
    conversation = uuid.uuid4()

    client.create_connection(
        owner_id=owner,
        payload={"conversationId": str(conversation), "name": "ERP", "kind": "mysql"},
    )

    assert captured["json"]["ownerId"] == str(owner)
    assert captured["json"]["conversationId"] == str(conversation)


@pytest.mark.parametrize(
    ("status", "error"),
    [
        (404, NotFoundError),
        (400, InvalidRequestError),
        (409, InvariantViolationError),
        (500, DataServiceUnavailableError),
    ],
)
def test_http_errors_become_domain_errors(monkeypatch, status, error):
    monkeypatch.setattr(
        "app.services.data_service.client.httpx.request",
        lambda *a, **k: _response(status, {"code": "X", "message": "坏了"}),
    )
    client = DataServiceClient(Settings())

    with pytest.raises(error, match="坏了"):
        client.get_connection(uuid.uuid4(), owner_id=uuid.uuid4())


def test_transport_error_is_unavailable(monkeypatch):
    def explode(*args, **kwargs):  # noqa: ARG001
        raise httpx.ConnectError("refused")

    monkeypatch.setattr("app.services.data_service.client.httpx.request", explode)
    client = DataServiceClient(Settings())

    with pytest.raises(DataServiceUnavailableError, match="数据服务不可用"):
        client.get_state(uuid.uuid4(), owner_id=uuid.uuid4())


def test_422_preserves_code_and_details():
    details = {"issues": [{"code": "NODE_CONFIG_INCOMPLETE", "message": "缺少名称"}]}

    def handler(request: httpx.Request) -> httpx.Response:  # noqa: ARG001
        return httpx.Response(
            422,
            json={"code": "PIPELINE_NOT_READY", "message": "流程未就绪", "details": details},
        )

    client = DataServiceClient(Settings(), transport=httpx.MockTransport(handler))

    with pytest.raises(UnprocessableEntityError) as caught:
        client.preview_node(uuid.uuid4(), owner_id=uuid.uuid4(), payload={})

    assert caught.value.code == "PIPELINE_NOT_READY"
    assert caught.value.details == details
    assert "流程未就绪" in str(caught.value)


def test_pipeline_client_uses_documented_paths(monkeypatch):
    captured = []

    def fake_request(method, url, **kwargs):
        captured.append((method, url, kwargs))
        return _response(200, {"id": str(uuid.uuid4())})

    monkeypatch.setattr("app.services.data_service.client.httpx.request", fake_request)
    client = DataServiceClient(Settings(data_service_url="http://java:18001"))
    owner = uuid.uuid4()
    pipeline = uuid.uuid4()
    dataset = uuid.uuid4()
    operation = uuid.uuid4()

    client.create_pipeline(
        owner_id=owner,
        payload={"pipelineId": str(pipeline), "conversationId": str(uuid.uuid4()), "name": "流程"},
        operation_id=operation,
    )
    client.put_schedule(
        pipeline,
        owner_id=owner,
        payload={"name": "每日", "cronExpression": "0 2 * * *", "timezone": "Asia/Shanghai"},
    )
    client.get_output_sources(pipeline, dataset, owner_id=owner)

    assert captured[0][0] == "POST"
    assert captured[0][1] == "http://java:18001/internal/v1/pipelines"
    assert captured[0][2]["json"]["ownerId"] == str(owner)
    assert captured[0][2]["headers"]["X-Operation-Id"] == str(operation)
    assert captured[1][0] == "PUT"
    assert captured[1][1] == f"http://java:18001/internal/v1/pipelines/{pipeline}/schedule"
    assert captured[2][1] == (
        f"http://java:18001/internal/v1/pipelines/{pipeline}/datasets/{dataset}/sources"
    )


def test_schedule_runs_accepts_json_array_through_http_transport():
    owner = uuid.uuid4()
    pipeline = uuid.uuid4()
    run_id = uuid.uuid4()

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == f"/internal/v1/pipelines/{pipeline}/schedule/runs"
        assert request.url.params["ownerId"] == str(owner)
        return httpx.Response(200, json=[{"id": str(run_id), "status": "dispatched"}])

    client = DataServiceClient(
        Settings(data_service_url="http://java:18001"),
        transport=httpx.MockTransport(handler),
    )

    assert client.list_schedule_runs(pipeline, owner_id=owner, limit=25, offset=5) == [
        {"id": str(run_id), "status": "dispatched"}
    ]


def test_object_response_is_rejected_for_list_routes():
    def handler(request: httpx.Request) -> httpx.Response:  # noqa: ARG001
        return httpx.Response(200, json={"items": []})

    client = DataServiceClient(Settings(), transport=httpx.MockTransport(handler))

    with pytest.raises(DataServiceUnavailableError, match="非数组响应"):
        client.list_schedule_runs(uuid.uuid4(), owner_id=uuid.uuid4(), limit=20, offset=0)


def test_array_response_is_rejected_for_object_routes():
    def handler(request: httpx.Request) -> httpx.Response:  # noqa: ARG001
        return httpx.Response(200, json=[])

    client = DataServiceClient(Settings(), transport=httpx.MockTransport(handler))

    with pytest.raises(DataServiceUnavailableError, match="非对象响应"):
        client.get_schedule(uuid.uuid4(), owner_id=uuid.uuid4())


def test_list_route_keeps_the_same_http_error_mapping():
    def handler(request: httpx.Request) -> httpx.Response:  # noqa: ARG001
        return httpx.Response(409, json={"code": "SCHEDULE_CONFLICT", "message": "冲突"})

    client = DataServiceClient(Settings(), transport=httpx.MockTransport(handler))

    with pytest.raises(InvariantViolationError, match="冲突（SCHEDULE_CONFLICT）"):
        client.list_schedule_runs(uuid.uuid4(), owner_id=uuid.uuid4(), limit=20, offset=0)


def test_input_datasets_uses_the_java_page_contract():
    owner = uuid.uuid4()

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/v1/datasets"
        assert request.url.params["ownerId"] == str(owner)
        assert request.url.params["limit"] == "30"
        assert request.url.params["offset"] == "10"
        return httpx.Response(
            200,
            json={"items": [{"id": "dataset-1"}], "total": 1, "limit": 30, "offset": 10},
        )

    client = DataServiceClient(Settings(), transport=httpx.MockTransport(handler))

    assert client.list_input_datasets(owner_id=owner, limit=30, offset=10)["items"] == [
        {"id": "dataset-1"}
    ]
