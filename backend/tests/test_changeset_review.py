import uuid

from app.models import ChangesetOp
from app.services import (
    changeset,
    changeset_review,
    conversation,
    element_edit,
    embedding,
    graph,
    ontology,
)


def _conversation_with_changeset(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="回执")
    cs = changeset.create_changeset(session, graph_id=conv.resource_id, owner_id=owner)
    return owner, conv, cs


def test_summarize_lists_counts_in_a_stable_order():
    ops = [
        ChangesetOp(op="delete"),
        ChangesetOp(op="create"),
        ChangesetOp(op="create"),
        ChangesetOp(op="view"),
    ]
    assert changeset_review.summarize(changeset_review.REVERT, ops) == (
        "已撤销本次改动：新增 2、删除 1、视图调整 1。"
    )


def test_summarize_handles_empty_changeset():
    assert changeset_review.summarize(changeset_review.CONFIRM, []) == "已确认本次改动。"


def test_confirm_appends_a_notice_message(session):
    owner, conv, cs = _conversation_with_changeset(session)
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    changeset.record_op(
        session, changeset_id=cs.id, op="create", target_id=obj.id, payload={"kind": "object_type"}
    )

    changeset_review.confirm(session, cs.id, owner_id=owner)

    last = conversation.list_messages(session, conv.id)[-1]
    assert last.role == "assistant"
    assert last.content == "已确认本次改动：新增 1。"
    assert last.blocks[0]["kind"] == changeset_review.NOTICE_BLOCK_KIND
    assert last.blocks[0]["text"] == last.content


def test_confirm_is_what_writes_the_embedding(session):
    owner, _, cs = _conversation_with_changeset(session)
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    changeset.record_op(
        session, changeset_id=cs.id, op="create", target_id=obj.id, payload={"kind": "object_type"}
    )
    assert obj.embedding is None

    changeset_review.confirm(session, cs.id, owner_id=owner, embedder=embedding.HashingEmbedder())

    assert obj.status == "confirmed"
    assert obj.embedding is not None


def test_revert_recomputes_the_embedding_after_a_rename(session):
    owner, _, cs = _conversation_with_changeset(session)
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    ontology.transition_object_type(session, obj.id, "confirmed", owner_id=owner)
    emb = embedding.HashingEmbedder()
    embedding.refresh_object_type_embedding(session, object_type_id=obj.id, embedder=emb)
    original = list(obj.embedding)

    element_edit.record_update(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        element_kind="object_type",
        element_id=obj.id,
        patch={"name": "终端客户"},
        embedder=emb,
    )
    assert list(obj.embedding) != original

    changeset_review.revert(session, cs.id, owner_id=owner, embedder=emb)

    assert obj.name == "客户"
    assert list(obj.embedding) == original


def test_confirm_survives_embedding_failure(session, failing_embedder):
    owner, _, cs = _conversation_with_changeset(session)
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    changeset.record_op(
        session, changeset_id=cs.id, op="create", target_id=obj.id, payload={"kind": "object_type"}
    )

    changeset_review.confirm(session, cs.id, owner_id=owner, embedder=failing_embedder)

    # 嵌入服务挂了也不能拖垮确认：本体照常定稿，只是这个对象暂时检索不到。
    assert changeset.get_changeset(session, cs.id).status == "confirmed"
    assert obj.status == "confirmed"
    assert obj.embedding is None


def test_revert_appends_a_notice_message(session):
    owner, conv, cs = _conversation_with_changeset(session)
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    changeset.record_op(
        session, changeset_id=cs.id, op="create", target_id=obj.id, payload={"kind": "object_type"}
    )

    changeset_review.revert(session, cs.id, owner_id=owner)

    last = conversation.list_messages(session, conv.id)[-1]
    assert last.content == "已撤销本次改动：新增 1。"
    assert last.blocks[0]["kind"] == changeset_review.NOTICE_BLOCK_KIND


def test_find_conversation_by_graph_respects_owner(session):
    owner, conv, _ = _conversation_with_changeset(session)

    assert (
        conversation.find_conversation_by_graph(session, conv.resource_id, owner_id=owner)
        is not None
    )
    assert conversation.find_conversation_by_graph(session, uuid.uuid4()) is None
    assert (
        conversation.find_conversation_by_graph(session, conv.resource_id, owner_id=uuid.uuid4())
        is None
    )


