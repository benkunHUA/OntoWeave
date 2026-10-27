"""对象类型、属性与关系的草稿创建。"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import ObjectType, Property, Relation
from app.services import changeset, english_names, graph, ontology, status
from app.services.element_edit.ops import OBJECT_TYPE, PROPERTY, RELATION
from app.vocabulary import DEFAULT_CARDINALITY, DEFAULT_DATATYPE


def create_object_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    name: str,
    name_en: str | None = None,
    description: str | None = None,
    name_generator: english_names.EnglishNameGenerator | None = None,
) -> ObjectType:
    """新建草稿对象类型，并把它挂进当前图。"""
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    obj = ontology.create_object_type(
        session,
        owner_id=owner_id,
        name=name,
        name_en=name_en,
        description=description,
        home_graph_id=cs.graph_id,
        name_generator=name_generator,
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
    name_en: str | None = None,
    datatype: str = DEFAULT_DATATYPE,
    required: bool = False,
    is_array: bool = False,
    is_primary_key: bool = False,
    is_title_key: bool = False,
    enum_values: list[str] | None = None,
) -> Property:
    """新建草稿字段并记进改动集。先校验对象归属，避免往别人的类型上加字段。"""
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, OBJECT_TYPE, owner_type_id, owner_id=owner_id)
    prop = ontology.create_property(
        session,
        owner_type_id=owner_type_id,
        name=name,
        name_en=name_en,
        datatype=datatype,
        required=required,
        is_array=is_array,
        is_primary_key=is_primary_key,
        is_title_key=is_title_key,
        enum_values=enum_values,
    )
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=changeset.OP_CREATE,
        target_id=prop.id,
        payload={"kind": PROPERTY},
    )
    return prop
