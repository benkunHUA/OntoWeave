"""`test_conversation.py` 的共享 fixture、假客户端与构造 helper。"""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.main import create_app

API = "/api/v1"


def build_client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(create_app(session_factory=session_factory))


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    return build_client(session_factory)
