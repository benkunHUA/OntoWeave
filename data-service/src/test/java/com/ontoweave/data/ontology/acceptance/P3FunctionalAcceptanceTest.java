package com.ontoweave.data.ontology.acceptance;

import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.iceberg.IcebergSnapshotReader;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectDetailResponse;
import com.ontoweave.data.ontology.query.api.dto.OntologyObjectPageResponse;
import com.ontoweave.data.ontology.query.api.dto.RelatedObjectPageResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationGroupResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationGroupsResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationInstancePageResponse;
import com.ontoweave.data.ontology.query.filter.FilterCondition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "p3.acceptance", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class P3FunctionalAcceptanceTest extends P3AcceptanceSupport {

    @MockitoBean
    private IcebergSnapshotReader snapshots;

    private P3FunctionalFixture fixture;

    @BeforeAll
    void generateIndexedObjectsAndRelations() {
        fixture = P3FunctionalFixture.build(this, snapshots, mapper);
        P3FunctionalEvidence.log(this, fixture, relationQueries);
    }

    @Test
    void generatesObjectsAndAtomicallySwitchesCurrentVersion() {
        OntologyIndexRunEntity failed = runs.findById(fixture.failedRunId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(currentObjectVersion(fixture.orderBinding().bindingId()))
                .isEqualTo(fixture.orderV2VersionId());
        assertThat(versionStatus(fixture.orderV1VersionId())).isEqualTo("RETIRED");
        assertThat(versionStatus(fixture.orderV2VersionId())).isEqualTo("ACTIVE");
        assertThat(objectRows(fixture.orderV1VersionId())).isEqualTo(20_000);
        assertThat(objectRows(fixture.orderV2VersionId())).isEqualTo(20_001);
    }

    @Test
    void listsPagesAndSearchesPrimaryKeyAndTitle() {
        OntologyObjectPageResponse first = list(1, 20, null);
        assertThat(first.total()).isEqualTo(20_001);
        assertThat(first.items()).hasSize(20);
        assertThat(first.items()).extracting(item -> item.primaryKey())
                .containsExactlyElementsOf(keys(1, 20));

        OntologyObjectPageResponse second = list(2, 20, null);
        assertThat(second.items()).extracting(item -> item.primaryKey())
                .containsExactlyElementsOf(keys(21, 40));

        OntologyObjectPageResponse byTitle = list(1, 20, "order 00042");
        assertThat(byTitle.total()).isEqualTo(1);
        assertThat(byTitle.items().get(0).primaryKey()).isEqualTo("42");

        OntologyObjectPageResponse byPrimaryKey = list(1, 20, "19999");
        assertThat(byPrimaryKey.total()).isEqualTo(1);
        assertThat(byPrimaryKey.items().get(0).title()).isEqualTo("Order 19999");
    }

    @Test
    void appliesRepresentativeFiltersAndThreeConditionAnd() {
        assertThat(filtered("eq", P3FunctionalModels.ORDER_NOTES, "note-123")).isEqualTo(1);
        assertThat(filtered("contains", P3FunctionalModels.ORDER_NOTES, "note-1234"))
                .isGreaterThan(0);
        assertThat(filtered("is_null", P3FunctionalModels.ORDER_NOTES, null)).isEqualTo(2_000);
        assertThat(filtered("is_not_null", P3FunctionalModels.ORDER_NOTES, null))
                .isEqualTo(18_001);
        assertThat(filtered("eq", P3FunctionalModels.ORDER_STATUS, "PAID"))
                .isEqualTo(expectedStatus("PAID"));
        assertThat(filtered("ne", P3FunctionalModels.ORDER_STATUS, "CANCELLED"))
                .isEqualTo(15_001);
        assertThat(filtered("gte", P3FunctionalModels.ORDER_AMOUNT, new BigDecimal("499.25")))
                .isEqualTo(40);
        assertThat(filtered("eq", P3FunctionalModels.ORDER_PRIORITY, true)).isEqualTo(6_667);

        LocalDate cutoff = LocalDate.of(2026, 12, 1);
        long expectedDates = java.util.stream.LongStream.rangeClosed(1, 20_001)
                .filter(id -> !LocalDate.of(2026, 1, 1).plusDays(id % 365).isBefore(cutoff))
                .count();
        assertThat(filtered("gte", P3FunctionalModels.ORDER_CREATED_ON, cutoff))
                .isEqualTo(expectedDates);

        long expectedThree = java.util.stream.LongStream.rangeClosed(1, 20_001)
                .filter(P3SnapshotData::matchesThreeConditionFilter)
                .count();
        long actualThree = objectQueries.list(
                fixture.orderTypeId(),
                fixture.ownerId(),
                1,
                20,
                null,
                List.of(
                        filter("eq", P3FunctionalModels.ORDER_STATUS, "PAID"),
                        filter("gte", P3FunctionalModels.ORDER_AMOUNT, new BigDecimal("250")),
                        filter("eq", P3FunctionalModels.ORDER_PRIORITY, true)
                )
        ).total();
        assertThat(actualThree).isEqualTo(expectedThree).isGreaterThan(0);
    }

    @Test
    void returnsObjectDetailInFrozenMappingOrder() {
        OntologyObjectDetailResponse detail = objectQueries.detail(
                fixture.orderTypeId(), fixture.ownerId(), "123");
        assertThat(detail.primaryKey()).isEqualTo("123");
        assertThat(detail.title()).isEqualTo("Order 00123");
        assertThat(detail.properties()).extracting(property -> property.attributeId())
                .containsExactly(
                        P3FunctionalModels.ORDER_ID,
                        P3FunctionalModels.ORDER_NO,
                        P3FunctionalModels.ORDER_STATUS,
                        P3FunctionalModels.ORDER_AMOUNT,
                        P3FunctionalModels.ORDER_CREATED_ON,
                        P3FunctionalModels.ORDER_PRIORITY,
                        P3FunctionalModels.ORDER_CUSTOMER_ID,
                        P3FunctionalModels.ORDER_NOTES
                );
        assertThat(detail.properties().get(2).value().asText()).isEqualTo("PAID");
        assertThat(detail.properties().get(3).value().decimalValue())
                .isEqualByComparingTo(new BigDecimal("123.25"));
        assertThat(detail.properties().get(7).value().asText()).isEqualTo("note-123");
    }

    @Test
    void queriesForeignKeyAndManyToManyRelationsInBothDirections() {
        RelationGroupsResponse orderGroups = relationQueries.groups(
                fixture.orderTypeId(), fixture.ownerId(), "1", "both");
        assertThat(group(orderGroups, fixture.fkRelationId(), "outgoing").total()).isEqualTo(1);
        assertThat(group(orderGroups, fixture.m2mRelationId(), "outgoing").total()).isEqualTo(2);

        RelatedObjectPageResponse customer = relationQueries.related(
                fixture.orderTypeId(), fixture.ownerId(), "1",
                fixture.fkRelationId(), "outgoing", 1, 20);
        assertThat(customer.total()).isEqualTo(1);
        assertThat(customer.items()).extracting(item -> item.primaryKey()).containsExactly("1");
        assertThat(customer.items().get(0).title()).isEqualTo("Customer 00001");

        RelatedObjectPageResponse customerOrders = relationQueries.related(
                fixture.customerTypeId(), fixture.ownerId(), "1",
                fixture.fkRelationId(), "incoming", 1, 20);
        assertThat(customerOrders.total()).isEqualTo(11);
        assertThat(customerOrders.items()).extracting(item -> item.primaryKey())
                .containsExactly("1", "2001", "4001", "6001", "8001",
                        "10001", "12001", "14001", "16001", "18001", "20001");

        RelationInstancePageResponse fkInstances = relationQueries.instances(
                fixture.fkRelationId(), fixture.ownerId(), null, null, 1, 20);
        assertThat(fkInstances.total()).isEqualTo(20_001);
        assertThat(count("""
                SELECT count(*) FROM data.ontology_relation_version
                WHERE relation_id = ? AND relation_kind = 'FK' AND status = 'ACTIVE'
                """, fixture.fkRelationId())).isEqualTo(1);

        RelatedObjectPageResponse products = relationQueries.related(
                fixture.orderTypeId(), fixture.ownerId(), "1",
                fixture.m2mRelationId(), "outgoing", 1, 20);
        assertThat(products.total()).isEqualTo(2);
        assertThat(products.items()).extracting(item -> item.primaryKey())
                .containsExactly("1", "1001");

        RelatedObjectPageResponse productOrders = relationQueries.related(
                fixture.productTypeId(), fixture.ownerId(), "1",
                fixture.m2mRelationId(), "incoming", 1, 20);
        assertThat(productOrders.total()).isEqualTo(10);
        assertThat(productOrders.items()).extracting(item -> item.primaryKey())
                .containsExactly("1", "1001", "2001", "3001", "4001",
                        "5001", "6001", "7001", "8001", "9001");

        RelationInstancePageResponse instances = relationQueries.instances(
                fixture.m2mRelationId(), fixture.ownerId(), null, null, 1, 20);
        assertThat(instances.total()).isEqualTo(20_000);
        RelationInstancePageResponse fromOrderOne = relationQueries.instances(
                fixture.m2mRelationId(), fixture.ownerId(), "1", null, 1, 20);
        assertThat(fromOrderOne.total()).isEqualTo(2);
    }

    @Test
    void failedRebuildKeepsPreviousVersionQueryableAndRecordsSamples() {
        OntologyIndexRunEntity failed = runs.findById(fixture.failedRunId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("STAGE_VALIDATION_FAILED");
        assertThat(failed.getErrorDetails().toString()).contains("duplicate");
        assertThat(count("""
                SELECT count(*) FROM data.ontology_object_version WHERE run_id = ?
                """, fixture.failedRunId())).isZero();
        assertThat(count("""
                SELECT count(*) FROM data.ontology_object_stage WHERE run_id = ?
                """, fixture.failedRunId())).isZero();
        assertThat(currentObjectVersion(fixture.orderBinding().bindingId()))
                .isEqualTo(fixture.orderV2VersionId());
        assertThat(list(1, 20, null).total()).isEqualTo(20_001);
        assertThat(objectQueries.detail(fixture.orderTypeId(), fixture.ownerId(), "1").title())
                .isEqualTo("Order 00001");
    }

    private String versionStatus(UUID versionId) {
        return jdbc.queryForObject(
                "SELECT status FROM data.ontology_object_version WHERE id = ?",
                String.class,
                versionId
        );
    }

    private OntologyObjectPageResponse list(int page, int pageSize, String q) {
        return objectQueries.list(
                fixture.orderTypeId(), fixture.ownerId(), page, pageSize, q, List.of());
    }

    private long filtered(String operation, UUID attributeId, Object value) {
        return objectQueries.list(
                fixture.orderTypeId(), fixture.ownerId(), 1, 20, null,
                List.of(filter(operation, attributeId, value))
        ).total();
    }

    private FilterCondition filter(String operation, UUID attributeId, Object value) {
        return new FilterCondition(attributeId.toString(), operation, mapper.valueToTree(value));
    }

    private long expectedStatus(String status) {
        return java.util.stream.LongStream.rangeClosed(1, 20_001)
                .filter(id -> status.equals(P3SnapshotData.orderStatus(id)))
                .count();
    }

    private RelationGroupResponse group(
            RelationGroupsResponse response,
            UUID relationId,
            String direction
    ) {
        return response.groups().stream()
                .filter(group -> relationId.equals(group.relationId()))
                .filter(group -> direction.equals(group.direction()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> keys(int from, int to) {
        List<String> values = new ArrayList<>();
        for (int value = from; value <= to; value++) {
            values.add(Integer.toString(value));
        }
        return values;
    }
}
