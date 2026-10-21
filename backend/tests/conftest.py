import os
import uuid
from pathlib import Path

import pytest
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy import Engine, create_engine, text
from sqlalchemy.engine import Connection, make_url
from sqlalchemy.orm import Session, sessionmaker

from alembic import command
from app.models import EMBEDDING_DIM

BACKEND_DIR = Path(__file__).resolve().parents[1]

# 测试绝不连真实的对话 / 嵌入服务：清掉 key，让运行时回落到本地确定性实现。
# 必须在任何 get_settings() 之前设置——它带 lru_cache，晚了就固化了。
os.environ["CHAT_API_KEY"] = ""
os.environ["EMBEDDING_API_KEY"] = ""
# 测试不连 RabbitMQ / Java 数据服务；需要时由用例注入假客户端或直接调事件入库。
os.environ["EVENT_CONSUMER_ENABLED"] = "false"

TEST_DATABASE_URL = os.environ.get(
    "TEST_DATABASE_URL",
    "postgresql+psycopg://ontoweave:ontoweave@localhost:5432/ontoweave_test",
)

# 防呆：测试会重建 public schema，库名不以 _test 结尾就直接拒绝启动。
_db_name = make_url(TEST_DATABASE_URL).database or ""
if not _db_name.endswith("_test"):
    pytest.exit(f"拒绝在非测试库上跑测试：{_db_name}", returncode=1)


@pytest.fixture(scope="session")
def alembic_config() -> Config:
    cfg = Config(str(BACKEND_DIR / "alembic.ini"))
    cfg.set_main_option("script_location", str(BACKEND_DIR / "alembic"))
    # env.py 优先读 attributes 里的地址，避免被应用配置无条件覆盖。
    cfg.attributes["sqlalchemy_url"] = TEST_DATABASE_URL
    return cfg


@pytest.fixture(scope="session")
def engine(alembic_config: Config) -> Engine:
    """schema 一律用 Alembic 迁移建，保证测试验证的是真迁移，而不是 create_all。"""
    eng = create_engine(TEST_DATABASE_URL, future=True)
    with eng.begin() as conn:
        conn.execute(text("DROP SCHEMA IF EXISTS public CASCADE"))
        conn.execute(text("CREATE SCHEMA public"))
    command.upgrade(alembic_config, "head")
    yield eng
    eng.dispose()


@pytest.fixture
def connection(engine: Engine) -> Connection:
    """每个测试一条会被回滚的外层连接，测试之间互不污染。"""
    conn = engine.connect()
    transaction = conn.begin()
    try:
        yield conn
    finally:
        transaction.rollback()
        conn.close()


@pytest.fixture
def session_factory(connection: Connection) -> sessionmaker[Session]:
    """绑在测试连接上的 session 工厂，可以原样交给 create_app()。"""
    return sessionmaker(
        bind=connection,
        autoflush=False,
        expire_on_commit=False,
        class_=Session,
        # 让 session 的 commit 只释放 SAVEPOINT，外层事务仍由 fixture 回滚，
        # 这样 app 里真实的提交行为也能被测到，同时不污染库。
        join_transaction_mode="create_savepoint",
    )


@pytest.fixture
def session(session_factory: sessionmaker[Session]) -> Session:
    sess = session_factory()
    try:
        yield sess
    finally:
        sess.close()


class _FailingEmbedder:
    """永远算不出向量的嵌入器，用来验证嵌入失败不阻断本体改动。"""

    dim = EMBEDDING_DIM

    def embed(self, texts):  # noqa: ARG002
        raise RuntimeError("embedding service is down")


@pytest.fixture
def failing_embedder() -> _FailingEmbedder:
    return _FailingEmbedder()


