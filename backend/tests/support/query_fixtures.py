"""查询测试共享的假 Java 客户端与本体构造。"""

from __future__ import annotations

import uuid

from app.services import ontology


class FakeQueryClient:
    def __init__(self) -> None:
        self.objects: dict[uuid.UUID, dict] = {}
        self.details: dict[tuple[uuid.UUID, str], dict] = {}
        self.steps: dict = {}
        self.last_list_args: dict = {}
        self.last_step_args: dict = {}

    def query_objects(
        self,
        object_type_id,
        *,
        owner_id,
        q=None,
        filters=None,
        sort=None,
        limit=20,
        project=None,
    ):
        self.last_list_args = {
            "object_type_id": object_type_id,
            "owner_id": owner_id,
            "q": q,
            "filters": filters,
            "sort": sort,
            "limit": limit,
            "project": project,
        }
        return self.objects[object_type_id]

    def get_object(self, object_type_id, *, owner_id, primary_key):
        return self.details[(object_type_id, primary_key)]

    def query_step(
        self,
        relation_id,
        *,
        owner_id,
        direction,
        inputs,
        filters=None,
        sort=None,
        limit=20,
        project=None,
    ):
        self.last_step_args = {
            "relation_id": relation_id,
            "owner_id": owner_id,
            "direction": direction,
            "inputs": inputs,
            "filters": filters,
            "sort": sort,
            "limit": limit,
            "project": project,
        }
        return self.steps


def _object_with_id(session, owner, name: str):
    obj = ontology.create_object_type(session, owner_id=owner, name=name)
    prop = ontology.create_property(
        session,
        owner_type_id=obj.id,
        name="id",
        datatype="text",
        is_primary_key=True,
    )
    title = ontology.create_property(
        session,
        owner_type_id=obj.id,
        name="name",
        datatype="text",
        is_title_key=True,
    )
    return obj, prop, title
