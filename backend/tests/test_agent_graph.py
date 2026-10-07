import uuid

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command

from app.agent import graph as agent_graph
from app.agent import tools as agent_tools
from app.services import changeset, conversation, embedding


class ScriptedModel:
    """按预设脚本依次返回消息的假模型，测试不联网。"""

    def __init__(self, script):
        self._script = list(script)

    def bind_tools(self, tools):  # noqa: ARG002
        return self

    def invoke(self, messages, config=None):  # noqa: ARG002
        return self._script.pop(0)


def _tool_call(name, args=None, call_id="c1"):
    return AIMessage(
        content="",
        tool_calls=[{"name": name, "args": args or {}, "id": call_id, "type": "tool_call"}],
    )


def _ctx(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="图测试")
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)
    return conv, agent_tools.ToolContext(
        session=session,
        owner_id=owner,
        graph_id=conv.graph_id,
        changeset_id=cs.id,
        embedder=embedding.HashingEmbedder(),
    )


def test_graph_runs_a_tool_then_finishes(session):
    owner = uuid.uuid4()
    conv, ctx = _ctx(session, owner)
    model = ScriptedModel([_tool_call("list_types"), AIMessage(content="已经查完了。")])
    app = agent_graph.build_graph(
        model, agent_tools.build_model_tools(ctx), checkpointer=InMemorySaver()
    )

    result = app.invoke(
        {"messages": [HumanMessage("看看现在有哪些类型")], "iterations": 0},
        config={"configurable": {"thread_id": str(conv.id)}},
    )
    assert result["messages"][-1].content == "已经查完了。"
    assert any(type(m).__name__ == "ToolMessage" for m in result["messages"])


def test_graph_asks_user_then_resumes(session):
    owner = uuid.uuid4()
    conv, ctx = _ctx(session, owner)
    model = ScriptedModel(
        [
            _tool_call(
                "ask_user",
                {"question": "张三算客户还是供应商？", "options": ["客户", "供应商"]},
            ),
            AIMessage(content="好的，按客户处理。"),
        ]
    )
    app = agent_graph.build_graph(
        model, agent_tools.build_model_tools(ctx), checkpointer=InMemorySaver()
    )
    config = {"configurable": {"thread_id": str(conv.id)}}

    paused = app.invoke(
        {"messages": [HumanMessage("张三上周下了个订单")], "iterations": 0}, config=config
    )
    interrupts = paused.get("__interrupt__")
    assert interrupts
    assert interrupts[0].value["question"] == "张三算客户还是供应商？"
    assert interrupts[0].value["options"] == ["客户", "供应商"]

    resumed = app.invoke(Command(resume="客户"), config=config)
    assert resumed["messages"][-1].content == "好的，按客户处理。"


def test_graph_stops_at_max_iterations(session):
    owner = uuid.uuid4()
    conv, ctx = _ctx(session, owner)
    model = ScriptedModel([_tool_call("list_types", call_id=f"c{i}") for i in range(10)])
    app = agent_graph.build_graph(
        model,
        agent_tools.build_model_tools(ctx),
        checkpointer=InMemorySaver(),
        max_iterations=3,
    )
    result = app.invoke(
        {"messages": [HumanMessage("一直调工具")], "iterations": 0},
        config={"configurable": {"thread_id": str(conv.id)}},
    )
    assert result["iterations"] <= 3


def test_tool_errors_are_returned_to_the_model(session):
    owner = uuid.uuid4()
    conv, ctx = _ctx(session, owner)
    model = ScriptedModel(
        [
            _tool_call("get_subgraph", {"object_type_id": str(uuid.uuid4())}),
            AIMessage(content="那个节点不在图里。"),
        ]
    )
    app = agent_graph.build_graph(
        model, agent_tools.build_model_tools(ctx), checkpointer=InMemorySaver()
    )
    result = app.invoke(
        {"messages": [HumanMessage("看看这个节点周边")], "iterations": 0},
        config={"configurable": {"thread_id": str(conv.id)}},
    )
    tool_messages = [m for m in result["messages"] if type(m).__name__ == "ToolMessage"]
    assert "NotFoundError" in tool_messages[0].content
    assert result["messages"][-1].content == "那个节点不在图里。"
