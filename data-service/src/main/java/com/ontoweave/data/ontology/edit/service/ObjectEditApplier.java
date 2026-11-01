package com.ontoweave.data.ontology.edit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.ontology.edit.model.EditSetRequest;
import com.ontoweave.data.ontology.edit.model.EditSetResult;
import com.ontoweave.data.ontology.edit.store.JdbcObjectEditStore;
import com.ontoweave.data.ontology.query.common.QueryErrors;
import com.ontoweave.data.ontology.query.repository.JdbcObjectQuery;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ObjectEditApplier {

    private final JdbcObjectEditStore objectStore;
    private final JdbcObjectQuery objectQuery;
    private final EditContextResolver contexts;
    private final ObjectEditValues values;
    private final ObjectEditReadback readback;
    private final ObjectMapper mapper;

    public ObjectEditApplier(
            JdbcObjectEditStore objectStore,
            JdbcObjectQuery objectQuery,
            EditContextResolver contexts,
            ObjectEditValues values,
            ObjectEditReadback readback,
            ObjectMapper mapper
    ) {
        this.objectStore = objectStore;
        this.objectQuery = objectQuery;
        this.contexts = contexts;
        this.values = values;
        this.readback = readback;
        this.mapper = mapper;
    }

    EditSetResult.ObjectResult create(
            EditSetRequest request,
            EditSetRequest.ObjectCreate create,
            List<EditSetAuditChange> changes
    ) {
        EditContextResolver.EditContext context =
                contexts.require(request.ownerId(), create.objectTypeId());
        objectStore.findEditForUpdate(
                request.ownerId(), create.objectTypeId(), create.primaryKey());
        if (objectQuery.findDetail(context.versionId(), request.ownerId(),
                create.objectTypeId(), create.primaryKey()).isPresent()) {
            throw EditSetErrors.conflict(
                    "OBJECT_ALREADY_EXISTS", create.objectTypeId(), create.primaryKey());
        }
        validateValues(context, create.values());
        ObjectNode patch = values.createProperties(
                context.mapping(), create.primaryKey(), create.values());
        String titleValue = values.titleValue(context.mapping(), patch);
        objectStore.deleteValues(request.ownerId(), create.objectTypeId(), create.primaryKey());
        objectStore.upsertObject(
                request.ownerId(), create.objectTypeId(), create.primaryKey(),
                "USER", "ACTIVE", true, titleValue, patch, new String[0],
                values.primaryNumber(context.mapping(), create.primaryKey()),
                values.primaryDate(context.mapping(), create.primaryKey()),
                values.primaryTimestamp(context.mapping(), create.primaryKey()),
                null, null, null, null, request.executionId(), request.ownerId()
        );
        patch.fields().forEachRemaining(entry -> {
            UUID attributeId = UUID.fromString(entry.getKey());
            objectStore.upsertValue(
                    request.ownerId(), create.objectTypeId(), create.primaryKey(),
                    attributeId, values.datatype(context.mapping(), attributeId), entry.getValue(),
                    request.executionId(), request.ownerId()
            );
        });
        JsonNode after = readback.state(
                create.primaryKey(),
                values.effectiveTitle(
                        context.mapping(), create.primaryKey(), true, titleValue, create.primaryKey()),
                patch,
                readback.currentVersion(
                        request.ownerId(), create.objectTypeId(), create.primaryKey())
        );
        changes.add(new EditSetAuditChange(
                "OBJECT_CREATE", create.objectTypeId(), create.primaryKey(),
                null, null, null, null, null, null, null, after
        ));
        return readback.read(request.ownerId(), create.objectTypeId(), create.primaryKey());
    }

    EditSetResult.ObjectResult update(
            EditSetRequest request,
            EditSetRequest.ObjectUpdate update,
            List<EditSetAuditChange> changes
    ) {
        EditContextResolver.EditContext context =
                contexts.require(request.ownerId(), update.objectTypeId());
        JdbcObjectEditStore.ObjectEditRow edit = objectStore.findEditForUpdate(
                request.ownerId(), update.objectTypeId(), update.primaryKey()).orElse(null);
        JdbcObjectQuery.ObjectDetailRow beforeRow = objectQuery.findDetail(
                context.versionId(), request.ownerId(), update.objectTypeId(), update.primaryKey()
        ).orElseThrow(() -> EditSetErrors.conflict(
                "OBJECT_NOT_FOUND", update.objectTypeId(), update.primaryKey()));
        validateUpdate(context, update);

        Map<UUID, JsonNode> set = update.set();
        ObjectNode existingPatch = edit == null || edit.stale()
                || !edit.propertiesPatch().isObject()
                ? null : (ObjectNode) edit.propertiesPatch();
        ObjectNode patch = values.patch(existingPatch, set, update.clear());
        Set<UUID> cleared = edit == null || edit.stale()
                ? new HashSet<>()
                : values.cleared(edit.propertiesCleared());
        set.forEach((attributeId, value) -> {
            if (value == null || value.isNull()) {
                cleared.add(attributeId);
            } else {
                cleared.remove(attributeId);
            }
        });
        update.clear().forEach(cleared::add);

        UUID titleAttributeId = context.mapping().titleKeyAttributeId();
        boolean titleChanged = titleAttributeId != null
                && (set.containsKey(titleAttributeId) || update.clear().contains(titleAttributeId));
        boolean priorTitleEdited = edit != null && !edit.stale() && edit.titleEdited();
        boolean titleEdited = titleChanged || priorTitleEdited;
        String titleValue = titleChanged
                ? values.titleValue(context.mapping(), patch)
                : priorTitleEdited ? edit.titleValue() : null;
        String origin = edit == null || edit.stale() ? "BASE" : edit.origin();
        objectStore.upsertObject(
                request.ownerId(), update.objectTypeId(), update.primaryKey(), origin, "ACTIVE",
                titleEdited, titleValue, patch,
                cleared.stream().map(UUID::toString).toArray(String[]::new),
                values.primaryNumber(context.mapping(), update.primaryKey()),
                values.primaryDate(context.mapping(), update.primaryKey()),
                values.primaryTimestamp(context.mapping(), update.primaryKey()),
                sourceBindingId(origin, context), sourceDatasetId(origin, context),
                sourcePrimaryKeyAttributeId(origin, context),
                sourceTitleKeyAttributeId(origin, context),
                request.executionId(), request.ownerId()
        );
        set.forEach((attributeId, value) -> objectStore.upsertValue(
                request.ownerId(), update.objectTypeId(), update.primaryKey(), attributeId,
                values.datatype(context.mapping(), attributeId), value,
                request.executionId(), request.ownerId()
        ));
        for (UUID attributeId : update.clear()) {
            objectStore.upsertValue(
                    request.ownerId(), update.objectTypeId(), update.primaryKey(), attributeId,
                    values.datatype(context.mapping(), attributeId), null,
                    request.executionId(), request.ownerId()
            );
        }

        long version = readback.currentVersion(
                request.ownerId(), update.objectTypeId(), update.primaryKey());
        JsonNode before = readback.state(
                beforeRow.primaryKey(), beforeRow.title(), beforeRow.properties(),
                edit == null || edit.stale() ? 0 : edit.version()
        );
        JsonNode after = readback.state(
                update.primaryKey(),
                values.effectiveTitle(
                        context.mapping(), update.primaryKey(), titleEdited, titleValue,
                        beforeRow.title()
                ),
                patch,
                version
        );
        changes.add(new EditSetAuditChange(
                "OBJECT_MODIFY", update.objectTypeId(), update.primaryKey(),
                null, null, null, null, null, null, before, after
        ));
        for (Map.Entry<UUID, JsonNode> entry : set.entrySet()) {
            changes.add(new EditSetAuditChange(
                    entry.getValue() == null || entry.getValue().isNull()
                            ? "VALUE_CLEAR" : "VALUE_SET",
                    update.objectTypeId(), update.primaryKey(), entry.getKey(), null,
                    null, null, null, null, before, entry.getValue()
            ));
        }
        for (UUID attributeId : update.clear()) {
            changes.add(new EditSetAuditChange(
                    "VALUE_CLEAR", update.objectTypeId(), update.primaryKey(),
                    attributeId, null, null, null, null, null, before, null
            ));
        }
        return readback.read(request.ownerId(), update.objectTypeId(), update.primaryKey());
    }

    EditSetResult.ObjectResult delete(
            EditSetRequest request,
            EditSetRequest.ObjectDelete delete,
            List<EditSetAuditChange> changes
    ) {
        EditContextResolver.EditContext context =
                contexts.require(request.ownerId(), delete.objectTypeId());
        JdbcObjectEditStore.ObjectEditRow edit = objectStore.findEditForUpdate(
                request.ownerId(), delete.objectTypeId(), delete.primaryKey()).orElse(null);
        JdbcObjectQuery.ObjectDetailRow beforeRow = objectQuery.findDetail(
                context.versionId(), request.ownerId(), delete.objectTypeId(), delete.primaryKey()
        ).orElseThrow(() -> EditSetErrors.conflict(
                "OBJECT_NOT_FOUND", delete.objectTypeId(), delete.primaryKey()));
        String origin = edit == null || edit.stale() ? "BASE" : edit.origin();
        objectStore.upsertObject(
                request.ownerId(), delete.objectTypeId(), delete.primaryKey(),
                origin, "DELETED", false, null, mapper.createObjectNode(), new String[0],
                values.primaryNumber(context.mapping(), delete.primaryKey()),
                values.primaryDate(context.mapping(), delete.primaryKey()),
                values.primaryTimestamp(context.mapping(), delete.primaryKey()),
                sourceBindingId(origin, context), sourceDatasetId(origin, context),
                sourcePrimaryKeyAttributeId(origin, context),
                sourceTitleKeyAttributeId(origin, context),
                request.executionId(), request.ownerId()
        );
        objectStore.deleteValues(request.ownerId(), delete.objectTypeId(), delete.primaryKey());
        objectStore.deleteRelationsForObject(
                request.ownerId(), delete.objectTypeId(), delete.primaryKey());
        JsonNode before = readback.state(
                beforeRow.primaryKey(), beforeRow.title(), beforeRow.properties(),
                edit == null || edit.stale() ? 0 : edit.version()
        );
        changes.add(new EditSetAuditChange(
                "OBJECT_DELETE", delete.objectTypeId(), delete.primaryKey(),
                null, null, null, null, null, null, before, null
        ));
        return new EditSetResult.ObjectResult(
                delete.objectTypeId(), delete.primaryKey(), false, null,
                mapper.createObjectNode(),
                readback.currentVersion(
                        request.ownerId(), delete.objectTypeId(), delete.primaryKey())
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
            EditSetRequest.ObjectUpdate update
    ) {
        UUID primaryKeyAttributeId = context.mapping().primaryKeyAttributeId();
        if (update.set().containsKey(primaryKeyAttributeId)
                || update.clear().contains(primaryKeyAttributeId)) {
            throw QueryErrors.invalidRequest("主键不可修改");
        }
        validateValues(context, update.set());
        update.clear().forEach(attributeId ->
                values.requireAttribute(context.mapping(), attributeId));
    }

    private UUID sourceBindingId(
            String origin,
            EditContextResolver.EditContext context
    ) {
        return "USER".equals(origin) ? null : context.binding().bindingId();
    }

    private UUID sourceDatasetId(
            String origin,
            EditContextResolver.EditContext context
    ) {
        return "USER".equals(origin) ? null : context.binding().datasetId();
    }

    private UUID sourcePrimaryKeyAttributeId(
            String origin,
            EditContextResolver.EditContext context
    ) {
        return "USER".equals(origin) ? null : context.mapping().primaryKeyAttributeId();
    }

    private UUID sourceTitleKeyAttributeId(
            String origin,
            EditContextResolver.EditContext context
    ) {
        return "USER".equals(origin) ? null : context.mapping().titleKeyAttributeId();
    }
}
