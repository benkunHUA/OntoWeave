"""删图之后共享池清理策略（``app.services.graph.cleanup``）的行为。

这些用例走的是会话删除这条真实路径：先 `conversation.delete_conversation`，
再断言共享池里留下了什么。
"""

import uuid

from app.models import ActionParameter, ActionType, ObjectType, Property, Relation
from app.services import action, conversation, graph, ontology
from app.services.graph import cleanup as graph_cleanup


def test_graph_only_scope_keeps_shared_pool_intact(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="只删图")
    graph_id = conv.resource_id

    source = ontology.create_object_type(
        session, owner_id=owner, name="保留来源", home_graph_id=graph_id
    )
    target = ontology.create_object_type(
        session, owner_id=owner, name="保留目标", home_graph_id=graph_id
    )
    relation = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=source.id,
        target_type_id=target.id,
        name="保留关系",
        home_graph_id=graph_id,
    )
    ids = (source.id, target.id, relation.id)

    conversation.delete_conversation(session, conv.id, owner_id=owner)
    session.expire_all()

    kept_source = session.get(ObjectType, ids[0])
    kept_target = session.get(ObjectType, ids[1])
    kept_relation = session.get(Relation, ids[2])
    assert kept_source is not None and kept_source.home_graph_id is None
    assert kept_target is not None and kept_target.home_graph_id is None
    assert kept_relation is not None and kept_relation.home_graph_id is None


def test_drafts_scope_removes_only_local_drafts(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="连草稿一起删")
    graph_id = conv.resource_id

    draft_source = ontology.create_object_type(
        session, owner_id=owner, name="草稿来源", home_graph_id=graph_id
    )
    draft_target = ontology.create_object_type(
        session, owner_id=owner, name="草稿目标", home_graph_id=graph_id
    )
    draft_property = ontology.create_property(
        session, owner_type_id=draft_source.id, name="草稿属性"
    )
    draft_relation = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=draft_source.id,
        target_type_id=draft_target.id,
        name="草稿关系",
        home_graph_id=graph_id,
    )
    confirmed = ontology.create_object_type(
        session, owner_id=owner, name="已确认类型", home_graph_id=graph_id
    )
    ontology.transition_object_type(session, confirmed.id, "confirmed")
    other = conversation.create_conversation(session, owner_id=owner, title="另一张图")
    reused = ontology.create_object_type(
        session, owner_id=owner, name="别处复用的草稿", home_graph_id=other.resource_id
    )

    ids = {
        "source": draft_source.id,
        "target": draft_target.id,
        "property": draft_property.id,
        "relation": draft_relation.id,
        "confirmed": confirmed.id,
        "reused": reused.id,
    }
    other_graph_id = other.resource_id

    conversation.delete_conversation(session, conv.id, owner_id=owner, scope="graph-and-drafts")
    session.expire_all()

    assert session.get(ObjectType, ids["source"]) is None
    assert session.get(ObjectType, ids["target"]) is None
    assert session.get(Property, ids["property"]) is None
    assert session.get(Relation, ids["relation"]) is None

    # 已确认的、从别处复用来的都保留；图没了只把归属置空。
    kept_confirmed = session.get(ObjectType, ids["confirmed"])
    assert kept_confirmed is not None
    assert kept_confirmed.home_graph_id is None
    kept_reused = session.get(ObjectType, ids["reused"])
    assert kept_reused is not None
    assert kept_reused.home_graph_id == other_graph_id


def test_unreferenced_scope_removes_local_elements_but_keeps_shared(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="清理未被引用")
    graph_id = conv.resource_id

    shared = ontology.create_object_type(
        session, owner_id=owner, name="被别的图复用", home_graph_id=graph_id
    )
    lonely = ontology.create_object_type(
        session, owner_id=owner, name="没人用", home_graph_id=graph_id
    )
    confirmed_lonely = ontology.create_object_type(
        session, owner_id=owner, name="确认了也没人用", home_graph_id=graph_id
    )
    ontology.transition_object_type(session, confirmed_lonely.id, "confirmed")
    relation = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=shared.id,
        target_type_id=lonely.id,
        name="只在本图",
        home_graph_id=graph_id,
    )
    other = conversation.create_conversation(session, owner_id=owner, title="另一张图")
    graph.add_object_to_graph(session, graph_id=other.resource_id, object_type_id=shared.id)

    ids = {
        "shared": shared.id,
        "lonely": lonely.id,
        "confirmed": confirmed_lonely.id,
        "relation": relation.id,
    }

    conversation.delete_conversation(
        session, conv.id, owner_id=owner, scope="graph-and-unreferenced"
    )
    session.expire_all()

    # 被别的图引用的保留；本图独有的清掉，已确认也不例外。
    assert session.get(ObjectType, ids["shared"]) is not None
    assert session.get(ObjectType, ids["lonely"]) is None
    assert session.get(ObjectType, ids["confirmed"]) is None
    assert session.get(Relation, ids["relation"]) is None


