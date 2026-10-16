"""提交校验的条件树：形状校验、运算符矩阵、以及自引用表的删除顺序。"""

import uuid
import warnings

import pytest
from sqlalchemy.exc import SAWarning
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import ActionCriteriaNode, ActionType, ObjectType
from app.services import action, changeset, criteria, element_edit, graph, ontology


class _Fixture:
    def __init__(self, session: Session, owner: uuid.UUID) -> None:
        self.owner = owner
        self.action_type = action.create_action_type(session, owner_id=owner, name="指派工单")
        self.ticket = ontology.create_object_type(session, owner_id=owner, name="工单")
        self.employee = ontology.create_object_type(session, owner_id=owner, name="员工")
        self.status_prop = ontology.create_property(
            session,
            owner_type_id=self.ticket.id,
            name="状态",
            datatype="enum",
            enum_values=["open", "closed"],
        )
        self.employee_name = ontology.create_property(
            session, owner_type_id=self.employee.id, name="姓名", datatype="text"
        )
        self.amount_param = action.create_parameter(
            session, action_type_id=self.action_type.id, name="金额", datatype="decimal"
        )
        self.status_param = action.create_parameter(
            session,
            action_type_id=self.action_type.id,
            name="状态",
            datatype="enum",
            enum_values=["open", "closed"],
        )
        self.other_status_param = action.create_parameter(
            session,
            action_type_id=self.action_type.id,
            name="别的状态",
            datatype="enum",
            enum_values=["open", "pending"],
        )
        self.ticket_param = action.create_parameter(
            session,
            action_type_id=self.action_type.id,
            name="工单",
            kind="object_ref",
            target_object_type_id=self.ticket.id,
        )

    @property
    def action_type_id(self) -> uuid.UUID:
        return self.action_type.id


@pytest.fixture
def fixture(session: Session) -> _Fixture:
    return _Fixture(session, uuid.uuid4())


def _replace(session: Session, fixture: _Fixture, conditions):
    return criteria.replace_criteria(
        session, action_type_id=fixture.action_type_id, conditions=conditions
    )


def test_replace_criteria_wraps_conditions_in_one_and_root(session: Session, fixture: _Fixture):
    _replace(
        session,
        fixture,
        [
            {
                "operator": ">",
                "failure_message": "金额需大于 1000",
                "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                "right": {"kind": "static", "static_value": 1000},
            },
            {
                "operator": "is",
                "failure_message": "工单必须为未关闭",
                "left": {
                    "kind": "object_property",
                    "object_parameter_id": fixture.ticket_param.id,
                    "object_property_id": fixture.status_prop.id,
                },
                "right": {"kind": "static", "static_value": "open"},
            },
        ],
    )

    roots = criteria.load_tree(session, fixture.action_type_id)
    assert len(roots) == 1
    root = roots[0]
    assert (root.node_kind, root.logic) == ("group", "and")
    assert [child.operator for child in root.children] == [">", "is"]
    assert root.children[0].left.kind == "parameter"
    assert root.children[0].right.static_value == 1000
    assert root.children[1].left.object_property_id == fixture.status_prop.id
    assert root.children[1].failure_message == "工单必须为未关闭"


def test_replace_criteria_clears_the_old_tree_first(session: Session, fixture: _Fixture):
    _replace(
        session,
        fixture,
        [
            {
                "operator": "is null",
                "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
            }
        ],
    )
    first = criteria.list_nodes(session, fixture.action_type_id)

    _replace(
        session,
        fixture,
        [
            {
                "operator": "is null",
                "left": {"kind": "parameter", "value_parameter_id": fixture.status_param.id},
            }
        ],
    )

    nodes = criteria.list_nodes(session, fixture.action_type_id)
    assert {node.id for node in nodes}.isdisjoint({node.id for node in first})
    assert criteria.list_operands(session, [node.id for node in first]) == []

    _replace(session, fixture, [])
    assert criteria.list_nodes(session, fixture.action_type_id) == []


