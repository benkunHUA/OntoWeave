import uuid
from concurrent.futures import ThreadPoolExecutor

from app.agent.tools import ToolContext
from app.agent.tools.ontology import build_model_tools
from app.services import changeset, conversation, embedding, ontology


def _context(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="绑定测试")
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)
    return ToolContext(
        session=session,
        owner_id=owner,
        graph_id=conv.graph_id,
        changeset_id=cs.id,
        embedder=embedding.HashingEmbedder(),
    )


def test_binds_expected_tool_names(session):
    ctx = _context(session, uuid.uuid4())
    names = [item.name for item in build_model_tools(ctx)]
    assert names == [
        "search",
        "get_subgraph",
        "propose_type",
        "reuse_type",
        "propose_property",
        "propose_relation",
        "update_element",
        "remove_element",
        "ask_user",
    ]


def test_every_tool_exposes_a_description(session):
    ctx = _context(session, uuid.uuid4())
    for item in build_model_tools(ctx):
        assert item.description and item.description.strip()


def test_tool_calls_reach_the_service_layer(session):
    owner = uuid.uuid4()
    ctx = _context(session, owner)
    by_name = {item.name: item for item in build_model_tools(ctx)}

    assert by_name["search"].invoke({}) == []

    created = by_name["propose_type"].invoke({"name": "客户"})
    assert created["status"] == "draft"
    assert [op.op for op in changeset.list_ops(session, ctx.changeset_id)] == ["create"]


def test_concurrent_tool_calls_do_not_corrupt_the_shared_session(session):
    """deepagents 会在线程池里并发跑同一轮的多个工具，而它们共用一个 Session。
    上下文里的锁保证串行执行，N 个属性必须一个不少地建出来。"""
    owner = uuid.uuid4()
    ctx = _context(session, owner)
    by_name = {item.name: item for item in build_model_tools(ctx)}
    owner_type = by_name["propose_type"].invoke({"name": "订单"})

    def add_property(index: int) -> dict:
        return by_name["propose_property"].invoke(
            {"owner_type_id": owner_type["id"], "name": f"字段{index}"}
        )

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(add_property, range(12)))

    assert all("error" not in item for item in results)
    props = ontology.list_properties(session, uuid.UUID(owner_type["id"]))
    assert len(props) == 12
