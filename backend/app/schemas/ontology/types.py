"""本体工作台共享枚举与过滤操作集合。"""

from __future__ import annotations

from enum import StrEnum


class DatasetKind(StrEnum):
    SYNC = "SYNC"
    PIPELINE = "PIPELINE"


class SubjectKind(StrEnum):
    OBJECT_TYPE = "OBJECT_TYPE"
    RELATION = "RELATION"


class RelationKind(StrEnum):
    FK = "FK"
    MANY_TO_MANY = "MANY_TO_MANY"


class RunStatus(StrEnum):
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"
    SUPERSEDED = "SUPERSEDED"


class RunPhase(StrEnum):
    READING = "READING"
    VALIDATING = "VALIDATING"
    WRITING = "WRITING"
    SWITCHING = "SWITCHING"


class TriggerType(StrEnum):
    MANUAL = "MANUAL"
    DATASET_PUBLISHED = "DATASET_PUBLISHED"
    RETRY = "RETRY"


class ValidationState(StrEnum):
    OK = "OK"
    NEEDS_REPAIR = "NEEDS_REPAIR"


class FilterOp(StrEnum):
    EQ = "eq"
    NE = "ne"
    CONTAINS = "contains"
    GT = "gt"
    GTE = "gte"
    LT = "lt"
    LTE = "lte"
    IS_NULL = "is_null"
    IS_NOT_NULL = "is_not_null"


UNARY_FILTER_OPS = frozenset({FilterOp.IS_NULL, FilterOp.IS_NOT_NULL})
ORDERED_FILTER_OPS = frozenset({FilterOp.GT, FilterOp.GTE, FilterOp.LT, FilterOp.LTE})
