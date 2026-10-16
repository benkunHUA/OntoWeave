"""`enum` 数据类型的规则：候选值与数据类型必须配套，撤销要能还原候选值。"""

import uuid

import pytest
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import Property
from app.services import changeset, element_edit, graph, ontology


def _graph(session, owner_id: uuid.UUID):
    return graph.create_graph(session, owner_id=owner_id, name="测试图")


def test_create_enum_property_keeps_candidates(session: Session):
    owner = uuid.uuid4()
    obj = ontology.create_object_type(session, owner_id=owner, name="工单")

    prop = ontology.create_property(
        session,
        owner_type_id=obj.id,
        name="状态",
        datatype="enum",
        enum_values=["  待处理 ", "已完成"],
    )

    assert prop.enum_values == ["待处理", "已完成"]


def test_enum_requires_candidates(session: Session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="工单")

    with pytest.raises(InvariantViolationError, match="requires enum_values"):
        ontology.create_property(session, owner_type_id=obj.id, name="状态", datatype="enum")


@pytest.mark.parametrize(
    "values",
    [
        ["只有一个"],
        ["待处理", "待处理"],
        ["待处理", "  "],
    ],
)
def test_enum_candidates_must_be_distinct_and_non_empty(session: Session, values: list[str]):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="工单")

    with pytest.raises(InvariantViolationError):
        ontology.create_property(
            session,
            owner_type_id=obj.id,
            name="状态",
            datatype="enum",
            enum_values=values,
        )


def test_non_enum_datatype_rejects_candidates(session: Session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="工单")

    with pytest.raises(InvariantViolationError, match="only allowed when datatype is 'enum'"):
        ontology.create_property(
            session,
            owner_type_id=obj.id,
            name="备注",
            datatype="text",
            enum_values=["A", "B"],
        )


def test_switching_to_enum_needs_candidates_in_the_same_patch(session: Session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    obj = ontology.create_object_type(session, owner_id=owner, name="工单")
    prop = ontology.create_property(session, owner_type_id=obj.id, name="状态", datatype="text")

    with pytest.raises(InvariantViolationError, match="requires enum_values"):
        element_edit.apply_edits(
            session,
            graph_id=g.id,
            ops=[element_edit.ElementUpdate("property", prop.id, {"datatype": "enum"})],
            owner_id=owner,
        )

    element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[
            element_edit.ElementUpdate(
                "property",
                prop.id,
                {"datatype": "enum", "enum_values": ["待处理", "已完成"]},
            )
        ],
        owner_id=owner,
    )
    assert session.get(Property, prop.id).enum_values == ["待处理", "已完成"]


def test_switching_away_from_enum_must_clear_the_candidates(session: Session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    obj = ontology.create_object_type(session, owner_id=owner, name="工单")
    prop = ontology.create_property(
        session, owner_type_id=obj.id, name="状态", datatype="enum", enum_values=["A", "B"]
    )

    with pytest.raises(InvariantViolationError, match="only allowed when datatype is 'enum'"):
        element_edit.apply_edits(
            session,
            graph_id=g.id,
            ops=[element_edit.ElementUpdate("property", prop.id, {"datatype": "text"})],
            owner_id=owner,
        )

    element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[
            element_edit.ElementUpdate(
                "property", prop.id, {"datatype": "text", "enum_values": None}
            )
        ],
        owner_id=owner,
    )
    assert session.get(Property, prop.id).enum_values is None


def test_reject_restores_the_candidates(session: Session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    obj = ontology.create_object_type(session, owner_id=owner, name="工单")
    prop = ontology.create_property(
        session, owner_type_id=obj.id, name="状态", datatype="enum", enum_values=["A", "B"]
    )

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[
            element_edit.ElementUpdate(
                "property", prop.id, {"enum_values": ["待处理", "已完成", "已关闭"]}
            )
        ],
        owner_id=owner,
    )
    assert session.get(Property, prop.id).enum_values == ["待处理", "已完成", "已关闭"]

    changeset.reject_changeset(session, cs.id, owner_id=owner)
    assert session.get(Property, prop.id).enum_values == ["A", "B"]


def test_database_refuses_enum_without_candidates(session: Session):
    """服务层之外的路子（脚本、以后的新入口）也不能建出没有候选值的枚举属性。"""
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="工单")
    session.add(Property(owner_type_id=obj.id, name="状态", datatype="enum", status="draft"))

    with pytest.raises(IntegrityError):
        session.flush()
    session.rollback()
