from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import ObjectType, Relation
from app.services import changeset, embedding, graph, ontology


def _type_brief(obj: ObjectType) -> dict:
    return {
        "id": str(obj.id),
        "name": obj.name,
        "description": obj.description,
        "status": obj.status,
    }


def search_ontology(
    session: Session,
    *,
    query: str,
    embedder: embedding.Embedder,
    owner_id: uuid.UUID,
    limit: int = 10,
) -> list[dict]:
    """按语义找已有对象类型。新建之前应该先调这个，避免造重复。"""
    hits = embedding.search_object_types(
        session, query=query, embedder=embedder, owner_id=owner_id, limit=limit
    )
    return [{**_type_brief(obj), "similarity": round(score, 4)} for obj, score in hits]


def list_types(session: Session, *, owner_id: uuid.UUID, limit: int = 50) -> list[dict]:
    return [
        _type_brief(obj)
        for obj in ontology.list_object_types(session, owner_id=owner_id, limit=limit)
    ]


def get_subgraph(
    session: Session,
    *,
    graph_id: uuid.UUID,
    object_type_id: uuid.UUID,
    owner_id: uuid.UUID,
    max_nodes: int = 50,
) -> dict:
    """从给定节点出发，沿图内的关系走一跳，返回周围的节点与关系。"""
    graph.get_graph(session, graph_id, owner_id=owner_id)
    present = set(graph.list_graph_objects(session, graph_id))
    if object_type_id not in present:
        raise NotFoundError(f"object type {object_type_id} is not in graph {graph_id}")

    relation_ids = set(graph.list_graph_relations(session, graph_id))
    relations = (
        list(session.scalars(select(Relation).where(Relation.id.in_(relation_ids))))
        if relation_ids
        else []
    )
    neighbours = {
        rel.target_type_id if rel.source_type_id == object_type_id else rel.source_type_id
        for rel in relations
        if object_type_id in (rel.source_type_id, rel.target_type_id)
    }
    node_ids = sorted({object_type_id, *neighbours}, key=str)[:max_nodes]
    nodes = list(session.scalars(select(ObjectType).where(ObjectType.id.in_(node_ids))))
    connected = [
        rel
        for rel in relations
        if rel.source_type_id in node_ids and rel.target_type_id in node_ids
    ]
    return {
        "nodes": [_type_brief(node) for node in nodes],
        "relations": [
            {
                "id": str(rel.id),
                "name": rel.name,
                "source_type_id": str(rel.source_type_id),
                "target_type_id": str(rel.target_type_id),
                "direction": rel.direction,
            }
            for rel in connected
        ],
    }


DEFAULT_REUSE_THRESHOLD = 0.98


def _candidates(
    session: Session,
    *,
    name: str,
    embedder: embedding.Embedder,
    owner_id: uuid.UUID,
    limit: int = 5,
) -> list[dict]:
    return search_ontology(session, query=name, embedder=embedder, owner_id=owner_id, limit=limit)


def propose_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    name: str,
    embedder: embedding.Embedder,
    description: str | None = None,
    home_graph_id: uuid.UUID | None = None,
    reuse_threshold: float = DEFAULT_REUSE_THRESHOLD,
) -> dict:
    """新建对象类型。护栏：先查近邻——同名或足够像就复用，否则把候选一并返回。"""
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    candidates = _candidates(session, name=name, embedder=embedder, owner_id=owner_id)
    existing = next((item for item in candidates if item["name"] == name), None) or next(
        (item for item in candidates if item["similarity"] >= reuse_threshold), None
    )
    if existing is not None:
        return {**existing, "reused": True, "similar": candidates}

    obj = ontology.create_object_type(
        session,
        owner_id=owner_id,
        name=name,
        description=description,
        home_graph_id=home_graph_id or cs.graph_id,
    )
    embedding.save_embedding(session, object_type_id=obj.id, embedder=embedder)
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op="create",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )
    return {**_type_brief(obj), "reused": False, "similar": candidates}


def propose_property(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    owner_type_id: uuid.UUID,
    name: str,
    datatype: str = "text",
    required: bool = False,
) -> dict:
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, "object_type", owner_type_id, owner_id=owner_id)
    prop = ontology.create_property(
        session,
        owner_type_id=owner_type_id,
        name=name,
        datatype=datatype,
        required=required,
    )
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op="create",
        target_id=prop.id,
        payload={"kind": "property"},
    )
    return {
        "id": str(prop.id),
        "name": prop.name,
        "datatype": prop.datatype,
        "required": prop.required,
        "status": prop.status,
    }


def propose_relation(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    source_type_id: uuid.UUID,
    target_type_id: uuid.UUID,
    name: str,
    direction: str = "forward",
    home_graph_id: uuid.UUID | None = None,
) -> dict:
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    rel = ontology.create_relation(
        session,
        owner_id=owner_id,
        source_type_id=source_type_id,
        target_type_id=target_type_id,
        name=name,
        direction=direction,
        home_graph_id=home_graph_id or cs.graph_id,
    )
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op="create",
        target_id=rel.id,
        payload={"kind": "relation"},
    )
    return {
        "id": str(rel.id),
        "name": rel.name,
        "source_type_id": str(rel.source_type_id),
        "target_type_id": str(rel.target_type_id),
        "status": rel.status,
    }


def update_element(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    element_kind: str,
    element_id: uuid.UUID,
    patch: dict,
    before: dict,
) -> dict:
    """改已有元素。before 是改动前的字段快照，撤销时靠它还原，必填。"""
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    allowed = changeset.RESTORABLE_FIELDS.get(element_kind)
    if allowed is None:
        raise InvariantViolationError(f"unsupported element kind: {element_kind!r}")
    if not before:
        raise InvariantViolationError("update requires a 'before' snapshot for undo")
    unknown = set(patch) - allowed
    if unknown:
        raise InvariantViolationError(f"cannot patch fields: {sorted(unknown)}")

    element = changeset.load_element(session, element_kind, element_id, owner_id=owner_id)
    for field, value in patch.items():
        setattr(element, field, value)
    session.flush()
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op="update",
        target_id=element_id,
        payload={"kind": element_kind, "before": before},
    )
    return {"id": str(element_id), "patched": sorted(patch), "before": before}


def remove_element(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    element_kind: str,
    element_id: uuid.UUID,
) -> dict:
    """标记删除。草稿阶段不真删，确认时才删——对应界面上的幽灵态。"""
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, element_kind, element_id, owner_id=owner_id)
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op="delete",
        target_id=element_id,
        payload={"kind": element_kind},
    )
    return {"id": str(element_id), "marked_for_deletion": True}
