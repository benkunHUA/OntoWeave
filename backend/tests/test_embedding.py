import uuid

import pytest

from app.errors import InvariantViolationError, NotFoundError
from app.services import embedding, ontology


def test_hashing_embedder_is_deterministic_and_normalised():
    emb = embedding.HashingEmbedder(dim=64)
    vector = emb.embed(["客户"])[0]
    assert vector == emb.embed(["客户"])[0]
    assert len(vector) == 64
    assert abs(sum(value * value for value in vector) - 1.0) < 1e-9


def test_similar_text_scores_higher_than_unrelated_text():
    emb = embedding.HashingEmbedder(dim=256)
    customer = emb.embed(["客户"])[0]
    close = emb.embed(["客户公司"])[0]
    far = emb.embed(["完全无关的词汇"])[0]
    assert embedding.cosine(customer, close) > embedding.cosine(customer, far)


def test_save_and_search_embeddings(session):
    emb = embedding.HashingEmbedder()
    owner = uuid.uuid4()
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    other = ontology.create_object_type(session, owner_id=owner, name="订单")
    for item in (obj, other):
        embedding.save_embedding(session, object_type_id=item.id, embedder=emb)

    hits = embedding.search_object_types(
        session, query="客户公司", embedder=emb, owner_id=owner, limit=5
    )
    assert hits[0][0].name == "客户"
    assert hits[0][1] > hits[-1][1]


def test_search_skips_rows_without_embedding(session):
    emb = embedding.HashingEmbedder()
    owner = uuid.uuid4()
    ontology.create_object_type(session, owner_id=owner, name="没有向量的类型")
    assert (
        embedding.search_object_types(session, query="客户", embedder=emb, owner_id=owner, limit=5)
        == []
    )


def test_search_filters_by_owner(session):
    emb = embedding.HashingEmbedder()
    mine, other = uuid.uuid4(), uuid.uuid4()
    mine_obj = ontology.create_object_type(session, owner_id=mine, name="客户")
    other_obj = ontology.create_object_type(session, owner_id=other, name="客户")
    for item in (mine_obj, other_obj):
        embedding.save_embedding(session, object_type_id=item.id, embedder=emb)

    hits = embedding.search_object_types(
        session, query="客户", embedder=emb, owner_id=mine, limit=5
    )
    assert [obj.id for obj, _ in hits] == [mine_obj.id]


def test_save_embedding_rejects_unknown_object(session):
    with pytest.raises(NotFoundError):
        embedding.save_embedding(
            session,
            object_type_id=uuid.uuid4(),
            embedder=embedding.HashingEmbedder(),
        )


def test_embedder_dimension_mismatch_is_reported_clearly(session):
    with pytest.raises(InvariantViolationError):
        embedding.save_embedding(
            session,
            object_type_id=uuid.uuid4(),
            embedder=embedding.HashingEmbedder(dim=256),
        )
    with pytest.raises(InvariantViolationError):
        embedding.search_object_types(
            session, query="客户", embedder=embedding.HashingEmbedder(dim=256)
        )
