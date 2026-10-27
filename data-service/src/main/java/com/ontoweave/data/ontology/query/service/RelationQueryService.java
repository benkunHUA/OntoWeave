package com.ontoweave.data.ontology.query.service;

import com.ontoweave.data.ontology.query.api.dto.RelatedObjectPageResponse;
import com.ontoweave.data.ontology.query.api.dto.RelatedObjectResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationGroupResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationGroupsResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationInstancePageResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationInstanceResponse;
import com.ontoweave.data.ontology.query.api.dto.QueryObjectResponse;
import com.ontoweave.data.ontology.query.api.dto.QueryPageResponse;
import com.ontoweave.data.ontology.query.api.dto.QueryStepRequest;
import com.ontoweave.data.ontology.query.common.PageRequest;
import com.ontoweave.data.ontology.query.common.QueryConstants;
import com.ontoweave.data.ontology.query.common.QueryDirection;
import com.ontoweave.data.ontology.query.common.QueryErrors;
import com.ontoweave.data.ontology.query.common.QueryLimits;
import com.ontoweave.data.ontology.query.common.MappingSupport;
import com.ontoweave.data.ontology.query.common.ObjectProjection;
import com.ontoweave.data.ontology.query.common.SortParser;
import com.ontoweave.data.ontology.query.filter.QueryDatatype;
import com.ontoweave.data.ontology.query.filter.FilterParser;
import com.ontoweave.data.ontology.query.filter.ParsedFilters;
import com.ontoweave.data.ontology.query.repository.JdbcRelationQuery;
import com.ontoweave.data.ontology.query.repository.JdbcRelationStepQuery;
import com.ontoweave.data.ontology.query.repository.JdbcObjectQuery;
import com.ontoweave.data.ontology.query.repository.InstancePage;
import com.ontoweave.data.ontology.query.repository.RelationDefinitionRow;
import com.ontoweave.data.ontology.query.repository.RelationVersionRow;
import com.ontoweave.data.ontology.query.repository.RelatedPage;
import com.ontoweave.data.ontology.query.repository.RelationStepRows;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RelationQueryService {

    private final JdbcRelationQuery relations;
    private final JdbcRelationStepQuery steps;
    private final JdbcObjectQuery objects;
    private final FilterParser filters;
    private final SortParser sorts;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public RelationQueryService(
            JdbcRelationQuery relations,
            JdbcRelationStepQuery steps,
            JdbcObjectQuery objects,
            FilterParser filters,
            SortParser sorts,
            com.fasterxml.jackson.databind.ObjectMapper mapper
    ) {
        this.relations = relations;
        this.steps = steps;
        this.objects = objects;
        this.filters = filters;
        this.sorts = sorts;
        this.mapper = mapper;
    }

    public QueryPageResponse step(QueryStepRequest request) {
        requireIds(request == null ? null : request.relationId(), request == null ? null : request.ownerId());
        if (request.inputs().isEmpty()) {
            return new QueryPageResponse(List.of(), 0, false, null);
        }
        QueryDirection direction = QueryDirection.parse(request.direction(), false);
        RelationVersionRow version = relations.findCurrentVersion(request.relationId(), request.ownerId())
                .orElse(null);
        if (version == null) {
            return new QueryPageResponse(List.of(), 0, false, null);
        }
        UUID inputTypeId = request.inputs().get(0).objectTypeId();
        UUID targetTypeId = direction == QueryDirection.OUTGOING
                ? version.toObjectTypeId()
                : version.fromObjectTypeId();
        UUID expectedInputType = direction == QueryDirection.OUTGOING
                ? version.fromObjectTypeId()
                : version.toObjectTypeId();
        List<String> primaryKeys = new ArrayList<>();
        for (var input : request.inputs()) {
            if (input == null || input.objectTypeId() == null || !expectedInputType.equals(inputTypeId)
                    || !expectedInputType.equals(input.objectTypeId())
                    || input.primaryKey() == null || input.primaryKey().isBlank()) {
                throw QueryErrors.invalidRequest("inputs 必须来自同一关系端点且主键非空");
            }
            primaryKeys.add(input.primaryKey());
        }
        JdbcObjectQuery.ObjectVersionRow targetVersion = objects
                .findCurrentVersion(targetTypeId, request.ownerId())
                .orElse(null);
        if (targetVersion == null) {
            return new QueryPageResponse(List.of(), 0, false, null);
        }
        var mapping = MappingSupport.parse(mapper, targetVersion.mappingSnapshot());
        ParsedFilters parsed = filters.parse(mapping, request.filters(), "peer");
        int limit = QueryLimits.limit(request.limit());
        RelationStepRows rows = steps.query(
                version,
                request.ownerId(),
                direction,
                inputTypeId,
                primaryKeys,
                parsed,
                sorts.parse(mapping, request.sort()),
                MappingSupport.primaryKeyDatatype(mapping),
                limit
        );
        List<QueryObjectResponse> items = rows.items().stream()
                .map(row -> new QueryObjectResponse(
                        targetTypeId,
                        row.primaryKey(),
                        row.title(),
                        ObjectProjection.project(mapping, row.properties(), request.project())
                ))
                .toList();
        return new QueryPageResponse(items, rows.total(), rows.total() > limit, targetVersion.id());
    }

    public RelationGroupsResponse groups(
            UUID objectTypeId,
            UUID ownerId,
            String primaryKey,
            String direction
    ) {
        requireIds(objectTypeId, ownerId);
        requirePrimaryKey(primaryKey);
        QueryDirection requested = QueryDirection.parse(direction, true);
        List<RelationGroupResponse> groups = new ArrayList<>();
        for (RelationDefinitionRow definition : relations.findDefinitionsForObjectType(
                objectTypeId,
                ownerId
        )) {
            if (requested != QueryDirection.INCOMING && definition.sourceTypeId().equals(objectTypeId)) {
                groups.add(group(definition, QueryDirection.OUTGOING, ownerId, objectTypeId, primaryKey));
            }
            if (requested != QueryDirection.OUTGOING && definition.targetTypeId().equals(objectTypeId)) {
                groups.add(group(definition, QueryDirection.INCOMING, ownerId, objectTypeId, primaryKey));
            }
        }
        return new RelationGroupsResponse(groups);
    }

    public RelatedObjectPageResponse related(
            UUID objectTypeId,
            UUID ownerId,
            String primaryKey,
            UUID relationId,
            String direction,
            Integer page,
            Integer pageSize
    ) {
        requireIds(objectTypeId, ownerId);
        requirePrimaryKey(primaryKey);
        if (relationId == null) {
            throw QueryErrors.invalidRequest("relationId 不能为空");
        }
        PageRequest request = PageRequest.of(page, pageSize);
        QueryDirection requested = QueryDirection.parse(direction, false);
        RelationVersionRow version = relations.findCurrentVersion(relationId, ownerId).orElse(null);
        if (version == null) {
            return new RelatedObjectPageResponse(
                    List.of(),
                    0,
                    request.page(),
                    request.pageSize(),
                    null
            );
        }
        QueryDatatype peerType = peerPrimaryKeyType(relationId, ownerId, requested);
        RelatedPage rows = relations.relatedPage(
                version,
                ownerId,
                requested,
                objectTypeId,
                primaryKey,
                peerType,
                request
        );
        List<RelatedObjectResponse> items = rows.items().stream()
                .map(row -> new RelatedObjectResponse(row.primaryKey(), row.title()))
                .toList();
        return new RelatedObjectPageResponse(
                items,
                rows.total(),
                request.page(),
                request.pageSize(),
                version.id()
        );
    }

    public RelationInstancePageResponse instances(
            UUID relationId,
            UUID ownerId,
            String fromPrimaryKey,
            String toPrimaryKey,
            Integer page,
            Integer pageSize
    ) {
        requireIds(relationId, ownerId);
        PageRequest request = PageRequest.of(page, pageSize);
        RelationVersionRow version = relations.findCurrentVersion(relationId, ownerId).orElse(null);
        if (version == null) {
            return new RelationInstancePageResponse(
                    List.of(),
                    0,
                    request.page(),
                    request.pageSize()
            );
        }
        InstancePage rows = relations.instances(
                version.id(),
                relationId,
                fromPrimaryKey,
                toPrimaryKey,
                request
        );
        List<RelationInstanceResponse> items = rows.items().stream()
                .map(row -> new RelationInstanceResponse(
                        row.fromObjectTypeId(),
                        row.fromPrimaryKey(),
                        row.toObjectTypeId(),
                        row.toPrimaryKey(),
                        version.id()
                ))
                .toList();
        return new RelationInstancePageResponse(
                items,
                rows.total(),
                request.page(),
                request.pageSize()
        );
    }

    private RelationGroupResponse group(
            RelationDefinitionRow definition,
            QueryDirection direction,
            UUID ownerId,
            UUID objectTypeId,
            String primaryKey
    ) {
        RelationVersionRow version = relations
                .findCurrentVersion(definition.relationId(), ownerId)
                .orElse(null);
        if (version == null) {
            return new RelationGroupResponse(
                    definition.relationId(),
                    direction.wireName(),
                    definition.name(),
                    0,
                    List.of()
            );
        }
        QueryDatatype peerType = direction == QueryDirection.OUTGOING
                ? safeDatatype(definition.targetPrimaryKeyDatatype())
                : safeDatatype(definition.sourcePrimaryKeyDatatype());
        long total = relations.countRelated(
                version,
                ownerId,
                direction,
                objectTypeId,
                primaryKey
        );
        List<RelatedObjectResponse> preview = total == 0
                ? List.of()
                : relations.previewRelated(
                                version,
                                ownerId,
                                direction,
                                objectTypeId,
                                primaryKey,
                                peerType,
                                QueryConstants.GROUP_PREVIEW_SIZE
                        ).stream()
                        .map(row -> new RelatedObjectResponse(row.primaryKey(), row.title()))
                        .toList();
        return new RelationGroupResponse(
                definition.relationId(),
                direction.wireName(),
                definition.name(),
                total,
                preview
        );
    }

    private QueryDatatype peerPrimaryKeyType(
            UUID relationId,
            UUID ownerId,
            QueryDirection direction
    ) {
        return relations.findDefinition(relationId, ownerId)
                .map(definition -> direction == QueryDirection.OUTGOING
                        ? safeDatatype(definition.targetPrimaryKeyDatatype())
                        : safeDatatype(definition.sourcePrimaryKeyDatatype()))
                .orElse(QueryDatatype.TEXT);
    }

    private QueryDatatype safeDatatype(String datatype) {
        try {
            return QueryDatatype.from(datatype);
        } catch (IllegalArgumentException exception) {
            throw QueryErrors.invalidMapping(
                    "关系端点主键类型不合法",
                    java.util.Map.of("datatype", String.valueOf(datatype))
            );
        }
    }

    private void requireIds(UUID subjectId, UUID ownerId) {
        if (subjectId == null || ownerId == null) {
            throw QueryErrors.invalidRequest("资源 ID 和 ownerId 不能为空");
        }
    }

    private void requirePrimaryKey(String primaryKey) {
        if (primaryKey == null || primaryKey.isBlank()) {
            throw QueryErrors.invalidRequest("primaryKey 不能为空");
        }
    }
}
