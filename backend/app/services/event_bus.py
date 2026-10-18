"""对话事件的进程内唤醒信号。

SSE 仍以数据库为准，这里只负责把「有新事件」告诉同进程里的推送循环，避免每秒空轮询。
多 worker 部署时其它进程的写入靠轮询兜底，所以它不是正确性的来源。
"""

from __future__ import annotations

import threading
import uuid


class ConversationEventBus:
    def __init__(self) -> None:
        self._condition = threading.Condition()
        self._versions: dict[uuid.UUID, int] = {}

    def publish(self, conversation_id: uuid.UUID) -> None:
        with self._condition:
            self._versions[conversation_id] = self._versions.get(conversation_id, 0) + 1
            self._condition.notify_all()

    def snapshot(self, conversation_id: uuid.UUID) -> int:
        with self._condition:
            return self._versions.get(conversation_id, 0)

    def wait(self, conversation_id: uuid.UUID, seen: int, timeout: float) -> int:
        with self._condition:
            self._condition.wait_for(
                lambda: self._versions.get(conversation_id, 0) > seen,
                timeout=timeout,
            )
            return self._versions.get(conversation_id, 0)
