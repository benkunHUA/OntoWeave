from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel


class GraphPublicationRead(BaseModel):
    graph_id: uuid.UUID
    graph_card: str
    embedding_model: str | None = None
    published_at: datetime
