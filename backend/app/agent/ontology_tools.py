from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import ObjectType, Relation
from app.services import changeset, element_edit, embedding, graph, ontology
from app.vocabulary import DEFAULT_CARDINALITY, DEFAULT_DATATYPE


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
                "cardinality": rel.cardinality,
            }
            for rel in connected
        ],
    }


def propose_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    name: str,
    description: str | None = None,
    home_graph_id: uuid.UUID | None = None,
) -> dict:
    """新建对象类型（草稿）。要不要复用已有类型，由调用方先 search_ontology 再决定。

    草稿不写向量：只有确认后才会在改动集定稿时补上，检索因此只认已确认的类型。
    """
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    obj = ontology.create_object_type(
        session,
        owner_id=owner_id,
        name=name,
        description=description,
        home_graph_id=home_graph_id or cs.graph_id,
    )
    graph.add_object_to_graph(session, cs.graph_id, obj.id, owner_id=owner_id)
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op="create",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )
    return _type_brief(obj)


def reuse_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
) -> dict:
    """把已有对象类型并进当前图。

    只允许复用已确认的类型（confirmed / approved）：draft 还没被用户确认过，
    把它并入新模型等于带进一件没定稿的东西，所以这里直接拒绝。
    复用不改本体（没有新建/修改），只是把共享池里的类型挂到这张图上，
    因此不记录改动集操作——它跟着图成员关系走，不属于本轮待确认的本体改动。
    """
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    obj = changeset.load_element(session, "object_type", object_type_id, owner_id=owner_id)
    if obj.status == ontology.DRAFT:
        raise InvariantViolationError(
            f"object type {object_type_id} is still a draft and cannot be reused"
        )
    graph.add_object_to_graph(session, cs.graph_id, obj.id, owner_id=owner_id)
    return {**_type_brief(obj), "reused": True}


def propose_property(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    owner_type_id: uuid.UUID,
    name: str,
    datatype: str = DEFAULT_DATATYPE,
    required: bool = False,
    is_array: bool = False,
    is_primary_key: bool = False,
    is_title_key: bool = False,
) -> dict:
    prop = element_edit.create_property(
        session,
        changeset_id=changeset_id,
        owner_id=owner_id,
        owner_type_id=owner_type_id,
        name=name,
        datatype=datatype,
        required=required,
        is_array=is_array,
        is_primary_key=is_primary_key,
        is_title_key=is_title_key,
    )
    return {
        "id": str(prop.id),
        "name": prop.name,
        "datatype": prop.datatype,
        "required": prop.required,
        "is_array": prop.is_array,
        "is_primary_key": prop.is_primary_key,
        "is_title_key": prop.is_title_key,
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
    cardinality: str = DEFAULT_CARDINALITY,
    forward_label: str | None = None,
    reverse_label: str | None = None,
    home_graph_id: uuid.UUID | None = None,
) -> dict:
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, "object_type", source_type_id, owner_id=owner_id)
    changeset.load_element(session, "object_type", target_type_id, owner_id=owner_id)
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
        home_graph_id=home_graph_id or cs.graph_id,
    )
    graph.add_relation_to_graph(session, cs.graph_id, rel.id, owner_id=owner_id)
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
        "cardinality": rel.cardinality,
        "forward_label": rel.forward_label,
        "reverse_label": rel.reverse_label,
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
    embedder: embedding.Embedder,
) -> dict:
    """改已有元素。服务层采集 before、校验当前值并应用 after；名称/描述变了顺带
    刷新检索向量，否则检索到的还是旧语义（相似度、命中结果都会失真）。
    """
    op = element_edit.record_update(
        session,
        changeset_id=changeset_id,
        element_kind=element_kind,
        element_id=element_id,
        patch=patch,
        owner_id=owner_id,
        embedder=embedder,
    )
    return {
        "id": str(element_id),
        "patched": sorted(op.payload["after"]),
        "before": op.payload["before"],
        "after": op.payload["after"],
    }


def remove_element(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    element_kind: str,
    element_id: uuid.UUID,
) -> dict:
    """标记删除。草稿阶段不真删，确认时才删——对应界面上的幽灵态。"""
    element_edit.remove_element(
        session,
        changeset_id=changeset_id,
        element_kind=element_kind,
        element_id=element_id,
        owner_id=owner_id,
    )
    return {"id": str(element_id), "marked_for_deletion": True}
