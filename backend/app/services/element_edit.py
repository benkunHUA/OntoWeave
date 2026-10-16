"""往改动集里登记本体改动：新建对象类型 / 字段 / 关系，改字段，标记删除。

调用方有两种——界面上的直接编辑，以及模型通过工具写入的草稿——两者落在同一套规则上：
先校验归属与当前状态，再改本体，最后登记成对应的操作（update / create / delete）。
登记失败就直接抛错，半成品由调用方的单元事务整体回滚——这一层不自己提交或回滚。
接口层与工具层只做参数转换。
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass

from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import Changeset, ChangesetOp, ObjectType, Property, Relation
from app.services import changeset, element_kinds, embedding, graph, ontology, status
from app.vocabulary import DEFAULT_CARDINALITY, DEFAULT_DATATYPE

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


@dataclass(frozen=True)
class ObjectTypeCreate:
    """新建一个草稿对象类型。"""

    name: str
    description: str | None = None


@dataclass(frozen=True)
class ObjectTypeReuse:
    """把共享池里已有的类型并进当前图。"""

    object_type_id: uuid.UUID


@dataclass(frozen=True)
class RelationCreate:
    """新建一条草稿关系。"""

    source_type_id: uuid.UUID
    target_type_id: uuid.UUID
    name: str
    direction: str = "forward"
    cardinality: str = DEFAULT_CARDINALITY
    forward_label: str | None = None
    reverse_label: str | None = None
    foreign_key_property_id: uuid.UUID | None = None


EditOp = (
    ElementUpdate
    | PropertyCreate
    | ElementDelete
    | ObjectTypeCreate
    | ObjectTypeReuse
    | RelationCreate
)


def apply_edits(
    session: Session,
    *,
    graph_id: uuid.UUID,
    ops: list[EditOp],
    owner_id: uuid.UUID | None = None,
    embedder: embedding.Embedder | None = None,
) -> Changeset:
    """把一批编辑登记进图上未定稿的改动集，返回那个改动集。

    覆盖本体改动的全部形态：建对象类型、复用已有类型、建关系、加字段、改字段、标记删除。
    写入一律落在某个 owner 名下，缺 owner 直接拒绝，避免建出无归属的本体元素。
    逐条处理：服务层自己采集 before / 校验当前值 / 应用 after，所以整批编辑要么全成，
    要么随事务一起回滚。
    """
    if not ops:
        raise InvariantViolationError("at least one edit is required")
    if owner_id is None:
        raise InvariantViolationError("owner is required to register edits")
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
        elif isinstance(op, ObjectTypeCreate):
            create_object_type(
                session,
                changeset_id=cs.id,
                owner_id=owner_id,
                name=op.name,
                description=op.description,
            )
        elif isinstance(op, ObjectTypeReuse):
            reuse_object_type(
                session,
                changeset_id=cs.id,
                owner_id=owner_id,
                object_type_id=op.object_type_id,
            )
        elif isinstance(op, RelationCreate):
            create_relation(
                session,
                changeset_id=cs.id,
                owner_id=owner_id,
                source_type_id=op.source_type_id,
                target_type_id=op.target_type_id,
                name=op.name,
                direction=op.direction,
                cardinality=op.cardinality,
                forward_label=op.forward_label,
                reverse_label=op.reverse_label,
                foreign_key_property_id=op.foreign_key_property_id,
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
    """更新前的语义校验走元素种类注册表，规则在各自领域层，别漏在接口后面。"""
    entry = element_kinds.REGISTRY.get(element_kind)
    if entry is not None and entry.validate_patch is not None:
        entry.validate_patch(session, element_id, patch)


def create_object_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    name: str,
    description: str | None = None,
) -> ObjectType:
    """新建草稿对象类型，并把它挂进当前图。

    草稿不写向量：只有确认后才会在改动集定稿时补上，检索因此只认已确认的类型。
    """
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    obj = ontology.create_object_type(
        session, owner_id=owner_id, name=name, description=description, home_graph_id=cs.graph_id
    )
    graph.add_object_to_graph(session, cs.graph_id, obj.id, owner_id=owner_id)
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=obj.id,
        payload={"kind": OBJECT_TYPE},
    )
    return obj


def reuse_object_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
) -> ObjectType:
    """把共享池里已确认的对象类型并进当前图。

    只允许复用已确认的类型（confirmed / approved）：draft 还没被用户确认过，
    把它并入新模型等于带进一件没定稿的东西，所以这里直接拒绝。
    复用不改本体（没有新建/修改），只是把类型挂到这张图上，因此不记录改动集操作
    ——它跟着图成员关系走，不属于本轮待确认的本体改动。
    """
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    obj = changeset.load_element(session, OBJECT_TYPE, object_type_id, owner_id=owner_id)
    if obj.status == status.DRAFT:
        raise InvariantViolationError(
            f"object type {object_type_id} is still a draft and cannot be reused"
        )
    graph.add_object_to_graph(session, cs.graph_id, obj.id, owner_id=owner_id)
    return obj


def create_relation(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    source_type_id: uuid.UUID,
    target_type_id: uuid.UUID,
    name: str,
    direction: str = "forward",
    cardinality: str = DEFAULT_CARDINALITY,
    forward_label: str | None = None,
    reverse_label: str | None = None,
    foreign_key_property_id: uuid.UUID | None = None,
) -> Relation:
    """新建草稿关系，并把两端与关系本身挂进当前图。"""
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    # 两端都必须已在共享池里且属于同一 owner，否则等于凭空连到别人的类型上。
    changeset.load_element(session, OBJECT_TYPE, source_type_id, owner_id=owner_id)
    changeset.load_element(session, OBJECT_TYPE, target_type_id, owner_id=owner_id)
    graph.add_object_to_graph(session, cs.graph_id, source_type_id, owner_id=owner_id)
    graph.add_object_to_graph(session, cs.graph_id, target_type_id, owner_id=owner_id)
    rel = ontology.create_relation(
        session,
        owner_id=owner_id,
        source_type_id=source_type_id,
        target_type_id=target_type_id,
        name=name,
        direction=direction,
        cardinality=cardinality,
        forward_label=forward_label,
        reverse_label=reverse_label,
        foreign_key_property_id=foreign_key_property_id,
        home_graph_id=cs.graph_id,
    )
    graph.add_relation_to_graph(session, cs.graph_id, rel.id, owner_id=owner_id)
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=rel.id,
        payload={"kind": RELATION},
    )
    return rel


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
