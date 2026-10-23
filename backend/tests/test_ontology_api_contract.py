from app.main import _create_app

EXPECTED_PATHS = {
    "/api/v1/ontology/resources",
    "/api/v1/ontology/resources/{resource_kind}/{resource_id}",
    "/api/v1/ontology/conversations",
    "/api/v1/ontology/bindable-datasets",
    "/api/v1/ontology/bindable-datasets/{kind}/{dataset_id}/columns",
    "/api/v1/ontology/object-types/{object_type_id}/binding",
    "/api/v1/ontology/object-types/{object_type_id}/binding/validate",
    "/api/v1/ontology/object-types/{object_type_id}/binding/preview",
    "/api/v1/ontology/object-types/{object_type_id}/binding/suggest",
    "/api/v1/ontology/object-types/{object_type_id}/binding/generate",
    "/api/v1/ontology/object-types/{object_type_id}/binding/runs",
    "/api/v1/ontology/relation-types/{relation_id}/binding",
    "/api/v1/ontology/relation-types/{relation_id}/binding/preview",
    "/api/v1/ontology/relation-types/{relation_id}/binding/suggest",
    "/api/v1/ontology/relation-types/{relation_id}/binding/generate",
    "/api/v1/ontology/relation-types/{relation_id}/binding/runs",
    "/api/v1/ontology/relation-types/{relation_id}/overview",
    "/api/v1/ontology/runs/{run_id}",
    "/api/v1/ontology/runs/{run_id}/cancel",
    "/api/v1/ontology/object-types/{object_type_id}/objects",
    "/api/v1/ontology/object-types/{object_type_id}/object",
    "/api/v1/ontology/object-types/{object_type_id}/object-relations",
    "/api/v1/ontology/object-types/{object_type_id}/object-relations/{relation_id}",
    "/api/v1/ontology/relation-types/{relation_id}/instances",
}


def test_public_ontology_paths_are_registered():
    app = _create_app(lambda: None, enable_agent_checkpoints=False)
    paths = set(app.openapi()["paths"])

    assert EXPECTED_PATHS <= paths
