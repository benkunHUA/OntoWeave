import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.config import get_settings
from app.main import _create_app, create_app
from app.services import ontology

API = "/api/v1"


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    """不覆盖依赖：app 直接拿到绑在测试连接上的 session 工厂，走真实装配链路。

    关掉检查点：这些用例不跑智能体，开启会让定稿回写去连生产库建检查点。
    线程回写本身在 test_agent_thread.py 里用 InMemorySaver 单独测。
    """
    return TestClient(_create_app(session_factory, enable_agent_checkpoints=False))


def test_create_and_confirm_object_type(client):
    created = client.post(f"{API}/object-types", json={"name": "客户"})
    assert created.status_code == 201
    body = created.json()
    assert body["status"] == "draft"

    confirmed = client.post(f"{API}/object-types/{body['id']}/confirm")
    assert confirmed.status_code == 200
    assert confirmed.json()["status"] == "confirmed"


def test_confirm_twice_is_rejected(client):
    body = client.post(f"{API}/object-types", json={"name": "订单"}).json()
    client.post(f"{API}/object-types/{body['id']}/confirm")
    again = client.post(f"{API}/object-types/{body['id']}/confirm")
    assert again.status_code == 409
    assert again.json()["error"] == "invalid_transition"


def test_unknown_object_type_returns_404(client):
    resp = client.post(f"{API}/object-types/{uuid.uuid4()}/confirm")
    assert resp.status_code == 404
    assert resp.json()["error"] == "not_found"


def test_graph_relation_endpoint_invariant_returns_409(client, session_factory):
    with session_factory() as s:
        customer = ontology.create_object_type(s, owner_id=uuid.uuid4(), name="客户")
        order = ontology.create_object_type(s, owner_id=uuid.uuid4(), name="订单")
        rel = ontology.create_relation(
            s,
            owner_id=uuid.uuid4(),
            source_type_id=customer.id,
            target_type_id=order.id,
            name="下订单",
        )
        s.commit()
        customer_id, relation_id = customer.id, rel.id

    gid = client.post(f"{API}/graphs", json={"name": "销售域"}).json()["id"]
    added = client.post(f"{API}/graphs/{gid}/objects", json={"object_type_id": str(customer_id)})
    assert added.status_code == 204

    resp = client.post(f"{API}/graphs/{gid}/relations", json={"relation_id": str(relation_id)})
    assert resp.status_code == 409
    assert resp.json()["error"] == "invariant_violation"


def test_object_type_response_has_no_internal_fields(client):
    body = client.post(f"{API}/object-types", json={"name": "产品"}).json()
    assert set(body) == {"id", "name", "description", "status"}


def test_graph_snapshot_endpoint_returns_canvas_data(client):
    graph_id = client.post(f"{API}/conversations", json={"title": "快照"}).json()["graph_id"]
    customer = client.post(f"{API}/object-types", json={"name": "客户"}).json()
    client.post(
        f"{API}/graphs/{graph_id}/objects",
        json={"object_type_id": customer["id"]},
    )

    snapshot = client.get(f"{API}/graphs/{graph_id}/snapshot")

    assert snapshot.status_code == 200
    body = snapshot.json()
    assert body["graph"]["name"] == "快照"
    assert [node["name"] for node in body["nodes"]] == ["客户"]
    assert body["nodes"][0]["properties"] == []
    assert body["relations"] == []


def test_response_carries_request_id_header(client):
    resp = client.get("/health")
    assert resp.headers["x-request-id"]


def test_request_id_is_propagated_when_supplied(client):
    resp = client.get("/health", headers={"X-Request-ID": "trace-me-123"})
    assert resp.headers["x-request-id"] == "trace-me-123"


def test_default_app_wires_factory_from_settings():
    """不带参数装配时，连接串必须来自应用配置，而不是被写死在别处。"""
    app = create_app()
    engine = app.state.session_factory.kw["bind"]
    assert engine.url.render_as_string(hide_password=False) == (get_settings().database_url)


def test_list_object_types_paginates(client):
    for name in ("pg-a", "pg-b", "pg-c"):
        client.post(f"{API}/object-types", json={"name": name})

    all_items = client.get(f"{API}/object-types", params={"limit": 200}).json()
    assert len(all_items) >= 3

    page1 = client.get(f"{API}/object-types", params={"limit": 2}).json()
    page2 = client.get(f"{API}/object-types", params={"limit": 2, "offset": 2}).json()

    # 分页结果必须与整体列表的前缀一致，顺序也要稳定。
    assert page1 == all_items[:2]
    assert page2 == all_items[2:4]


