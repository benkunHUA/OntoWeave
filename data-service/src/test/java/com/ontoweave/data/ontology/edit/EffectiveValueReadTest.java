package com.ontoweave.data.ontology.edit;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.query.api.dto.ObjectQueryRequest;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectPageResponse;
import com.ontoweave.data.ontology.query.api.dto.QueryPageResponse;
import com.ontoweave.data.ontology.query.api.dto.SortCondition;
import com.ontoweave.data.ontology.query.filter.FilterCondition;
import com.ontoweave.data.ontology.query.service.ObjectQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveValueReadTest extends EditOverlayFixtureSupport {

    @Autowired
    private ObjectQueryService service;

    @Test
    void filterUsesEditedValue() {
        ObjectFixture fixture = textCustomerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec level = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C001", "张三",
                Map.of(name.attributeId(), "张三", level.attributeId(), "普通"));
        insertObject(fixture.versionId(), fixture, "C002", "李四",
                Map.of(name.attributeId(), "李四", level.attributeId(), "普通"));
        insertObjectEdit(fixture, "C001", "BASE", "ACTIVE", null,
                Map.of(level.attributeId(), "VIP"));
        insertValueEdit(fixture, "C001", level.attributeId(), "VIP");

        OntologyObjectPageResponse vip = service.list(
                fixture.objectTypeId(), fixture.ownerId(), 1, 20, null,
                List.of(condition(level, "eq", "\"VIP\""))
        );
        OntologyObjectPageResponse ordinary = service.list(
                fixture.objectTypeId(), fixture.ownerId(), 1, 20, null,
                List.of(condition(level, "eq", "\"普通\""))
        );

        assertThat(vip.items()).extracting(item -> item.primaryKey()).containsExactly("C001");
        assertThat(ordinary.items()).extracting(item -> item.primaryKey()).containsExactly("C002");
    }

    @Test
    void sortUsesEditedValue() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec amount = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C001", "A",
                Map.of(name.attributeId(), "A", amount.attributeId(), 10));
        insertObject(fixture.versionId(), fixture, "C002", "B",
                Map.of(name.attributeId(), "B", amount.attributeId(), 50));
        insertObjectEdit(fixture, "C001", "BASE", "ACTIVE", null,
                Map.of(amount.attributeId(), 100));
        insertValueEdit(fixture, "C001", amount.attributeId(), 100);

        QueryPageResponse page = service.query(
                fixture.objectTypeId(),
                new ObjectQueryRequest(
                        fixture.ownerId(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(new SortCondition(amount.attributeId().toString(), "desc")),
                        20,
                        List.of(name.attributeId())
                )
        );

        assertThat(page.items()).extracting(item -> item.primaryKey())
                .containsExactly("C001", "C002");
    }

    @Test
    void clearedValueSortsAsNull() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec amount = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C001", "A",
                Map.of(name.attributeId(), "A", amount.attributeId(), 10));
        insertObject(fixture.versionId(), fixture, "C002", "B",
                Map.of(name.attributeId(), "B", amount.attributeId(), 50));
        insertObjectEdit(fixture, "C001", "BASE", "ACTIVE", null, Map.of());
        insertValueEdit(fixture, "C001", amount.attributeId(), null);

        QueryPageResponse page = service.query(
                fixture.objectTypeId(),
                new ObjectQueryRequest(
                        fixture.ownerId(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(new SortCondition(amount.attributeId().toString(), "asc")),
                        20,
                        List.of(name.attributeId())
                )
        );

        assertThat(page.items()).extracting(item -> item.primaryKey())
                .containsExactly("C002", "C001");
    }

    @Test
    void totalAndPaginationUseEffectiveRows() {
        ObjectFixture fixture = customerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        insertObject(fixture.versionId(), fixture, "C001", "A",
                Map.of(name.attributeId(), "A"));
        insertObject(fixture.versionId(), fixture, "C002", "B",
                Map.of(name.attributeId(), "B"));
        insertUserObject(fixture, "C003", "C", Map.of(name.attributeId(), "C"));

        OntologyObjectPageResponse all = service.list(
                fixture.objectTypeId(), fixture.ownerId(), 1, 20, null, List.of()
        );
        OntologyObjectPageResponse secondPage = service.list(
                fixture.objectTypeId(), fixture.ownerId(), 2, 2, null, List.of()
        );

        assertThat(all.total()).isEqualTo(3);
        assertThat(secondPage.items()).extracting(item -> item.primaryKey())
                .containsExactly("C003");
    }

    @Test
    void userCreatedObjectIgnoresBaseValuesInFilters() {
        ObjectFixture fixture = textCustomerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec level = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C002", "数据源标题",
                Map.of(name.attributeId(), "数据源名称", level.attributeId(), "普通"));
        insertUserObject(fixture, "C002", "用户标题",
                Map.of(name.attributeId(), "用户名称", level.attributeId(), "VIP"));

        OntologyObjectPageResponse baseValue = service.list(
                fixture.objectTypeId(), fixture.ownerId(), 1, 20, null,
                List.of(condition(name, "eq", "\"数据源名称\""))
        );
        OntologyObjectPageResponse editedValue = service.list(
                fixture.objectTypeId(), fixture.ownerId(), 1, 20, null,
                List.of(condition(name, "eq", "\"用户名称\""))
        );

        assertThat(baseValue.items()).isEmpty();
        assertThat(editedValue.items()).extracting(item -> item.primaryKey())
                .containsExactly("C002");
    }

    private ObjectFixture customerFixture() {
        return createObjectFixture(UUID.randomUUID(), List.of(
                attribute("id", "text"),
                attribute("name", "text"),
                attribute("amount", "integer")
        ));
    }

    private ObjectFixture textCustomerFixture() {
        return createObjectFixture(UUID.randomUUID(), List.of(
                attribute("id", "text"),
                attribute("name", "text"),
                attribute("level", "text")
        ));
    }

    private AttributeSpec attribute(String column, String datatype) {
        return new AttributeSpec(UUID.randomUUID(), column, datatype);
    }

    private FilterCondition condition(AttributeSpec attribute, String op, String value) {
        return new FilterCondition(
                attribute.attributeId().toString(),
                op,
                value == null ? null : json(value)
        );
    }

    private JsonNode json(String value) {
        return listFilters(value);
    }
}
