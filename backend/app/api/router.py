from fastapi import APIRouter

from app.api import (
    actions,
    agent,
    changesets,
    conversation_events,
    conversations,
    data_connections,
    graphs,
    object_types,
    view_state,
)

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(object_types.router)
api_router.include_router(graphs.router)
api_router.include_router(conversations.router)
api_router.include_router(conversation_events.router)
api_router.include_router(data_connections.router)
api_router.include_router(changesets.router)
api_router.include_router(view_state.router)
api_router.include_router(agent.router)
api_router.include_router(actions.router)
