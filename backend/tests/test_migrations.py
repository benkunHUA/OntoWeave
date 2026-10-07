from alembic import command
from alembic.config import Config
from sqlalchemy import Engine


def test_migrations_match_models(engine: Engine, alembic_config: Config) -> None:
    """模型和迁移一旦漂移，alembic check 会抛异常，测试随之失败。"""
    command.check(alembic_config)
