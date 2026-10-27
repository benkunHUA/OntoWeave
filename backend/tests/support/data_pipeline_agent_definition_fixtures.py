"""`test_data_pipeline_agent_definition.py` 的共享 fixture、假客户端与构造 helper。"""

from app.services import conversation
from app.vocabulary import ConversationKind
from tests.support.pipeline_test_fakes import PIPELINE_ID


def _conversation(session, owner):
    return conversation.create_conversation(
        session,
        owner_id=owner,
        title="订单清洗",
        kind=ConversationKind.DATA_PIPELINE.value,
        resource_id=PIPELINE_ID,
    )
