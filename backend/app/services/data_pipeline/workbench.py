"""草稿、预览、构建与来源的 BFF 编排；只做 DTO 转换与转发。"""

from __future__ import annotations

import uuid
from typing import Any

from app.clients.data_service import mapping
from app.clients.data_service.client import DataServiceClient
from app.schemas.data_pipeline import DraftCommandRequest, NodePosition, PreviewRequest


def get_draft(client: DataServiceClient, *, owner_id: uuid.UUID, pipeline_id: uuid.UUID) -> dict:
    return mapping.draft(client.get_draft(pipeline_id, owner_id=owner_id))


def _node_payload(node: dict[str, Any]) -> dict[str, Any]:
    payload = {
        "id": node.get("id") or node.get("nodeId"),
        "type": node.get("type") or node.get("nodeType"),
        "name": node.get("name"),
        "config": node.get("config"),
        "position": node.get("position"),
    }
    return {key: value for key, value in payload.items() if value is not None}


def _edge_payload(edge: dict[str, Any]) -> dict[str, Any]:
    payload = {
        "id": edge.get("id") or edge.get("edgeId"),
        "sourceNodeId": edge.get("source_node_id") or edge.get("sourceNodeId"),
        "sourcePort": edge.get("source_port") or edge.get("sourcePort"),
        "targetNodeId": edge.get("target_node_id") or edge.get("targetNodeId"),
        "targetPort": edge.get("target_port") or edge.get("targetPort"),
    }
    return {key: value for key, value in payload.items() if value is not None}


def _output_payload(output: dict[str, Any]) -> dict[str, Any]:
    payload = {
        "nodeId": output.get("node_id") or output.get("nodeId"),
        "name": output.get("name"),
        "writeMode": output.get("write_mode") or output.get("writeMode"),
        "primaryKeyColumns": output.get("primary_key_columns") or output.get("primaryKeyColumns"),
        "expectations": output.get("expectations"),
    }
    return {key: value for key, value in payload.items() if value is not None}


def draft_command_payload(command: DraftCommandRequest) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "type": command.type,
        "baseRevision": command.base_revision,
        "operationId": str(command.operation_id),
        "turnId": str(command.turn_id or uuid.uuid4()),
    }
    if command.node is not None:
        payload["node"] = _node_payload(command.node)
    if command.edge is not None:
        payload["edge"] = _edge_payload(command.edge)
    if command.output is not None:
        payload["output"] = _output_payload(command.output)
    return payload


def apply_draft_command(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    command: DraftCommandRequest,
) -> dict:
    return mapping.draft(
        client.apply_draft_command(
            pipeline_id,
            owner_id=owner_id,
            payload=draft_command_payload(command),
        )
    )


def move_nodes(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    positions: list[NodePosition],
) -> dict:
    result: dict = {}
    for position in positions:
        result = mapping.draft(
            client.move_node(
                pipeline_id,
                owner_id=owner_id,
                payload={
                    "nodeId": str(position.node_id),
                    "position": {"x": position.x, "y": position.y},
                },
            )
        )
    return result


def check_pipeline(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    revision: int,
) -> dict:
    return mapping.check_result(
        client.check_pipeline(pipeline_id, owner_id=owner_id, revision=revision)
    )


def undo_turn(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    turn_id: uuid.UUID,
    revision: int,
) -> dict:
    return mapping.draft(
        client.undo_turn(pipeline_id, turn_id, owner_id=owner_id, revision=revision)
    )


def node_catalog(client: DataServiceClient) -> dict:
    return mapping.node_catalog(client.get_node_catalog())


def input_datasets(
    client: DataServiceClient, *, owner_id: uuid.UUID, limit: int, offset: int
) -> dict:
    return mapping.input_dataset_page(
        client.list_input_datasets(owner_id=owner_id, limit=limit, offset=offset)
    )


def dataset_schema(
    client: DataServiceClient, *, owner_id: uuid.UUID, dataset_id: uuid.UUID
) -> dict:
    return mapping.dataset_schema(client.get_dataset_schema(dataset_id, owner_id=owner_id))


def preview_node(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    request: PreviewRequest,
) -> dict:
    return mapping.preview(
        client.preview_node(
            pipeline_id,
            owner_id=owner_id,
            payload={
                "draftRevision": request.draft_revision,
                "nodeId": str(request.node_id),
                "requestId": str(request.request_id),
                "rowLimit": request.row_limit,
            },
        )
    )


def cancel_preview(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
) -> None:
    client.cancel_preview(pipeline_id, owner_id=owner_id)


def create_build(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    draft_revision: int,
    operation_id: uuid.UUID,
) -> dict:
    return mapping.build(
        client.create_build(
            pipeline_id,
            owner_id=owner_id,
            payload={
                "draftRevision": draft_revision,
                "operationId": str(operation_id),
            },
        )
    )


def get_build(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    build_id: uuid.UUID,
) -> dict:
    return mapping.build(client.get_build(pipeline_id, build_id, owner_id=owner_id))


def get_latest_build(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
) -> dict:
    return mapping.build(client.get_latest_build(pipeline_id, owner_id=owner_id))


def cancel_build(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    build_id: uuid.UUID,
    operation_id: uuid.UUID | None = None,
) -> dict[str, str]:
    client.cancel_build(
        pipeline_id,
        build_id,
        owner_id=owner_id,
        operation_id=operation_id,
    )
    return {"status": "cancel_requested"}


def output_sources(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    dataset_id: uuid.UUID,
    version_id: uuid.UUID | None = None,
) -> dict:
    return mapping.output_sources(
        client.get_output_sources(
            pipeline_id,
            dataset_id,
            owner_id=owner_id,
            version_id=version_id,
        )
    )


def output_preview(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    dataset_id: uuid.UUID,
    version_id: uuid.UUID | None = None,
    limit: int = 100,
) -> dict:
    return mapping.dataset_preview(
        client.get_pipeline_dataset_preview(
            pipeline_id,
            dataset_id,
            owner_id=owner_id,
            version_id=version_id,
            limit=limit,
        )
    )
