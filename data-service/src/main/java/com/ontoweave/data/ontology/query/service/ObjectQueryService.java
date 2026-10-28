package com.ontoweave.data.ontology.query.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectDetailResponse;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectPageResponse;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectResponse;
import com.ontoweave.data.ontology.query.api.dto.OntologyPropertyResponse;
import com.ontoweave.data.ontology.query.api.dto.ObjectQueryRequest;
import com.ontoweave.data.ontology.query.api.dto.QueryObjectResponse;
import com.ontoweave.data.ontology.query.api.dto.QueryPageResponse;
import com.ontoweave.data.ontology.query.common.MappingSupport;
import com.ontoweave.data.ontology.query.common.ObjectProjection;
import com.ontoweave.data.ontology.query.common.PageRequest;
import com.ontoweave.data.ontology.query.common.QueryErrors;
import com.ontoweave.data.ontology.query.common.QueryLimits;
import com.ontoweave.data.ontology.query.common.SortParser;
import com.ontoweave.data.ontology.query.filter.FilterCondition;
import com.ontoweave.data.ontology.query.filter.FilterParser;
import com.ontoweave.data.ontology.query.filter.ParsedFilters;
import com.ontoweave.data.ontology.query.repository.JdbcObjectQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ObjectQueryService {

    private final JdbcObjectQuery objects;
    private final FilterParser filters;
    private final SortParser sorts;
    private final ObjectMapper mapper;

    public ObjectQueryService(
            JdbcObjectQuery objects,
            FilterParser filters,
            SortParser sorts,
            ObjectMapper mapper
    ) {
        this.objects = objects;
        this.filters = filters;
        this.sorts = sorts;
        this.mapper = mapper;
    }

    public QueryPageResponse query(UUID objectTypeId, ObjectQueryRequest request) {
        requireIds(objectTypeId, request == null ? null : request.ownerId());
        int limit = QueryLimits.limit(request.limit());
        List<String> primaryKeys = QueryLimits.primaryKeys(request.primaryKeys());
        JdbcObjectQuery.ObjectVersionRow version = objects
                .findCurrentVersion(objectTypeId, request.ownerId())
                .orElse(null);
        if (version == null) {
            return new QueryPageResponse(List.of(), 0, false, null);
        }
        MappingSnapshot mapping = MappingSupport.parse(mapper, version.mappingSnapshot());
        ParsedFilters parsed = filters.parse(mapping, request.filters());
        JdbcObjectQuery.ObjectQueryRows rows = objects.query(
                version.id(),
                objectTypeId,
                request.q(),
                primaryKeys,
                parsed,
                sorts.parse(mapping, request.sort()),
                MappingSupport.primaryKeyDatatype(mapping),
                limit
        );
        List<QueryObjectResponse> items = rows.items().stream()
                .map(row -> new QueryObjectResponse(
                        objectTypeId,
                        row.primaryKey(),
                        row.title(),
                        ObjectProjection.project(mapping, row.properties(), request.project())
                ))
                .toList();
        return new QueryPageResponse(items, rows.total(), rows.total() > limit, version.id());
    }

    public OntologyObjectPageResponse list(
            UUID objectTypeId,
            UUID ownerId,
            Integer page,
            Integer pageSize,
            String q,
            List<FilterCondition> conditions
    ) {
        requireIds(objectTypeId, ownerId);
        PageRequest request = PageRequest.of(page, pageSize);
        JdbcObjectQuery.ObjectVersionRow version = objects
                .findCurrentVersion(objectTypeId, ownerId)
                .orElse(null);
        if (version == null) {
            return new OntologyObjectPageResponse(
                    List.of(),
                    0,
                    request.page(),
                    request.pageSize(),
                    null
            );
        }
        MappingSnapshot mapping = MappingSupport.parse(mapper, version.mappingSnapshot());
        ParsedFilters parsed = filters.parse(mapping, conditions);
        JdbcObjectQuery.ObjectPageRows rows = objects.findPage(
                version.id(),
                objectTypeId,
                q,
                parsed,
                MappingSupport.primaryKeyDatatype(mapping),
                request
        );
        List<OntologyObjectResponse> items = rows.items().stream()
                .map(row -> new OntologyObjectResponse(
                        row.primaryKey(),
                        row.title(),
                        version.id()
                ))
                .toList();
        return new OntologyObjectPageResponse(
                items,
                rows.total(),
                request.page(),
                request.pageSize(),
                version.id()
        );
    }

    public OntologyObjectDetailResponse detail(
            UUID objectTypeId,
            UUID ownerId,
            String primaryKey
    ) {
        requireIds(objectTypeId, ownerId);
        if (primaryKey == null || primaryKey.isBlank()) {
            throw QueryErrors.invalidRequest("primaryKey 不能为空");
        }
        JdbcObjectQuery.ObjectVersionRow version = objects
                .findCurrentVersion(objectTypeId, ownerId)
                .orElseThrow(() -> QueryErrors.notFound("对象不存在"));
        MappingSnapshot mapping = MappingSupport.parse(mapper, version.mappingSnapshot());
        JdbcObjectQuery.ObjectDetailRow row = objects
                .findDetail(version.id(), objectTypeId, primaryKey)
                .orElseThrow(() -> QueryErrors.notFound("对象不存在"));
        List<OntologyPropertyResponse> properties = mapping.attributes().stream()
                .map(attribute -> new OntologyPropertyResponse(
                        attribute.attributeId(),
                        MappingSupport.propertyValue(row.properties(), attribute)
                ))
                .toList();
        return new OntologyObjectDetailResponse(
                row.primaryKey(),
                row.title(),
                version.id(),
                properties
        );
    }

    private void requireIds(UUID objectTypeId, UUID ownerId) {
        if (objectTypeId == null || ownerId == null) {
            throw QueryErrors.invalidRequest("objectTypeId 和 ownerId 不能为空");
        }
    }
}
