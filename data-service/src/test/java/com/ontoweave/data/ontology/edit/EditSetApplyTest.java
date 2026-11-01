package com.ontoweave.data.ontology.edit;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.ontology.edit.model.EditSetPreview;
import com.ontoweave.data.ontology.edit.model.EditSetRequest;
import com.ontoweave.data.ontology.edit.model.EditSetResult;
import com.ontoweave.data.ontology.edit.service.EditSetApplyService;
import com.ontoweave.data.ontology.edit.service.EditSetPreviewService;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectDetailResponse;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectPageResponse;
import com.ontoweave.data.ontology.query.service.ObjectQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EditSetApplyTest extends EditSetFixtureSupport {

    @Autowired
    private EditSetApplyService applyService;

    @Autowired
    private EditSetPreviewService previewService;

    @Autowired
    private ObjectQueryService objectQueryService;

    @Test
    void modifyAppliesAndIsIdempotent() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec level = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C001", "张三",
                Map.of(name.attributeId(), "张三", level.attributeId(), "普通"));
        EditSetRequest request = editSetRequest(
                fixture.ownerId(), "idem-modify", UUID.randomUUID(),
                List.of(), List.of(editSetUpdate(fixture.objectTypeId(), "C001",
                        Map.of(level.attributeId(), textValue("VIP")), List.of())),
                List.of(), List.of(), List.of()
        );

        EditSetResult first = applyService.apply(request);
        EditSetResult second = applyService.apply(request);

        assertThat(first.objects()).singleElement().satisfies(result -> {
            assertThat(result.exists()).isTrue();
            assertThat(result.title()).isEqualTo("张三");
            assertThat(result.properties().get(level.attributeId().toString()).asText())
                    .isEqualTo("VIP");
            assertThat(result.version()).isEqualTo(1);
        });
        assertThat(second.executionId()).isEqualTo(request.executionId());
        assertThat(value(objectQueryService.detail(
                fixture.objectTypeId(), fixture.ownerId(), "C001"), level)).isEqualTo("VIP");
        assertThat(countRows("SELECT count(*) FROM data.ontology_edit_execution WHERE owner_id = ?",
                fixture.ownerId())).isEqualTo(1);
        assertThat(countRows("""
                SELECT version FROM data.ontology_object_edit
                WHERE owner_id = ? AND object_type_id = ? AND primary_key_value = 'C001'
                """, fixture.ownerId(), fixture.objectTypeId())).isEqualTo(1);
    }

    @Test
    void createDefaultsTitleAndIgnoresBaseValues() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec level = fixture.attributes().get(2);
        EditSetRequest request = editSetRequest(
                fixture.ownerId(), "idem-create", UUID.randomUUID(),
                List.of(new EditSetRequest.ObjectCreate(
                        fixture.objectTypeId(), "C002", Map.of(level.attributeId(), textValue("VIP")))),
                List.of(), List.of(), List.of(), List.of()
        );

        EditSetResult result = applyService.apply(request);

        assertThat(result.objects()).singleElement().satisfies(created -> {
            assertThat(created.exists()).isTrue();
            assertThat(created.title()).isEqualTo("C002");
            assertThat(created.properties().get(name.attributeId().toString()).asText())
                    .isEqualTo("C002");
        });
        assertThat(countRows("""
                SELECT count(*) FROM data.ontology_object_instance
                WHERE object_type_id = ? AND primary_key_value = 'C002'
                """, fixture.objectTypeId())).isZero();

        insertObject(fixture.versionId(), fixture, "C002", "数据源标题",
                Map.of(name.attributeId(), "数据源名称", level.attributeId(), "普通"));
        OntologyObjectDetailResponse detail = objectQueryService.detail(
                fixture.objectTypeId(), fixture.ownerId(), "C002");

        assertThat(detail.title()).isEqualTo("C002");
        assertThat(value(detail, level)).isEqualTo("VIP");
    }

    @Test
    void createPreviewMatchesApply() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec level = fixture.attributes().get(2);
        EditSetRequest request = editSetRequest(
                fixture.ownerId(), "idem-create-preview", UUID.randomUUID(),
                List.of(new EditSetRequest.ObjectCreate(
                        fixture.objectTypeId(), "C003", Map.of(level.attributeId(), textValue("VIP")))),
                List.of(), List.of(), List.of(), List.of()
        );

        EditSetPreview preview = previewService.preview(request);
        EditSetResult result = applyService.apply(request);

        assertThat(preview.applicable()).isTrue();
        assertThat(result.objects().get(0).title()).isEqualTo(preview.objects().get(0).after().title());
        assertThat(result.objects().get(0).properties())
                .isEqualTo(preview.objects().get(0).after().properties());
    }

    @Test
    void deleteThenCreateStartsFreshObject() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec level = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C001", "张三",
                Map.of(name.attributeId(), "张三", level.attributeId(), "普通"));
        applyService.apply(editSetRequest(
                fixture.ownerId(), "idem-update-before-delete", UUID.randomUUID(),
                List.of(), List.of(editSetUpdate(fixture.objectTypeId(), "C001",
                        Map.of(level.attributeId(), textValue("VIP")), List.of())),
                List.of(), List.of(), List.of()
        ));

        EditSetResult deleted = applyService.apply(editSetRequest(
                fixture.ownerId(), "idem-delete", UUID.randomUUID(),
                List.of(), List.of(),
                List.of(new EditSetRequest.ObjectDelete(fixture.objectTypeId(), "C001")),
                List.of(), List.of()
        ));
        OntologyObjectPageResponse afterDelete = objectQueryService.list(
                fixture.objectTypeId(), fixture.ownerId(), 1, 20, null, List.of());
        EditSetResult recreated = applyService.apply(editSetRequest(
                fixture.ownerId(), "idem-recreate", UUID.randomUUID(),
                List.of(new EditSetRequest.ObjectCreate(
                        fixture.objectTypeId(), "C001", Map.of(name.attributeId(), textValue("重建")))),
                List.of(), List.of(), List.of(), List.of()
        ));
        OntologyObjectDetailResponse detail = objectQueryService.detail(
                fixture.objectTypeId(), fixture.ownerId(), "C001");

        assertThat(deleted.objects()).singleElement()
                .satisfies(result -> assertThat(result.exists()).isFalse());
        assertThat(afterDelete.total()).isZero();
        assertThat(recreated.objects()).singleElement()
                .satisfies(result -> assertThat(result.exists()).isTrue());
        assertThat(detail.title()).isEqualTo("重建");
        assertThat(detail.properties())
                .filteredOn(property -> property.attributeId().equals(level.attributeId()))
                .singleElement()
                .satisfies(property -> assertThat(property.value().isNull()).isTrue());
        assertThat(jdbc.queryForObject("""
                SELECT origin || ':' || state
                FROM data.ontology_object_edit
                WHERE owner_id = ? AND object_type_id = ? AND primary_key_value = 'C001'
                """, String.class, fixture.ownerId(), fixture.objectTypeId()))
                .isEqualTo("USER:ACTIVE");
        assertThat(countRows("""
                SELECT count(*) FROM data.ontology_object_value_edit
                WHERE owner_id = ? AND object_type_id = ? AND primary_key_value = 'C001'
                  AND attribute_id = ?
                """, fixture.ownerId(), fixture.objectTypeId(), level.attributeId())).isZero();
    }

    @Test
    void conflictDoesNotWriteAnything() {
        ObjectFixture fixture = customerFixture();
        EditSetRequest request = editSetRequest(
                fixture.ownerId(), "idem-conflict", UUID.randomUUID(),
                List.of(), List.of(editSetUpdate(fixture.objectTypeId(), "C999",
                        Map.of(), List.of())),
                List.of(), List.of(), List.of()
        );

        assertThatThrownBy(() -> applyService.apply(request))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code())
                        .isEqualTo("EDIT_CONFLICT"));
        assertThat(countRows("SELECT count(*) FROM data.ontology_edit_execution")).isZero();
        assertThat(countRows("SELECT count(*) FROM data.ontology_edit_change")).isZero();
        assertThat(countRows("SELECT count(*) FROM data.ontology_object_edit")).isZero();
    }

    @Test
    void deleteAuditKeepsBeforeState() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        insertObject(fixture.versionId(), fixture, "C001", "张三",
                Map.of(name.attributeId(), "张三"));

        applyService.apply(editSetRequest(
                fixture.ownerId(), "idem-delete-audit", UUID.randomUUID(),
                List.of(), List.of(),
                List.of(new EditSetRequest.ObjectDelete(fixture.objectTypeId(), "C001")),
                List.of(), List.of()
        ));

        Map<String, Object> change = jdbc.queryForMap("""
                SELECT before_value::text AS before_value, after_value::text AS after_value
                FROM data.ontology_edit_change
                WHERE change_kind = 'OBJECT_DELETE'
                """);
        assertThat((String) change.get("before_value")).contains("张三");
        assertThat(change.get("after_value")).isNull();
    }

    private String value(OntologyObjectDetailResponse detail, AttributeSpec attribute) {
        return detail.properties().stream()
                .filter(property -> property.attributeId().equals(attribute.attributeId()))
                .findFirst()
                .map(property -> property.value().asText())
                .orElse(null);
    }

}
