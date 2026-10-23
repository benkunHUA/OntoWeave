"""本体 BFF 路由聚合。"""

from __future__ import annotations

from fastapi import APIRouter

from app.api.ontology import bindings, queries, relations, resources

router = APIRouter(prefix="/ontology", tags=["ontology"])
router.include_router(resources.router)
router.include_router(bindings.router)
router.include_router(relations.router)
router.include_router(queries.router)

__all__ = ["router"]
