"""数据连接对前端暴露的 REST 边界。"""

from fastapi import APIRouter

from app.api.data_connections import connections, datasets, sync

router = APIRouter(tags=["data-connections"])
router.include_router(connections.router)
router.include_router(sync.router)
router.include_router(datasets.router)

__all__ = ["router"]
