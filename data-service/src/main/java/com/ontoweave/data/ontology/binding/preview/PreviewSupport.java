package com.ontoweave.data.ontology.binding.preview;

import com.ontoweave.data.ontology.binding.api.dto.PreviewBindingRequest;
import com.ontoweave.data.ontology.binding.api.dto.PreviewSkip;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PreviewSupport {

    static final int MAX_ROWS = 20;

    private PreviewSupport() {
    }

    static Map<UUID, MappingSnapshot.AttributeMapping> attributes(MappingSnapshot mapping) {
        Map<UUID, MappingSnapshot.AttributeMapping> attributes = new HashMap<>();
        for (MappingSnapshot.AttributeMapping attribute : mapping.attributes()) {
            if (attribute.attributeId() != null) {
                attributes.put(attribute.attributeId(), attribute);
            }
        }
        return attributes;
    }

    static void runtimeBlock(
            List<MappingValidationResult.MappingIssue> blocking,
            List<PreviewSkip> skipped,
            String code,
            String message,
            String reference
    ) {
        blocking.add(new MappingValidationResult.MappingIssue(code, message, null, reference));
        skipped.add(new PreviewSkip(code, message, reference));
    }

    static void warning(
            List<MappingValidationResult.MappingIssue> warnings,
            String code,
            String message,
            String reference
    ) {
        warnings.add(new MappingValidationResult.MappingIssue(code, message, null, reference));
    }

    static int limit(PreviewBindingRequest request) {
        if (request == null || request.limit() == null) {
            return MAX_ROWS;
        }
        return Math.max(1, Math.min(request.limit(), MAX_ROWS));
    }
}
