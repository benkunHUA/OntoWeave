package com.ontoweave.data.ontology.edit.service;

import com.ontoweave.data.ontology.edit.model.EditSetPreview;
import com.ontoweave.data.ontology.edit.model.EditSetRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EditSetPreviewService {

    private final ObjectPreviewBuilder objects;
    private final RelationPreviewBuilder relations;

    public EditSetPreviewService(
            ObjectPreviewBuilder objects,
            RelationPreviewBuilder relations
    ) {
        this.objects = objects;
        this.relations = relations;
    }

    public EditSetPreview preview(EditSetRequest request) {
        List<EditSetPreview.ObjectChange> objectChanges = new ArrayList<>();
        List<EditSetPreview.RelationChange> relationChanges = new ArrayList<>();
        List<EditSetPreview.Conflict> conflicts = new ArrayList<>();
        Map<String, Long> expectedObjects = expectedObjects(request);
        Map<String, Long> expectedRelations = expectedRelations(request);
        request.objectCreates().forEach(item -> objectChanges.add(
                objects.create(request.ownerId(), item, expectedObjects, conflicts)));
        request.objectUpdates().forEach(item -> objectChanges.add(
                objects.update(request.ownerId(), item, expectedObjects, conflicts)));
        request.objectDeletes().forEach(item -> objectChanges.add(
                objects.delete(request.ownerId(), item, expectedObjects, conflicts)));
        request.relationCreates().forEach(item -> relationChanges.add(
                relations.change(request.ownerId(), item.relationId(), item.fromPrimaryKey(),
                        item.toPrimaryKey(), true, expectedRelations, conflicts)));
        request.relationDeletes().forEach(item -> relationChanges.add(
                relations.change(request.ownerId(), item.relationId(), item.fromPrimaryKey(),
                        item.toPrimaryKey(), false, expectedRelations, conflicts)));
        return new EditSetPreview(
                request.executionId(), objectChanges, relationChanges, conflicts,
                conflicts.isEmpty()
        );
    }

    private Map<String, Long> expectedObjects(EditSetRequest request) {
        Map<String, Long> values = new HashMap<>();
        for (EditSetRequest.ObjectExpectation expected : request.expected().objects()) {
            values.put(objectKey(expected.objectTypeId(), expected.primaryKey()), expected.version());
        }
        return values;
    }

    private Map<String, Long> expectedRelations(EditSetRequest request) {
        Map<String, Long> values = new HashMap<>();
        for (EditSetRequest.RelationExpectation expected : request.expected().relations()) {
            values.put(
                    relationKey(expected.relationId(), expected.fromPrimaryKey(),
                            expected.toPrimaryKey()),
                    expected.version()
            );
        }
        return values;
    }

    private String objectKey(java.util.UUID objectTypeId, String primaryKey) {
        return objectTypeId + ":" + primaryKey;
    }

    private String relationKey(
            java.util.UUID relationId,
            String fromPrimaryKey,
            String toPrimaryKey
    ) {
        return relationId + ":" + fromPrimaryKey + ":" + toPrimaryKey;
    }
}
