"""子智能体的过程可见性：命名空间绑回委派调用，以及只有登记的清单才往外流。"""

import json
import uuid

from langchain_core.language_models.fake_chat_models import GenericFakeChatModel
from langchain_core.messages import AIMessage, AIMessageChunk, HumanMessage
from langchain_core.outputs import ChatGenerationChunk
from langchain_core.tools import tool
from sqlalchemy import select

from app.agent import graph as agent_graph
from app.agent.runtime import assembly, subagent_trace
from app.agent.runtime.stream import stream_run
from app.agent.tools.action.subagent import ACTION_AGENT_NAME
from app.config import Settings
from app.models import ConversationMessage
from app.services import conversation


@tool("probe_tool")
def probe_tool(value: str) -> str:
    """子智能体内部的假工具，只为了让子图命名空间真的出现。"""
    return f"probe:{value}"


class _ScriptedModel(GenericFakeChatModel):
    """按脚本吐消息的假模型：主智能体与子智能体共用一条脚本队列。

    必须自己实现 `_stream`：编排层订阅了 `messages` 流，基类的假模型不支持流式，
    会直接抛「No generations found in stream」。
    """

    def bind_tools(self, tools, **kwargs):  # noqa: ARG002
        return self

    def _stream(self, messages, stop=None, run_manager=None, **kwargs):  # noqa: ARG002
        message = next(self.messages)
        if message.content:
            yield ChatGenerationChunk(message=AIMessageChunk(content=message.content))
        for index, call in enumerate(message.tool_calls or []):
            yield ChatGenerationChunk(
                message=AIMessageChunk(
                    content="",
                    tool_call_chunks=[
                        {
                            "name": str(call["name"]),
                            "args": json.dumps(call["args"]),
                            "id": str(call["id"]),
                            "index": index,
                            "type": "tool_call_chunk",
                        }
                    ],
                )
            )


def _task_call(call_id: str, subagent_type: str) -> AIMessage:
    return AIMessage(
        content="",
        tool_calls=[
            {
                "name": "task",
                "id": call_id,
                "args": {"description": "干活", "subagent_type": subagent_type},
            }
        ],
    )


def _probe_call(call_id: str) -> AIMessage:
    return AIMessage(
        content="", tool_calls=[{"name": "probe_tool", "id": call_id, "args": {"value": "x"}}]
    )


def _subagents() -> list[dict]:
    """两个子智能体：动作的登记为可见，视觉的默认不外露内部过程。"""
    return [
        {
            "name": ACTION_AGENT_NAME,
            "description": "动作建模",
            "system_prompt": "你只调 probe_tool。",
            "tools": [probe_tool],
        },
        {
            "name": "view_designer",
            "description": "绘图",
            "system_prompt": "你只调 probe_tool。",
            "tools": [probe_tool],
        },
    ]


def _script() -> list[AIMessage]:
    return [
        _task_call("t1", ACTION_AGENT_NAME),
        _probe_call("p1"),
        AIMessage(content="动作定义好了"),
        _task_call("t2", "view_designer"),
        _probe_call("p2"),
        AIMessage(content="图也画好了"),
        AIMessage(content="都办完了"),
    ]


def _scripted_agent():
    return agent_graph.build_agent(
        _ScriptedModel(messages=iter(_script())), [], subagents=_subagents()
    )


def test_binds_a_namespace_to_the_task_call_that_started_it():
    trace = subagent_trace.SubagentTrace()
    trace.note_task_call(call_id="c1", subagent_type=ACTION_AGENT_NAME)

    call = trace.call_for(("tools:abc",))

    assert call is not None
    assert (call.call_id, call.name, call.visible) == ("c1", ACTION_AGENT_NAME, True)
    # 同一个命名空间会反复出现，绑定只做一次，后面直接复用。
    assert trace.call_for(("tools:abc",)) == call


def test_top_level_namespace_is_the_main_agent():
    assert subagent_trace.SubagentTrace().call_for(()) is None


