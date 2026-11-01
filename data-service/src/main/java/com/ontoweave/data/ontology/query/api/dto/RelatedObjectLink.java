package com.ontoweave.data.ontology.query.api.dto;

import java.util.UUID;

/** 一条关系实例对，始终按业务图 source → target 定向。 */
public record RelatedObjectLink(
        UUID sourceTypeId,
        String sourcePrimaryKey,
        UUID targetTypeId,
        String targetPrimaryKey
) {
}
