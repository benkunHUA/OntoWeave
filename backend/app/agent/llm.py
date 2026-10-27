from __future__ import annotations

from typing import Any

from langchain_core.language_models import LanguageModelInput
from langchain_deepseek import ChatDeepSeek

from app.config import Settings
from app.errors import InvariantViolationError


class DeepSeekJSONChatModel(ChatDeepSeek):
    """把 LangChain 的原生结构化输出降级成 DeepSeek 支持的 json_object。

    DeepSeek 的 JSON Output 只接受 response_format={"type": "json_object"}；
    langchain 的 ProviderStrategy 会发 json_schema，思考模式下直接 400。
    """

    def _get_request_payload(
        self,
        input_: LanguageModelInput,
        *,
        stop: list[str] | None = None,
        **kwargs: Any,
    ) -> dict[str, Any]:
        payload = super()._get_request_payload(input_, stop=stop, **kwargs)
        response_format = payload.get("response_format")
        if isinstance(response_format, dict) and response_format.get("type") == "json_schema":
            payload["response_format"] = {"type": "json_object"}
        return payload


def build_chat_model(settings: Settings) -> ChatDeepSeek:
    """装配对话模型。

    deepseek-flash 是推理模型：它先花 token 推理再出正文，max_tokens 给小了
    会得到「200 但 content 为空」的响应，所以这里必须给足。
    """
    if not settings.chat_configured:
        raise InvariantViolationError("chat api key is not configured")

    kwargs: dict[str, Any] = {
        "model": settings.chat_model,
        "api_key": settings.chat_api_key,
        "api_base": settings.chat_base_url,
        "temperature": settings.chat_temperature,
        "max_tokens": settings.chat_max_tokens,
        "timeout": settings.chat_timeout_seconds,
    }
    if settings.chat_reasoning_effort:
        kwargs["model_kwargs"] = {"reasoning_effort": settings.chat_reasoning_effort}
    return DeepSeekJSONChatModel(**kwargs)
