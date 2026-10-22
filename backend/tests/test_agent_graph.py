import uuid

from langgraph.checkpoint.memory import InMemorySaver

from app.agent import graph as agent_graph
from app.agent import llm
from app.agent.prompts import SYSTEM_PROMPT
from app.agent.tools import OntologyToolContext, build_model_tools
from app.config import Settings
from app.services import changeset, conversation, embedding


def _fake_model():
    """构造但不调用：只用来验证装配，不发网络请求。"""
    return llm.build_chat_model(Settings(chat_api_key="fake-key"))


def _context(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="装配测试")
    cs = changeset.create_changeset(session, graph_id=conv.resource_id, owner_id=owner)
    return conv, OntologyToolContext(
        session=session,
        owner_id=owner,
        graph_id=conv.resource_id,
        changeset_id=cs.id,
        embedder=embedding.HashingEmbedder(),
    )


def test_build_agent_compiles(session):
    conv, ctx = _context(session, uuid.uuid4())
    agent = agent_graph.build_agent(
        _fake_model(),
        build_model_tools(ctx),
        system_prompt=SYSTEM_PROMPT,
        checkpointer=InMemorySaver(),
    )
    assert type(agent).__name__ == "CompiledStateGraph"


def test_build_agent_without_checkpointer_skips_interrupts(session):
    """interrupt_on 需要 checkpointer，没给的时候不能硬塞，否则装配期就炸。"""
    conv, ctx = _context(session, uuid.uuid4())
    agent = agent_graph.build_agent(
        _fake_model(),
        build_model_tools(ctx),
        system_prompt=SYSTEM_PROMPT,
    )
    assert type(agent).__name__ == "CompiledStateGraph"


def test_build_checkpointer_checks_connections_before_use(monkeypatch):
    """池里可能留着服务端已关闭的旧连接，取连接时必须先探活。"""
    import langgraph.checkpoint.postgres as checkpoint_postgres
    from psycopg_pool import ConnectionPool

    expected_check = ConnectionPool.check_connection
    captured: dict = {}

    class FakePool:
        check_connection = expected_check

        def __init__(self, **kwargs):  # noqa: ANN003
            captured.update(kwargs)

    class FakeSaver:
        def __init__(self, pool):  # noqa: ANN001, ARG002
            pass

        def setup(self):
            pass

    monkeypatch.setattr(checkpoint_postgres, "PostgresSaver", FakeSaver)
    monkeypatch.setattr("psycopg_pool.ConnectionPool", FakePool)

    agent_graph.build_checkpointer("postgresql+psycopg://user:pass@localhost/db")

    assert captured["check"] is expected_check


def test_system_prompt_keeps_explicit_property_requests_as_properties():
    assert "用户明确说" in SYSTEM_PROMPT
    assert "必须按属性处理" in SYSTEM_PROMPT
    assert "不要改写成关系" in SYSTEM_PROMPT


def test_system_prompt_hands_view_tasks_off_without_pre_reading_the_graph():
    """视图任务直接交给子智能体，别让主 agent 用 get_subgraph 逐节点把图读一遍。"""
    assert "直接交给 view_designer" in SYSTEM_PROMPT
    assert "不要自己先调 get_subgraph" in SYSTEM_PROMPT


def test_tool_domain_errors_become_payloads_not_exceptions(session):
    """deepagents 的 ToolNode 不会兜工具异常，异常会打挂整轮 run。
    所以工具必须把领域错误转成结构化返回值。"""
    owner = uuid.uuid4()
    _, ctx = _context(session, owner)
    by_name = {item.name: item for item in build_model_tools(ctx)}

    result = by_name["get_subgraph"].invoke({"object_type_id": str(uuid.uuid4())})
    assert result["error"] == "NotFoundError"
    assert "not in graph" in result["message"]


def test_tool_bad_uuid_becomes_payload(session):
    _, ctx = _context(session, uuid.uuid4())
    by_name = {item.name: item for item in build_model_tools(ctx)}

    result = by_name["get_subgraph"].invoke({"object_type_id": "not-a-uuid"})
    assert result["error"] == "ValueError"
