"""行动执行测试共享对象：一小组带主键、数组属性和两类关系的本体。"""

from __future__ import annotations

import copy
import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.errors import DataServiceUnavailableError, NotFoundError
from app.services import action, ontology
from app.services.action import rules as action_rules
from app.services.action.detail import ActionDetail, load_action_detail


class ActionExecutionFixture:
    def __init__(self, session: Session, owner: uuid.UUID) -> None:
        self.owner = owner
        self.action_type = action.create_action_type(session, owner_id=owner, name="处理工单")
        self.ticket = ontology.create_object_type(session, owner_id=owner, name="工单")
        self.employee = ontology.create_object_type(session, owner_id=owner, name="员工")
        self.tag = ontology.create_object_type(session, owner_id=owner, name="标签")

        self.ticket_key = ontology.create_property(
            session,
            owner_type_id=self.ticket.id,
            name="工单号",
            datatype="text",
            is_primary_key=True,
        )
        self.amount = ontology.create_property(
            session, owner_type_id=self.ticket.id, name="金额", datatype="decimal"
        )
        self.status = ontology.create_property(
            session,
            owner_type_id=self.ticket.id,
            name="状态",
            datatype="enum",
            enum_values=["open", "closed"],
        )
        self.channel = ontology.create_property(
            session,
            owner_type_id=self.ticket.id,
            name="渠道",
            datatype="enum",
            enum_values=["电话", "在线"],
        )
        self.aliases = ontology.create_property(
            session,
            owner_type_id=self.ticket.id,
            name="别名",
            datatype="text",
            is_array=True,
        )
        self.employee_key = ontology.create_property(
            session,
            owner_type_id=self.employee.id,
            name="员工号",
            datatype="integer",
            is_primary_key=True,
        )
        self.employee_name = ontology.create_property(
            session, owner_type_id=self.employee.id, name="姓名", datatype="text"
        )
        self.salary = ontology.create_property(
            session, owner_type_id=self.employee.id, name="薪资", datatype="decimal"
        )

        self.ticket_param = self._object_parameter(session, "工单", self.ticket.id)
        self.employee_param = self._object_parameter(session, "员工", self.employee.id)
        self.tag_param = self._object_parameter(session, "标签", self.tag.id)
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
        self.labels = ontology.create_relation(
            session,
            owner_id=owner,
            source_type_id=self.ticket.id,
            target_type_id=self.tag.id,
            name="打标签",
            cardinality="many_to_many",
        )
        self.owns = ontology.create_relation(
            session,
            owner_id=owner,
            source_type_id=self.employee.id,
            target_type_id=self.ticket.id,
            name="负责",
            cardinality="one_to_many",
        )

    def _object_parameter(self, session: Session, name: str, object_type_id: uuid.UUID) -> Any:
        return action.create_parameter(
            session,
            action_type_id=self.action_type.id,
            name=name,
            kind="object_ref",
            target_object_type_id=object_type_id,
            is_required=True,
        )

    def rule(self, session: Session, rule_kind: str, **kwargs: Any):
        return action_rules.create_rule(
            session,
            action_type_id=self.action_type.id,
            rule_kind=rule_kind,
            **kwargs,
        )

    def detail(self, session: Session) -> ActionDetail:
        return load_action_detail(session, self.action_type.id)


def build_action_execution_fixture(session: Session) -> ActionExecutionFixture:
    return ActionExecutionFixture(session, uuid.uuid4())


class CloseTicketEditClient:
    """`graph_with_close_ticket_action` 的 preview/apply 假客户端。"""

    def __init__(self, ids: dict[str, uuid.UUID]) -> None:
        self.ids = ids
        self.objects: dict[tuple[uuid.UUID, str], dict] = {}
        self.preview_requests: list[dict[str, Any]] = []
        self.apply_requests: list[dict[str, Any]] = []
        self.version = 4
        self.applicable = True
        self.conflicts: list[dict[str, Any]] = []
        self.apply_failures = 0

    def get_object(self, object_type_id, *, owner_id, primary_key):
        del owner_id
        try:
            return self.objects[(object_type_id, primary_key)]
        except KeyError as exc:
            raise NotFoundError("object not found") from exc

    def preview_edit_set(self, payload: dict[str, Any]) -> dict[str, Any]:
        self.preview_requests.append(copy.deepcopy(payload))
        return {
            "execution_id": str(payload["execution_id"]),
            "objects": self._object_changes(),
            "relations": self._relation_changes(),
            "conflicts": self.conflicts,
            "applicable": self.applicable and not self.conflicts,
        }

    def apply_edit_set(self, payload: dict[str, Any]) -> dict[str, Any]:
        self.apply_requests.append(copy.deepcopy(payload))
        if self.apply_failures:
            self.apply_failures -= 1
            raise DataServiceUnavailableError("edit set apply timed out")
        objects = []
        for change in self._object_changes():
            after = change.get("after")
            state = after if isinstance(after, dict) else {}
            objects.append(
                {
                    "object_type_id": change["object_type_id"],
                    "primary_key": change["primary_key"],
                    "exists": change["kind"] != "OBJECT_DELETE",
                    "title": state.get("title"),
                    "properties": state.get("properties"),
                    "version": state.get("version"),
                }
            )
        relations = [
            {
                "relation_id": item["relation_id"],
                "from_object_type_id": item["from_object_type_id"],
                "from_primary_key": item["from_primary_key"],
                "to_object_type_id": item["to_object_type_id"],
                "to_primary_key": item["to_primary_key"],
                "exists": item["after_exists"],
            }
            for item in self._relation_changes()
        ]
        return {
            "execution_id": payload["execution_id"],
            "objects": objects,
            "relations": relations,
        }

    def _object_changes(self) -> list[dict[str, Any]]:
        ids = self.ids
        return [
            {
                "kind": "OBJECT_MODIFY",
                "object_type_id": str(ids["ticket_id"]),
                "primary_key": "T-1",
                "before": {
                    "primary_key": "T-1",
                    "title": "打印机故障",
                    "properties": {
                        str(ids["ticket_status_id"]): "处理中",
                        str(ids["resolution_id"]): None,
                    },
                    "version": self.version,
                },
                "after": {
                    "primary_key": "T-1",
                    "title": "打印机故障",
                    "properties": {
                        str(ids["ticket_status_id"]): "处理中",
                        str(ids["resolution_id"]): "已修复",
                    },
                    "version": self.version + 1,
                },
            }
        ]

    def _relation_changes(self) -> list[dict[str, Any]]:
        ids = self.ids
        return [
            {
                "kind": "RELATION_CREATE",
                "relation_id": str(ids["relation_id"]),
                "from_object_type_id": str(ids["ticket_id"]),
                "from_primary_key": "T-1",
                "to_object_type_id": str(ids["part_id"]),
                "to_primary_key": "P-1",
                "before_exists": False,
                "after_exists": True,
                "before_version": 0,
            }
        ]
