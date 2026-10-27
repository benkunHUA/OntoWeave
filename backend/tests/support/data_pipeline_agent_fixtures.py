"""`test_data_pipeline_agent.py` 的共享 fixture、假客户端与构造 helper。"""

import uuid

from app.agent.tools.data_pipeline import (
    DataPipelineToolContext,
    build_data_pipeline_tools,
)
from app.services import conversation
from app.vocabulary import ConversationKind
from tests.support.pipeline_test_fakes import PIPELINE_ID


def _conversation(session, owner, pipeline_id=PIPELINE_ID):
    return conversation.create_conversation(
        session,
        owner_id=owner,
        title="订单清洗",
        kind=ConversationKind.DATA_PIPELINE.value,
        resource_id=pipeline_id,
    )


def _context(session, client, owner, *, turn_id=None, load_error=None):
    conv = _conversation(session, owner)
    return DataPipelineToolContext(
        session=session,
        owner_id=owner,
        conversation_id=conv.id,
        pipeline_id=conv.resource_id,
        client=client,
        draft_revision=3,
        turn_id=turn_id or uuid.uuid4(),
        last_check_result=None,
        load_error=load_error,
    )


def _tools(ctx):
    return {item.name: item for item in build_data_pipeline_tools(ctx)}
