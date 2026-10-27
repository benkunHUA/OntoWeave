"""`test_conversation.py` 中 service 相关测试。"""

import uuid

import pytest

from app.errors import InvariantViolationError, NotFoundError
from app.models import ConversationMessage, Graph, ViewState
from app.services import conversation, view_state


def test_create_conversation_creates_a_graph(session):
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="销售域建模")
    assert session.get(Graph, conv.resource_id) is not None
    assert conv.title == "销售域建模"


def test_modeling_conversation_exposes_resource_id(session):
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="建模会话")
    assert conv.kind == "modeling"
    assert session.get(Graph, conv.resource_id) is not None


def test_create_resource_conversation_does_not_create_a_graph(session):
    owner = uuid.uuid4()
    resource_id = uuid.uuid4()
    conv = conversation.create_conversation(
        session, owner_id=owner, title="数据连接", kind="data_connection", resource_id=resource_id
    )
    assert conv.kind == "data_connection"
    assert conv.resource_id == resource_id
    assert session.query(Graph).filter(Graph.owner_id == owner).count() == 0


def test_create_modeling_conversation_rejects_resource_id(session):
    with pytest.raises(InvariantViolationError, match="resource_id"):
        conversation.create_conversation(
            session, owner_id=uuid.uuid4(), kind="modeling", resource_id=uuid.uuid4()
        )


def test_create_resource_conversation_requires_resource_id(session):
    with pytest.raises(InvariantViolationError, match="resource_id"):
        conversation.create_conversation(session, owner_id=uuid.uuid4(), kind="data_connection")


def test_service_rejects_unsupported_conversation_kind(session):
    with pytest.raises(InvariantViolationError, match="unsupported conversation kind"):
        conversation.create_conversation(
            session, owner_id=uuid.uuid4(), kind="not_a_kind", resource_id=uuid.uuid4()
        )
    with pytest.raises(InvariantViolationError, match="unsupported conversation kind"):
        conversation.find_conversation_by_resource(
            session, kind="not_a_kind", resource_id=uuid.uuid4()
        )
    with pytest.raises(InvariantViolationError, match="unsupported conversation kind"):
        conversation.list_conversations(session, kind="not_a_kind")


def test_delete_resource_conversation_does_not_touch_graph(session):
    owner = uuid.uuid4()
    graph_conv = conversation.create_conversation(session, owner_id=owner, title="建模")
    view_state.upsert_view_state(
        session,
        graph_id=graph_conv.resource_id,
        user_id=owner,
        spec={"layout": {"kind": "grid"}},
        owner_id=owner,
    )
    conv = conversation.create_conversation(
        session, owner_id=owner, title="数据连接", kind="data_connection", resource_id=uuid.uuid4()
    )
    message = conversation.add_message(
        session, conversation_id=conv.id, role="user", content="连接信息"
    )
    message_id = message.id
    session.commit()

    conversation.delete_conversation(session, conv.id, owner_id=owner)
    session.expire_all()

    assert session.get(Graph, graph_conv.resource_id) is not None
    assert session.get(ViewState, (graph_conv.resource_id, owner)).name == "建模"
    assert session.get(ConversationMessage, message_id) is None


def test_find_conversation_by_resource(session):
    owner = uuid.uuid4()
    resource_id = uuid.uuid4()
    conv = conversation.create_conversation(
        session, owner_id=owner, title="数据连接", kind="data_connection", resource_id=resource_id
    )
    found = conversation.find_conversation_by_resource(
        session, kind="data_connection", resource_id=resource_id, owner_id=owner
    )
    assert found is not None and found.id == conv.id


def test_list_conversations_filters_by_kind(session):
    owner = uuid.uuid4()
    conversation.create_conversation(session, owner_id=owner, title="建模")
    resource = conversation.create_conversation(
        session, owner_id=owner, title="数据连接", kind="data_connection", resource_id=uuid.uuid4()
    )

    listed = conversation.list_conversations(session, owner_id=owner, kind="data_connection")

    assert [item.id for item in listed] == [resource.id]


def test_list_conversations_paginates(session):
    owner = uuid.uuid4()
    conversation.create_conversation(session, owner_id=owner, title="c-1")
    conversation.create_conversation(session, owner_id=owner, title="c-2")
    conversation.create_conversation(session, owner_id=owner, title="c-3")

    page = conversation.list_conversations(session, limit=2, offset=1)
    assert len(page) == 2


def test_missing_conversation_raises(session):
    with pytest.raises(NotFoundError):
        conversation.get_conversation(session, uuid.uuid4())


def test_rename_conversation_updates_conversation_and_graph(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="新会话")
    view = view_state.upsert_view_state(
        session,
        graph_id=conv.resource_id,
        user_id=owner,
        spec={"layout": {"kind": "grid"}},
        owner_id=owner,
    )
    assert view.name == "新会话"

    renamed = conversation.rename_conversation(session, conv.id, title="民航本体", owner_id=owner)

    assert renamed.title == "民航本体"
    assert conversation.get_conversation(session, conv.id, owner_id=owner).title == "民航本体"
    graph = session.get(Graph, conv.resource_id)
    assert graph.name == "民航本体"
    assert session.get(ViewState, (conv.resource_id, owner)).name == "民航本体"


