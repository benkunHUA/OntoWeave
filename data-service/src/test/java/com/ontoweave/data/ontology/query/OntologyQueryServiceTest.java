package com.ontoweave.data.ontology.query;

import com.ontoweave.data.ontology.query.api.dto.ObjectQueryRequest;
import com.ontoweave.data.ontology.query.api.dto.ObjectReference;
import com.ontoweave.data.ontology.query.api.dto.QueryPageResponse;
import com.ontoweave.data.ontology.query.api.dto.RelatedObjectsRequest;
import com.ontoweave.data.ontology.query.api.dto.RelatedObjectsResponse;
import com.ontoweave.data.ontology.query.api.dto.SortCondition;
import com.ontoweave.data.ontology.query.service.ObjectQueryService;
import com.ontoweave.data.ontology.query.service.RelationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OntologyQueryServiceTest extends RelationQueryFixtureSupport {

    @Autowired
    private ObjectQueryService objects;

    @Autowired
    private RelationQueryService relations;

    @Test
    void objectQuerySortsProjectsAndReportsTruncatedTotal() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture fixture = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text"),
                attribute("amount", "integer")
        ));
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec amount = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "1", "one",
                Map.of(name.attributeId(), "one", amount.attributeId(), 10));
        insertObject(fixture.versionId(), fixture, "2", "two",
                Map.of(name.attributeId(), "two", amount.attributeId(), 30));
        insertObject(fixture.versionId(), fixture, "3", "three",
                Map.of(name.attributeId(), "three", amount.attributeId(), 20));

        QueryPageResponse page = objects.query(
                fixture.objectTypeId(),
                new ObjectQueryRequest(
                        ownerId,
                        null,
                        List.of(),
                        List.of(),
                        List.of(new SortCondition(amount.attributeId().toString(), "desc")),
                        1,
                        List.of(name.attributeId())
                )
        );

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.truncated()).isTrue();
        assertThat(page.indexVersionId()).isEqualTo(fixture.versionId());
        assertThat(page.items()).extracting(item -> item.primaryKey()).containsExactly("2");
        assertThat(page.items().get(0).properties()).extracting(item -> item.attributeId())
                .containsExactly(name.attributeId());
        assertThat(page.items().get(0).properties().get(0).value().asText()).isEqualTo("two");
    }

    @Test
    void objectQueryReadsOnlyRequestedPrimaryKeys() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture fixture = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        AttributeSpec name = fixture.attributes().get(1);
        insertObject(fixture.versionId(), fixture, "1", "one", Map.of(name.attributeId(), "one"));
        insertObject(fixture.versionId(), fixture, "2", "two", Map.of(name.attributeId(), "two"));
        insertObject(fixture.versionId(), fixture, "3", "three", Map.of(name.attributeId(), "three"));

        QueryPageResponse page = objects.query(
                fixture.objectTypeId(),
                new ObjectQueryRequest(
                        ownerId,
                        null,
                        List.of("1", "3"),
                        List.of(),
                        List.of(),
                        20,
                        List.of(name.attributeId())
                )
        );

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(item -> item.primaryKey()).containsExactly("1", "3");
    }

    @Test
    void relatedBatchDeduplicatesAndReportsTruncation() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture customer = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        ObjectFixture order = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        insertObject(customer.versionId(), customer, "1", "Alice", Map.of());
        insertObject(customer.versionId(), customer, "2", "Bob", Map.of());
        insertObject(order.versionId(), order, "10", "small", Map.of());
        insertObject(order.versionId(), order, "20", "large", Map.of());

        UUID relationId = createRelationDefinition(
                ownerId,
                customer,
                order,
                "orders",
                "many_to_many"
        );
        M2mFixture relation = createM2mFixture(
                ownerId,
                relationId,
                customer,
                order,
                customer.versionId(),
                order.versionId(),
                true
        );
        insertInstance(relation.versionId(), relationId, customer, order, "1", "10");
        insertInstance(relation.versionId(), relationId, customer, order, "2", "10");
        insertInstance(relation.versionId(), relationId, customer, order, "2", "20");

        RelatedObjectsResponse page = relations.relatedBatch(
                relationId,
                new RelatedObjectsRequest(
                        ownerId,
                        "outgoing",
                        List.of(
                                new ObjectReference(customer.objectTypeId(), "1"),
                                new ObjectReference(customer.objectTypeId(), "2")
                        ),
                        1
                )
        );

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.truncated()).isTrue();
        assertThat(page.indexVersionId()).isEqualTo(relation.versionId());
        assertThat(page.items()).extracting(item -> item.primaryKey()).containsExactly("10");
    }

    private AttributeSpec attribute(String column, String datatype) {
        return new AttributeSpec(UUID.randomUUID(), column, datatype);
    }
}
