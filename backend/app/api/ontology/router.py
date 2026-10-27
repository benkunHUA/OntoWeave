"""本体 BFF 路由聚合。"""

from __future__ import annotations

from fastapi import APIRouter

from app.api.ontology.bindings import router as bindings_router
from app.api.ontology.queries import router as queries_router
from app.api.ontology.relations import router as relations_router
from app.api.ontology.resources import router as resources_router

router = APIRouter(prefix="/ontology", tags=["ontology"])
router.include_router(resources_router)
router.include_router(bindings_router)
router.include_router(relations_router)
router.include_router(queries_router)

__all__ = ["router"]
