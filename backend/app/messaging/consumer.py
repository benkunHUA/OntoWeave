"""消费 Java Outbox 发出的数据连接 / 同步事件。

队列只负责把事件送到 Python；落库和幂等由 `conversation_event.ingest_envelope`
负责。消费线程保持阻塞式连接，停止时用事件标记让 `process_data_events` 退出，
避免跨线程操作 pika 连接。
"""

from __future__ import annotations

import json
import logging
import threading

import pika
from sqlalchemy.orm import Session, sessionmaker

from app.config import Settings
from app.services import conversation_event
from app.services.event_bus import ConversationEventBus

logger = logging.getLogger(__name__)


class DataEventConsumer:
    def __init__(
        self,
        settings: Settings,
        factory: sessionmaker[Session],
        bus: ConversationEventBus,
    ) -> None:
        self._settings = settings
        self._factory = factory
        self._bus = bus
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, name="data-event-consumer", daemon=True)

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread.is_alive():
            self._thread.join(timeout=5)

    def _run(self) -> None:
        while not self._stop.is_set():
            try:
                self._consume()
            except Exception as exc:  # 连接断开、RabbitMQ 未启动都走重连
                if self._stop.is_set():
                    return
                logger.warning("data event consumer disconnected: %s", exc)
                self._stop.wait(self._settings.event_consumer_reconnect_seconds)

    def _consume(self) -> None:
        parameters = pika.URLParameters(self._settings.rabbitmq_url)
        connection = pika.BlockingConnection(parameters)
        channel = connection.channel()
        channel.exchange_declare(
            exchange=self._settings.data_event_exchange,
            exchange_type="topic",
            durable=True,
        )
        channel.queue_declare(queue=self._settings.data_event_queue, durable=True)
        channel.queue_bind(
            queue=self._settings.data_event_queue,
            exchange=self._settings.data_event_exchange,
            routing_key="#",
        )
        channel.basic_qos(prefetch_count=10)
        channel.basic_consume(
            queue=self._settings.data_event_queue,
            on_message_callback=self._on_message,
            auto_ack=False,
        )
        logger.info(
            "consuming data events from %s (%s)",
            self._settings.data_event_queue,
            self._settings.data_event_exchange,
        )
        try:
            while not self._stop.is_set():
                connection.process_data_events(time_limit=1)
        finally:
            if connection.is_open:
                connection.close()

    def _on_message(self, channel, method, properties, body: bytes) -> None:  # noqa: ANN001
        del properties
        try:
            envelope = json.loads(body)
        except ValueError:
            logger.warning("discard non-json data event")
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return
        if not isinstance(envelope, dict):
            logger.warning("discard non-object data event")
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return
        try:
            conversation_event.ingest_envelope(self._factory, envelope, bus=self._bus)
        except Exception:
            logger.exception("failed to persist data event, will retry")
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
            self._stop.wait(1)
            return
        channel.basic_ack(delivery_tag=method.delivery_tag)
