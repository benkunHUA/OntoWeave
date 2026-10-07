from fastapi import APIRouter

from app.api import conversations, graphs, object_types

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(object_types.router)
api_router.include_router(graphs.router)
api_router.include_router(conversations.router)
