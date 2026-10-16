import pytest

from app.agent import llm
from app.config import Settings
from app.errors import InvariantViolationError


def test_build_chat_model_requires_key():
    with pytest.raises(InvariantViolationError):
        llm.build_chat_model(Settings(chat_api_key=""))


def test_build_chat_model_passes_configured_values():
    model = llm.build_chat_model(
        Settings(
            chat_api_key="k",
            chat_model="deepseek-flash",
            chat_base_url="https://api.deepseek.com",
            chat_max_tokens=2048,
        )
    )
    assert model.model_name == "deepseek-flash"
    assert model.max_tokens == 2048
