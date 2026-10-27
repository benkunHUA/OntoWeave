"""`test_dataset_search.py` 的共享 fixture、假客户端与构造 helper。"""

from __future__ import annotations

import uuid

from app.constants import EMBEDDING_DIM


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
