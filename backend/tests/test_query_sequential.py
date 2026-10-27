from __future__ import annotations

from langchain.agents.middleware import ModelRequest

from app.agent.tools.query.sequential import SequentialToolCallsMiddleware


def test_sequential_tool_calls_middleware_disables_parallel_tool_calls() -> None:
    captured: list[dict] = []
    request = ModelRequest(
        model=None,  # type: ignore[arg-type]
        messages=[],
        model_settings={"temperature": 0},
    )

    SequentialToolCallsMiddleware().wrap_model_call(
        request,
        lambda next_request: captured.append(next_request.model_settings) or {"ok": True},
    )

    assert captured == [{"temperature": 0, "parallel_tool_calls": False}]
