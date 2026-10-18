import uuid

import httpx
import pytest

from app.config import Settings
from app.errors import (
    DataServiceUnavailableError,
    InvalidRequestError,
    InvariantViolationError,
    NotFoundError,
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
