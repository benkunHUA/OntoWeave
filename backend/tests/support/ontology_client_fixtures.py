"""`test_ontology_client.py` 的共享 fixture、假客户端与构造 helper。"""

import httpx

from app.clients.data_service.ontology_client import OntologyDataServiceClient
from app.config import Settings


def _client(handler) -> OntologyDataServiceClient:
    return OntologyDataServiceClient(
        Settings(data_service_url="http://java:18001"),
        transport=httpx.MockTransport(handler),
    )
