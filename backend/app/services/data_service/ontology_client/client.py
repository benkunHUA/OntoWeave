"""本体 Java 客户端组合。"""

from __future__ import annotations

from app.services.data_service.ontology_client.base import OntologyClientBase
from app.services.data_service.ontology_client.bindings import BindingClientMixin
from app.services.data_service.ontology_client.datasets import DatasetClientMixin
from app.services.data_service.ontology_client.queries import QueryClientMixin


class OntologyDataServiceClient(
    DatasetClientMixin,
    BindingClientMixin,
    QueryClientMixin,
    OntologyClientBase,
):
    pass


__all__ = ["OntologyDataServiceClient"]