def test_hidden_subagent_keeps_its_internals_back():
    trace = subagent_trace.SubagentTrace()
    trace.note_task_call(call_id="c1", subagent_type="view_designer")

    call = trace.call_for(("tools:abc",))

    assert call is not None
    assert call.visible is False


def test_namespace_without_a_pending_task_call_is_unknown():
    assert subagent_trace.SubagentTrace().call_for(("tools:abc",)) is None


def test_a_finished_task_call_is_not_bound_to_a_later_namespace():
    trace = subagent_trace.SubagentTrace()
    trace.note_task_call(call_id="c1", subagent_type=ACTION_AGENT_NAME)
    trace.note_tool_end("c1")

    assert trace.call_for(("tools:abc",)) is None


def test_parallel_delegations_are_bound_in_the_order_they_started():
    trace = subagent_trace.SubagentTrace()
    trace.note_task_call(call_id="c1", subagent_type=ACTION_AGENT_NAME)
    trace.note_task_call(call_id="c2", subagent_type="view_designer")

    first = trace.call_for(("tools:one",))
    second = trace.call_for(("tools:two",))

    assert first is not None and first.call_id == "c1"
    assert second is not None and second.call_id == "c2"


def test_real_graph_namespaces_map_back_to_the_delegating_call():
    """真图跑一遍：命名空间确实长成 tools:<id>，且两支子智能体都能认出来。"""
    trace = subagent_trace.SubagentTrace()
    bindings: dict[str, tuple[str, str, bool]] = {}

    for namespace, _mode, data in _scripted_agent().stream(
        {"messages": [HumanMessage("建个动作，再把图画一下")]},
        stream_mode=["updates"],
        subgraphs=True,
    ):
        if not namespace:
            # 主智能体自己发起的 task 调用，先登记下来。
            for _node, update in (data or {}).items():
                for message in (update or {}).get("messages", []) or []:
                    if not isinstance(message, AIMessage):
                        continue
                    for call in message.tool_calls or []:
                        if call.get("name") == "task":
                            trace.note_task_call(
                                call_id=str(call.get("id")),
                                subagent_type=str((call.get("args") or {}).get("subagent_type")),
                            )
            continue
        call = trace.call_for(namespace)
        if call is not None:
            bindings[str(namespace[0])] = (call.call_id, call.name, call.visible)

    assert sorted(bindings.values()) == [
        ("t1", ACTION_AGENT_NAME, True),
        ("t2", "view_designer", False),
    ]


def test_stream_run_tags_subagent_steps_and_hides_the_rest(monkeypatch, session, session_factory):
    """编排那一层：可见子智能体的步骤带 agent / parentCallId，隐藏的那支只剩委派行。"""
    monkeypatch.setattr(assembly, "build_agent_for_run", lambda *args, **kwargs: _scripted_agent())

    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="子智能体可见性")
    session.commit()

    frames = [
        json.loads(line.removeprefix("data: "))
        for line in stream_run(
            session_factory,
            # Langfuse 关掉：这轮不该往外面发审计。
            Settings(chat_api_key="fake-key", langfuse_public_key="", langfuse_secret_key=""),
            owner,
            conversation_id=conv.id,
            text="建个动作，再把图画一下",
        )
        if line.startswith("data: ")
    ]
    started = [frame for frame in frames if frame["type"] == "tool.start"]

    assert [(frame["name"], frame.get("agent")) for frame in started] == [
        ("task", None),
        ("probe_tool", ACTION_AGENT_NAME),
        ("task", None),
    ]
    assert started[1]["parentCallId"] == started[0]["callId"]
    assert not any(frame.get("agent") == "view_designer" for frame in started)

    # 落库的块与线上一份形状：刷新页面后子智能体的步骤照样画得出来。
    with session_factory() as fresh:
        stored = fresh.scalars(
            select(ConversationMessage).where(ConversationMessage.conversation_id == conv.id)
        ).all()
    blocks = [block for message in stored for block in (message.blocks or [])]
    steps = [block for block in blocks if block.get("agent") == ACTION_AGENT_NAME]
    assert [block["name"] for block in steps] == ["probe_tool"]
    assert steps[0]["parentCallId"] == started[0]["callId"]
