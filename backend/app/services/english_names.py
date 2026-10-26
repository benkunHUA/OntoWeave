"""本体中文名到英文名的只补空值服务。

英文名只在第一次生成时写入；之后中文名怎么改都不再联动。对象名在创建时生成，
属性名在对象定稿时按对象批量补齐，这样同一对象的字段术语可以保持一致。
"""

from __future__ import annotations

import json
import logging
import re
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Any, Protocol

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import Settings
from app.models import ObjectType, Property

logger = logging.getLogger(__name__)

_IDENTIFIER = re.compile(r"^[A-Za-z][A-Za-z0-9_]*$")


class EnglishNameGenerator(Protocol):
    def generate(self, *, object_name: str, property_names: Sequence[str]) -> EnglishNames: ...


@dataclass(frozen=True)
class EnglishNames:
    object_name: str | None
    properties: dict[str, str]


class LlmEnglishNameGenerator:
    """一次调用生成一个对象的英文名和它全部缺失字段的英文名。"""

    def __init__(self, model: Any) -> None:
        self._model = model

    def generate(self, *, object_name: str, property_names: Sequence[str]) -> EnglishNames:
        prompt = _prompt(object_name, property_names)
        response = self._model.invoke(prompt)
        content = _content_text(getattr(response, "content", response))
        payload = _json_object(content)
        properties: dict[str, str] = {}
        for item in payload.get("properties", []):
            if not isinstance(item, dict):
                continue
            source = str(item.get("source") or "").strip()
            name = _identifier(item.get("name"))
            if source and name:
                properties[source] = name
        return EnglishNames(
            object_name=_identifier(payload.get("object_name")),
            properties=properties,
        )


def build_english_name_generator(settings: Settings) -> EnglishNameGenerator | None:
    """没配对话模型就不自动生成，英文名保持可空，用户仍可手动填写。"""
    if not settings.chat_configured:
        return None
    # 延迟导入，避免服务层在模块加载时依赖智能体装配。
    from app.agent.llm import build_chat_model

    return LlmEnglishNameGenerator(build_chat_model(settings))


def fill_object_english_names(
    session: Session,
    *,
    object_type: ObjectType,
    generator: EnglishNameGenerator | None,
) -> bool:
    """只补 name_en IS NULL 的值；已有英文名永不被自动覆盖。"""
    properties = list(
        session.scalars(
            select(Property)
            .where(Property.owner_type_id == object_type.id)
            .order_by(Property.name, Property.id)
        )
    )
    missing_object = object_type.name_en is None
    missing_properties = [prop for prop in properties if prop.name_en is None]
    if not missing_object and not missing_properties:
        return False

    changed = False
    direct_object = _identifier(object_type.name)
    if missing_object and direct_object is not None:
        object_type.name_en = direct_object
        changed = True
        missing_object = False
    for prop in missing_properties:
        direct = _identifier(prop.name)
        if direct is not None:
            prop.name_en = direct
            changed = True
    missing_properties = [prop for prop in missing_properties if prop.name_en is None]
    if not missing_object and not missing_properties:
        if changed:
            session.flush()
        return changed

    if generator is None:
        if changed:
            session.flush()
        return changed

    try:
        generated = generator.generate(
            object_name=object_type.name,
            property_names=[prop.name for prop in missing_properties],
        )
    except Exception:
        logger.warning(
            "english name generation failed for object type %s", object_type.id, exc_info=True
        )
        if changed:
            session.flush()
        return changed

    if missing_object:
        generated_object = _identifier(generated.object_name)
        if generated_object is not None:
            object_type.name_en = generated_object
            changed = True
    for prop in missing_properties:
        generated_name = _identifier(generated.properties.get(prop.name))
        if generated_name is not None:
            prop.name_en = generated_name
            changed = True
    if changed:
        session.flush()
    return changed


def _prompt(object_name: str, property_names: Sequence[str]) -> str:
    return (
        "把下面的中文本体名称翻译成简短、稳定的英文标识符。\n"
        "只输出 JSON，不要 Markdown。对象名用 snake_case；字段名逐项对应 source，"
        "使用 snake_case 且不要带表名前缀。\n"
        f"对象中文名：{object_name}\n"
        f"字段中文名：{json.dumps(list(property_names), ensure_ascii=False)}\n"
        '输出格式：{"object_name":"order","properties":'
        '[{"source":"下单时间","name":"ordered_at"}]}'
    )


def _content_text(content: Any) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(
            str(item.get("text") or "")
            for item in content
            if isinstance(item, dict) and item.get("type") == "text"
        )
    return str(content)


def _json_object(content: str) -> dict[str, Any]:
    start = content.find("{")
    end = content.rfind("}")
    if start < 0 or end < start:
        raise ValueError("english name response does not contain a JSON object")
    value = json.loads(content[start : end + 1])
    if not isinstance(value, dict):
        raise ValueError("english name response must be a JSON object")
    return value


def _identifier(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    text = value.strip().lower().replace("-", "_").replace(" ", "_")
    if not _IDENTIFIER.fullmatch(text):
        return None
    return text


__all__ = [
    "EnglishNameGenerator",
    "EnglishNames",
    "LlmEnglishNameGenerator",
    "build_english_name_generator",
    "fill_object_english_names",
]
