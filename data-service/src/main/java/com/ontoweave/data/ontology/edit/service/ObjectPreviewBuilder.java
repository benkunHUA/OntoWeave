package com.ontoweave.data.ontology.edit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.ontology.edit.model.EditSetPreview;
import com.ontoweave.data.ontology.edit.model.EditSetRequest;
import com.ontoweave.data.ontology.edit.store.JdbcObjectEditStore;
import com.ontoweave.data.ontology.query.common.QueryErrors;
import com.ontoweave.data.ontology.query.repository.JdbcObjectQuery;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ObjectPreviewBuilder {

    private final JdbcObjectEditStore objectStore;
    private final JdbcObjectQuery objectQuery;
    private final EditContextResolver contexts;
    private final ObjectEditValues values;
    private final ObjectMapper mapper;

    public ObjectPreviewBuilder(
            JdbcObjectEditStore objectStore,
            JdbcObjectQuery objectQuery,
            EditContextResolver contexts,
            ObjectEditValues values,
            ObjectMapper mapper
    ) {
        this.objectStore = objectStore;
        this.objectQuery = objectQuery;
        this.contexts = contexts;
        this.values = values;
        this.mapper = mapper;
    }

    EditSetPreview.ObjectChange create(
            UUID ownerId,
            EditSetRequest.ObjectCreate request,
            Map<String, Long> expected,
            List<EditSetPreview.Conflict> conflicts
    ) {
        requireIdentity(request.objectTypeId(), request.primaryKey());
        EditContextResolver.EditContext context = contexts
                .find(ownerId, request.objectTypeId()).orElse(null);
        if (context == null) {
            conflicts.add(conflict("NOT_INDEXED", "对象类型没有可用索引", request.objectTypeId()));
            return new EditSetPreview.ObjectChange(
                    "OBJECT_CREATE", request.objectTypeId(), request.primaryKey(), null, null);
        }
        JdbcObjectEditStore.ObjectEditRow edit = objectStore
                .findEdit(ownerId, request.objectTypeId(), request.primaryKey()).orElse(null);
        JdbcObjectQuery.ObjectDetailRow base = objectQuery
                .findDetail(context.versionId(), ownerId, request.objectTypeId(), request.primaryKey())
                .orElse(null);
        long actualVersion = activeVersion(edit);
        addObjectConflict(request.objectTypeId(), request.primaryKey(), actualVersion,
                expected, conflicts);
        if (base != null || (edit != null && !edit.stale() && "ACTIVE".equals(edit.state()))) {
            conflicts.add(conflict("OBJECT_ALREADY_EXISTS", "对象已存在", request.objectTypeId()));
        }
        validateValues(context, request.values());
        ObjectNode properties = values.createProperties(
                context.mapping(), request.primaryKey(), request.values());
        return new EditSetPreview.ObjectChange(
                "OBJECT_CREATE", request.objectTypeId(), request.primaryKey(), null,
                new EditSetPreview.ObjectState(
                        request.primaryKey(),
                        values.effectiveTitle(
                                context.mapping(), request.primaryKey(), true,
                                values.titleValue(context.mapping(), properties),
                                request.primaryKey()),
                        properties,
                        nextVersion(edit)
                )
        );
    }

    EditSetPreview.ObjectChange update(
            UUID ownerId,
            EditSetRequest.ObjectUpdate request,
            Map<String, Long> expected,
            List<EditSetPreview.Conflict> conflicts
    ) {
        requireIdentity(request.objectTypeId(), request.primaryKey());
        EditContextResolver.EditContext context = contexts
                .find(ownerId, request.objectTypeId()).orElse(null);
        if (context == null) {
            conflicts.add(conflict("NOT_INDEXED", "对象类型没有可用索引", request.objectTypeId()));
            return new EditSetPreview.ObjectChange(
                    "OBJECT_MODIFY", request.objectTypeId(), request.primaryKey(), null, null);
        }
        JdbcObjectEditStore.ObjectEditRow edit = objectStore
                .findEdit(ownerId, request.objectTypeId(), request.primaryKey()).orElse(null);
        JdbcObjectQuery.ObjectDetailRow beforeRow = objectQuery
                .findDetail(context.versionId(), ownerId, request.objectTypeId(), request.primaryKey())
                .orElse(null);
        long actualVersion = activeVersion(edit);
        addObjectConflict(request.objectTypeId(), request.primaryKey(), actualVersion,
                expected, conflicts);
        if (edit != null && !edit.stale() && "DELETED".equals(edit.state())) {
            conflicts.add(conflict("OBJECT_DELETED", "不能修改已删除对象", request.objectTypeId()));
        } else if (beforeRow == null) {
            conflicts.add(conflict("OBJECT_NOT_FOUND", "对象不存在或不可见", request.objectTypeId()));
        }
        validateUpdate(context, request);
        ObjectNode properties = beforeRow == null
                ? mapper.createObjectNode()
                : beforeRow.properties().deepCopy();
        ObjectNode patched = values.patch(properties, request.set(), request.clear());
        UUID titleAttributeId = context.mapping().titleKeyAttributeId();
        boolean titleChanged = titleAttributeId != null
                && (request.set().containsKey(titleAttributeId)
                || request.clear().contains(titleAttributeId));
        String fallbackTitle = beforeRow == null
                ? request.primaryKey() : beforeRow.title();
        String title = titleChanged
                ? values.effectiveTitle(
                        context.mapping(), request.primaryKey(), true,
                        values.titleValue(context.mapping(), patched), fallbackTitle)
                : fallbackTitle;
        EditSetPreview.ObjectState before = beforeRow == null
                ? null : state(beforeRow, actualVersion);
        return new EditSetPreview.ObjectChange(
                "OBJECT_MODIFY", request.objectTypeId(), request.primaryKey(), before,
                new EditSetPreview.ObjectState(
                        request.primaryKey(), title, patched, nextVersion(edit))
        );
    }

    EditSetPreview.ObjectChange delete(
            UUID ownerId,
            EditSetRequest.ObjectDelete request,
            Map<String, Long> expected,
            List<EditSetPreview.Conflict> conflicts
    ) {
        requireIdentity(request.objectTypeId(), request.primaryKey());
        EditContextResolver.EditContext context = contexts
                .find(ownerId, request.objectTypeId()).orElse(null);
        if (context == null) {
            conflicts.add(conflict("NOT_INDEXED", "对象类型没有可用索引", request.objectTypeId()));
            return new EditSetPreview.ObjectChange(
                    "OBJECT_DELETE", request.objectTypeId(), request.primaryKey(), null, null);
        }
        JdbcObjectEditStore.ObjectEditRow edit = objectStore
                .findEdit(ownerId, request.objectTypeId(), request.primaryKey()).orElse(null);
        JdbcObjectQuery.ObjectDetailRow beforeRow = objectQuery
                .findDetail(context.versionId(), ownerId, request.objectTypeId(), request.primaryKey())
                .orElse(null);
        long actualVersion = activeVersion(edit);
        addObjectConflict(request.objectTypeId(), request.primaryKey(), actualVersion,
                expected, conflicts);
        if (beforeRow == null) {
            conflicts.add(conflict("OBJECT_NOT_FOUND", "对象不存在或不可见", request.objectTypeId()));
        }
        return new EditSetPreview.ObjectChange(
                "OBJECT_DELETE", request.objectTypeId(), request.primaryKey(),
                beforeRow == null ? null : state(beforeRow, actualVersion), null
        );
    }

    private void validateValues(
            EditContextResolver.EditContext context,
            Map<UUID, JsonNode> values
    ) {
        values.forEach((attributeId, value) -> {
            this.values.requireAttribute(context.mapping(), attributeId);
            this.values.validateValue(value);
        });
    }

    private void validateUpdate(
            EditContextResolver.EditContext context,
            EditSetRequest.ObjectUpdate request
    ) {
        UUID primaryKeyAttributeId = context.mapping().primaryKeyAttributeId();
        if (request.set().containsKey(primaryKeyAttributeId)
                || request.clear().contains(primaryKeyAttributeId)) {
            throw QueryErrors.invalidRequest("主键不可修改");
        }
        validateValues(context, request.set());
        request.clear().forEach(attributeId ->
                values.requireAttribute(context.mapping(), attributeId));
    }

    private void addObjectConflict(
            UUID objectTypeId,
            String primaryKey,
            long actual,
            Map<String, Long> expected,
            List<EditSetPreview.Conflict> conflicts
    ) {
        Long wanted = expected.get(objectKey(objectTypeId, primaryKey));
        if (wanted != null && wanted != actual) {
            conflicts.add(conflict("EDIT_CONFLICT", "对象版本已变化", objectTypeId,
                    Map.of("expectedVersion", wanted, "actualVersion", actual)));
        }
    }

    private EditSetPreview.ObjectState state(
            JdbcObjectQuery.ObjectDetailRow row,
            long version
    ) {
        return new EditSetPreview.ObjectState(
                row.primaryKey(), row.title(), row.properties(), version);
    }

    private long activeVersion(JdbcObjectEditStore.ObjectEditRow edit) {
        return edit == null || edit.stale() ? 0 : edit.version();
    }

    private long nextVersion(JdbcObjectEditStore.ObjectEditRow edit) {
        return edit == null || edit.stale() ? 1 : edit.version() + 1;
    }

    private void requireIdentity(UUID objectTypeId, String primaryKey) {
        if (objectTypeId == null || blank(primaryKey)) {
            throw QueryErrors.invalidRequest("objectTypeId 和 primaryKey 不能为空");
        }
    }

    private EditSetPreview.Conflict conflict(
            String code,
            String message,
            UUID subjectId
    ) {
        return new EditSetPreview.Conflict(code, message, Map.of("id", subjectId.toString()));
    }

    private EditSetPreview.Conflict conflict(
            String code,
            String message,
            UUID subjectId,
            Map<String, Object> details
    ) {
        Map<String, Object> all = new HashMap<>(details);
        all.put("id", subjectId.toString());
        return new EditSetPreview.Conflict(code, message, all);
    }

    private String objectKey(UUID objectTypeId, String primaryKey) {
        return objectTypeId + ":" + primaryKey;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
