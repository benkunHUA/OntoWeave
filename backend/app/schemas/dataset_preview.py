"""数据集预览的跨域共享响应模型。"""

from __future__ import annotations

import uuid
from typing import Any

from pydantic import BaseModel


class PreviewColumnRead(BaseModel):
    name: str
    type: str
    nullable: bool


class DatasetPreviewRead(BaseModel):
    dataset_id: uuid.UUID
    version_id: uuid.UUID | None = None
    version_number: int | None = None
    snapshot_id: int
    live: bool = False
    columns: list[PreviewColumnRead]
    row_count: int = 0
    rows: list[dict[str, Any]]
    truncated: bool = False
