import uuid

from app.config import DEFAULT_USER_ID, Settings


def test_settings_have_defaults():
    settings = Settings()
    assert settings.database_url.startswith("postgresql+psycopg://")
    assert settings.default_user_id == DEFAULT_USER_ID


def test_default_user_id_is_a_valid_uuid_literal():
    assert uuid.UUID(DEFAULT_USER_ID)
