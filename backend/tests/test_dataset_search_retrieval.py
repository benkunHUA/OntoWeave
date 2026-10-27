"""`test_dataset_search.py` 中 retrieval 相关测试。"""

from __future__ import annotations

import uuid

from app.services import dataset_search, embedding, ontology
from tests.support.dataset_search_fixtures import FakeDatasetClient, _detail


def test_retrieval_uses_vector_recall_then_type_and_required_rules(session):
    owner = uuid.uuid4()
    obj = ontology.create_object_type(session, owner_id=owner, name="订单", name_en="order")
    ontology.create_property(
        session, owner_type_id=obj.id, name="id", name_en="id", is_primary_key=True
    )
    ontology.create_property(
        session,
        owner_type_id=obj.id,
        name="下单时间",
        name_en="ordered_at",
        datatype="timestamp",
        required=True,
    )
    ontology.create_property(
        session,
        owner_type_id=obj.id,
        name="消费者id",
        name_en="customer_id",
        datatype="text",
    )
    embedder = embedding.HashingEmbedder()
    compatible = _detail(owner_id=owner, name="retail_sales.orders")
    incompatible = _detail(
        owner_id=owner,
        name="legacy_orders",
        columns=[
            {"name": "id", "type": "long", "optional": False},
            {"name": "ordered_at", "type": "long", "optional": True},
            {"name": "customer_id", "type": "long", "optional": True},
        ],
    )
    client = FakeDatasetClient([compatible, incompatible])
    for item in (compatible, incompatible):
        dataset_search.index_dataset_version(
            session,
            client=client,
            embedder=embedder,
            dataset_kind="SYNC",
            dataset_id=uuid.UUID(item["id"]),
            version_id=uuid.UUID(item["version_id"]),
        )

    result = dataset_search.find_bindable_datasets(
        session,
        owner_id=owner,
        subject_kind="object_type",
        subject_id=obj.id,
        embedder=embedder,
    )

    assert [item["name"] for item in result["items"]] == ["retail_sales.orders"]
    assert result["items"][0]["status"] == "READY"
    assert result["items"][0]["primary_key_column"] == "id"
    assert result["items"][0]["missing_required"] == []
    assert result["near_misses"] == []


def test_retrieval_returns_near_miss_when_only_incompatible_types_exist(session):
    owner = uuid.uuid4()
    obj = ontology.create_object_type(session, owner_id=owner, name="订单", name_en="order")
    ontology.create_property(
        session, owner_type_id=obj.id, name="id", name_en="id", is_primary_key=True
    )
    detail = _detail(
        owner_id=owner,
        name="legacy_orders",
        columns=[{"name": "id", "type": "long", "optional": False}],
    )
    client = FakeDatasetClient([detail])
    embedder = embedding.HashingEmbedder()
    dataset_search.index_dataset_version(
        session,
        client=client,
        embedder=embedder,
        dataset_kind="SYNC",
        dataset_id=uuid.UUID(detail["id"]),
        version_id=uuid.UUID(detail["version_id"]),
    )

    result = dataset_search.find_bindable_datasets(
        session,
        owner_id=owner,
        subject_kind="object_type",
        subject_id=obj.id,
        embedder=embedder,
    )

    assert result["items"] == []
    assert result["near_misses"][0]["name"] == "legacy_orders"
    assert result["near_misses"][0]["reason"] == "缺少类型兼容的主键列"


def test_relation_retrieval_requires_both_endpoint_keys(session):
    owner = uuid.uuid4()
    source = ontology.create_object_type(session, owner_id=owner, name="客户", name_en="customer")
    target = ontology.create_object_type(session, owner_id=owner, name="订单", name_en="order")
    source_key = ontology.create_property(
        session, owner_type_id=source.id, name="id", name_en="id", is_primary_key=True
    )
    target_key = ontology.create_property(
        session,
        owner_type_id=target.id,
        name="id",
        name_en="id",
        datatype="integer",
        is_primary_key=True,
    )
    relation = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=source.id,
        target_type_id=target.id,
        name="下订单",
        cardinality="many_to_many",
    )
    detail = _detail(
        owner_id=owner,
        name="analytics.customer_orders",
        kind="PIPELINE",
        columns=[
            {"name": "customer_id", "type": "string", "optional": False},
            {"name": "order_id", "type": "long", "optional": False},
        ],
    )
    client = FakeDatasetClient([detail])
    embedder = embedding.HashingEmbedder()
    dataset_search.index_dataset_version(
        session,
        client=client,
        embedder=embedder,
        dataset_kind="PIPELINE",
        dataset_id=uuid.UUID(detail["id"]),
        version_id=uuid.UUID(detail["version_id"]),
    )

    result = dataset_search.find_bindable_datasets(
        session,
        owner_id=owner,
        subject_kind="relation",
        subject_id=relation.id,
        embedder=embedder,
    )

    item = result["items"][0]
    assert item["status"] == "READY"
    assert item["primary_key_columns"] == {"源主键": "customer_id", "目标主键": "order_id"}
    assert source_key.name_en == "id"
    assert target_key.name_en == "id"
