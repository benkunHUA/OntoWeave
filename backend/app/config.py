from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

from app.models import EMBEDDING_DIM

# 一期没有用户系统，所有 owner_id / user_id 都用这个固定值。
DEFAULT_USER_ID = "00000000-0000-0000-0000-000000000001"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql+psycopg://ontoweave:ontoweave@localhost:5432/ontoweave"
    default_user_id: str = DEFAULT_USER_ID

    # ---- 对话模型（智能体用）。OpenAI 兼容接口，私有化环境换成自建网关即可。
    chat_base_url: str = "https://api.openai.com/v1"
    chat_api_key: str = ""
    chat_model: str = "gpt-4o-mini"
    chat_temperature: float = 0.0
    chat_timeout_seconds: float = 60.0

    # ---- 嵌入模型（相似检索用）。维度必须和 object_type.embedding 的列维度一致。
    embedding_base_url: str = "https://api.openai.com/v1"
    embedding_api_key: str = ""
    embedding_model: str = "text-embedding-3-small"
    embedding_dim: int = EMBEDDING_DIM
    embedding_timeout_seconds: float = 30.0
    # 有些 OpenAI 兼容实现不认 dimensions 字段，遇到报错就把它设成 false。
    embedding_send_dimensions: bool = True

    # ---- 智能体运行时
    agent_max_iterations: int = 12

    @property
    def chat_configured(self) -> bool:
        return bool(self.chat_api_key)

    @property
    def embedding_configured(self) -> bool:
        return bool(self.embedding_api_key)


@lru_cache
def get_settings() -> Settings:
    return Settings()
