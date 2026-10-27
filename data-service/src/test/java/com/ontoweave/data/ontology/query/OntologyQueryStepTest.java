package com.ontoweave.data.ontology.query;

import com.ontoweave.data.ontology.query.api.dto.ObjectQueryRequest;
import com.ontoweave.data.ontology.query.api.dto.ObjectReference;
import com.ontoweave.data.ontology.query.api.dto.QueryPageResponse;
import com.ontoweave.data.ontology.query.api.dto.QueryStepRequest;
import com.ontoweave.data.ontology.query.api.dto.SortCondition;
import com.ontoweave.data.ontology.query.filter.FilterCondition;
import com.ontoweave.data.ontology.query.service.ObjectQueryService;
import com.ontoweave.data.ontology.query.service.RelationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OntologyQueryStepTest extends RelationQueryFixtureSupport {

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
    void relationStepDeduplicatesFiltersSortsAndProjectsAgainstTargets() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture customer = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        ObjectFixture order = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text"),
                attribute("amount", "integer")
        ));
        AttributeSpec orderName = order.attributes().get(1);
        AttributeSpec orderAmount = order.attributes().get(2);
        insertObject(customer.versionId(), customer, "1", "Alice", Map.of());
        insertObject(customer.versionId(), customer, "2", "Bob", Map.of());
        insertObject(order.versionId(), order, "10", "small",
                Map.of(orderName.attributeId(), "small", orderAmount.attributeId(), 10));
        insertObject(order.versionId(), order, "20", "large",
                Map.of(orderName.attributeId(), "large", orderAmount.attributeId(), 30));

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
        insertInstance(relation.versionId(), relationId, customer, order, "1", "20");
        insertInstance(relation.versionId(), relationId, customer, order, "2", "20");

        QueryPageResponse page = relations.step(new QueryStepRequest(
                ownerId,
                relationId,
                "outgoing",
                List.of(
                        new ObjectReference(customer.objectTypeId(), "1"),
                        new ObjectReference(customer.objectTypeId(), "2")
                ),
                List.of(new FilterCondition(
                        orderAmount.attributeId().toString(),
                        "gte",
                        listFilters("20")
                )),
                List.of(new SortCondition(orderAmount.attributeId().toString(), "desc")),
                1,
                List.of(orderName.attributeId())
        ));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.truncated()).isFalse();
        assertThat(page.indexVersionId()).isEqualTo(order.versionId());
        assertThat(page.items()).extracting(item -> item.primaryKey()).containsExactly("20");
        assertThat(page.items().get(0).properties()).extracting(item -> item.attributeId())
                .containsExactly(orderName.attributeId());
        assertThat(page.items().get(0).properties().get(0).value().asText()).isEqualTo("large");
    }

    private AttributeSpec attribute(String column, String datatype) {
        return new AttributeSpec(UUID.randomUUID(), column, datatype);
    }
}
