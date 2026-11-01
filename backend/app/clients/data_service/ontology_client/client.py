"""本体 Java 客户端组合。"""

from __future__ import annotations

from app.clients.data_service.ontology_client.base import OntologyClientBase
from app.clients.data_service.ontology_client.bindings import BindingClientMixin
from app.clients.data_service.ontology_client.datasets import DatasetClientMixin
from app.clients.data_service.ontology_client.edits import EditClientMixin
from app.clients.data_service.ontology_client.errors import EditConflict
from app.clients.data_service.ontology_client.queries import QueryClientMixin


class OntologyDataServiceClient(
    DatasetClientMixin,
    BindingClientMixin,
    QueryClientMixin,
    EditClientMixin,
    OntologyClientBase,
):
    pass


__all__ = ["EditConflict", "OntologyDataServiceClient"]
