from app.agent import schemas


def test_ontology_tool_names_are_stable():
    assert [tool.name for tool in schemas.ONTOLOGY_TOOLS] == [
        "search_ontology",
        "list_types",
        "get_subgraph",
        "propose_type",
        "reuse_type",
        "propose_property",
        "propose_relation",
        "update_element",
        "remove_element",
    ]


def test_view_tool_names_are_stable():
    assert [tool.name for tool in schemas.VIEW_TOOLS] == [
        "get_view_state",
        "get_ontology_slice",
        "set_layout",
        "focus",
        "fit_view",
        "reset_view",
        "highlight",
        "dim",
        "group",
        "collapse",
        "expand",
        "filter_view",
        "annotate",
        "save_view",
        "load_view",
    ]


def test_every_tool_has_a_description():
    for tool in [*schemas.ONTOLOGY_TOOLS, *schemas.VIEW_TOOLS]:
        assert tool.name
        assert tool.description