def test_list_object_types_rejects_invalid_paging(client):
    assert client.get(f"{API}/object-types", params={"limit": 1000}).status_code == 422
    assert client.get(f"{API}/object-types", params={"limit": 0}).status_code == 422
    assert client.get(f"{API}/object-types", params={"offset": -1}).status_code == 422


def test_reject_changeset_leaves_a_notice_in_the_conversation(client):
    conv = client.post(f"{API}/conversations", json={"title": "撤销回执"}).json()
    cs = client.post(f"{API}/graphs/{conv['graph_id']}/changesets").json()
    obj = client.post(f"{API}/object-types", json={"name": "客户"}).json()
    client.post(
        f"{API}/changesets/{cs['id']}/ops",
        json={"op": "create", "target_id": obj["id"], "payload": {"kind": "object_type"}},
    )

    rejected = client.post(f"{API}/changesets/{cs['id']}/reject")
    assert rejected.status_code == 200

    history = client.get(f"{API}/conversations/{conv['id']}/messages").json()
    assert history[-1]["role"] == "assistant"
    assert history[-1]["content"] == "已撤销本次改动：新增 1。"
    assert history[-1]["blocks"][0]["kind"] == "notice"


def test_reject_writes_the_decision_back_into_the_model_thread(client, monkeypatch):
    """撤销不只是数据回滚，还要让模型线程知道，否则下一轮会继续引用已删的对象。"""
    captured: list[dict] = []
    monkeypatch.setattr(
        "app.agent.thread.record_decision", lambda *args, **kwargs: captured.append(kwargs)
    )
    conv = client.post(f"{API}/conversations", json={"title": "线程回写"}).json()
    cs = client.post(f"{API}/graphs/{conv['graph_id']}/changesets").json()
    obj = client.post(f"{API}/object-types", json={"name": "景点"}).json()
    client.post(
        f"{API}/changesets/{cs['id']}/ops",
        json={"op": "create", "target_id": obj["id"], "payload": {"kind": "object_type"}},
    )

    assert client.post(f"{API}/changesets/{cs['id']}/reject").status_code == 200

    assert captured and captured[0]["conversation_id"] == uuid.UUID(conv["id"])
    assert captured[0]["changeset_id"] == uuid.UUID(cs["id"])
    text = captured[0]["text"]
    assert text.startswith("（系统回执）用户已撤销本次改动，以下内容已回退：")
    assert "- 新增 对象类型「景点」" in text
    assert "不要再引用" in text


def test_graph_edits_endpoint_builds_types_and_relations(client):
    """抽屉保存：新建类型、建关系走的是和模型写工具同一套 service 规则。"""
    graph_id = client.post(f"{API}/conversations", json={"title": "手建"}).json()["graph_id"]

    created = client.post(
        f"{API}/graphs/{graph_id}/edits",
        json={
            "edits": [
                {"op": "create_object_type", "name": "客户", "description": "下订单的一方"},
                {"op": "create_object_type", "name": "订单"},
            ]
        },
    )
    assert created.status_code == 200
    assert [op["payload"]["kind"] for op in created.json()["ops"]] == [
        "object_type",
        "object_type",
    ]

    nodes = {
        node["name"]: node["id"]
        for node in client.get(f"{API}/graphs/{graph_id}/snapshot").json()["nodes"]
    }
    assert set(nodes) == {"客户", "订单"}

    linked = client.post(
        f"{API}/graphs/{graph_id}/edits",
        json={
            "edits": [
                {
                    "op": "create_relation",
                    "source_type_id": nodes["客户"],
                    "target_type_id": nodes["订单"],
                    "name": "下订单",
                    "cardinality": "one_to_many",
                }
            ]
        },
    )
    assert linked.status_code == 200
    relations = client.get(f"{API}/graphs/{graph_id}/snapshot").json()["relations"]
    assert [(rel["name"], rel["cardinality"]) for rel in relations] == [("下订单", "one_to_many")]


def test_graph_edits_endpoint_refuses_to_reuse_a_draft(client):
    graph_id = client.post(f"{API}/conversations", json={"title": "草稿"}).json()["graph_id"]
    draft_id = client.post(
        f"{API}/graphs/{graph_id}/edits",
        json={"edits": [{"op": "create_object_type", "name": "客户"}]},
    ).json()["ops"][0]["target_id"]

    other_graph = client.post(f"{API}/conversations", json={"title": "另一张图"}).json()["graph_id"]
    response = client.post(
        f"{API}/graphs/{other_graph}/edits",
        json={"edits": [{"op": "reuse_object_type", "object_type_id": draft_id}]},
    )

    assert response.status_code == 409
