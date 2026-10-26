from __future__ import annotations

import uuid

from sqlalchemy import select

from app.models import EMBEDDING_DIM, DatasetSearchIndex
from app.services import dataset_search, embedding, ontology


class FakeDatasetClient:
    def __init__(self, details: list[dict]) -> None:
        self.details = {
            (item["kind"], uuid.UUID(str(item["id"])), uuid.UUID(str(item["version_id"]))): item
            for item in details
        }
        self.page_items = list(details)

    def get_bindable_dataset(self, *, dataset_kind, dataset_id, version_id, owner_id=None):
        del owner_id
        return dict(self.details[(dataset_kind, dataset_id, version_id)])

    def list_bindable_datasets(self, *, owner_id, page=1, size=20):
        del owner_id
        start = (page - 1) * size
        items = self.page_items[start : start + size]
        return {"items": items, "total": len(self.page_items), "page": page, "size": size}


class FailingEmbedder:
    dim = EMBEDDING_DIM

    def embed(self, texts):
        del texts
        raise RuntimeError("embedding down")


def _detail(
    *,
    owner_id: uuid.UUID,
    name: str,
    version_number: int = 1,
    dataset_id: uuid.UUID | None = None,
    version_id: uuid.UUID | None = None,
    kind: str = "SYNC",
    columns: list[dict] | None = None,
) -> dict:
    return {
        "kind": kind,
        "id": str(dataset_id or uuid.uuid4()),
        "name": name,
        "version_id": str(version_id or uuid.uuid4()),
        "version_number": version_number,
        "row_count": 10,
        "owner_id": str(owner_id),
        "published_at": "2026-10-24T10:00:00Z",
        "columns": columns
        or [
            {"name": "id", "type": "string", "optional": False},
            {"name": "ordered_at", "type": "timestamp", "optional": True},
            {"name": "customer_id", "type": "string", "optional": True},
        ],
    }


def test_index_write_is_idempotent_and_switches_current(session):
    owner = uuid.uuid4()
    dataset_id = uuid.uuid4()
    first = _detail(
        owner_id=owner, name="retail_sales.orders", dataset_id=dataset_id, version_number=1
    )
    client = FakeDatasetClient([first])
    embedder = embedding.HashingEmbedder()

    row = dataset_search.index_dataset_version(
        session,
        client=client,
        embedder=embedder,
        dataset_kind="SYNC",
        dataset_id=dataset_id,
        version_id=uuid.UUID(first["version_id"]),
    )
    assert row.status == "ready"
    assert row.embedding is not None
    assert set(row.column_types) == {"string", "timestamp"}

    dataset_search.index_dataset_version(
        session,
        client=client,
        embedder=embedder,
        dataset_kind="SYNC",
        dataset_id=dataset_id,
        version_id=uuid.UUID(first["version_id"]),
    )
    assert len(session.scalars(select(DatasetSearchIndex)).all()) == 1

    second = _detail(
        owner_id=owner, name="retail_sales.orders", dataset_id=dataset_id, version_number=2
    )
    client.details[("SYNC", dataset_id, uuid.UUID(second["version_id"]))] = second
    client.page_items.append(second)
    dataset_search.index_dataset_version(
        session,
        client=client,
        embedder=embedder,
        dataset_kind="SYNC",
        dataset_id=dataset_id,
        version_id=uuid.UUID(second["version_id"]),
    )
    rows = session.scalars(
        select(DatasetSearchIndex).order_by(DatasetSearchIndex.version_number)
    ).all()
    assert [(row.version_number, row.is_current) for row in rows] == [(1, False), (2, True)]


def test_embedding_failure_is_recorded_without_raising(session):
    owner = uuid.uuid4()
    detail = _detail(owner_id=owner, name="retail.orders")
    row = dataset_search.index_dataset_version(
        session,
        client=FakeDatasetClient([detail]),
        embedder=FailingEmbedder(),
        dataset_kind="SYNC",
        dataset_id=uuid.UUID(detail["id"]),
        version_id=uuid.UUID(detail["version_id"]),
    )
    assert row.status == "failed"
    assert row.embedding is None
    assert "embedding down" in (row.error_message or "")


def test_publish_events_index_sync_and_pipeline_outputs(session_factory):
    owner = uuid.uuid4()
    sync = _detail(owner_id=owner, name="retail.customers")
    pipeline = _detail(owner_id=owner, name="analytics.order_stats", kind="PIPELINE")
    client = FakeDatasetClient([sync, pipeline])
    embedder = embedding.HashingEmbedder()

    assert (
        dataset_search.consume_published_event(
            session_factory,
            {
                "eventType": "dataset.version.published",
                "payload": {"datasetId": sync["id"], "versionId": sync["version_id"]},
            },
            client=client,
            embedder=embedder,
        )
        == 1
    )
    assert (
        dataset_search.consume_published_event(
            session_factory,
            {
                "eventType": "pipeline.build.succeeded",
                "payload": {
                    "outputs": [
                        {"datasetId": pipeline["id"], "datasetVersionId": pipeline["version_id"]}
                    ]
                },
            },
            client=client,
            embedder=embedder,
        )
        == 1
    )

    with session_factory() as session:
        rows = session.scalars(
            select(DatasetSearchIndex).order_by(DatasetSearchIndex.dataset_kind)
        ).all()
    assert {(row.dataset_kind, row.status) for row in rows} == {
        ("PIPELINE", "ready"),
        ("SYNC", "ready"),
    }


def test_backfill_indexes_every_current_bindable_dataset(session):
    owner = uuid.uuid4()
    first = _detail(owner_id=owner, name="retail.orders")
    second = _detail(owner_id=owner, name="retail.customers", kind="PIPELINE")
    count = dataset_search.backfill_owner(
        session,
        client=FakeDatasetClient([first, second]),
        embedder=embedding.HashingEmbedder(),
        owner_id=owner,
    )
    assert count == 2
    assert len(session.scalars(select(DatasetSearchIndex)).all()) == 2


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
