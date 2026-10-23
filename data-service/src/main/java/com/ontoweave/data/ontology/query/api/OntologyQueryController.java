package com.ontoweave.data.ontology.query.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectDetailResponse;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectPageResponse;
import com.ontoweave.data.ontology.query.api.dto.RelatedObjectPageResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationGroupsResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationInstancePageResponse;
import com.ontoweave.data.ontology.query.common.QueryErrors;
import com.ontoweave.data.ontology.query.filter.FilterCondition;
import com.ontoweave.data.ontology.query.service.ObjectQueryService;
import com.ontoweave.data.ontology.query.service.RelationQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/ontology")
public class OntologyQueryController {

    private static final TypeReference<List<FilterCondition>> FILTER_LIST = new TypeReference<>() {
    };

    private final ObjectQueryService objects;
    private final RelationQueryService relations;
    private final ObjectMapper mapper;

    public OntologyQueryController(
            ObjectQueryService objects,
            RelationQueryService relations,
            ObjectMapper mapper
    ) {
        this.objects = objects;
        this.relations = relations;
        this.mapper = mapper;
    }

    @GetMapping("/object-types/{objectTypeId}/objects")
    public OntologyObjectPageResponse objects(
            @PathVariable UUID objectTypeId,
            @RequestParam UUID ownerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String filters
    ) {
        return objects.list(objectTypeId, ownerId, page, pageSize, q, parseFilters(filters));
    }

    @GetMapping("/object-types/{objectTypeId}/object")
    public OntologyObjectDetailResponse object(
            @PathVariable UUID objectTypeId,
            @RequestParam UUID ownerId,
            @RequestParam String primaryKey
    ) {
        return objects.detail(objectTypeId, ownerId, primaryKey);
    }

    @GetMapping("/object-types/{objectTypeId}/object-relations")
    public RelationGroupsResponse relationGroups(
            @PathVariable UUID objectTypeId,
            @RequestParam UUID ownerId,
            @RequestParam String primaryKey,
            @RequestParam(defaultValue = "both") String direction
    ) {
        return relations.groups(objectTypeId, ownerId, primaryKey, direction);
    }

    @GetMapping("/object-types/{objectTypeId}/object-relations/{relationId}")
    public RelatedObjectPageResponse relatedObjects(
            @PathVariable UUID objectTypeId,
            @PathVariable UUID relationId,
            @RequestParam UUID ownerId,
            @RequestParam String primaryKey,
            @RequestParam(defaultValue = "outgoing") String direction,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return relations.related(
                objectTypeId,
                ownerId,
                primaryKey,
                relationId,
                direction,
                page,
                pageSize
        );
    }

    @GetMapping("/relation-types/{relationId}/instances")
    public RelationInstancePageResponse relationInstances(
            @PathVariable UUID relationId,
            @RequestParam UUID ownerId,
            @RequestParam(required = false) String fromPrimaryKey,
            @RequestParam(required = false) String toPrimaryKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return relations.instances(
                relationId,
                ownerId,
                fromPrimaryKey,
                toPrimaryKey,
                page,
                pageSize
        );
    }

    private List<FilterCondition> parseFilters(String filters) {
        if (filters == null || filters.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(filters, FILTER_LIST);
        } catch (JsonProcessingException exception) {
            throw QueryErrors.invalidMapping("filters 不是合法的 JSON 数组", java.util.Map.of());
        }
    }
}
