import uuid

import pytest

from app.agent import view_tools as tools
from app.errors import InvariantViolationError


def test_set_layout_writes_declarative_spec():
    spec = tools.set_layout({}, kind="domain-grouped", options={"spacing": 80})
    assert spec["layout"] == {"kind": "domain-grouped", "options": {"spacing": 80}}


def test_set_layout_rejects_unknown_kind():
    with pytest.raises(InvariantViolationError):
        tools.set_layout({}, kind="spiral")


def test_highlight_dedups_and_sorts():
    node = str(uuid.uuid4())
    spec = tools.highlight({}, node_ids=[node, node])
    assert [entry["nodeIds"] for entry in spec["highlight"]] == [[node]]


def test_group_moves_nodes_out_of_previous_group():
    first, second = str(uuid.uuid4()), str(uuid.uuid4())
    spec = tools.group({}, group_id="g1", label="销售域", node_ids=[first, second])
    spec = tools.group(spec, group_id="g2", label="供应链域", node_ids=[second])
    assert {group["id"]: group["nodeIds"] for group in spec["groups"]} == {
        "g1": [first],
        "g2": [second],
    }


def test_collapse_expand_and_filter_round_trip():
    spec = tools.collapse({}, group_ids=["g1", "g2"])
    assert tools.expand(spec, group_ids=["g1"])["collapsed"] == ["g2"]
    assert tools.filter_view({}, predicate=None)["filters"] == {}


def test_focus_and_reset_are_local_to_spec():
    node = str(uuid.uuid4())
    spec = tools.focus({}, node_ids=[node])
    assert spec["camera"] == {"focus": [node]}
    assert tools.reset_view(spec) == {}


def test_annotate_rejects_blank_text():
    with pytest.raises(InvariantViolationError):
        tools.annotate({}, node_id=str(uuid.uuid4()), text="   ")


def test_tools_do_not_mutate_the_input_spec():
    original = {"layout": {"kind": "hierarchical"}}
    tools.set_layout(original, kind="force")
    assert original == {"layout": {"kind": "hierarchical"}}
