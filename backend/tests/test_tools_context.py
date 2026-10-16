"""ToolContext.invoke 的两条策略：串行执行与错误兜底。"""

import time
import uuid
from concurrent.futures import ThreadPoolExecutor

from app.agent.tools.context import ToolContext
from app.services import embedding


def _context(session) -> ToolContext:
    return ToolContext(
        session=session,
        owner_id=uuid.uuid4(),
        graph_id=uuid.uuid4(),
        changeset_id=uuid.uuid4(),
        embedder=embedding.HashingEmbedder(),
    )


def test_invoke_turns_errors_into_a_result_the_model_can_read(session):
    ctx = _context(session)

    assert ctx.invoke(lambda: 1 / 0) == {
        "error": "ZeroDivisionError",
        "message": "division by zero",
    }


def test_invoke_also_catches_errors_raised_while_building_the_arguments(session):
    """模型编一个坏 id 不该掀翻整轮：参数构造期的异常同样兜住。"""
    ctx = _context(session)

    result = ctx.invoke(lambda: uuid.UUID("不是 uuid"))

    assert result["error"] == "ValueError"


def test_invoke_serializes_concurrent_calls(session):
    """同一轮的多个工具调用共用一个 Session，必须一个接一个跑。"""
    ctx = _context(session)
    active = 0
    overlapped = False

    def body() -> None:
        nonlocal active, overlapped
        active += 1
        overlapped = overlapped or active > 1
        time.sleep(0.01)
        active -= 1

    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(lambda _: ctx.invoke(body), range(8)))

    assert overlapped is False
