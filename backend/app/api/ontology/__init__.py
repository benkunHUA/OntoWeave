"""对象映射工作台的浏览器 BFF。"""

from __future__ import annotations

from app.api.ontology.deps import get_ontology_client
from app.api.ontology.router import router

__all__ = ["get_ontology_client", "router"]
