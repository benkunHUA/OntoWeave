import uuid

import pytest

from app.agent import tools as agent_tools
from app.config import Settings
from app.errors import InvariantViolationError
from app.services import changeset, conversation, embedding


def _context(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="绑定测试")
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)
    return agent_tools.ToolContext(
        session=session,
        owner_id=owner,
        graph_id=conv.graph_id,
        changeset_id=cs.id,
        embedder=embedding.HashingEmbedder(),
    )


def test_binds_expected_tool_names(session):
    ctx = _context(session, uuid.uuid4())
    names = [item.name for item in agent_tools.build_model_tools(ctx)]
    assert names == [
        "search_ontology",
        "list_types",
        "get_subgraph",
        "propose_type",
        "propose_property",
        "propose_relation",
        "update_element",
        "remove_element",
        "ask_user",
    ]


def test_every_tool_exposes_a_description(session):
    ctx = _context(session, uuid.uuid4())
    for item in agent_tools.build_model_tools(ctx):
        assert item.description and item.description.strip()


def test_tool_calls_reach_the_service_layer(session):
    owner = uuid.uuid4()
    ctx = _context(session, owner)
    by_name = {item.name: item for item in agent_tools.build_model_tools(ctx)}

    assert by_name["list_types"].invoke({}) == []

    created = by_name["propose_type"].invoke({"name": "客户"})
    assert created["status"] == "draft"
    assert [op.op for op in changeset.list_ops(session, ctx.changeset_id)] == ["create"]


def test_build_chat_model_requires_key():
    from app.agent import llm

    with pytest.raises(InvariantViolationError):
        llm.build_chat_model(Settings(chat_api_key=""))


def test_build_chat_model_passes_configured_values():
    from app.agent import llm

    model = llm.build_chat_model(
        Settings(
            chat_api_key="k",
            chat_model="deepseek-flash",
            chat_base_url="https://api.deepseek.com",
            chat_max_tokens=2048,
        )
    )
    assert model.model_name == "deepseek-flash"
    assert model.max_tokens == 2048
