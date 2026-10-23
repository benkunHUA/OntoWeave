package com.ontoweave.data.ontology.query;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectDetailResponse;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectPageResponse;
import com.ontoweave.data.ontology.query.filter.FilterCondition;
import com.ontoweave.data.ontology.query.service.ObjectQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OntologyObjectQuerySqlTest extends ObjectQueryFixtureSupport {

    @Autowired
    private ObjectQueryService service;

    @Test
    void sortsNumericPrimaryKeysNumericallyWithStablePagination() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture fixture = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        insertObject(fixture.versionId(), fixture, "100", "hundred", Map.of());
        insertObject(fixture.versionId(), fixture, "2", "two", Map.of());
        insertObject(fixture.versionId(), fixture, "10", "ten", Map.of());
        insertObject(fixture.versionId(), fixture, "11", "eleven", Map.of());

        OntologyObjectPageResponse first = service.list(
                fixture.objectTypeId(), ownerId, 1, 2, null, List.of()
        );
        OntologyObjectPageResponse second = service.list(
                fixture.objectTypeId(), ownerId, 2, 2, null, List.of()
        );

        assertThat(first.total()).isEqualTo(4);
        assertThat(first.items()).extracting(item -> item.primaryKey())
                .containsExactly("2", "10");
        assertThat(second.items()).extracting(item -> item.primaryKey())
                .containsExactly("11", "100");
        assertThat(first.indexVersionId()).isEqualTo(fixture.versionId());
        assertThat(first.items()).allMatch(item -> item.indexVersionId().equals(fixture.versionId()));
    }

    @Test
    void searchesTitleAndPrimaryKeyAndCombinesFiltersWithAnd() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture fixture = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text"),
                attribute("status", "text"),
                attribute("amount", "integer")
        ));
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec status = fixture.attributes().get(2);
        AttributeSpec amount = fixture.attributes().get(3);
        insertObject(fixture.versionId(), fixture, "1", "alpha one",
                Map.of(name.attributeId(), "alpha one", status.attributeId(), "active",
                        amount.attributeId(), 10));
        insertObject(fixture.versionId(), fixture, "2", "alpha two",
                Map.of(name.attributeId(), "alpha two", status.attributeId(), "active",
                        amount.attributeId(), 20));
        insertObject(fixture.versionId(), fixture, "3", "alpha three",
                Map.of(name.attributeId(), "alpha three", status.attributeId(), "closed",
                        amount.attributeId(), 30));
        insertObject(fixture.versionId(), fixture, "4", "beta", Map.of(
                name.attributeId(), "beta", status.attributeId(), "active",
                amount.attributeId(), 40
        ));
        OntologyObjectPageResponse result = service.list(
                fixture.objectTypeId(), ownerId, 1, 20, "alpha",
                List.of(
                        condition(name, "contains", "\"alpha\""),
                        condition(status, "eq", "\"active\""),
                        condition(amount, "gte", "10")
                )
        );

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).extracting(item -> item.primaryKey())
                .containsExactly("1", "2");

        OntologyObjectPageResponse byPrimaryKey = service.list(
                fixture.objectTypeId(), ownerId, 1, 20, "3", List.of()
        );
        assertThat(byPrimaryKey.items()).extracting(item -> item.primaryKey())
                .containsExactly("3");
    }

    @Test
    void nullFilterTreatsMissingDerivedValueAsNull() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture fixture = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text"),
                attribute("amount", "integer")
        ));
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec amount = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "1", "has amount",
                Map.of(name.attributeId(), "has amount", amount.attributeId(), 5));
        insertObject(fixture.versionId(), fixture, "2", "no amount",
                Map.of(name.attributeId(), "no amount"));

        OntologyObjectPageResponse result = service.list(
                fixture.objectTypeId(), ownerId, 1, 20, null,
                List.of(condition(amount, "is_null", null))
        );

        assertThat(result.items()).extracting(item -> item.primaryKey())
                .containsExactly("2");
    }

    @Test
    void detailUsesFrozenMappingOrderAndCurrentVersionOnly() {
        UUID ownerId = UUID.randomUUID();
        List<AttributeSpec> attributes = List.of(
                attribute("id", "integer"),
                attribute("name", "text"),
                attribute("status", "text"),
                attribute("amount", "integer")
        );
        ObjectFixture fixture = createObjectFixture(ownerId, attributes);
        insertObject(fixture.versionId(), fixture, "7", "old title", Map.of(
                attributes.get(1).attributeId(), "old title",
                attributes.get(2).attributeId(), "old",
                attributes.get(3).attributeId(), 1
        ));
        UUID currentVersion = addObjectVersion(fixture, attributes);
        insertObject(currentVersion, fixture, "7", "new title", Map.of(
                attributes.get(1).attributeId(), "new title",
                attributes.get(2).attributeId(), "active",
                attributes.get(3).attributeId(), 42
        ));

        OntologyObjectDetailResponse detail = service.detail(
                fixture.objectTypeId(), ownerId, "7"
        );

        assertThat(detail.title()).isEqualTo("new title");
        assertThat(detail.indexVersionId()).isEqualTo(currentVersion);
        assertThat(detail.properties()).extracting(property -> property.attributeId())
                .containsExactlyElementsOf(attributes.stream().map(AttributeSpec::attributeId).toList());
        assertThat(detail.properties().get(2).value().asText()).isEqualTo("active");
        assertThat(detail.properties().get(3).value().asInt()).isEqualTo(42);
    }

    @Test
    void emptyOrUnpointedBindingReturnsEmptyPageInsteadOfFailure() {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();

        OntologyObjectPageResponse noBinding = service.list(
                objectTypeId, ownerId, 1, 20, null, List.of()
        );
        assertThat(noBinding.items()).isEmpty();
        assertThat(noBinding.total()).isZero();
        assertThat(noBinding.indexVersionId()).isNull();

        ObjectFixture fixture = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        insertObject(fixture.versionId(), fixture, "1", "one", Map.of());
        jdbc.update("""
                UPDATE data.ontology_object_binding
                SET current_index_version_id = NULL
                WHERE id = ?
                """, fixture.bindingId());

        OntologyObjectPageResponse unpointed = service.list(
                fixture.objectTypeId(), ownerId, 1, 20, null, List.of()
        );
        assertThat(unpointed.items()).isEmpty();
        assertThat(unpointed.indexVersionId()).isNull();
        assertThatThrownBy(() -> service.detail(fixture.objectTypeId(), ownerId, "1"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    private AttributeSpec attribute(String column, String datatype) {
        return new AttributeSpec(UUID.randomUUID(), column, datatype);
    }

    private FilterCondition condition(AttributeSpec attribute, String op, String jsonValue) {
        return new FilterCondition(
                attribute.attributeId().toString(),
                op,
                jsonValue == null ? null : listFilters(jsonValue)
        );
    }
}
