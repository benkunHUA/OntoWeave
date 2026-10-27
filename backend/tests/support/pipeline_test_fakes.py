"""数据整理测试的假 Java 客户端门面。"""

from tests.support.pipeline_client_crud import PipelineCrudFake
from tests.support.pipeline_client_datasets import PipelineDatasetFake
from tests.support.pipeline_client_schedule import PipelineScheduleFake
from tests.support.pipeline_constants import (
    API,
    BUILD_ID,
    CONVERSATION_ID,
    DATASET_ID,
    NOW,
    OWNER_ID,
    PIPELINE_ID,
    TURN_ID,
)

__all__ = [
    "API",
    "BUILD_ID",
    "CONVERSATION_ID",
    "DATASET_ID",
    "NOW",
    "OWNER_ID",
    "PIPELINE_ID",
    "TURN_ID",
    "FakePipelineClient",
]


class FakePipelineClient(PipelineCrudFake, PipelineDatasetFake, PipelineScheduleFake):
    """把流程、数据集与调度三组假响应组合成测试客户端。"""