class FakeDataServiceClient:
    """Java 内部接口的假实现，返回形状与真实 DTO 一致（camelCase）。"""

    def __init__(self) -> None:
        now = "2026-10-18T00:00:00Z"
        self.connection_id = uuid.uuid4()
        self.conversation_id = uuid.uuid4()
        self.task_id = uuid.uuid4()
        self.dataset_id = uuid.uuid4()
        self.run_id = uuid.uuid4()
        self.connection = {
            "id": str(self.connection_id),
            "conversationId": str(self.conversation_id),
            "name": "ERP",
            "kind": "mysql",
            "config": {"host": "mysql", "port": 3306, "database": "erp"},
            "configVersion": 1,
            "status": "active",
            "lastTestStatus": "success",
            "lastTestAt": now,
            "lastTestError": None,
            "lastTestDetails": None,
            "secretConfigured": True,
            "createdAt": now,
            "updatedAt": now,
        }
        self.tasks = [
            {
                "id": str(self.task_id),
                "connectionId": str(self.connection_id),
                "datasetId": str(self.dataset_id),
                "schema": "demo",
                "table": "orders",
                "syncMode": "cdc",
                "versionIntervalSeconds": 86400,
                "primaryKeyColumns": ["id"],
                "status": "enabled",
                "createdAt": now,
                "updatedAt": now,
            }
        ]
        self.run = {
            "id": str(self.run_id),
            "syncTaskId": str(self.task_id),
            "datasetId": str(self.dataset_id),
            "operationId": str(uuid.uuid4()),
            "runMode": "cdc",
            "status": "queued",
            "flinkJobState": None,
            "snapshotCompletedAt": None,
            "lastCheckpointAt": None,
            "nextVersionDueAt": None,
            "savepointPath": None,
            "errorCode": None,
            "errorMessage": None,
            "startedAt": None,
            "finishedAt": None,
            "createdAt": now,
        }
        self.dataset = {
            "id": str(self.dataset_id),
            "connectionId": str(self.connection_id),
            "syncTaskId": str(self.task_id),
            "name": "demo.orders",
            "activeIcebergCatalog": "ontoweave",
            "activeIcebergNamespace": "ns",
            "activeIcebergTable": "tbl",
            "currentVersionId": None,
            "currentVersion": None,
            "versions": [],
            "createdAt": now,
            "updatedAt": now,
        }

    def create_connection(self, *, owner_id, payload):  # noqa: ANN001, ARG002
        self.last_create_payload = payload
        self.connection_id = uuid.uuid4()
        self.connection.update(
            {
                "id": str(self.connection_id),
                "conversationId": payload["conversationId"],
                "name": payload["name"],
                "kind": payload["kind"],
                "config": payload.get("config") or {},
                "secretConfigured": payload.get("secret") is not None,
            }
        )
        return dict(self.connection)

    def list_connections(self, *, owner_id, limit, offset):  # noqa: ANN001, ARG002
        return {"items": [dict(self.connection)], "total": 1, "limit": limit, "offset": offset}

    def get_connection(self, connection_id, *, owner_id):  # noqa: ANN001, ARG002
        return dict(self.connection)

    def update_connection(  # noqa: ANN001, ARG002
        self, connection_id, *, owner_id, payload, operation_id=None
    ):
        self.connection.update(payload)
        if "secret" in payload:
            self.connection["secretConfigured"] = True
        return dict(self.connection)

    def test_connection(  # noqa: ANN001, ARG002
        self, connection_id, *, owner_id, operation_id=None
    ):
        return {
            "status": "success",
            "latencyMs": 12,
            "serverVersion": "8.0.36",
            "errorCode": None,
            "message": None,
            "testedAt": "2026-10-18T00:00:00Z",
            "details": None,
        }

    def list_tables(self, connection_id, *, owner_id):  # noqa: ANN001, ARG002
        return {
            "tables": [
                {
                    "schema": "demo",
                    "name": "orders",
                    "type": "BASE TABLE",
                    "comment": "订单",
                    "estimatedRows": 10,
                    "hasPrimaryKey": True,
                }
            ],
            "warnings": [],
        }

    def describe_table(self, connection_id, *, owner_id, schema, table):  # noqa: ANN001
        return {
            "schema": schema,
            "table": table,
            "type": "BASE TABLE",
            "comment": "订单",
            "columns": [
                {
                    "name": "id",
                    "ordinalPosition": 1,
                    "dataType": "bigint",
                    "columnType": "bigint",
                    "nullable": False,
                    "defaultValue": None,
                    "primaryKey": True,
                    "comment": None,
                }
            ],
            "primaryKeyColumns": ["id"],
        }

    def get_state(self, connection_id, *, owner_id):  # noqa: ANN001, ARG002
        return {
            "connection": dict(self.connection),
            "tasks": [dict(item) for item in self.tasks],
            "recentRuns": [dict(self.run)],
            "datasets": [dict(self.dataset)],
            "missingSteps": ["RUN_SYNC"],
            "lastEventId": None,
        }

    def create_sync_tasks(  # noqa: ANN001, ARG002
        self, connection_id, *, owner_id, tables, operation_id=None
    ):
        self.tasks = [
            {
                **self.tasks[0],
                "schema": item["schema"],
                "table": item["table"],
                "syncMode": item.get("syncMode", "cdc"),
            }
            for item in tables
        ]
        return {"tasks": [dict(item) for item in self.tasks]}

    def list_sync_tasks(self, connection_id, *, owner_id):  # noqa: ANN001, ARG002
        return {"tasks": [dict(item) for item in self.tasks]}

    def trigger_sync(self, connection_id, *, owner_id, operation_id, tables):  # noqa: ANN001
        return {
            "operationId": str(operation_id),
            "runs": [{**self.run, "operationId": str(operation_id)}],
        }

    def get_sync_run(self, run_id, *, owner_id):  # noqa: ANN001, ARG002
        return dict(self.run)

    def stop_sync_run(self, run_id, *, owner_id, operation_id=None):  # noqa: ANN001, ARG002
        return {**self.run, "status": "stopping"}

    def get_dataset(self, dataset_id, *, owner_id):  # noqa: ANN001, ARG002
        return dict(self.dataset)

    def get_dataset_versions(self, dataset_id, *, owner_id, limit, offset):  # noqa: ANN001, ARG002
        versions = self.dataset.get("versions") or []
        return {
            "datasetId": str(dataset_id),
            "total": len(versions),
            "limit": limit,
            "offset": offset,
            "versions": versions[offset : offset + limit],
        }

    def get_dataset_version(self, dataset_id, version_id, *, owner_id):  # noqa: ANN001, ARG002
        versions = self.dataset.get("versions") or []
        return next(item for item in versions if item["id"] == str(version_id))

    def get_dataset_preview(  # noqa: ANN001, ARG002
        self, dataset_id, *, owner_id, version_id, limit, live=False
    ):
        return {
            "datasetId": str(dataset_id),
            "versionId": None if live else str(version_id or uuid.uuid4()),
            "versionNumber": None if live else 1,
            "snapshotId": 100,
            "live": live,
            "columns": [
                {"name": "id", "type": "long", "nullable": False},
                {"name": "name", "type": "string", "nullable": True},
            ],
            "rowCount": 1,
            "rows": [{"id": 1, "name": "Ada"}][:limit],
            "truncated": False,
        }


@pytest.fixture
def fake_data_service() -> FakeDataServiceClient:
    return FakeDataServiceClient()


@pytest.fixture
def fake_pipeline_client():
    from pipeline_test_fakes import FakePipelineClient

    return FakePipelineClient()


@pytest.fixture
def pipeline_client(session_factory, fake_pipeline_client):
    from app.deps import get_data_service_client
    from app.main import _create_app

    app = _create_app(session_factory, enable_agent_checkpoints=False)
    app.dependency_overrides[get_data_service_client] = lambda: fake_pipeline_client
    return TestClient(app)
