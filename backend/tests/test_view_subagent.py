import uuid

from langgraph.checkpoint.memory import InMemorySaver

from app.agent import graph as agent_graph
from app.agent import llm
from app.agent import tools as agent_tools
from app.config import Settings
from app.services import (
    changeset,
    conversation,
    embedding,
    graph,
    ontology,
    view_state,
)


def _context(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="绘图测试")
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)
    return conv, agent_tools.ToolContext(
        session=session,
        owner_id=owner,
        graph_id=conv.graph_id,
        changeset_id=cs.id,
        embedder=embedding.HashingEmbedder(),
    )


def _fake_model():
    return llm.build_chat_model(Settings(chat_api_key="fake-key"))


def test_view_subagent_exposes_only_view_tools(session):
    _, ctx = _context(session, uuid.uuid4())

    subagent = agent_tools.build_view_subagent(ctx)

    assert subagent["name"] == "view_designer"
    # 描述里点明它自己读图，免得主 agent 先逐节点 get_subgraph 再转述一份可能过期的清单。
    assert "get_ontology_slice" in subagent["description"]
    names = [item.name for item in subagent["tools"]]
    assert names == [
        "get_view_state",
        "get_ontology_slice",
        "set_layout",
        "focus",
        "fit_view",
        "reset_view",
        "highlight",
        "dim",
        "group",
        "collapse",
        "expand",
        "filter_view",
        "annotate",
        "save_view",
        "load_view",
    ]
    assert not {"propose_type", "update_element", "remove_element"} & set(names)


def test_view_tools_persist_spec_and_read_the_graph_slice(session):
    owner = uuid.uuid4()
    conv, ctx = _context(session, owner)
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    order = ontology.create_object_type(session, owner_id=owner, name="订单")
    graph.add_object_to_graph(session, conv.graph_id, customer.id, owner_id=owner)
    graph.add_object_to_graph(session, conv.graph_id, order.id, owner_id=owner)

    by_name = {item.name: item for item in agent_tools.build_view_tools(ctx)}
    by_name["set_layout"].invoke({"kind": "domain-grouped", "options": {"spacing": 80}})
    by_name["highlight"].invoke({"node_ids": [str(customer.id)]})

    saved = view_state.get_view_state(
        session, graph_id=conv.graph_id, user_id=owner, owner_id=owner
    )
    assert saved is not None
    assert saved.spec["layout"] == {
        "kind": "domain-grouped",
        "options": {"spacing": 80},
    }
    assert saved.spec["highlight"] == [{"nodeIds": [str(customer.id)], "style": "focus"}]

    slice_result = by_name["get_ontology_slice"].invoke({"node_ids": []})
    assert {node["name"] for node in slice_result["nodes"]} == {"客户", "订单"}


def test_deep_agent_accepts_the_view_subagent(session):
    _, ctx = _context(session, uuid.uuid4())
    subagent = agent_tools.build_view_subagent(ctx)

    compiled = agent_graph.build_agent(
        _fake_model(),
        [],
        subagents=[subagent],
        checkpointer=InMemorySaver(),
    )

    assert type(compiled).__name__ == "CompiledStateGraph"
