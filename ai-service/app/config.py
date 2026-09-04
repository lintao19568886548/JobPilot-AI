from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file="../.env", extra="ignore", populate_by_name=True)
    internal_token: str = Field(default="", validation_alias="AI_SERVICE_INTERNAL_TOKEN")
    parser_mode: str = Field(default="RULES_ONLY", validation_alias="JOB_PARSER_MODE")
    llm_base_url: str | None = Field(default=None, validation_alias="LLM_BASE_URL")
    llm_api_key: str | None = Field(default=None, validation_alias="LLM_API_KEY")
    llm_model: str | None = Field(default=None, validation_alias="LLM_MODEL")
    llm_timeout_seconds: float = Field(default=30.0, validation_alias="LLM_TIMEOUT_SECONDS")
    llm_max_retries: int = Field(default=2, validation_alias="LLM_MAX_RETRIES")
    llm_circuit_failure_threshold: int = Field(default=3, validation_alias="LLM_CIRCUIT_FAILURE_THRESHOLD")
    llm_circuit_reset_seconds: float = Field(default=60.0, validation_alias="LLM_CIRCUIT_RESET_SECONDS")
    llm_max_concurrency: int = Field(default=2, ge=1, le=32, validation_alias="LLM_MAX_CONCURRENCY")
    embedding_model: str = Field(default="BAAI/bge-m3", validation_alias="EMBEDDING_MODEL")
    embedding_provider: str = Field(default="sentence-transformers", validation_alias="EMBEDDING_PROVIDER")
    embedding_dimension: int = Field(default=1024, validation_alias="EMBEDDING_DIMENSION")
    embedding_version: str = Field(default="bge-m3-v1", validation_alias="EMBEDDING_VERSION")
    embedding_device: str = Field(default="cpu", validation_alias="EMBEDDING_DEVICE")
    embedding_max_chars: int = Field(default=12000, validation_alias="EMBEDDING_MAX_CHARS")
    milvus_uri: str = Field(default="http://127.0.0.1:19531", validation_alias="MILVUS_URI")
    milvus_collection: str = Field(default="jobpilot_embeddings_bge_m3_v1", validation_alias="MILVUS_COLLECTION")


@lru_cache
def get_settings() -> Settings:
    return Settings()
