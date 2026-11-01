from __future__ import annotations

from collections.abc import Sequence

import pytest
from sqlalchemy.orm import Session

from app.constants import EMBEDDING_DIM
from app.errors import InvalidRequestError
from app.models import ObjectType
from app.services import action, graph, status
from app.services.graph import publication as graph_publication
from tests.support.action_presentation_fixtures import graph_with_close_ticket_action


class _RecordingEmbedder:
    dim = EMBEDDING_DIM

    def __init__(self) -> None:
        self.texts: list[str] = []

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        self.texts.extend(texts)
        return [[0.0] * self.dim for _ in texts]


def test_graph_card_contains_actions_and_embeds_the_whole_card(session: Session):
    ids = graph_with_close_ticket_action(session)
    embedder = _RecordingEmbedder()

    publication = graph_publication.publish_graph(
        session,
        graph_id=ids["graph_id"],
        owner_id=ids["owner"],
        embedder=embedder,
    )

    assert "可执行动作：" in publication.graph_card
    assert "关闭工单" in publication.graph_card
    assert "参数：工单（对象：工单，必填）" in publication.graph_card
    assert "影响：修改 工单；新增 使用配件" in publication.graph_card
    assert "校验：工单.状态 是 处理中" in publication.graph_card
    assert embedder.texts == [publication.graph_card]


def test_publish_rejects_draft_action_elements(session: Session):
    ids = graph_with_close_ticket_action(session)
    parameter = action.list_parameters(session, ids["action_id"])[0]
    parameter.status = status.DRAFT
    session.flush()

    with pytest.raises(InvalidRequestError, match="unpublished actions"):
        graph_publication.publish_graph(
            session,
            graph_id=ids["graph_id"],
            owner_id=ids["owner"],
            embedder=_RecordingEmbedder(),
        )


def test_publish_rejects_action_elements_outside_graph(session: Session):
    ids = graph_with_close_ticket_action(session)
    graph.remove_object_from_graph(session, ids["graph_id"], ids["part_id"])

    with pytest.raises(InvalidRequestError, match="outside the graph"):
        graph_publication.publish_graph(
            session,
            graph_id=ids["graph_id"],
            owner_id=ids["owner"],
            embedder=_RecordingEmbedder(),
        )


def test_publish_rejects_confirmed_action_with_draft_target_object(session: Session):
    ids = graph_with_close_ticket_action(session)
    ticket = session.get(ObjectType, ids["ticket_id"])
    ticket.status = status.DRAFT
    session.flush()

    with pytest.raises(InvalidRequestError, match="unpublished elements"):
        graph_publication.publish_graph(
            session,
            graph_id=ids["graph_id"],
            owner_id=ids["owner"],
            embedder=_RecordingEmbedder(),
        )
