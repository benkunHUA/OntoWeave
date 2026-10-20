package com.ontoweave.data.pipeline.graph;

import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class PipelineGraphValidator {

    private final NodeSchemaDeriver schemaDeriver = new NodeSchemaDeriver();
    private final OutputValidator outputValidator = new OutputValidator();

    public List<PipelineIssue> validate(
            PipelineDraftGraph draft,
            PipelineNodeCatalog catalog,
            Map<UUID, InputSchema> inputSchemas,
            Set<UUID> outputIds
    ) {
        return validate(draft, catalog, inputSchemas, outputIds, Map.of());
    }

    public List<PipelineIssue> validate(
            PipelineDraftGraph draft,
            PipelineNodeCatalog catalog,
            Map<UUID, InputSchema> inputSchemas,
            Set<UUID> outputIds,
            Map<UUID, OutputConfiguration> outputConfigurations
    ) {
        List<PipelineIssue> issues = new ArrayList<>();
        DraftStructureChecker checker = new DraftStructureChecker(catalog);
        DraftStructureChecker.StructureResult structure = checker.check(draft, issues);
        if (structure.fatal()) {
            return List.copyOf(new LinkedHashSet<>(issues));
        }
        GraphTopology.cycleNodes(structure.nodes(), structure.edges(), issues);
        GraphTopology.reportRequiredInputs(structure.nodes(), structure.edges(), catalog, issues);
        outputValidator.validateReachability(
                structure.nodes(),
                outputIds == null ? Set.of() : outputIds,
                issues
        );
        Map<UUID, List<FieldSchema>> schemas = deriveSchemas(
                structure.nodes(),
                structure.edges(),
                inputSchemas == null ? Map.of() : inputSchemas,
                issues
        );
        outputValidator.validateConfiguration(
                structure.nodes(),
                schemas,
                outputIds == null ? Set.of() : outputIds,
                outputConfigurations == null ? Map.of() : outputConfigurations,
                issues
        );
        return List.copyOf(new LinkedHashSet<>(issues));
    }

    private Map<UUID, List<FieldSchema>> deriveSchemas(
            Map<UUID, DraftNode> nodes,
            List<DraftEdge> edges,
            Map<UUID, InputSchema> inputSchemas,
            List<PipelineIssue> issues
    ) {
        Map<UUID, List<DraftEdge>> incoming = GraphTopology.incoming(edges);
        Map<UUID, List<FieldSchema>> schemas = new LinkedHashMap<>();
        List<UUID> order = GraphTopology.processingOrder(nodes, edges);
        for (int pass = 0; pass < order.size(); pass++) {
            boolean progress = false;
            for (UUID nodeId : order) {
                if (schemas.containsKey(nodeId)) {
                    continue;
                }
                DraftNode node = nodes.get(nodeId);
                Map<String, List<List<FieldSchema>>> byPort = new LinkedHashMap<>();
                for (DraftEdge edge : incoming.getOrDefault(nodeId, List.of())) {
                    List<FieldSchema> source = schemas.get(edge.sourceNodeId());
                    if (source != null) {
                        byPort.computeIfAbsent(edge.targetPort(), ignored -> new ArrayList<>()).add(source);
                    }
                }
                List<FieldSchema> output = schemaDeriver.derive(
                        node,
                        new NodeSchemaDeriver.Inputs(byPort),
                        inputSchemas,
                        issues
                );
                if (output != null) {
                    schemas.put(nodeId, List.copyOf(output));
                    progress = true;
                }
            }
            if (!progress) {
                break;
            }
        }
        return schemas;
    }
}
