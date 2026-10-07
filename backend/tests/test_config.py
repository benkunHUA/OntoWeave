import uuid

from app.config import DEFAULT_USER_ID, Settings


def test_settings_have_defaults():
    settings = Settings()
    assert settings.database_url.startswith("postgresql+psycopg://")
    assert settings.default_user_id == DEFAULT_USER_ID


def test_default_user_id_is_a_valid_uuid_literal():
    assert uuid.UUID(DEFAULT_USER_ID)


def test_model_settings_can_be_overridden():
    settings = Settings(
        chat_api_key="chat-key", embedding_api_key="embed-key", chat_model="custom-model"
    )
    assert settings.chat_configured is True
    assert settings.embedding_configured is True
    assert settings.chat_model == "custom-model"


def test_unconfigured_models_are_reported_as_such():
    settings = Settings(chat_api_key="", embedding_api_key="")
    assert settings.chat_configured is False
    assert settings.embedding_configured is False