def test_rename_resource_conversation_does_not_touch_graph_or_view_state(session):
    owner = uuid.uuid4()
    graph_conv = conversation.create_conversation(session, owner_id=owner, title="建模")
    view_state.upsert_view_state(
        session,
        graph_id=graph_conv.resource_id,
        user_id=owner,
        spec={"layout": {"kind": "grid"}},
        owner_id=owner,
    )
    conv = conversation.create_conversation(
        session, owner_id=owner, title="数据连接", kind="data_connection", resource_id=uuid.uuid4()
    )

    renamed = conversation.rename_conversation(session, conv.id, title="生产库连接", owner_id=owner)

    assert renamed.title == "生产库连接"
    assert session.get(Graph, graph_conv.resource_id).name == "建模"
    assert session.get(ViewState, (graph_conv.resource_id, owner)).name == "建模"


def test_list_conversations_only_returns_own(session):
    mine = uuid.uuid4()
    conversation.create_conversation(session, owner_id=mine, title="mine")
    conversation.create_conversation(session, owner_id=uuid.uuid4(), title="other")

    titles = [c.title for c in conversation.list_conversations(session, owner_id=mine)]
    assert titles == ["mine"]


def test_get_conversation_hides_other_owner(session):
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="other")
    with pytest.raises(NotFoundError):
        conversation.get_conversation(session, conv.id, owner_id=uuid.uuid4())


def test_conversation_messages_are_persisted_in_order(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="历史")
    conversation.add_message(session, conversation_id=conv.id, role="user", content="客户会下订单")
    conversation.add_message(
        session,
        conversation_id=conv.id,
        role="assistant",
        content="我把它整理成两个类型。",
    )

    messages = conversation.list_messages(session, conv.id, owner_id=owner)

    assert [(message.role, message.content) for message in messages] == [
        ("user", "客户会下订单"),
        ("assistant", "我把它整理成两个类型。"),
    ]


def test_conversation_messages_are_scoped_to_owner(session):
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="私有")
    conversation.add_message(session, conversation_id=conv.id, role="user", content="secret")

    with pytest.raises(NotFoundError):
        conversation.list_messages(session, conv.id, owner_id=uuid.uuid4())


def test_delete_conversation_removes_graph_and_children(session):
    from app.models import Changeset
    from app.services import changeset as changeset_service

    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="待删")
    graph_id = conv.resource_id
    conversation.add_message(session, conversation_id=conv.id, role="user", content="你好")
    cs = changeset_service.create_changeset(session, graph_id=graph_id, owner_id=owner)
    cs_id = cs.id

    conversation.delete_conversation(session, conv.id, owner_id=owner)
    # 级联是数据库做的，ORM 身份映射里还留着对象；过期后按库里的真实状态断言。
    session.expire_all()

    with pytest.raises(NotFoundError):
        conversation.get_conversation(session, conv.id)
    assert session.get(Graph, graph_id) is None
    assert session.get(Changeset, cs_id) is None


def test_delete_conversation_rejects_other_owner(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="别人的")
    with pytest.raises(NotFoundError):
        conversation.delete_conversation(session, conv.id, owner_id=uuid.uuid4())
    # 没被误删
    assert conversation.get_conversation(session, conv.id) is not None


def test_create_conversation_accepts_caller_supplied_id(session):
    conversation_id = uuid.uuid4()
    resource_id = uuid.uuid4()

    conv = conversation.create_conversation(
        session,
        owner_id=uuid.uuid4(),
        title="稳定 ID",
        kind="data_connection",
        resource_id=resource_id,
        conversation_id=conversation_id,
    )

    assert conv.id == conversation_id


def test_archived_conversation_is_hidden_but_still_readable(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="归档")
    conversation.archive_conversation(session, conv.id, owner_id=owner)

    assert conversation.list_conversations(session, owner_id=owner) == []
    assert conversation.list_conversations(session, owner_id=owner, include_archived=True) == [conv]
    assert conversation.get_conversation(session, conv.id, owner_id=owner).id == conv.id


def test_archived_conversation_rejects_writes_but_archive_is_idempotent(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="归档后写")
    conversation.archive_conversation(session, conv.id, owner_id=owner)
    conversation.archive_conversation(session, conv.id, owner_id=owner)

    with pytest.raises(InvariantViolationError, match="archived"):
        conversation.rename_conversation(session, conv.id, title="新名字", owner_id=owner)
    with pytest.raises(InvariantViolationError, match="archived"):
        conversation.add_message(session, conversation_id=conv.id, role="user", content="hi")
    with pytest.raises(InvariantViolationError, match="archived"):
        conversation.resolve_tool_block(
            session, conversation_id=conv.id, call_id="call", status="done"
        )
    with pytest.raises(InvariantViolationError, match="archived"):
        conversation.delete_conversation(session, conv.id, owner_id=owner)
