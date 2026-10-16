from __future__ import annotations

import sys
import types
import uuid

from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from app.agent import observability
from app.config import Settings


def test_trace_disabled_without_keys() -> None:
    """没配 key 时完全不碰 langfuse，链路零开销。"""
    # 显式传空值，隔离开发者本地 .env 里的真实 key。
    settings = Settings(langfuse_public_key="", langfuse_secret_key="")
    assert observability.build_trace_callback(settings) is None
    assert (
        observability.trace_config(settings, conversation_id=uuid.uuid4(), owner_id=uuid.uuid4())
        == {}
    )


def test_trace_config_carries_session_and_user(monkeypatch) -> None:
    sentinel = object()
    monkeypatch.setattr(observability, "build_trace_callback", lambda settings: sentinel)
    settings = Settings(langfuse_public_key="pk", langfuse_secret_key="sk")
    conversation_id = uuid.uuid4()
    owner_id = uuid.uuid4()

    config = observability.trace_config(
        settings, conversation_id=conversation_id, owner_id=owner_id
    )

    assert config["callbacks"] == [sentinel]
    assert config["metadata"]["langfuse_session_id"] == str(conversation_id)
    assert config["metadata"]["langfuse_user_id"] == str(owner_id)


def test_build_trace_callback_uses_configured_client(monkeypatch) -> None:
    """凭证按配置传给 Langfuse 客户端，handler 从同一客户端取。"""
    captured: dict = {}

    class FakeLangfuse:
        def __init__(self, **kwargs: object) -> None:
            captured["client"] = kwargs

    class FakeHandler:
        pass

    fake_langfuse = types.ModuleType("langfuse")
    fake_langfuse.Langfuse = FakeLangfuse  # type: ignore[attr-defined]
    fake_langchain = types.ModuleType("langfuse.langchain")
    fake_langchain.CallbackHandler = FakeHandler  # type: ignore[attr-defined]
    monkeypatch.setitem(sys.modules, "langfuse", fake_langfuse)
    monkeypatch.setitem(sys.modules, "langfuse.langchain", fake_langchain)

    settings = Settings(
        langfuse_public_key="pk",
        langfuse_secret_key="sk",
        langfuse_base_url="http://localhost:3000",
        langfuse_environment="dev",
    )
    handler = observability.build_trace_callback(settings)

    assert isinstance(handler, FakeHandler)
    assert captured["client"]["public_key"] == "pk"
    assert captured["client"]["secret_key"] == "sk"
    assert captured["client"]["base_url"] == "http://localhost:3000"
    assert captured["client"]["environment"] == "dev"


class _RecordingAgent:
    """记下 langgraph config，用来验证追踪回调真的挂上去了。"""

    def __init__(self) -> None:
        self.configs: list[dict] = []

    def stream(self, payload, config=None, stream_mode=None, subgraphs=False):  # noqa: ARG002
        self.configs.append(config)
        yield {"model": {"messages": [AIMessage(content="好的")]}}


def test_runtime_attaches_trace_callback_to_config(session_factory, monkeypatch) -> None:
    sentinel = object()
    monkeypatch.setattr("app.agent.observability.build_trace_callback", lambda settings: sentinel)
    monkeypatch.setattr(
        "app.api.agent.get_settings",
        lambda: Settings(langfuse_public_key="pk", langfuse_secret_key="sk"),
    )
    agent = _RecordingAgent()
    monkeypatch.setattr("app.agent.runtime.assembly.build_agent_for_run", lambda *a, **k: agent)

    from app.main import _create_app

    client = TestClient(_create_app(session_factory, enable_agent_checkpoints=False))
    conversation = client.post("/api/v1/conversations", json={"title": "追踪"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conversation['id']}/messages",
        json={"text": "帮我建客户类型"},
    ) as response:
        "".join(response.iter_text())

    config = agent.configs[-1]
    assert config["callbacks"] == [sentinel]
    assert config["metadata"]["langfuse_session_id"] == conversation["id"]
    assert config["configurable"]["thread_id"] == conversation["id"]


def test_runtime_omits_callbacks_when_unconfigured(session_factory, monkeypatch) -> None:
    monkeypatch.setattr(
        "app.api.agent.get_settings",
        lambda: Settings(langfuse_public_key="", langfuse_secret_key=""),
    )
    agent = _RecordingAgent()
    monkeypatch.setattr("app.agent.runtime.assembly.build_agent_for_run", lambda *a, **k: agent)

    from app.main import _create_app

    client = TestClient(_create_app(session_factory, enable_agent_checkpoints=False))
    conversation = client.post("/api/v1/conversations", json={"title": "无追踪"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conversation['id']}/messages",
        json={"text": "随便说说"},
    ) as response:
        "".join(response.iter_text())

    assert "callbacks" not in agent.configs[-1]
