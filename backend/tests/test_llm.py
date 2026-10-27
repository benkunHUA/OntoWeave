import pytest
from langchain_core.messages import HumanMessage

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


def test_build_chat_model_downgrades_json_schema_to_json_object():
    model = llm.build_chat_model(Settings(chat_api_key="k"))
    payload = model._get_request_payload(
        [HumanMessage(content="hi")],
        response_format={
            "type": "json_schema",
            "json_schema": {"name": "answer", "schema": {"type": "object"}},
        },
    )

    assert payload["response_format"] == {"type": "json_object"}
