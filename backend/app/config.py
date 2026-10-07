from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

# 一期没有用户系统，所有 owner_id / user_id 都用这个固定值。
DEFAULT_USER_ID = "00000000-0000-0000-0000-000000000001"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = (
        "postgresql+psycopg://ontoweave:ontoweave@localhost:5432/ontoweave"
    )
    default_user_id: str = DEFAULT_USER_ID


@lru_cache
def get_settings() -> Settings:
    return Settings()
