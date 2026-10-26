"""回填历史数据集搜索索引。

用法（在 backend 目录）：
    .venv/bin/python scripts/backfill_dataset_search.py --owner-id <uuid>

不传 owner-id 时使用配置里的默认用户。发布事件覆盖之后的新版本；这个脚本只用于
功能上线前已经存在的数据集，或 indexing 失败后的重试。
"""

from __future__ import annotations

import argparse
import sys
import uuid
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_DIR))

from app.config import get_settings  # noqa: E402
from app.db import build_engine, build_session_factory  # noqa: E402
from app.services import dataset_search, embedding  # noqa: E402
from app.services.data_service.ontology_client import OntologyDataServiceClient  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description="回填数据集向量搜索索引")
    parser.add_argument("--owner-id", help="只回填这个用户的数据集；默认使用 DEFAULT_USER_ID")
    args = parser.parse_args()

    settings = get_settings()
    owner_id = uuid.UUID(args.owner_id) if args.owner_id else uuid.UUID(settings.default_user_id)
    engine = build_engine(settings.database_url)
    factory = build_session_factory(engine)
    client = OntologyDataServiceClient(settings)
    embedder = embedding.build_embedder(settings)
    try:
        with factory() as session:
            count = dataset_search.backfill_owner(
                session,
                client=client,
                embedder=embedder,
                owner_id=owner_id,
            )
            session.commit()
    finally:
        engine.dispose()
    print(f"indexed {count} dataset versions for owner {owner_id}")


if __name__ == "__main__":
    main()
