"""数据流程预览、构建与输出来源的浏览器路由。"""

from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, Header, Query, Response

from app.deps import get_current_user_id, get_data_service_client
from app.schemas.data_pipeline import BuildCreateRequest, PreviewRead, PreviewRequest
from app.schemas.data_pipeline_execution import BuildRead, SourceRead
from app.services import data_pipeline_workbench
from app.services.data_service.client import DataServiceClient

router = APIRouter(tags=["data-pipeline-execution"])


@router.post("/pipelines/{pipeline_id}/previews", response_model=PreviewRead)
def preview_node(
    pipeline_id: uuid.UUID,
    payload: PreviewRequest,
    client: DataServiceClient = Depends(get_data_service_client),
) -> PreviewRead:
    return PreviewRead.model_validate(
        data_pipeline_workbench.preview_node(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            request=payload,
        )
    )


@router.post("/pipelines/{pipeline_id}/previews/cancel", status_code=204)
def cancel_preview(
    pipeline_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> Response:
    data_pipeline_workbench.cancel_preview(
        client,
        owner_id=get_current_user_id(),
        pipeline_id=pipeline_id,
    )
    return Response(status_code=204)


@router.post("/pipelines/{pipeline_id}/builds", response_model=BuildRead)
def create_build(
    pipeline_id: uuid.UUID,
    payload: BuildCreateRequest,
    client: DataServiceClient = Depends(get_data_service_client),
) -> BuildRead:
    return BuildRead.model_validate(
        data_pipeline_workbench.create_build(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            draft_revision=payload.draft_revision,
            operation_id=payload.operation_id,
        )
    )


@router.get("/pipelines/{pipeline_id}/builds/{build_id}", response_model=BuildRead)
def get_build(
    pipeline_id: uuid.UUID,
    build_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> BuildRead:
    return BuildRead.model_validate(
        data_pipeline_workbench.get_build(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            build_id=build_id,
        )
    )


@router.post("/pipelines/{pipeline_id}/builds/{build_id}/cancel")
def cancel_build(
    pipeline_id: uuid.UUID,
    build_id: uuid.UUID,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    client: DataServiceClient = Depends(get_data_service_client),
) -> dict[str, str]:
    return data_pipeline_workbench.cancel_build(
        client,
        owner_id=get_current_user_id(),
        pipeline_id=pipeline_id,
        build_id=build_id,
        operation_id=operation_id,
    )


@router.get(
    "/pipelines/{pipeline_id}/datasets/{dataset_id}/sources",
    response_model=SourceRead,
)
def get_output_sources(
    pipeline_id: uuid.UUID,
    dataset_id: uuid.UUID,
    version_id: uuid.UUID | None = Query(None),
    client: DataServiceClient = Depends(get_data_service_client),
) -> SourceRead:
    return SourceRead.model_validate(
        data_pipeline_workbench.output_sources(
            client,
            owner_id=get_current_user_id(),
            pipeline_id=pipeline_id,
            dataset_id=dataset_id,
            version_id=version_id,
        )
    )
