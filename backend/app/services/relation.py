"""对象类型之间关系的读写：基数、两端可读名，以及外键绑定。"""

from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import Property, Relation
from app.services import object_type, status
from app.vocabulary import (
    DEFAULT_CARDINALITY,
    RELATION_CARDINALITIES,
    RelationCardinality,
)


def validate_relation_patch(session: Session, *, relation_id: uuid.UUID, patch: dict) -> None:
    """更新关系前的校验：基数合法，且外键绑定与基数、属性相符。"""
    rel = get_relation(session, relation_id)
    cardinality = patch.get("cardinality", rel.cardinality)
    _ensure_cardinality(cardinality)
    if "cardinality" in patch or "foreign_key_property_id" in patch:
        _validate_foreign_key(
            session,
            source_type_id=rel.source_type_id,
            target_type_id=rel.target_type_id,
            cardinality=cardinality,
            foreign_key_property_id=patch.get(
                "foreign_key_property_id", rel.foreign_key_property_id
            ),
        )


def _ensure_cardinality(cardinality: str) -> None:
    try:
        RelationCardinality(cardinality)
    except ValueError as exc:
        raise InvariantViolationError(
            f"unsupported cardinality {cardinality!r}; "
            f"expected one of {list(RELATION_CARDINALITIES)}"
        ) from exc


def _validate_foreign_key(
    session: Session,
    *,
    source_type_id: uuid.UUID,
    target_type_id: uuid.UUID,
    cardinality: str,
    foreign_key_property_id: uuid.UUID | None,
) -> None:
    """外键绑定规则（对齐 Palantir）：外键属性存的是对端主键的值。

    - 只有一对一 / 一对多才用外键；多对多由链接表承载，不允许设外键。
    - 属性必须属于关系两端之一，且是单值属性。
    - 对端已有主键时类型必须与主键一致；主键还没建好不拦，留到确认阶段。
    """
    if foreign_key_property_id is None:
        return
    if cardinality == RelationCardinality.MANY_TO_MANY:
        raise InvariantViolationError("many-to-many relation cannot bind a foreign key property")
    prop = session.get(Property, foreign_key_property_id)
    if prop is None:
        raise NotFoundError(f"property {foreign_key_property_id} not found")
    if prop.owner_type_id not in {source_type_id, target_type_id}:
        raise InvariantViolationError(
            "foreign key property must belong to one of the relation's object types"
        )
    if prop.is_array:
        raise InvariantViolationError("foreign key property must be single-valued")
    other_type_id = target_type_id if prop.owner_type_id == source_type_id else source_type_id
    referenced_pk = session.scalar(
        select(Property).where(
            Property.owner_type_id == other_type_id,
            Property.is_primary_key.is_(True),
        )
    )
    if referenced_pk is not None and referenced_pk.datatype != prop.datatype:
        raise InvariantViolationError(
            f"foreign key datatype {prop.datatype!r} must match the referenced "
            f"primary key datatype {referenced_pk.datatype!r}"
        )


def create_relation(
    session: Session,
    *,
    owner_id: uuid.UUID,
    source_type_id: uuid.UUID,
    target_type_id: uuid.UUID,
    name: str,
    direction: str = "forward",
    cardinality: str = DEFAULT_CARDINALITY,
    forward_label: str | None = None,
    reverse_label: str | None = None,
    foreign_key_property_id: uuid.UUID | None = None,
    home_graph_id: uuid.UUID | None = None,
) -> Relation:
    """幂等：同一对类型之间同名同向的关系只建一条，重复调用返回已有的那条。

    cardinality / 两端标签 / 外键绑定属于关系本身的语义，重复调用不会覆盖已存在的那条。
    """
    object_type.get_object_type(session, source_type_id)
    object_type.get_object_type(session, target_type_id)
    _ensure_cardinality(cardinality)
    _validate_foreign_key(
        session,
        source_type_id=source_type_id,
        target_type_id=target_type_id,
        cardinality=cardinality,
        foreign_key_property_id=foreign_key_property_id,
    )
    existing = session.scalar(
        select(Relation).where(
            Relation.source_type_id == source_type_id,
            Relation.target_type_id == target_type_id,
            Relation.name == name,
            Relation.direction == direction,
        )
    )
    if existing is not None:
        return existing
    rel = Relation(
        owner_id=owner_id,
        source_type_id=source_type_id,
        target_type_id=target_type_id,
        name=name,
        direction=direction,
        cardinality=cardinality,
        forward_label=forward_label,
        reverse_label=reverse_label,
        foreign_key_property_id=foreign_key_property_id,
        status=status.DRAFT,
        home_graph_id=home_graph_id,
    )
    session.add(rel)
    session.flush()
    return rel


def get_relation(session: Session, relation_id: uuid.UUID) -> Relation:
    rel = session.get(Relation, relation_id)
    if rel is None:
        raise NotFoundError(f"relation {relation_id} not found")
    return rel


def relation_label(
    relation: Relation,
    direction: str,
    *,
    source_name: str | None = None,
    target_name: str | None = None,
) -> str:
    """返回方向相关的可读关系名，跳过与两端对象名重复的无信息标签。"""
    if direction == "incoming":
        candidate = relation.reverse_label
    elif direction == "outgoing":
        candidate = relation.forward_label
    else:
        candidate = relation.name
    label = candidate.strip() if candidate else ""
    endpoint_names = {name.strip() for name in (source_name, target_name) if name and name.strip()}
    if label and label not in endpoint_names:
        return label
    return relation.name.strip() or label


def transition_relation(
    session: Session,
    relation_id: uuid.UUID,
    target: str,
    *,
    owner_id: uuid.UUID | None = None,
) -> Relation:
    rel = get_relation(session, relation_id)
    if owner_id is not None and rel.owner_id != owner_id:
        raise NotFoundError(f"relation {relation_id} not found")
    status.ensure_transition(rel.status, target, f"relation {relation_id}")
    rel.status = target
    session.flush()
    return rel
