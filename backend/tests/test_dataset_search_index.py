"""`test_dataset_search.py` 中 index 相关测试。"""

from __future__ import annotations

import uuid

from sqlalchemy import select

from app.models import DatasetSearchIndex
from app.services import dataset_search, embedding
from tests.support.dataset_search_fixtures import FailingEmbedder, FakeDatasetClient, _detail


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
