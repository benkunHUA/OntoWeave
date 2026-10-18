import uuid

from app.agent import tools as agent_tools
from app.services import changeset, conversation, embedding, graph, ontology, view_state


def _context(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="撤销视图")
    cs = changeset.create_changeset(session, graph_id=conv.resource_id, owner_id=owner)
    return conv, agent_tools.OntologyToolContext(
        session=session,
        owner_id=owner,
        graph_id=conv.resource_id,
        changeset_id=cs.id,
        embedder=embedding.HashingEmbedder(),
    )


def _view_tools(ctx):
    return {item.name: item for item in agent_tools.build_view_tools(ctx)}


def _spec(session, conv, owner):
    saved = view_state.get_view_state(
        session, graph_id=conv.resource_id, user_id=owner, owner_id=owner
    )
    return saved.spec if saved is not None else {}


def test_reject_reverts_view_changes(session):
    owner = uuid.uuid4()
    conv, ctx = _context(session, owner)
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    graph.add_object_to_graph(session, conv.resource_id, customer.id, owner_id=owner)

    _view_tools(ctx)["highlight"].invoke({"node_ids": [str(customer.id)]})
    assert _spec(session, conv, owner)["highlight"] == [
        {"nodeIds": [str(customer.id)], "style": "focus"}
    ]

    changeset.reject_changeset(session, ctx.changeset_id, owner_id=owner)

    assert _spec(session, conv, owner) == {}


def test_reject_reverts_every_view_change_in_the_changeset(session):
    owner = uuid.uuid4()
    conv, ctx = _context(session, owner)
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    order = ontology.create_object_type(session, owner_id=owner, name="订单")

    tools = _view_tools(ctx)
    tools["set_layout"].invoke({"kind": "domain-grouped"})
    tools["highlight"].invoke({"node_ids": [str(customer.id)]})
    tools["dim"].invoke({"node_ids": [str(order.id)]})
    assert set(_spec(session, conv, owner)) == {"layout", "highlight", "dim"}

    changeset.reject_changeset(session, ctx.changeset_id, owner_id=owner)

    assert _spec(session, conv, owner) == {}


def test_reject_keeps_view_state_that_predates_the_changeset(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="既有视图")
    # 改动集之前用户就手工保存过视图，撤销只能翻回它，不能清空。
    view_state.upsert_view_state(
        session,
        graph_id=conv.resource_id,
        user_id=owner,
        spec={"layout": {"kind": "grid", "options": {}}},
        owner_id=owner,
    )
    cs = changeset.create_changeset(session, graph_id=conv.resource_id, owner_id=owner)
    ctx = agent_tools.OntologyToolContext(
        session=session,
        owner_id=owner,
        graph_id=conv.resource_id,
        changeset_id=cs.id,
        embedder=embedding.HashingEmbedder(),
    )
    _view_tools(ctx)["highlight"].invoke({"node_ids": [str(uuid.uuid4())]})

    changeset.reject_changeset(session, cs.id, owner_id=owner)

    assert _spec(session, conv, owner) == {"layout": {"kind": "grid", "options": {}}}


def test_confirm_keeps_view_changes(session):
    owner = uuid.uuid4()
    conv, ctx = _context(session, owner)
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    graph.add_object_to_graph(session, conv.resource_id, customer.id, owner_id=owner)

    _view_tools(ctx)["highlight"].invoke({"node_ids": [str(customer.id)]})
    changeset.confirm_changeset(session, ctx.changeset_id, owner_id=owner)

    assert _spec(session, conv, owner)["highlight"] == [
        {"nodeIds": [str(customer.id)], "style": "focus"}
    ]


def test_reject_reverts_view_and_ontology_together(session):
    owner = uuid.uuid4()
    conv, ctx = _context(session, owner)

    types = {item.name: item for item in agent_tools.build_model_tools(ctx)}
    proposed = types["propose_type"].invoke({"name": "客户"})
    _view_tools(ctx)["highlight"].invoke({"node_ids": [proposed["id"]]})

    changeset.reject_changeset(session, ctx.changeset_id, owner_id=owner)

    assert _spec(session, conv, owner) == {}
    left = ontology.list_object_types(session, owner_id=owner)
    assert [obj.name for obj in left] == []
