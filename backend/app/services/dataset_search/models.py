"""数据集检索使用的领域对象。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass

RETRIEVAL_LIMIT = 50
NEAR_MISS_LIMIT = 5


@dataclass(frozen=True)
class Requirement:
    label: str
    name: str
    name_en: str | None
    datatype: str
    required: bool
    is_array: bool
    is_primary_key: bool
    is_title_key: bool


@dataclass(frozen=True)
class SubjectQuery:
    subject_kind: str
    subject_id: uuid.UUID
    name: str
    name_en: str | None
    query_text: str
    requirements: tuple[Requirement, ...]
