"""数据流程的浏览器 REST 边界。"""

from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, Header, Query, Response
from sqlalchemy.orm import Session

from app.api import data_pipeline_execution, data_pipeline_schedule
from app.deps import get_current_user_id, get_data_service_client, get_session
from app.errors import InvalidRequestError
from app.schemas.conversation import ConversationRead
from app.schemas.data_pipeline import (
    DataPipelineRead,
    DatasetSchemaRead,
    DraftCommandRequest,
    DraftPositionsRequest,
    DraftRead,
    InputDatasetPageRead,
    NodeCatalogRead,
    PipelineCheckRead,
    PipelineCopy,
    PipelineCreate,
    PipelinePageRead,
    PipelineUpdate,
    UndoTurnRequest,
)
from app.services import data_pipeline, data_pipeline_workbench
from app.services.data_service.client import DataServiceClient

router = APIRouter(tags=["data-pipelines"])


def _operation_id(value: uuid.UUID | None) -> uuid.UUID:
    if value is None:
        raise InvalidRequestError("X-Operation-Id is required")
    return value


@router.get("/pipelines/node-catalog", response_model=NodeCatalogRead)
def get_node_catalog(
    client: DataServiceClient = Depends(get_data_service_client),
) -> NodeCatalogRead:
    return NodeCatalogRead.model_validate(data_pipeline_workbench.node_catalog(client))


@router.get("/pipelines/input-datasets", response_model=InputDatasetPageRead)
def list_input_datasets(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    client: DataServiceClient = Depends(get_data_service_client),
) -> InputDatasetPageRead:
    return InputDatasetPageRead.model_validate(
        data_pipeline_workbench.input_datasets(
            client,
            owner_id=get_current_user_id(),
            limit=limit,
            offset=offset,
        )
    )


@router.get(
    "/pipelines/input-datasets/{dataset_id}/schema",
    response_model=DatasetSchemaRead,
)
def get_dataset_schema(
    dataset_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> DatasetSchemaRead:
    return DatasetSchemaRead.model_validate(
        data_pipeline_workbench.dataset_schema(
            client, owner_id=get_current_user_id(), dataset_id=dataset_id
        )
    )


@router.post("/pipelines", response_model=DataPipelineRead, status_code=201)
def create_pipeline(
    payload: PipelineCreate,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataPipelineRead:
    return DataPipelineRead.model_validate(
        data_pipeline.create_pipeline(
            session,
            owner_id=get_current_user_id(),
            client=client,
            payload=payload,
            operation_id=_operation_id(operation_id),
        )
    )


@router.get("/pipelines", response_model=PipelinePageRead)
def list_pipelines(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    client: DataServiceClient = Depends(get_data_service_client),
) -> PipelinePageRead:
    return PipelinePageRead.model_validate(
        data_pipeline.list_pipelines(
            client, owner_id=get_current_user_id(), limit=limit, offset=offset
        )
    )


@router.get("/pipelines/{pipeline_id}", response_model=DataPipelineRead)
def get_pipeline(
    pipeline_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataPipelineRead:
    return DataPipelineRead.model_validate(
        data_pipeline.get_pipeline(client, owner_id=get_current_user_id(), pipeline_id=pipeline_id)
    )


@router.get("/pipelines/{pipeline_id}/conversation", response_model=ConversationRead | None)
def get_pipeline_conversation(
    pipeline_id: uuid.UUID,
    session: Session = Depends(get_session),
) -> ConversationRead | None:
    conv = data_pipeline.find_pipeline_conversation(
        session, owner_id=get_current_user_id(), pipeline_id=pipeline_id
    )
    if conv is None:
        return None
    return ConversationRead.model_validate(conv)


@router.post(
    "/pipelines/{pipeline_id}/conversation",
    response_model=ConversationRead,
    status_code=201,
)
def ensure_pipeline_conversation(
    pipeline_id: uuid.UUID,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> ConversationRead:
    return ConversationRead.model_validate(
        data_pipeline.ensure_pipeline_conversation(
            session,
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
        )
    )


@router.patch("/pipelines/{pipeline_id}", response_model=DataPipelineRead)
def rename_pipeline(
    pipeline_id: uuid.UUID,
    payload: PipelineUpdate,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataPipelineRead:
    return DataPipelineRead.model_validate(
        data_pipeline.rename_pipeline(
            session,
            owner_id=get_current_user_id(),
            client=client,
            pipeline_id=pipeline_id,
            name=payload.name,
            operation_id=operation_id,
        )
    )


@router.delete("/pipelines/{pipeline_id}", status_code=204)
def archive_pipeline(
    pipeline_id: uuid.UUID,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> Response:
    data_pipeline.archive_pipeline(
        session,
        owner_id=get_current_user_id(),
        client=client,
        pipeline_id=pipeline_id,
        operation_id=operation_id,
    )
    return Response(status_code=204)


@router.post("/pipelines/{pipeline_id}/copy", response_model=DataPipelineRead, status_code=201)
def copy_pipeline(
    pipeline_id: uuid.UUID,
    payload: PipelineCopy,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataPipelineRead:
    return DataPipelineRead.model_validate(
        data_pipeline.copy_pipeline(
            session,
            owner_id=get_current_user_id(),
            client=client,
            source_pipeline_id=pipeline_id,
            payload=payload,
            operation_id=_operation_id(operation_id),
        )
    )


@router.get("/pipelines/{pipeline_id}/draft", response_model=DraftRead)
def get_draft(
    pipeline_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> DraftRead:
    return DraftRead.model_validate(
        data_pipeline_workbench.get_draft(
            client, owner_id=get_current_user_id(), pipeline_id=pipeline_id
        )
    )


@router.post("/pipelines/{pipeline_id}/draft/commands", response_model=DraftRead)
def apply_draft_command(
    pipeline_id: uuid.UUID,
    payload: DraftCommandRequest,
    client: DataServiceClient = Depends(get_data_service_client),
) -> DraftRead:
    return DraftRead.model_validate(
        data_pipeline_workbench.apply_draft_command(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            command=payload,
        )
    )


@router.post("/pipelines/{pipeline_id}/draft/positions", response_model=DraftRead)
def move_nodes(
    pipeline_id: uuid.UUID,
    payload: DraftPositionsRequest,
    client: DataServiceClient = Depends(get_data_service_client),
) -> DraftRead:
    return DraftRead.model_validate(
        data_pipeline_workbench.move_nodes(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            positions=payload.positions,
        )
    )


@router.get("/pipelines/{pipeline_id}/check", response_model=PipelineCheckRead)
def check_pipeline(
    pipeline_id: uuid.UUID,
    revision: int = Query(ge=0),
    client: DataServiceClient = Depends(get_data_service_client),
) -> PipelineCheckRead:
    return PipelineCheckRead.model_validate(
        data_pipeline_workbench.check_pipeline(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            revision=revision,
        )
    )


@router.post("/pipelines/{pipeline_id}/turns/{turn_id}/undo", response_model=DraftRead)
def undo_turn(
    pipeline_id: uuid.UUID,
    turn_id: uuid.UUID,
    payload: UndoTurnRequest,
    client: DataServiceClient = Depends(get_data_service_client),
) -> DraftRead:
    return DraftRead.model_validate(
        data_pipeline_workbench.undo_turn(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            turn_id=turn_id,
            revision=payload.revision,
        )
    )


router.include_router(data_pipeline_execution.router)
router.include_router(data_pipeline_schedule.router)
