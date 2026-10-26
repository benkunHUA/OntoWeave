from __future__ import annotations

import uuid

from sqlalchemy import select

from app.models import ObjectType, Property
from app.services import changeset, changeset_review, element_edit, english_names, graph, ontology


class FakeEnglishNames:
    def __init__(self) -> None:
        self.calls: list[tuple[str, list[str]]] = []

    def generate(self, *, object_name, property_names):
        self.calls.append((object_name, list(property_names)))
        object_names = {"订单": "order", "客户": "customer"}
        property_names_map = {
            "下单时间": "ordered_at",
            "消费者id": "customer_id",
            "订单金额": "order_amount",
        }
        return english_names.EnglishNames(
            object_name=object_names.get(object_name, "generated_object"),
            properties={
                name: property_names_map.get(name, "generated_field") for name in property_names
            },
        )


def test_object_and_property_english_names_are_filled_only_when_missing(session):
    owner = uuid.uuid4()
    generator = FakeEnglishNames()
    obj = ontology.create_object_type(
        session, owner_id=owner, name="订单", name_generator=generator
    )
    assert obj.name_en == "order"

    primary = ontology.create_property(
        session, owner_type_id=obj.id, name="id", is_primary_key=True
    )
    ordered_at = ontology.create_property(
        session, owner_type_id=obj.id, name="下单时间", datatype="timestamp"
    )
    customer_id = ontology.create_property(
        session, owner_type_id=obj.id, name="消费者id", datatype="text"
    )
    assert primary.name_en is None

    assert english_names.fill_object_english_names(session, object_type=obj, generator=generator)
    assert primary.name_en == "id"
    assert ordered_at.name_en == "ordered_at"
    assert customer_id.name_en == "customer_id"
    assert generator.calls[-1] == ("订单", ["下单时间", "消费者id"])

    obj.name = "销售订单"
    ordered_at.name_en = "manually_ordered_at"
    generator.calls.clear()
    assert not english_names.fill_object_english_names(
        session, object_type=obj, generator=generator
    )
    assert obj.name_en == "order"
    assert ordered_at.name_en == "manually_ordered_at"
    assert generator.calls == []


def test_changeset_creation_and_confirmation_generate_names(session):
    owner = uuid.uuid4()
    generator = FakeEnglishNames()
    g = graph.create_graph(session, owner_id=owner, name="订单图")

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        owner_id=owner,
        ops=[element_edit.ObjectTypeCreate(name="订单")],
        name_generator=generator,
    )
    obj = session.scalars(select(ObjectType).where(ObjectType.owner_id == owner)).one()
    assert obj.name_en == "order"

    element_edit.apply_edits(
        session,
        graph_id=g.id,
        owner_id=owner,
        ops=[
            element_edit.PropertyCreate(
                owner_type_id=obj.id,
                name="下单时间",
                datatype="timestamp",
            )
        ],
    )
    changeset_review.confirm(
        session,
        cs.id,
        owner_id=owner,
        name_generator=generator,
    )
    prop = session.scalars(select(Property).where(Property.owner_type_id == obj.id)).one()
    assert prop.name_en == "ordered_at"


def test_name_en_is_user_editable(session):
    owner = uuid.uuid4()
    obj = ontology.create_object_type(session, owner_id=owner, name="订单")
    g = graph.create_graph(session, owner_id=owner)
    cs = changeset.ensure_open_changeset(session, graph_id=g.id, owner_id=owner)

    element_edit.record_update(
        session,
        changeset_id=cs.id,
        element_kind=element_edit.OBJECT_TYPE,
        element_id=obj.id,
        patch={"name_en": "sales_order"},
        owner_id=owner,
    )

    assert obj.name_en == "sales_order"
