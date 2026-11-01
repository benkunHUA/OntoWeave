package com.ontoweave.data.ontology.edit;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.edit.model.EditSetPreview;
import com.ontoweave.data.ontology.edit.model.EditSetRequest;
import com.ontoweave.data.ontology.edit.service.EditSetPreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EditSetPreviewTest extends EditOverlayFixtureSupport {

    @Autowired
    private EditSetPreviewService service;

    @Test
    void modifyReturnsBeforeAndAfter() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec level = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C001", "张三",
                Map.of(level.attributeId(), "普通"));

        EditSetPreview preview = service.preview(request(fixture.ownerId(), update(
                fixture.objectTypeId(), "C001", Map.of(level.attributeId(), mapper.valueToTree("VIP")),
                List.of()
        )));

        assertThat(preview.applicable()).isTrue();
        assertThat(preview.objects()).hasSize(1);
        assertThat(preview.objects().get(0).before().version()).isZero();
        assertThat(preview.objects().get(0).after().version()).isEqualTo(1);
        assertThat(preview.objects().get(0).before().properties()
                .get(level.attributeId().toString()).asText()).isEqualTo("普通");
        assertThat(preview.objects().get(0).after().properties()
                .get(level.attributeId().toString()).asText()).isEqualTo("VIP");
    }

    @Test
    void expectedVersionMismatchReturnsConflict() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec level = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C001", "张三", Map.of());
        insertObjectEdit(fixture, "C001", "BASE", "ACTIVE", null,
                Map.of(level.attributeId(), "VIP"));

        EditSetPreview preview = service.preview(new EditSetRequest(
                UUID.randomUUID(), fixture.ownerId(), null, UUID.randomUUID(), UUID.randomUUID(),
                "hash", mapper.createObjectNode(), mapper.createObjectNode(), "idem",
                new EditSetRequest.Expectations(
                        List.of(new EditSetRequest.ObjectExpectation(
                                fixture.objectTypeId(), "C001", 99)),
                        List.of()
                ),
                List.of(), List.of(new EditSetRequest.ObjectUpdate(
                        fixture.objectTypeId(), "C001", Map.of(), List.of()
                )), List.of(), List.of(), List.of()
        ));

        assertThat(preview.applicable()).isFalse();
        assertThat(preview.conflicts()).extracting(EditSetPreview.Conflict::code)
                .contains("EDIT_CONFLICT");
    }

    @Test
    void staleOverlayIsIgnoredInBeforeValue() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec level = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C001", "张三",
                Map.of(level.attributeId(), "普通"));
        insertObjectEdit(fixture, "C001", "BASE", "ACTIVE", null,
                Map.of(level.attributeId(), "VIP"));
        insertValueEdit(fixture, "C001", level.attributeId(), "VIP");
        markObjectStale(fixture, "C001", "mapping changed");

        EditSetPreview preview = service.preview(request(fixture.ownerId(), update(
                fixture.objectTypeId(), "C001", Map.of(), List.of()
        )));

        assertThat(preview.objects().get(0).before().properties()
                .get(level.attributeId().toString()).asText()).isEqualTo("普通");
    }

    @Test
    void deleteThenCreateIsAllowedForTombstone() {
        ObjectFixture fixture = customerFixture();
        insertObject(fixture.versionId(), fixture, "C001", "张三", Map.of());
        insertObjectEdit(fixture, "C001", "BASE", "DELETED", null, Map.of());

        EditSetPreview preview = service.preview(new EditSetRequest(
                UUID.randomUUID(), fixture.ownerId(), null, UUID.randomUUID(), UUID.randomUUID(),
                "hash", mapper.createObjectNode(), mapper.createObjectNode(), "idem",
                new EditSetRequest.Expectations(List.of(), List.of()),
                List.of(new EditSetRequest.ObjectCreate(
                        fixture.objectTypeId(), "C001",
                        Map.of(fixture.attributes().get(1).attributeId(), mapper.valueToTree("新名字"))
                )), List.of(), List.of(), List.of(), List.of()
        ));

        assertThat(preview.applicable()).isTrue();
        assertThat(preview.objects().get(0).kind()).isEqualTo("OBJECT_CREATE");
    }

    private EditSetRequest request(UUID ownerId, EditSetRequest.ObjectUpdate update) {
        return new EditSetRequest(
                UUID.randomUUID(), ownerId, null, UUID.randomUUID(), UUID.randomUUID(),
                "hash", mapper.createObjectNode(), mapper.createObjectNode(), "idem-" + UUID.randomUUID(),
                new EditSetRequest.Expectations(List.of(), List.of()),
                List.of(), List.of(update), List.of(), List.of(), List.of()
        );
    }

    private EditSetRequest.ObjectUpdate update(
            UUID objectTypeId,
            String primaryKey,
            Map<UUID, JsonNode> set,
            List<UUID> clear
    ) {
        return new EditSetRequest.ObjectUpdate(objectTypeId, primaryKey, set, clear);
    }

    private ObjectFixture customerFixture() {
        return createObjectFixture(UUID.randomUUID(), List.of(
                new AttributeSpec(UUID.randomUUID(), "id", "text"),
                new AttributeSpec(UUID.randomUUID(), "name", "text"),
                new AttributeSpec(UUID.randomUUID(), "level", "text")
        ));
    }
}