def test_unary_operators_take_no_right_operand(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="takes no right operand"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is null",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                    "right": {"kind": "static", "static_value": 1},
                }
            ],
        )


def test_binary_operators_need_a_right_operand(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="needs a right operand"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                }
            ],
        )


def test_a_condition_needs_a_left_operand(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="needs a left operand"):
        _replace(session, fixture, [{"operator": "is null"}])


def test_operator_matrix_follows_the_left_datatype(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="does not apply to datatype"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": ">",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.status_param.id},
                    "right": {"kind": "static", "static_value": "open"},
                }
            ],
        )

    _replace(
        session,
        fixture,
        [
            {
                "operator": ">=",
                "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                "right": {"kind": "static", "static_value": 0},
            }
        ],
    )


def test_static_values_and_enum_sides_are_type_checked(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="does not fit datatype"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": ">",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                    "right": {"kind": "static", "static_value": "很多"},
                }
            ],
        )

    with pytest.raises(InvariantViolationError, match="must use the same enum values"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.status_param.id},
                    "right": {
                        "kind": "parameter",
                        "value_parameter_id": fixture.other_status_param.id,
                    },
                }
            ],
        )


def test_the_left_side_must_name_a_value(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="left operand must be a parameter"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {"kind": "static", "static_value": 1},
                    "right": {"kind": "static", "static_value": 1},
                }
            ],
        )


def test_operand_kinds_map_to_their_own_columns(session: Session, fixture: _Fixture):
    with pytest.raises(InvariantViolationError, match="must not carry other columns"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                    "right": {
                        "kind": "parameter",
                        "value_parameter_id": fixture.amount_param.id,
                        "static_value": 1,
                    },
                }
            ],
        )

    with pytest.raises(InvariantViolationError, match="use 'is null' to test emptiness"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                    "right": {"kind": "static"},
                }
            ],
        )

    with pytest.raises(InvariantViolationError, match="unsupported operand kind"):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is null",
                    "left": {"kind": "magic", "static_value": 1},
                }
            ],
        )


def test_object_property_operands_must_live_on_the_parameter_type(
    session: Session, fixture: _Fixture
):
    message = "does not belong to the parameter's object type"
    with pytest.raises(InvariantViolationError, match=message):
        _replace(
            session,
            fixture,
            [
                {
                    "operator": "is",
                    "left": {
                        "kind": "object_property",
                        "object_parameter_id": fixture.ticket_param.id,
                        "object_property_id": fixture.employee_name.id,
                    },
                    "right": {"kind": "static", "static_value": "张三"},
                }
            ],
        )


def test_ensure_tree_shape_rejects_a_second_root(session: Session, fixture: _Fixture):
    criteria.create_group(session, action_type_id=fixture.action_type_id)
    criteria.create_group(session, action_type_id=fixture.action_type_id)

    with pytest.raises(InvariantViolationError, match="exactly one root"):
        criteria.ensure_tree_shape(session, action_type_id=fixture.action_type_id, where="test")


def test_ensure_tree_shape_rejects_nested_groups(session: Session, fixture: _Fixture):
    root = criteria.create_group(session, action_type_id=fixture.action_type_id)
    criteria.create_group(session, action_type_id=fixture.action_type_id, parent_id=root.id)

    with pytest.raises(InvariantViolationError, match="not a flat condition under the root"):
        criteria.ensure_tree_shape(session, action_type_id=fixture.action_type_id, where="test")


def test_ensure_tree_shape_rejects_a_cycle(session: Session, fixture: _Fixture):
    root = criteria.create_group(session, action_type_id=fixture.action_type_id)
    child = criteria.create_group(session, action_type_id=fixture.action_type_id)
    root.parent_id = child.id
    child.parent_id = root.id
    session.flush()

    with pytest.raises(InvariantViolationError, match="sits on a cycle"):
        criteria.ensure_tree_shape(session, action_type_id=fixture.action_type_id, where="test")


def test_empty_tree_is_fine(session: Session, fixture: _Fixture):
    criteria.ensure_tree_shape(session, action_type_id=fixture.action_type_id, where="test")


