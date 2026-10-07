import uuid

from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from app.main import create_app
from app.models import GraphObject, ObjectType


def test_full_stack_persists_to_real_database(engine):
    """不覆盖依赖、也不借用外层回滚事务：走真实 app.state.session_factory，
    再用一个全新的 session 去读，确认请求真的提交进了数据库。"""
    factory = sessionmaker(
        bind=engine, autoflush=False, expire_on_commit=False, class_=Session
    )
    client = TestClient(create_app(session_factory=factory))

    name = f"集成测试客户-{uuid.uuid4().hex[:8]}"

    created = client.post("/object-types", json={"name": name})
    assert created.status_code == 201
    object_type_id = created.json()["id"]

    assert client.post(f"/object-types/{object_type_id}/confirm").status_code == 200

    graph = client.post("/graphs", json={"name": "集成测试图"})
    assert graph.status_code == 201
    graph_id = graph.json()["id"]

    added = client.post(
        f"/graphs/{graph_id}/objects", json={"object_type_id": object_type_id}
    )
    assert added.status_code == 204

    # 关键断言：换一个 session 读，数据必须真的落库了。
    with factory() as fresh:
        persisted = fresh.get(ObjectType, uuid.UUID(object_type_id))
        assert persisted is not None
        assert persisted.status == "confirmed"
        assert fresh.scalar(
            select(GraphObject).where(
                GraphObject.graph_id == uuid.UUID(graph_id),
                GraphObject.object_type_id == uuid.UUID(object_type_id),
            )
        ) is not None
