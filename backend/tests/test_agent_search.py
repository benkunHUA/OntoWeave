"""共享检索工具 `search`：对象类型与动作一个入口。"""

import uuid

import pytest

from app.agent.tools import ToolContext
from app.agent.tools.search import build_search_tool, search
from app.errors import InvariantViolationError
from app.services import action, changeset, conversation, embedding, ontology


def _context(session, owner) -> ToolContext:
    conv = conversation.create_conversation(session, owner_id=owner, title="检索测试")
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)
    return ToolContext(
        session=session,
        owner_id=owner,
        graph_id=conv.graph_id,
        changeset_id=cs.id,
        embedder=embedding.HashingEmbedder(),
    )


def _confirmed_object_type(session, *, owner, name):
    obj = ontology.create_object_type(session, owner_id=owner, name=name)
    ontology.transition_object_type(session, obj.id, "confirmed", owner_id=owner)
    embedding.refresh_object_type_embedding(
        session, object_type_id=obj.id, embedder=embedding.HashingEmbedder()
    )
    return obj


def _confirmed_action(session, *, owner, name):
    action_type = action.create_action_type(session, owner_id=owner, name=name)
    action.transition_action_type(session, action_type.id, "confirmed", owner_id=owner)
    embedding.refresh_action_type_embedding(
        session, action_type_id=action_type.id, embedder=embedding.HashingEmbedder()
    )
    return action_type


def test_search_returns_confirmed_matches_with_scores(session):
    owner = uuid.uuid4()
    _confirmed_object_type(session, owner=owner, name="客户")
    hits = search(session, owner_id=owner, embedder=embedding.HashingEmbedder(), query="客户公司")
    assert [item["kind"] for item in hits] == ["object_type"]
    assert hits[0]["name"] == "客户"
    assert "similarity" in hits[0]


def test_search_only_returns_own_elements(session):
    owner, other = uuid.uuid4(), uuid.uuid4()
    mine = _confirmed_object_type(session, owner=owner, name="客户")
    _confirmed_object_type(session, owner=other, name="客户")

    hits = search(session, owner_id=owner, embedder=embedding.HashingEmbedder(), query="客户")

    assert [item["id"] for item in hits] == [str(mine.id)]


def test_search_lists_everything_without_a_query(session):
    """不给 query 就按名称列出全部，草稿也在其中——模型看 status 自己分辨。"""
    owner = uuid.uuid4()
    _confirmed_object_type(session, owner=owner, name="客户")
    ontology.create_object_type(session, owner_id=owner, name="草稿类型")
    _confirmed_action(session, owner=owner, name="关闭工单")

    listed = search(session, owner_id=owner, embedder=embedding.HashingEmbedder())

    # 对象类型在前、动作在后；各自按名称排序。
    assert [(item["kind"], item["name"], item["status"]) for item in listed] == [
        ("object_type", "客户", "confirmed"),
        ("object_type", "草稿类型", "draft"),
        ("action_type", "关闭工单", "confirmed"),
    ]


def test_search_kind_limits_to_one_kind(session):
    owner = uuid.uuid4()
    _confirmed_object_type(session, owner=owner, name="客户")
    _confirmed_action(session, owner=owner, name="关闭工单")
    embedder = embedding.HashingEmbedder()

    actions = search(session, owner_id=owner, embedder=embedder, kind="action_type")
    types = search(session, owner_id=owner, embedder=embedder, kind="object_type", query="客户")

    assert [item["name"] for item in actions] == ["关闭工单"]
    assert [item["name"] for item in types] == ["客户"]


def test_search_rejects_an_unknown_kind(session):
    with pytest.raises(InvariantViolationError):
        search(
            session,
            owner_id=uuid.uuid4(),
            embedder=embedding.HashingEmbedder(),
            kind="relation",
        )


def test_build_search_tool_exposes_one_tool(session):
    ctx = _context(session, uuid.uuid4())
    tool = build_search_tool(ctx)

    assert tool.name == "search"
    assert tool.invoke({"kind": "action_type"}) == []
