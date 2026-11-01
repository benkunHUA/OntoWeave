package com.ontoweave.data.ontology.edit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.edit.model.EditSetRequest;
import com.ontoweave.data.ontology.edit.model.EditSetResult;
import com.ontoweave.data.ontology.edit.store.JdbcRelationEditStore;
import com.ontoweave.data.ontology.query.repository.JdbcRelationQuery;
import com.ontoweave.data.ontology.query.repository.RelationDefinitionRow;
import com.ontoweave.data.ontology.query.repository.RelationVersionRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RelationEditApplier {

    private final JdbcRelationEditStore relationStore;
    private final JdbcRelationQuery relationQuery;
    private final ObjectMapper mapper;

    public RelationEditApplier(
            JdbcRelationEditStore relationStore,
            JdbcRelationQuery relationQuery,
            ObjectMapper mapper
    ) {
        this.relationStore = relationStore;
        this.relationQuery = relationQuery;
        this.mapper = mapper;
    }

    EditSetResult.RelationResult apply(
            EditSetRequest request,
            UUID relationId,
            String fromPrimaryKey,
            String toPrimaryKey,
            boolean create,
            List<EditSetAuditChange> changes
    ) {
        RelationDefinitionRow definition = relationQuery.findDefinition(relationId, request.ownerId())
                .orElseThrow(() -> EditSetErrors.conflict(
                        "RELATION_NOT_FOUND", relationId, fromPrimaryKey));
        RelationVersionRow version = relationQuery.findCurrentVersion(relationId, request.ownerId())
                .orElseThrow(() -> EditSetErrors.conflict(
                        "RELATION_NOT_INDEXED", relationId, fromPrimaryKey));
        if (!"MANY_TO_MANY".equals(version.relationKind())) {
            throw EditSetErrors.conflict("UNSUPPORTED_RELATION_KIND", relationId, fromPrimaryKey);
        }
        JdbcRelationEditStore.RelationEditRow edit = relationStore.findEditForUpdate(
                request.ownerId(), relationId, definition.sourceTypeId(), fromPrimaryKey,
                definition.targetTypeId(), toPrimaryKey
        ).orElse(null);
        relationStore.upsert(
                request.ownerId(), relationId, definition.sourceTypeId(), fromPrimaryKey,
                definition.targetTypeId(), toPrimaryKey, create ? "ACTIVE" : "DELETED",
                request.executionId(), request.ownerId()
        );
        changes.add(new EditSetAuditChange(
                create ? "RELATION_CREATE" : "RELATION_DELETE",
                null, null, null, relationId, definition.sourceTypeId(), fromPrimaryKey,
                definition.targetTypeId(), toPrimaryKey,
                mapper.valueToTree(Map.of(
                        "exists", edit != null && !edit.stale() && "ACTIVE".equals(edit.state()))),
                mapper.valueToTree(Map.of("exists", create))
        ));
        return new EditSetResult.RelationResult(
                relationId, definition.sourceTypeId(), fromPrimaryKey,
                definition.targetTypeId(), toPrimaryKey, create
        );
    }
}
