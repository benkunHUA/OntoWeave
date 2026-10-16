"""模型视角的本体操作：读图与写草稿的对外形状。

领域规则在 service 层（`element_edit` / `ontology` / `graph`），这里只做两件事：
把参数转过去，把结果转成模型看得懂的字段。所以它不需要 LangChain，可以直接单测。
"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.agent.tools.graph_read import load_membership, object_type_brief, render_slice
from app.errors import NotFoundError
from app.services import element_edit, embedding
from app.vocabulary import DEFAULT_CARDINALITY, DEFAULT_DATATYPE


def get_subgraph(
    session: Session,
    *,
    graph_id: uuid.UUID,
    object_type_id: uuid.UUID,
    owner_id: uuid.UUID,
    max_nodes: int = 50,
) -> dict:
    """从给定节点出发，沿图内的关系走一跳，返回周围的节点与关系。"""
    membership = load_membership(session, graph_id=graph_id, owner_id=owner_id)
    if object_type_id not in membership.node_ids:
        raise NotFoundError(f"object type {object_type_id} is not in graph {graph_id}")

    neighbours = {
        relation.target_type_id
        if relation.source_type_id == object_type_id
        else relation.source_type_id
        for relation in membership.relations
        if object_type_id in (relation.source_type_id, relation.target_type_id)
    }
    selected = sorted({object_type_id, *neighbours}, key=str)[:max_nodes]
    return render_slice(session, membership=membership, node_ids=set(selected))


def propose_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    name: str,
    description: str | None = None,
) -> dict:
    """新建草稿对象类型。要不要复用已有类型，由调用方先 search_ontology 再决定。"""
    obj = element_edit.create_object_type(
        session,
        changeset_id=changeset_id,
        owner_id=owner_id,
        name=name,
        description=description,
    )
    return object_type_brief(obj)


def reuse_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
) -> dict:
    """把共享池里已确认的对象类型并进当前图，不新建。"""
    obj = element_edit.reuse_object_type(
        session,
        changeset_id=changeset_id,
        owner_id=owner_id,
        object_type_id=object_type_id,
    )
    return {**object_type_brief(obj), "reused": True}


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
    enum_values: list[str] | None = None,
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
        enum_values=enum_values,
    )
    return {
        "id": str(prop.id),
        "name": prop.name,
        "datatype": prop.datatype,
        "required": prop.required,
        "is_array": prop.is_array,
        "is_primary_key": prop.is_primary_key,
        "is_title_key": prop.is_title_key,
        "enum_values": prop.enum_values,
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
    foreign_key_property_id: uuid.UUID | None = None,
) -> dict:
    rel = element_edit.create_relation(
        session,
        changeset_id=changeset_id,
        owner_id=owner_id,
        source_type_id=source_type_id,
        target_type_id=target_type_id,
        name=name,
        direction=direction,
        cardinality=cardinality,
        forward_label=forward_label,
        reverse_label=reverse_label,
        foreign_key_property_id=foreign_key_property_id,
    )
    return {
        "id": str(rel.id),
        "name": rel.name,
        "source_type_id": str(rel.source_type_id),
        "target_type_id": str(rel.target_type_id),
        "cardinality": rel.cardinality,
        "forward_label": rel.forward_label,
        "reverse_label": rel.reverse_label,
        "foreign_key_property_id": (
            str(rel.foreign_key_property_id) if rel.foreign_key_property_id else None
        ),
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