def test_revert_notifies_the_thread_with_specific_names(session):
    """撤销回执除了展示，还要交给钩子写回模型线程，并点名具体对象/关系/属性。"""
    owner, conv, cs = _conversation_with_changeset(session)
    guide = ontology.create_object_type(session, owner_id=owner, name="攻略")
    spot = ontology.create_object_type(session, owner_id=owner, name="景点")
    rel = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=guide.id,
        target_type_id=spot.id,
        name="包含",
    )
    for op, target, kind in (
        ("create", spot.id, "object_type"),
        ("create", rel.id, "relation"),
    ):
        changeset.record_op(
            session, changeset_id=cs.id, op=op, target_id=target, payload={"kind": kind}
        )
    notices: list[changeset_review.Notice] = []

    changeset_review.revert(session, cs.id, owner_id=owner, notify=lambda _, n: notices.append(n))

    assert len(notices) == 1
    notice = notices[0]
    assert notice.conversation_id == conv.id
    assert notice.graph_id == conv.resource_id
    assert notice.changeset_id == cs.id
    assert notice.text == (
        "（系统回执）用户已撤销本次改动，以下内容已回退：\n"
        "- 新增 对象类型「景点」\n"
        "- 新增 关系「包含」（攻略 → 景点）\n"
        "这些对象、关系和属性已从图中移除，后续请以当前图为准，不要再引用它们。"
    )


def test_confirm_notifies_the_thread_with_specific_names(session):
    owner, conv, cs = _conversation_with_changeset(session)
    obj = ontology.create_object_type(session, owner_id=owner, name="景点")
    changeset.record_op(
        session, changeset_id=cs.id, op="create", target_id=obj.id, payload={"kind": "object_type"}
    )
    notices: list[changeset_review.Notice] = []

    changeset_review.confirm(session, cs.id, owner_id=owner, notify=lambda _, n: notices.append(n))

    assert notices[0].text == (
        "（系统回执）用户已确认本次改动，以下内容已生效：\n"
        "- 新增 对象类型「景点」\n"
        "后续请以当前图为准。"
    )


def test_confirm_without_ops_falls_back_to_the_plain_summary(session):
    owner, conv, cs = _conversation_with_changeset(session)
    notices: list[changeset_review.Notice] = []

    changeset_review.confirm(session, cs.id, owner_id=owner, notify=lambda _, n: notices.append(n))

    assert notices[0].text == "已确认本次改动。后续请以当前图为准。"


def test_describe_ops_names_properties_updates_and_view_changes(session):
    owner, _, cs = _conversation_with_changeset(session)
    order = ontology.create_object_type(session, owner_id=owner, name="订单")
    prop = ontology.create_property(session, owner_type_id=order.id, name="下单时间")
    rel = ontology.create_relation(
        session, owner_id=owner, source_type_id=order.id, target_type_id=order.id, name="关联"
    )
    ops = [
        changeset.record_op(
            session,
            changeset_id=cs.id,
            op="create",
            target_id=prop.id,
            payload={"kind": "property"},
        ),
        changeset.record_update(
            session,
            changeset_id=cs.id,
            element_kind="relation",
            element_id=rel.id,
            patch={"name": "关联订单"},
        ),
        changeset.record_view_change(
            session,
            changeset_id=cs.id,
            user_id=owner,
            before={"layout": "dagre"},
            after={"layout": "grid"},
        ),
    ]

    assert changeset_review.describe_ops(session, ops) == [
        "新增 订单 的属性「下单时间」",
        "修改 关系「关联订单」（订单 → 订单）（name：关联 → 关联订单）",
        "调整视图",
    ]


def test_notify_is_skipped_without_a_conversation(session):
    """找不到会话时静默跳过，别因为回执连不上就把定稿主流程拖垮。"""
    owner = uuid.uuid4()
    orphan = graph.create_graph(session, owner_id=owner, name="没有会话的图")
    cs = changeset.create_changeset(session, graph_id=orphan.id, owner_id=owner)
    notices: list[changeset_review.Notice] = []

    changeset_review.revert(session, cs.id, owner_id=owner, notify=lambda _, n: notices.append(n))

    assert notices == []
