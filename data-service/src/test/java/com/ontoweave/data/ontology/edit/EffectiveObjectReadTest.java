package com.ontoweave.data.ontology.edit;

import com.ontoweave.data.ontology.query.api.dto.OntologyObjectDetailResponse;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectPageResponse;
import com.ontoweave.data.ontology.query.service.ObjectQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveObjectReadTest extends EditOverlayFixtureSupport {

    @Autowired
    private ObjectQueryService service;

    @Test
    void baseObjectUsesEditedFieldAndKeepsDatasourceField() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec level = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C001", "张三",
                Map.of(name.attributeId(), "张三", level.attributeId(), "普通"));
        insertObjectEdit(fixture, "C001", "BASE", "ACTIVE", "张三先生",
                Map.of(level.attributeId(), "VIP"));
        insertValueEdit(fixture, "C001", level.attributeId(), "VIP");

        OntologyObjectDetailResponse detail = service.detail(
                fixture.objectTypeId(), fixture.ownerId(), "C001"
        );

        assertThat(detail.title()).isEqualTo("张三先生");
        assertThat(value(detail, name.attributeId())).isEqualTo("张三");
        assertThat(value(detail, level.attributeId())).isEqualTo("VIP");
    }

    @Test
    void deletedObjectIsInvisible() {
        ObjectFixture fixture = customerFixture();
        insertObject(fixture.versionId(), fixture, "C001", "张三", Map.of());
        insertObjectEdit(fixture, "C001", "BASE", "DELETED", null, Map.of());

        OntologyObjectPageResponse page = service.list(
                fixture.objectTypeId(), fixture.ownerId(), 1, 20, null, List.of()
        );

        assertThat(page.total()).isZero();
        assertThat(page.items()).isEmpty();
    }

    @Test
    void userCreatedObjectIsVisibleWithoutBaseRowAndIgnoresLaterBaseRow() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        insertUserObject(fixture, "C002", "李四", Map.of(
                name.attributeId(), "李四"
        ));
        insertObject(fixture.versionId(), fixture, "C002", "数据源标题",
                Map.of(name.attributeId(), "数据源名称"));

        OntologyObjectDetailResponse detail = service.detail(
                fixture.objectTypeId(), fixture.ownerId(), "C002"
        );

        assertThat(detail.title()).isEqualTo("李四");
        assertThat(value(detail, name.attributeId())).isEqualTo("李四");
    }

    @Test
    void staleEditIsIgnored() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec level = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C001", "张三",
                Map.of(level.attributeId(), "普通"));
        insertObjectEdit(fixture, "C001", "BASE", "ACTIVE", null,
                Map.of(level.attributeId(), "VIP"));
        insertValueEdit(fixture, "C001", level.attributeId(), "VIP");
        markObjectStale(fixture, "C001", "mapping changed");

        OntologyObjectDetailResponse detail = service.detail(
                fixture.objectTypeId(), fixture.ownerId(), "C001"
        );

        assertThat(value(detail, level.attributeId())).isEqualTo("普通");
    }

    @Test
    void clearedTitleFallsBackToPrimaryKey() {
        ObjectFixture fixture = customerFixture();
        insertObject(fixture.versionId(), fixture, "C001", "数据源标题", Map.of());
        insertObjectEdit(fixture, "C001", "BASE", "ACTIVE", "", Map.of());

        OntologyObjectDetailResponse detail = service.detail(
                fixture.objectTypeId(), fixture.ownerId(), "C001"
        );

        assertThat(detail.title()).isEqualTo("C001");
    }

    private ObjectFixture customerFixture() {
        return createObjectFixture(UUID.randomUUID(), List.of(
                attribute("id", "text"),
                attribute("name", "text"),
                attribute("level", "text")
        ));
    }

    private AttributeSpec attribute(String column, String datatype) {
        return new AttributeSpec(UUID.randomUUID(), column, datatype);
    }

    private String value(OntologyObjectDetailResponse detail, UUID attributeId) {
        return detail.properties().stream()
                .filter(property -> property.attributeId().equals(attributeId))
                .findFirst()
                .orElseThrow()
                .value()
                .asText();
    }
}
