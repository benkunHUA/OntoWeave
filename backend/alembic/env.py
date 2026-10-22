from logging.config import fileConfig

from sqlalchemy import engine_from_config, pool

from alembic import context
from app.config import get_settings
from app.models import Base

config = context.config
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# 调用方可以通过 config.attributes["sqlalchemy_url"] 指定目标库（测试夹具就这么用），
# 没指定才回落到应用配置。
database_url = config.attributes.get("sqlalchemy_url") or get_settings().database_url
config.set_main_option(
    "sqlalchemy.url",
    database_url.replace("%", "%%"),
)
target_metadata = Base.metadata
version_table_schema = config.attributes.get("version_table_schema")
CHECKPOINT_TABLES = {
    "checkpoints",
    "checkpoint_blobs",
    "checkpoint_migrations",
    "checkpoint_writes",
}


def include_name(name: str | None, type_: str, parent_names: dict[str, str | None]) -> bool:
    """LangGraph 的检查点表由运行时自己维护，不参与应用模型迁移比对。"""
    del parent_names
    return not (type_ == "table" and name in CHECKPOINT_TABLES)


def run_migrations_offline() -> None:
    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        include_name=include_name,
        version_table_schema=version_table_schema,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            include_name=include_name,
            version_table_schema=version_table_schema,
        )
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