def test_node_patch_refuses_kind_changes(session: Session, fixture: _Fixture):
    root = criteria.create_group(session, action_type_id=fixture.action_type_id)

    with pytest.raises(InvariantViolationError, match="cannot change its kind"):
        criteria.validate_node_patch(session, node_id=root.id, patch={"node_kind": "condition"})
    with pytest.raises(InvariantViolationError, match="cannot be its own parent"):
        criteria.validate_node_patch(session, node_id=root.id, patch={"parent_id": root.id})


def test_operand_patch_is_checked_against_the_condition(session: Session, fixture: _Fixture):
    _replace(
        session,
        fixture,
        [
            {
                "operator": ">",
                "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
                "right": {"kind": "static", "static_value": 1000},
            }
        ],
    )
    node = criteria.list_nodes(session, fixture.action_type_id)[1]
    right = [item for item in criteria.list_operands(session, [node.id]) if item.side == "right"][0]

    with pytest.raises(InvariantViolationError, match="does not fit datatype"):
        criteria.validate_operand_patch(
            session, operand_id=right.id, patch={"static_value": "很多"}
        )
    criteria.validate_operand_patch(session, operand_id=right.id, patch={"static_value": 2000})


def test_nodes_by_depth_desc_puts_children_first(session: Session, fixture: _Fixture):
    root = criteria.create_group(session, action_type_id=fixture.action_type_id)
    middle = criteria.create_group(
        session, action_type_id=fixture.action_type_id, parent_id=root.id
    )
    leaf = criteria.create_condition(
        session, action_type_id=fixture.action_type_id, operator="is null", parent_id=middle.id
    )

    ordered = criteria.nodes_by_depth_desc([root, middle, leaf])

    assert [node.id for node in ordered] == [leaf.id, middle.id, root.id]


def test_confirming_deletes_criteria_nodes_child_first(session: Session, fixture: _Fixture):
    """同一档位里的自引用节点：删除顺序必须是子先父后。"""
    owner = fixture.owner
    g = graph.create_graph(session, owner_id=owner, name="测试图")
    root = criteria.create_group(session, action_type_id=fixture.action_type_id)
    middle = criteria.create_group(
        session, action_type_id=fixture.action_type_id, parent_id=root.id
    )
    leaf = criteria.create_condition(
        session, action_type_id=fixture.action_type_id, operator="is null", parent_id=middle.id
    )

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[
            element_edit.ElementDelete("action_criteria_node", root.id),
            element_edit.ElementDelete("action_criteria_node", middle.id),
            element_edit.ElementDelete("action_criteria_node", leaf.id),
        ],
        owner_id=owner,
    )
    with warnings.catch_warnings():
        warnings.simplefilter("error", SAWarning)
        changeset.confirm_changeset(session, cs.id, owner_id=owner)

    for node_id in (root.id, middle.id, leaf.id):
        assert session.get(ActionCriteriaNode, node_id) is None
    assert session.get(ActionType, fixture.action_type_id) is not None


def test_revert_removes_a_freshly_built_action_tree(session: Session, fixture: _Fixture):
    owner = uuid.uuid4()
    g = graph.create_graph(session, owner_id=owner, name="测试图")

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[element_edit.ActionTypeCreate(name="新建动作")],
        owner_id=owner,
    )
    action_type_id = changeset.list_ops(session, cs.id)[0].target_id
    parameter = action.create_parameter(
        session, action_type_id=action_type_id, name="金额", datatype="decimal"
    )
    criteria.create_group(session, action_type_id=action_type_id)
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op=changeset.OP_CREATE,
        target_id=parameter.id,
        payload={"kind": element_edit.ACTION_PARAMETER},
    )

    with warnings.catch_warnings():
        warnings.simplefilter("error", SAWarning)
        changeset.reject_changeset(session, cs.id, owner_id=owner)

    assert session.get(ActionType, action_type_id) is None
    assert criteria.list_nodes(session, action_type_id) == []
    assert session.get(ObjectType, fixture.ticket.id) is not None
