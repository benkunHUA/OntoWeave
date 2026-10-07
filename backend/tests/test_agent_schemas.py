from app.agent import schemas


def test_ontology_tool_names_are_stable():
    assert [tool.name for tool in schemas.ONTOLOGY_TOOLS] == [
        "search_ontology",
        "list_types",
        "get_subgraph",
        "propose_type",
        "propose_property",
        "propose_relation",
        "update_element",
        "remove_element",
    ]


def test_view_tool_names_are_stable():
    assert [tool.name for tool in schemas.VIEW_TOOLS] == [
        "set_layout",
        "highlight",
        "dim",
        "group",
        "collapse",
        "expand",
        "filter_view",
        "focus",
        "reset_view",
        "annotate",
    ]


def test_every_tool_has_a_description():
    for tool in [*schemas.ONTOLOGY_TOOLS, *schemas.VIEW_TOOLS]:
        assert tool.name
        assert tool.description
