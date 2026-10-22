from fastapi.testclient import TestClient

from app.main import create_app


def test_health_returns_ok():
    client = TestClient(create_app())
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_checkpointer_is_initialized_during_startup(monkeypatch, session_factory):
    """检查点必须在请求事务之外建好，否则建索引会和首个流式请求互等。"""

    class FakePool:
        closed = False

        def close(self):
            self.closed = True

    pool = FakePool()
    monkeypatch.setattr(
        "app.agent.graph.build_checkpointer",
        lambda _url: (object(), pool),
    )

    app = create_app(session_factory=session_factory)
    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        assert app.state.checkpointer is not None

    assert pool.closed is True