def test_unreferenced_scope_keeps_shared_relation_and_its_endpoints(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="共享关系")
    graph_id = conv.resource_id

    source = ontology.create_object_type(session, owner_id=owner, name="源", home_graph_id=graph_id)
    target = ontology.create_object_type(session, owner_id=owner, name="靶", home_graph_id=graph_id)
    relation = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=source.id,
        target_type_id=target.id,
        name="被别的图引用的关系",
        home_graph_id=graph_id,
    )

    other = conversation.create_conversation(session, owner_id=owner, title="另一张图")
    graph.add_object_to_graph(session, graph_id=other.resource_id, object_type_id=source.id)
    graph.add_object_to_graph(session, graph_id=other.resource_id, object_type_id=target.id)
    graph.add_relation_to_graph(session, graph_id=other.resource_id, relation_id=relation.id)

    ids = {"source": source.id, "target": target.id, "relation": relation.id}

    conversation.delete_conversation(
        session, conv.id, owner_id=owner, scope="graph-and-unreferenced"
    )
    session.expire_all()

    # 关系被别的图引用，它本身连同两个端点都要留着，不能连带删掉。
    assert session.get(Relation, ids["relation"]) is not None
    assert session.get(ObjectType, ids["source"]) is not None
    assert session.get(ObjectType, ids["target"]) is not None


def test_purge_graph_elements_is_a_noop_for_default_scope(session):
    """直接调模块本身时，默认范围不该动共享池。"""
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="默认范围")
    obj = ontology.create_object_type(
        session, owner_id=owner, name="原地不动", home_graph_id=conv.resource_id
    )

    graph_cleanup.purge_graph_elements(session, graph_id=conv.resource_id, owner_id=owner)
    session.expire_all()

    assert session.get(ObjectType, obj.id) is not None


def test_drafts_scope_removes_local_action_types(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="连草稿动作一起删")
    graph_id = conv.resource_id

    draft_action = action.create_action_type(
        session, owner_id=owner, name="草稿动作", home_graph_id=graph_id
    )
    action.add_action_to_graph(session, graph_id, draft_action.id)
    parameter = action.create_parameter(
        session, action_type_id=draft_action.id, name="金额", datatype="decimal"
    )
    confirmed_action = action.create_action_type(
        session, owner_id=owner, name="已确认动作", home_graph_id=graph_id
    )
    action.transition_action_type(session, confirmed_action.id, "confirmed")

    conversation.delete_conversation(session, conv.id, owner_id=owner, scope="graph-and-drafts")
    session.expunge_all()

    assert session.get(ActionType, draft_action.id) is None
    assert session.get(ActionParameter, parameter.id) is None
    kept = session.get(ActionType, confirmed_action.id)
    assert kept is not None
    assert kept.home_graph_id is None


def test_unreferenced_scope_keeps_action_types_used_elsewhere(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="清理没被引用的动作")
    graph_id = conv.resource_id

    shared = action.create_action_type(
        session, owner_id=owner, name="别处也在用", home_graph_id=graph_id
    )
    action.add_action_to_graph(session, graph_id, shared.id)
    other = conversation.create_conversation(session, owner_id=owner, title="另一张图")
    action.add_action_to_graph(session, other.resource_id, shared.id)
    alone = action.create_action_type(
        session, owner_id=owner, name="只在这张图", home_graph_id=graph_id
    )
    action.add_action_to_graph(session, graph_id, alone.id)

    conversation.delete_conversation(
        session, conv.id, owner_id=owner, scope="graph-and-unreferenced"
    )
    session.expunge_all()

    assert session.get(ActionType, alone.id) is None
    kept = session.get(ActionType, shared.id)
    assert kept is not None
    assert kept.home_graph_id is None
