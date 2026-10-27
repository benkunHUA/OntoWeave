from __future__ import annotations

from app.agent.tools.query.state import _merge_evidence, next_step_id, step_update


def test_parallel_calls_get_unique_step_ids_from_tool_call_id() -> None:
    state = {"steps": {"s0": [{"primary_key": "c-1"}]}, "step_counter": 0}

    first_id, first_counter = next_step_id(state, tool_call_id="call-1", parallel=True)
    second_id, second_counter = next_step_id(state, tool_call_id="call-2", parallel=True)

    assert first_id.startswith("s1-")
    assert second_id.startswith("s1-")
    assert first_id != second_id
    assert first_counter == second_counter == 1


def test_step_update_returns_only_new_state_for_reducers() -> None:
    state = {
        "steps": {"s0": [{"primary_key": "c-1"}]},
        "evidence": [{"step_id": "s0"}],
        "turn_id": "turn-2",
    }

    update = step_update(
        state,
        step_id="s1",
        counter=1,
        items=[
            {
                "object_type_id": "type-1",
                "primary_key": "o-1",
                "title": "订单 1",
                "properties": {},
            }
        ],
        result_status="OK",
        index_version_id="version-1",
        is_anchor=True,
    )

    assert update["steps"] == {
        "s1": [
            {
                "object_type_id": "type-1",
                "primary_key": "o-1",
                "title": "订单 1",
                "properties": {},
                "index_version_id": "version-1",
            }
        ]
    }
    assert update["evidence"][0]["step_id"] == "s1"
    assert update["evidence"][0]["turn_id"] == "turn-2"
    assert update["anchor_refs"][0]["primary_key"] == "o-1"
    assert "s0" not in update["steps"]


def test_evidence_reducer_drops_previous_turns() -> None:
    old = [{"turn_id": "turn-1", "step_id": "s0", "primary_key": "112"}]
    current = [{"turn_id": "turn-2", "step_id": "s1", "primary_key": "8"}]
    next_step = [{"turn_id": "turn-2", "step_id": "s2", "primary_key": "297"}]

    assert _merge_evidence(old, current) == current
    assert _merge_evidence(current, next_step) == [*current, *next_step]
