"""定稿结果写回模型线程：撤销 / 确认之后，模型下一轮不该再引用已删除的对象。

写回方式是把旁白并进上一条助手消息，线程保持严格 user/assistant 交替，
避免「旁白 + 用户提问」两条连续 user。

播种线程一律走真实的 `stream`（假模型），不走 `update_state`——后者在这套
deepagents 图里不给消息分配 id，会掩盖「按 id 覆盖」这条真实路径。
"""

import uuid

import pytest
from langchain_core.language_models.fake_chat_models import GenericFakeChatModel
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import InMemorySaver

from app.agent import graph as agent_graph
from app.agent import runtime, thread
from app.agent.prompts import SYSTEM_PROMPT
from app.agent.tools import OntologyToolContext, build_model_tools
from app.config import Settings
from app.services import changeset, conversation, embedding


class _ScriptedModel(GenericFakeChatModel):
    """按脚本吐消息的假模型：只用来把线程推到某个状态，不发网络请求。"""

    def bind_tools(self, tools, **kwargs):
        return self


def _conversation(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="线程回写")
    cs = changeset.create_changeset(session, graph_id=conv.resource_id, owner_id=owner)
    return owner, conv, cs


def _agent(saver, *script):
    ctx = OntologyToolContext(
        session=None,
        owner_id=uuid.uuid4(),
        graph_id=uuid.uuid4(),
        changeset_id=uuid.uuid4(),
        embedder=embedding.HashingEmbedder(),
    )
    return agent_graph.build_agent(
        _ScriptedModel(messages=iter(script)),
        build_model_tools(ctx),
        system_prompt=SYSTEM_PROMPT,
        subagents=[],
        checkpointer=saver,
    )


def _seed(conv, saver, *script):
    """跑一轮真实 stream，把线程推到「助手刚回复完」的状态。"""
    agent = _agent(saver, *script)
    cfg = runtime.thread_config(conv.id)
    list(agent.stream({"messages": [HumanMessage("新建景点")]}, config=cfg))
    return agent, cfg


def _messages(agent, cfg):
    return agent.get_state(cfg).values["messages"]


def test_append_note_merges_into_the_last_assistant_message(session):
    _, conv, _ = _conversation(session)
    saver = InMemorySaver()
    agent, cfg = _seed(conv, saver, AIMessage("好的，已加入草稿。"))
    assert isinstance(_messages(agent, cfg)[-1], AIMessage)

    thread.append_note(agent, conv.id, "（系统回执）用户已撤销本次改动。")

    messages = _messages(agent, cfg)
    assert len(messages) == 2  # 不新增消息
    assert isinstance(messages[-1], AIMessage)
    assert messages[-1].content == "好的，已加入草稿。\n\n（系统回执）用户已撤销本次改动。"


def test_append_note_preserves_tool_calls_on_a_pending_assistant_message(session):
    """中断挂在工具调用上时，最后一条助手消息带 tool_calls，合并不能把它们弄丢。"""
    _, conv, _ = _conversation(session)
    saver = InMemorySaver()
    call = AIMessage(
        content="",
        tool_calls=[{"name": "ask_user", "id": "c1", "args": {"question": "去哪了?"}}],
    )
    agent, cfg = _seed(conv, saver, call)

    thread.append_note(agent, conv.id, "（系统回执）用户已撤销本次改动。")

    last = _messages(agent, cfg)[-1]
    assert last.tool_calls == call.tool_calls
    assert last.content.endswith("（系统回执）用户已撤销本次改动。")


def test_append_note_falls_back_to_appending_an_assistant_message(session):
    """线程为空时没有可合并的助手消息，退化成追加一条 assistant。"""
    _, conv, _ = _conversation(session)
    agent = _agent(InMemorySaver())

    thread.append_note(agent, conv.id, "（系统回执）用户已撤销本次改动。")

    messages = _messages(agent, runtime.thread_config(conv.id))
    assert len(messages) == 1
    assert isinstance(messages[0], AIMessage)
    assert messages[0].content == "（系统回执）用户已撤销本次改动。"


def test_append_note_after_a_user_message_does_not_merge_into_it(session):
    """最后一条是 user 时不能把旁白塞进用户的话里，只能追加 assistant。"""
    _, conv, _ = _conversation(session)
    agent = _agent(InMemorySaver())
    cfg = runtime.thread_config(conv.id)
    agent.update_state(cfg, {"messages": [HumanMessage("把布局改成网格")]})

    thread.append_note(agent, conv.id, "（系统回执）用户已撤销本次改动。")

    messages = _messages(agent, cfg)
    assert [type(m).__name__ for m in messages] == ["HumanMessage", "AIMessage"]
    assert messages[0].content == "把布局改成网格"
    assert messages[1].content == "（系统回执）用户已撤销本次改动。"


def test_record_decision_writes_back_into_the_thread(session):
    owner, conv, cs = _conversation(session)
    saver = InMemorySaver()
    agent, cfg = _seed(conv, saver, AIMessage("好的，已加入草稿。"))

    thread.record_decision(
        session,
        Settings(chat_api_key="fake-key"),
        owner,
        conversation_id=conv.id,
        graph_id=conv.resource_id,
        changeset_id=cs.id,
        text="（系统回执）用户已撤销本次改动。",
        checkpointer=saver,
    )

    last = _messages(agent, cfg)[-1]
    assert last.content == "好的，已加入草稿。\n\n（系统回执）用户已撤销本次改动。"


def test_record_decision_without_checkpointer_is_a_silent_noop(session):
    """没开检查点（例如单元测试）时不能连库、也不能抛错。"""
    owner, conv, cs = _conversation(session)

    thread.record_decision(
        session,
        Settings(chat_api_key="fake-key"),
        owner,
        conversation_id=conv.id,
        graph_id=conv.resource_id,
        changeset_id=cs.id,
        text="（系统回执）用户已撤销本次改动。",
        checkpointer=None,
    )


@pytest.mark.parametrize("mismatch", ["owner", "graph"])
def test_record_decision_ignores_mismatched_context(session, monkeypatch, mismatch):
    """归属或图对不上时宁可不写，也不能把旁白写进错误的模型线程。"""
    owner, conv, cs = _conversation(session)
    saver = InMemorySaver()
    agent, cfg = _seed(conv, saver, AIMessage("好的，已加入草稿。"))
    calls: list[tuple] = []
    monkeypatch.setattr(thread, "append_note", lambda *args, **kwargs: calls.append(args))

    thread.record_decision(
        session,
        Settings(chat_api_key="fake-key"),
        uuid.uuid4() if mismatch == "owner" else owner,
        conversation_id=conv.id,
        graph_id=uuid.uuid4() if mismatch == "graph" else conv.resource_id,
        changeset_id=cs.id,
        text="（系统回执）用户已撤销本次改动。",
        checkpointer=saver,
    )

    assert calls == []
    assert _messages(agent, cfg)[-1].content == "好的，已加入草稿。"
