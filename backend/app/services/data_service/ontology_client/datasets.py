"""可绑定数据集与字段接口。"""

from __future__ import annotations

import uuid
from collections.abc import Callable
from typing import Any


class DatasetClientMixin:
    _ontology_request: Callable[..., dict]
    _ontology_list: Callable[..., list[dict]]

    def list_bindable_datasets(
        self,
        *,
        owner_id: uuid.UUID,
        keyword: str | None = None,
        page: int = 1,
        size: int = 20,
    ) -> dict:
        params: dict[str, Any] = {"ownerId": str(owner_id), "page": page, "size": size}
        if keyword:
            params["keyword"] = keyword
        return self._ontology_request("GET", "/bindable-datasets", params=params)

    def list_dataset_columns(
        self, *, owner_id: uuid.UUID, dataset_kind: str, dataset_id: uuid.UUID
    ) -> list[dict]:
        path = f"/bindable-datasets/{dataset_kind}/{dataset_id}/columns"
        return self._ontology_list(path, params={"ownerId": str(owner_id)})


__all__ = ["DatasetClientMixin"]
