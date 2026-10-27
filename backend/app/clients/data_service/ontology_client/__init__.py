"""`/internal/v1/ontology/**` 的 Java 内部接口客户端。"""

from __future__ import annotations

from app.clients.data_service.ontology_client.base import ONTOLOGY_PREFIX
from app.clients.data_service.ontology_client.client import OntologyDataServiceClient

__all__ = ["ONTOLOGY_PREFIX", "OntologyDataServiceClient"]
