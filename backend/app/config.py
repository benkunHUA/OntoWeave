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
    # deepseek-flash 是推理模型，token 给小了会只出推理、正文为空。
    chat_max_tokens: int = 4096
    # 留空表示不传，交给服务端默认。
    chat_reasoning_effort: str = ""

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

    # ---- 可观测性（Langfuse 审计追踪，可选）----
    # 两个 key 都填上才启用；留空则整条链路零开销。字段名即 LANGFUSE_* 环境变量名。
    langfuse_public_key: str = ""
    langfuse_secret_key: str = ""
    langfuse_base_url: str = "https://cloud.langfuse.com"
    # 区分环境 / 版本，方便在 Langfuse 里过滤。
    langfuse_environment: str = ""
    langfuse_release: str = ""

    @property
    def chat_configured(self) -> bool:
        return bool(self.chat_api_key)

    @property
    def embedding_configured(self) -> bool:
        return bool(self.embedding_api_key)

    @property
    def langfuse_configured(self) -> bool:
        return bool(self.langfuse_public_key and self.langfuse_secret_key)


@lru_cache
def get_settings() -> Settings:
    return Settings()
