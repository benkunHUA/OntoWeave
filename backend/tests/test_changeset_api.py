"""`test_changeset.py` 中 api 相关测试。"""

import pytest

from app.services import ontology
from tests.support.changeset_fixtures import API, _default_user, _graph_id, build_client


@pytest.fixture
def api_client(session_factory):
    return build_client(session_factory)


def test_changeset_api_confirm_flow(api_client, session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=_default_user(), name="客户")
    session.commit()

    cs = api_client.post(f"{API}/graphs/{graph_id}/changesets")
    assert cs.status_code == 201
    cid = cs.json()["id"]

    recorded = api_client.post(
        f"{API}/changesets/{cid}/ops",
        json={
            "op": "create",
            "target_id": str(obj.id),
            "payload": {"kind": "object_type"},
        },
    )
    assert recorded.status_code == 201

    detail = api_client.get(f"{API}/changesets/{cid}")
    assert detail.status_code == 200
    assert len(detail.json()["ops"]) == 1

    confirmed = api_client.post(f"{API}/changesets/{cid}/confirm")
    assert confirmed.status_code == 200
    assert confirmed.json()["status"] == "confirmed"


def test_changeset_api_reject_flow(api_client, session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=_default_user(), name="临时")
    session.commit()

    cid = api_client.post(f"{API}/graphs/{graph_id}/changesets").json()["id"]
    api_client.post(
        f"{API}/changesets/{cid}/ops",
        json={
            "op": "create",
            "target_id": str(obj.id),
            "payload": {"kind": "object_type"},
        },
    )
    rejected = api_client.post(f"{API}/changesets/{cid}/reject")
    assert rejected.status_code == 200
    assert rejected.json()["status"] == "rejected"


def test_recording_into_closed_changeset_returns_409(api_client):
    graph_id = api_client.post(f"{API}/conversations", json={"title": "接口测试"}).json()[
        "resource_id"
    ]
    cid = api_client.post(f"{API}/graphs/{graph_id}/changesets").json()["id"]
    api_client.post(f"{API}/changesets/{cid}/confirm")

    resp = api_client.post(f"{API}/changesets/{cid}/ops", json={"op": "create"})
    assert resp.status_code == 409
    assert resp.json()["error"] == "invalid_transition"
