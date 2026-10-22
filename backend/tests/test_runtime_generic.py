import json
import uuid

import psycopg
from langchain_core.messages import AIMessage, HumanMessage

from app.agent.definitions.base import AgentRunContext
from app.agent.runtime.stream import stream_run
from app.config import Settings
from app.services import conversation
from app.vocabulary import ConversationKind


class _FakeAgent:
    def stream(self, payload, config=None, stream_mode=None, subgraphs=False):  # noqa: ARG002
        assert isinstance(payload, dict)
        assert isinstance(payload["messages"][0], HumanMessage)
        yield {"model": {"messages": [AIMessage(content="通用运行时完成")]}}


class _FakeDefinition:
    kind = ConversationKind.DATA_CONNECTION
    visible_subagents = frozenset()

    def prepare_run(self, session, *, conversation, owner_id):  # noqa: ARG002
        return AgentRunContext(
            conversation_id=conversation.id,
            owner_id=owner_id,
            kind=self.kind,
            resource_id=conversation.resource_id,
        )

    def build_graph(self, session, settings, run, *, checkpointer=None):  # noqa: ARG002
        return _FakeAgent()

    def build_state_update(self, session, settings, run):  # noqa: ARG002
        return {"view": {}}


class _TurnCapturingDefinition(_FakeDefinition):
    def __init__(self) -> None:
        self.turn_ids: list[uuid.UUID | None] = []

    def build_graph(self, session, settings, run, *, checkpointer=None):  # noqa: ARG002
        self.turn_ids.append(run.turn_id)
        return _FakeAgent()


class _DatabaseBrokenAgent:
    def stream(self, payload, config=None, stream_mode=None, subgraphs=False):  # noqa: ARG002
        raise psycopg.OperationalError("consuming input failed: server closed the connection")
        yield  # pragma: no cover


class _DatabaseBrokenDefinition(_FakeDefinition):
    def build_graph(self, session, settings, run, *, checkpointer=None):  # noqa: ARG002
        return _DatabaseBrokenAgent()


def _events(text: str) -> list[dict]:
    return [
        json.loads(line.removeprefix("data: "))
        for line in text.splitlines()
        if line.startswith("data: ")
    ]


def test_stream_run_uses_a_non_ontology_definition(monkeypatch, session, session_factory):
    owner = uuid.uuid4()
    requested_kinds: list[str] = []

    def definition_for(kind: str) -> _FakeDefinition:
        requested_kinds.append(kind)
        return _FakeDefinition()

    stored = conversation.create_conversation(
        session,
        owner_id=owner,
        title="通用运行时",
        kind=ConversationKind.DATA_CONNECTION,
        resource_id=uuid.uuid4(),
    )
    session.commit()
    # 真实会话行仍用产品支持的 kind；只替换这种 kind 对应的运行策略。
    monkeypatch.setattr(
        "app.agent.runtime.stream.get_definition",
        definition_for,
    )

    frames = _events(
        "".join(
            stream_run(
                session_factory,
                Settings(),
                owner,
                conversation_id=stored.id,
                text="跑一轮",
            )
        )
    )

    kinds = [frame["type"] for frame in frames]
    assert kinds[0] == "run.start"
    assert "state.update" in kinds
    assert kinds[-1] == "run.end"
    assert frames[-1]["paused"] is False
    state = next(frame for frame in frames if frame["type"] == "state.update")
    assert state["view"] == {}
    assert requested_kinds == [ConversationKind.DATA_CONNECTION]


def test_stream_run_injects_explicit_or_generated_turn_id(monkeypatch, session, session_factory):
    owner = uuid.uuid4()
    definition = _TurnCapturingDefinition()
    stored = conversation.create_conversation(
        session,
        owner_id=owner,
        title="turn id",
        kind=ConversationKind.DATA_CONNECTION,
        resource_id=uuid.uuid4(),
    )
    session.commit()
    monkeypatch.setattr("app.agent.runtime.stream.get_definition", lambda kind: definition)

    explicit = uuid.uuid4()
    list(
        stream_run(
            session_factory,
            Settings(),
            owner,
            conversation_id=stored.id,
            text="第一轮",
            turn_id=explicit,
        )
    )
    list(
        stream_run(
            session_factory,
            Settings(),
            owner,
            conversation_id=stored.id,
            text="第二轮",
        )
    )

    assert definition.turn_ids[0] == explicit
    assert isinstance(definition.turn_ids[1], uuid.UUID)
    assert definition.turn_ids[1] != explicit


def test_stream_run_hides_database_disconnect_details(monkeypatch, session, session_factory):
    owner = uuid.uuid4()
    stored = conversation.create_conversation(
        session,
        owner_id=owner,
        title="数据库断连",
        kind=ConversationKind.DATA_CONNECTION,
        resource_id=uuid.uuid4(),
    )
    session.commit()
    monkeypatch.setattr(
        "app.agent.runtime.stream.get_definition",
        lambda kind: _DatabaseBrokenDefinition(),
    )

    frames = _events(
        "".join(
            stream_run(
                session_factory,
                Settings(),
                owner,
                conversation_id=stored.id,
                text="继续",
            )
        )
    )

    assert frames[-1]["type"] == "run.error"
    assert frames[-1]["message"] == "数据存储连接暂时中断，请重试。"
    history = conversation.list_messages(session, stored.id, owner_id=owner)
    assert history[-1].kind == "error"
    assert history[-1].content == "数据存储连接暂时中断，请重试。"
