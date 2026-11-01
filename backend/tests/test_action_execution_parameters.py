from __future__ import annotations

import uuid

import pytest
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.services import action, status
from app.services.action import execution, presentation
from tests.support.action_presentation_fixtures import graph_with_close_ticket_action


class _Client:
    def __init__(self) -> None:
        self.objects: dict[tuple[uuid.UUID, str], dict] = {}

    def get_object(self, object_type_id, *, owner_id, primary_key):
        del owner_id
        try:
            return self.objects[(object_type_id, primary_key)]
        except KeyError as exc:
            raise NotFoundError("object not found") from exc


def _fixture(session: Session) -> tuple[dict[str, uuid.UUID], execution.ActionContext, _Client]:
    ids = graph_with_close_ticket_action(session)
    default_param = action.create_parameter(
        session,
        action_type_id=ids["action_id"],
        name="备注",
        datatype="text",
        default_value="默认备注",
    )
    array_param = action.create_parameter(
        session,
        action_type_id=ids["action_id"],
        name="标签",
        datatype="text",
        is_array=True,
    )
    enum_param = action.create_parameter(
        session,
        action_type_id=ids["action_id"],
        name="新状态",
        datatype="enum",
        enum_values=["处理中", "已关闭"],
    )
    for parameter in (default_param, array_param, enum_param):
        parameter.status = status.CONFIRMED
    session.flush()
    detail = presentation.load_action_detail_in_graph(
        session,
        ids["action_id"],
        owner_id=ids["owner"],
        graph_id=ids["graph_id"],
    )
    return ids, detail, _Client()


def _object_ref(ids: dict[str, uuid.UUID], object_type_id: uuid.UUID) -> dict[str, str]:
    del ids
    return {"object_type_id": str(object_type_id), "primary_key": "T-1"}


def _with_required(
    ids: dict[str, uuid.UUID],
    parameter_ids: dict[str, uuid.UUID],
    client: _Client,
) -> dict[str, object]:
    client.objects[(ids["ticket_id"], "T-1")] = {"primary_key": "T-1"}
    client.objects[(ids["part_id"], "P-1")] = {"primary_key": "P-1"}
    return {
        str(parameter_ids["工单"]): {
            "object_type_id": str(ids["ticket_id"]),
            "primary_key": "T-1",
        },
        str(parameter_ids["配件"]): {
            "object_type_id": str(ids["part_id"]),
            "primary_key": "P-1",
        },
    }


def test_parameters_resolve_values_defaults_and_object_refs(session: Session):
    ids, detail, client = _fixture(session)
    parameter_ids = {item.name: item.id for item in detail.parameters}
    supplied = _with_required(ids, parameter_ids, client)

    resolved = execution.resolve_parameters(
        detail,
        {
            **supplied,
            str(parameter_ids["解决说明"]): "已更换硒鼓",
        },
        owner_id=ids["owner"],
        client=client,
    )

    assert resolved.values[parameter_ids["解决说明"]] == "已更换硒鼓"
    assert resolved.values[parameter_ids["备注"]] == "默认备注"
    assert resolved.objects[parameter_ids["工单"]].primary_key == "T-1"
    assert resolved.normalized["objects"][str(parameter_ids["工单"])]["primary_key"] == "T-1"


def test_explicit_null_clears_instead_of_applying_default(session: Session):
    ids, detail, client = _fixture(session)
    parameter_ids = {item.name: item.id for item in detail.parameters}
    supplied = _with_required(ids, parameter_ids, client)

    resolved = execution.resolve_parameters(
        detail,
        {**supplied, str(parameter_ids["备注"]): None},
        owner_id=ids["owner"],
        client=client,
    )

    assert resolved.values[parameter_ids["备注"]] is None


def test_missing_required_parameter_has_stable_code(session: Session):
    ids, detail, client = _fixture(session)

    with pytest.raises(execution.ActionExecutionError) as raised:
        execution.resolve_parameters(detail, {}, owner_id=ids["owner"], client=client)

    assert raised.value.code == "MISSING_PARAMETER"
    assert raised.value.details["parameter_name"] == "工单"


def test_unknown_parameter_is_rejected(session: Session):
    ids, detail, client = _fixture(session)

    with pytest.raises(execution.ActionExecutionError) as raised:
        execution.resolve_parameters(
            detail,
            {str(uuid.uuid4()): "x"},
            owner_id=ids["owner"],
            client=client,
        )

    assert raised.value.code == "UNKNOWN_PARAMETER"


def test_value_type_and_enum_are_validated(session: Session):
    ids, detail, client = _fixture(session)
    parameter_ids = {item.name: item.id for item in detail.parameters}
    supplied = _with_required(ids, parameter_ids, client)

    with pytest.raises(execution.ActionExecutionError) as bad_enum:
        execution.resolve_parameters(
            detail,
            {**supplied, str(parameter_ids["新状态"]): "未知状态"},
            owner_id=ids["owner"],
            client=client,
        )
    assert bad_enum.value.code == "INVALID_PARAMETER"

    with pytest.raises(execution.ActionExecutionError) as bad_text:
        execution.resolve_parameters(
            detail,
            {**supplied, str(parameter_ids["解决说明"]): 42},
            owner_id=ids["owner"],
            client=client,
        )
    assert bad_text.value.code == "INVALID_PARAMETER"


def test_object_ref_type_mismatch_and_missing_object_are_rejected(session: Session):
    ids, detail, client = _fixture(session)
    parameter_ids = {item.name: item.id for item in detail.parameters}
    supplied = _with_required(ids, parameter_ids, client)

    with pytest.raises(execution.ActionExecutionError) as mismatch:
        execution.resolve_parameters(
            detail,
            {
                **supplied,
                str(parameter_ids["工单"]): {
                    "object_type_id": str(ids["part_id"]),
                    "primary_key": "T-1",
                },
            },
            owner_id=ids["owner"],
            client=client,
        )
    assert mismatch.value.code == "OBJECT_TYPE_MISMATCH"

    with pytest.raises(execution.ActionExecutionError) as missing:
        client.objects.pop((ids["ticket_id"], "T-1"), None)
        execution.resolve_parameters(
            detail,
            {
                str(parameter_ids["配件"]): {
                    "object_type_id": str(ids["part_id"]),
                    "primary_key": "P-1",
                },
                str(parameter_ids["工单"]): _object_ref(ids, ids["ticket_id"]),
            },
            owner_id=ids["owner"],
            client=client,
        )
    assert missing.value.code == "OBJECT_NOT_FOUND"


def test_array_parameter_is_rejected_before_execution(session: Session):
    ids, detail, client = _fixture(session)
    parameter_ids = {item.name: item.id for item in detail.parameters}
    supplied = _with_required(ids, parameter_ids, client)

    with pytest.raises(execution.ActionExecutionError) as raised:
        execution.resolve_parameters(
            detail,
            {**supplied, str(parameter_ids["标签"]): ["a"]},
            owner_id=ids["owner"],
            client=client,
        )

    assert raised.value.code == "UNSUPPORTED_ARRAY"
