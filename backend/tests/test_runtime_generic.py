import json
import uuid

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
