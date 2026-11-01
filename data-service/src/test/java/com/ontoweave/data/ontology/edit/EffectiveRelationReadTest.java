package com.ontoweave.data.ontology.edit;

import com.ontoweave.data.ontology.query.api.dto.RelatedObjectPageResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationInstancePageResponse;
import com.ontoweave.data.ontology.query.service.RelationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveRelationReadTest extends EditOverlayFixtureSupport {

    @Autowired
    private RelationQueryService service;

    @Test
    void relationCreateOverlayIsVisible() {
        RelationFixture fixture = relationFixture();
        insertRelationEdit(fixture.relation(), fixture.person(), fixture.order(),
                "1", "10", "ACTIVE");

        RelatedObjectPageResponse page = service.related(
                fixture.person().objectTypeId(), fixture.ownerId(), "1",
                fixture.relation().relationId(), "outgoing", 1, 20
        );
        RelationInstancePageResponse instances = service.instances(
                fixture.relation().relationId(), fixture.ownerId(), null, null, 1, 20
        );

        assertThat(page.items()).extracting(item -> item.primaryKey()).containsExactly("10");
        assertThat(instances.items()).hasSize(1);
        assertThat(instances.items().get(0).toPrimaryKey()).isEqualTo("10");
    }

    @Test
    void relationDeleteOverlayHidesBaseRelation() {
        RelationFixture fixture = relationFixture();
        insertInstance(fixture.relation().versionId(), fixture.relation().relationId(),
                fixture.person(), fixture.order(), "1", "10");
        insertRelationEdit(fixture.relation(), fixture.person(), fixture.order(),
                "1", "10", "DELETED");

        RelatedObjectPageResponse page = service.related(
                fixture.person().objectTypeId(), fixture.ownerId(), "1",
                fixture.relation().relationId(), "outgoing", 1, 20
        );

        assertThat(page.items()).isEmpty();
    }

    @Test
    void relationToDeletedObjectIsHidden() {
        RelationFixture fixture = relationFixture();
        insertInstance(fixture.relation().versionId(), fixture.relation().relationId(),
                fixture.person(), fixture.order(), "1", "10");
        insertObjectEdit(fixture.order(), "10", "BASE", "DELETED", null, Map.of());

        RelatedObjectPageResponse page = service.related(
                fixture.person().objectTypeId(), fixture.ownerId(), "1",
                fixture.relation().relationId(), "outgoing", 1, 20
        );

        assertThat(page.items()).isEmpty();
    }

    @Test
    void userCreatedPeerIsVisibleThroughOverlayRelation() {
        RelationFixture fixture = relationFixture();
        insertUserObject(fixture.order(), "99", "新订单", Map.of());
        insertRelationEdit(fixture.relation(), fixture.person(), fixture.order(),
                "1", "99", "ACTIVE");

        RelatedObjectPageResponse page = service.related(
                fixture.person().objectTypeId(), fixture.ownerId(), "1",
                fixture.relation().relationId(), "outgoing", 1, 20
        );

        assertThat(page.items()).extracting(item -> item.primaryKey()).containsExactly("99");
        assertThat(page.items().get(0).title()).isEqualTo("新订单");
    }

    @Test
    void fkRelationUsesEditedForeignKeyValueAndSkipsStaticInstances() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture customer = createObjectFixture(ownerId, List.of(
                new AttributeSpec(UUID.randomUUID(), "customer_id", "text"),
                new AttributeSpec(UUID.randomUUID(), "customer_name", "text")
        ));
        ObjectFixture order = createObjectFixture(ownerId, List.of(
                new AttributeSpec(UUID.randomUUID(), "order_id", "text"),
                new AttributeSpec(UUID.randomUUID(), "order_name", "text"),
                new AttributeSpec(UUID.randomUUID(), "customer_id_fk", "text")
        ));
        AttributeSpec fk = order.attributes().get(2);
        insertObject(customer.versionId(), customer, "C001", "Alice", Map.of());
        insertObject(customer.versionId(), customer, "C002", "Bob", Map.of());
        insertObject(order.versionId(), order, "O001", "Order 1",
                Map.of(fk.attributeId(), "C001"));
        UUID relationId = createRelationDefinition(
                ownerId, order, customer, "placed_by", "many_to_many"
        );
        jdbc.update("""
                UPDATE data.ontology_relation_definition
                SET cardinality = 'one_to_many', foreign_key_property_id = ?,
                    foreign_key_datatype = 'text'
                WHERE relation_id = ?
                """, fk.attributeId(), relationId);
        UUID relationVersion = addFkVersion(
                ownerId, relationId, order, customer, order.versionId(), customer.versionId()
        );
        insertInstance(relationVersion, relationId, order, customer, "O001", "C001");

        RelatedObjectPageResponse before = service.related(
                order.objectTypeId(), ownerId, "O001", relationId, "outgoing", 1, 20
        );
        jdbc.update("""
                INSERT INTO data.ontology_object_edit
                    (owner_id, object_type_id, primary_key_value, origin, state,
                     properties_patch, source_binding_id, source_dataset_id,
                     source_primary_key_attr_id, updated_by)
                VALUES (?, ?, 'O001', 'BASE', 'ACTIVE', CAST(? AS jsonb), ?, ?, ?, ?)
                """,
                ownerId, order.objectTypeId(),
                "{\"" + fk.attributeId() + "\":\"C002\"}",
                order.bindingId(), UUID.randomUUID(), order.primaryKeyAttributeId(),
                UUID.randomUUID()
        );
        insertValueEdit(order, "O001", fk.attributeId(), "C002");
        RelatedObjectPageResponse after = service.related(
                order.objectTypeId(), ownerId, "O001", relationId, "outgoing", 1, 20
        );

        assertThat(before.items()).extracting(item -> item.primaryKey()).containsExactly("C001");
        assertThat(after.items()).extracting(item -> item.primaryKey()).containsExactly("C002");
    }

    @Test
    void fkRelationUsesForeignKeyDatatypeForComparison() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture customer = createObjectFixture(ownerId, List.of(
                new AttributeSpec(UUID.randomUUID(), "customer_id", "text"),
                new AttributeSpec(UUID.randomUUID(), "customer_name", "text")
        ));
        ObjectFixture order = createObjectFixture(ownerId, List.of(
                new AttributeSpec(UUID.randomUUID(), "order_id", "text"),
                new AttributeSpec(UUID.randomUUID(), "order_name", "text"),
                new AttributeSpec(UUID.randomUUID(), "customer_id_fk", "integer")
        ));
        AttributeSpec fk = order.attributes().get(2);
        insertObject(customer.versionId(), customer, "1001", "Alice", Map.of());
        insertObject(order.versionId(), order, "O001", "Order 1",
                Map.of(fk.attributeId(), 1001));
        UUID relationId = createRelationDefinition(
                ownerId, order, customer, "placed_by", "many_to_many"
        );
        jdbc.update("""
                UPDATE data.ontology_relation_definition
                SET cardinality = 'one_to_many', foreign_key_property_id = ?,
                    foreign_key_datatype = 'integer'
                WHERE relation_id = ?
                """, fk.attributeId(), relationId);
        addFkVersion(ownerId, relationId, order, customer, order.versionId(), customer.versionId());

        RelatedObjectPageResponse related = service.related(
                order.objectTypeId(), ownerId, "O001", relationId, "outgoing", 1, 20
        );

        assertThat(related.items()).extracting(item -> item.primaryKey()).containsExactly("1001");
    }

    private RelationFixture relationFixture() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture person = createObjectFixture(ownerId, List.of(
                new AttributeSpec(UUID.randomUUID(), "person_id", "text"),
                new AttributeSpec(UUID.randomUUID(), "person_name", "text")
        ));
        ObjectFixture order = createObjectFixture(ownerId, List.of(
                new AttributeSpec(UUID.randomUUID(), "order_id", "text"),
                new AttributeSpec(UUID.randomUUID(), "order_name", "text")
        ));
        insertObject(person.versionId(), person, "1", "Alice", Map.of());
        insertObject(order.versionId(), order, "10", "Order 10", Map.of());
        UUID relationId = createRelationDefinition(ownerId, person, order, "orders", "many_to_many");
        M2mFixture relation = createM2mFixture(
                ownerId, relationId, person, order,
                person.versionId(), order.versionId(), true
        );
        return new RelationFixture(ownerId, person, order, relation);
    }

    private record RelationFixture(
            UUID ownerId,
            ObjectFixture person,
            ObjectFixture order,
            M2mFixture relation
    ) {
    }
}
