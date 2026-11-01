"""edit-set preview / apply 接口。"""

from __future__ import annotations

from collections.abc import Callable


class EditClientMixin:
    _ontology_request: Callable[..., dict]

    def preview_edit_set(self, payload: dict) -> dict:
        return self._ontology_request("POST", "/edit-sets/preview", json=payload)

    def apply_edit_set(self, payload: dict) -> dict:
        return self._ontology_request("POST", "/edit-sets/apply", json=payload)


__all__ = ["EditClientMixin"]
