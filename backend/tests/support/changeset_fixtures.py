"""`test_changeset.py` 的共享 fixture、假客户端与构造 helper。"""

import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete
from sqlalchemy.orm import Session, sessionmaker

from app.config import get_settings
from app.main import create_app
from app.models import Conversation, Graph
from app.services import conversation

API = "/api/v1"


def build_client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(create_app(session_factory=session_factory))


@pytest.fixture
def api_client(session_factory: sessionmaker[Session]) -> TestClient:
    return build_client(session_factory)


def _default_user() -> uuid.UUID:
    """接口层全部按默认用户过滤，测试数据也要挂在这个用户下。"""
    return uuid.UUID(get_settings().default_user_id)


def _graph_id(session) -> uuid.UUID:
    """接口层按默认用户过滤，这里也用默认用户建图，保证接口看得见。"""
    return conversation.create_conversation(
        session, owner_id=_default_user(), title="测试"
    ).resource_id


def _delete_conversation_and_graph(
    session: Session, *, conversation_id: uuid.UUID, graph_id: uuid.UUID
) -> None:
    session.execute(delete(Conversation).where(Conversation.id == conversation_id))
    session.execute(delete(Graph).where(Graph.id == graph_id))
    session.commit()
