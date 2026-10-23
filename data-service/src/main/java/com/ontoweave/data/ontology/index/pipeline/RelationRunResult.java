package com.ontoweave.data.ontology.index.pipeline;

import java.util.Map;

public record RelationRunResult(RelationBuildResult relation, Map<String, Object> stats) {
}
