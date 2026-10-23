"""本体建模与映射工作台服务门面。"""

from __future__ import annotations

from app.services.object_type import (
    create_object_type,
    get_object_type,
    list_object_types,
    transition_object_type,
)
from app.services.ontology.agent_state import load_agent_state
from app.services.ontology.bindings import (
    delete_binding,
    generate_binding,
    get_binding,
    list_runs,
    normalize_binding,
    prepare_object_payload,
    preview_binding,
    safe_binding,
    save_binding,
    validate_binding,
    with_required_attributes,
)
from app.services.ontology.common import (
    DIRECTION_LABELS,
    OBJECT_TYPE,
    RELATION,
    normalize_resource_kind,
    resource_subject,
)
from app.services.ontology.definitions import get_subject_definition
from app.services.ontology.queries import (
    cancel_run,
    get_object,
    get_relation_overview,
    get_run,
    list_objects,
    list_relation_instances,
    merge_object_detail,
    normalize_relation_overview,
    related_objects,
    relation_groups,
)
from app.services.ontology.relation_sync import (
    relation_definition_payload,
    sync_relations,
    sync_relations_best_effort,
    sync_subject_relations,
    sync_subject_relations_best_effort,
)
from app.services.ontology.resources import ensure_conversation, list_resources
from app.services.ontology.suggest import suggest_mapping
from app.services.property import (
    create_property,
    get_property,
    list_properties,
    transition_property,
    validate_property_patch,
)
from app.services.relation import (
    create_relation,
    get_relation,
    transition_relation,
    validate_relation_patch,
)
from app.services.status import ALLOWED_TRANSITIONS, APPROVED, CONFIRMED, DRAFT

__all__ = [
    "ALLOWED_TRANSITIONS",
    "APPROVED",
    "CONFIRMED",
    "DIRECTION_LABELS",
    "DRAFT",
    "OBJECT_TYPE",
    "RELATION",
    "cancel_run",
    "create_object_type",
    "create_property",
    "create_relation",
    "delete_binding",
    "ensure_conversation",
    "generate_binding",
    "get_binding",
    "get_object",
    "get_object_type",
    "get_property",
    "get_relation",
    "get_relation_overview",
    "get_run",
    "get_subject_definition",
    "list_object_types",
    "list_objects",
    "list_properties",
    "list_relation_instances",
    "list_resources",
    "list_runs",
    "load_agent_state",
    "merge_object_detail",
    "normalize_binding",
    "prepare_object_payload",
    "normalize_relation_overview",
    "normalize_resource_kind",
    "preview_binding",
    "related_objects",
    "relation_definition_payload",
    "relation_groups",
    "resource_subject",
    "safe_binding",
    "save_binding",
    "suggest_mapping",
    "sync_relations",
    "sync_relations_best_effort",
    "sync_subject_relations",
    "sync_subject_relations_best_effort",
    "transition_object_type",
    "transition_property",
    "transition_relation",
    "validate_binding",
    "with_required_attributes",
    "validate_property_patch",
    "validate_relation_patch",
]
