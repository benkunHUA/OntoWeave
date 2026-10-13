"""从界面直接编辑本体元素：对象名称/描述、字段名与类型的修改，以及字段的增删。

编辑不进某个会话，但仍归改动集管：先确保图上有未定稿的改动集，再把每条编辑
登记成对应的操作（update / create / delete），最后刷新受影响对象类型的检索向量。
接口层只做校验与转发。
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass

from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import Changeset, ChangesetOp, Property
from app.services import changeset, embedding, ontology
from app.vocabulary import DEFAULT_DATATYPE

OBJECT_TYPE = "object_type"
PROPERTY = "property"
RELATION = "relation"

# 改这些字段会改变对象的检索语义（名称 + 描述拼成检索文本），要重算向量。
_EMBEDDING_FIELDS = {"name", "description"}


@dataclass(frozen=True)
class ElementUpdate:
    """改已有元素的字段：改哪个元素、改哪些字段。"""

    element_kind: str
    element_id: uuid.UUID
    patch: dict


@dataclass(frozen=True)
class PropertyCreate:
    """给对象类型加一个草稿字段。"""

    owner_type_id: uuid.UUID
    name: str
    datatype: str = DEFAULT_DATATYPE
    required: bool = False
    is_array: bool = False
    is_primary_key: bool = False
    is_title_key: bool = False


@dataclass(frozen=True)
class ElementDelete:
    """把元素标记删除（草稿阶段不真删，定稿时才删）。"""

    element_kind: str
    element_id: uuid.UUID


EditOp = ElementUpdate | PropertyCreate | ElementDelete


def apply_edits(
    session: Session,
    *,
    graph_id: uuid.UUID,
    ops: list[EditOp],
    owner_id: uuid.UUID | None = None,
    embedder: embedding.Embedder | None = None,
) -> Changeset:
    """把一批编辑登记进图上未定稿的改动集，返回那个改动集。

    逐条处理：服务层自己采集 before / 校验当前值 / 应用 after，所以整批编辑
    要么全成，要么随事务一起回滚。
    """
    if not ops:
        raise InvariantViolationError("at least one edit is required")
    cs = changeset.ensure_open_changeset(session, graph_id=graph_id, owner_id=owner_id)
    for op in ops:
        if isinstance(op, ElementUpdate):
            record_update(
                session,
                changeset_id=cs.id,
                element_kind=op.element_kind,
                element_id=op.element_id,
                patch=op.patch,
                owner_id=owner_id,
                embedder=embedder,
            )
        elif isinstance(op, PropertyCreate):
            create_property(
                session,
                changeset_id=cs.id,
                owner_id=owner_id,
                owner_type_id=op.owner_type_id,
                name=op.name,
                datatype=op.datatype,
                required=op.required,
                is_array=op.is_array,
                is_primary_key=op.is_primary_key,
                is_title_key=op.is_title_key,
            )
        elif isinstance(op, ElementDelete):
            remove_element(
                session,
                changeset_id=cs.id,
                owner_id=owner_id,
                element_kind=op.element_kind,
                element_id=op.element_id,
            )
        else:
            raise InvariantViolationError(f"unsupported edit op: {op!r}")
    return cs


def record_update(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    element_kind: str,
    element_id: uuid.UUID,
    patch: dict,
    owner_id: uuid.UUID | None = None,
    embedder: embedding.Embedder | None = None,
) -> ChangesetOp:
    """登记一次字段更新，并在名称/描述变化时刷新对象的检索向量。"""
    # 先做存在性与归属校验，再做语义校验——顺序反了会把别人家的元素也拿来判合法性。
    changeset.load_element(session, element_kind, element_id, owner_id=owner_id)
    _validate_patch(session, element_kind=element_kind, element_id=element_id, patch=patch)
    op = changeset.record_update(
        session,
        changeset_id=changeset_id,
        element_kind=element_kind,
        element_id=element_id,
        patch=patch,
        owner_id=owner_id,
    )
    if embedder is not None and element_kind == OBJECT_TYPE and _EMBEDDING_FIELDS & set(patch):
        embedding.try_refresh_object_type_embedding(
            session, object_type_id=element_id, embedder=embedder
        )
    return op


def _validate_patch(
    session: Session, *, element_kind: str, element_id: uuid.UUID, patch: dict
) -> None:
    """更新前的语义校验：属性类型 / 主键唯一的规则放领域层，别漏在接口后面。"""
    if element_kind == PROPERTY:
        ontology.validate_property_patch(session, property_id=element_id, patch=patch)
    elif element_kind == RELATION:
        ontology.validate_relation_patch(patch)


def create_property(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID | None,
    owner_type_id: uuid.UUID,
    name: str,
    datatype: str = DEFAULT_DATATYPE,
    required: bool = False,
    is_array: bool = False,
    is_primary_key: bool = False,
    is_title_key: bool = False,
) -> Property:
    """新建草稿字段并记进改动集。先校验对象归属，避免往别人的类型上加字段。"""
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, OBJECT_TYPE, owner_type_id, owner_id=owner_id)
    prop = ontology.create_property(
        session,
        owner_type_id=owner_type_id,
        name=name,
        datatype=datatype,
        required=required,
        is_array=is_array,
        is_primary_key=is_primary_key,
        is_title_key=is_title_key,
    )
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=prop.id,
        payload={"kind": PROPERTY},
    )
    return prop


def remove_element(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    element_kind: str,
    element_id: uuid.UUID,
    owner_id: uuid.UUID | None = None,
) -> None:
    """把元素标记删除：草稿阶段不真删，用户确认时才删。"""
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, element_kind, element_id, owner_id=owner_id)
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_DELETE,
        target_id=element_id,
        payload={"kind": element_kind},
    )
