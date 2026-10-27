"""数据集与版本读取。"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.clients.data_service import mapping
from app.clients.data_service.client import DataServiceClient
from app.services.data_connection.connections import get_state


def list_datasets(
    session: Session,
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    connection_id: uuid.UUID,
) -> list[dict]:
    return get_state(session, client, owner_id=owner_id, connection_id=connection_id)["datasets"]


def get_dataset(client: DataServiceClient, *, owner_id: uuid.UUID, dataset_id: uuid.UUID) -> dict:
    return mapping.dataset(client.get_dataset(dataset_id, owner_id=owner_id))


def get_dataset_versions(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    dataset_id: uuid.UUID,
    limit: int,
    offset: int,
) -> dict:
    row = client.get_dataset_versions(dataset_id, owner_id=owner_id, limit=limit, offset=offset)
    return {
        "dataset_id": row.get("datasetId"),
        "total": row.get("total", 0),
        "limit": row.get("limit", limit),
        "offset": row.get("offset", offset),
        "versions": [mapping.dataset_version(item) for item in row.get("versions") or []],
    }


def get_dataset_version(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    dataset_id: uuid.UUID,
    version_id: uuid.UUID,
) -> dict:
    return mapping.dataset_version(
        client.get_dataset_version(dataset_id, version_id, owner_id=owner_id)
    )


def get_dataset_preview(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    dataset_id: uuid.UUID,
    version_id: uuid.UUID | None,
    limit: int,
    live: bool = False,
) -> dict:
    return mapping.dataset_preview(
        client.get_dataset_preview(
            dataset_id,
            owner_id=owner_id,
            version_id=version_id,
            limit=limit,
            live=live,
        )
    )
