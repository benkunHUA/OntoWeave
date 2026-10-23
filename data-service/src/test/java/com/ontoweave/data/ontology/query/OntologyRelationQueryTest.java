package com.ontoweave.data.ontology.query;

import com.ontoweave.data.ontology.query.api.dto.RelatedObjectPageResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationGroupResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationGroupsResponse;
import com.ontoweave.data.ontology.query.api.dto.RelationInstancePageResponse;
import com.ontoweave.data.ontology.query.service.RelationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OntologyRelationQueryTest extends RelationQueryFixtureSupport {

    @Autowired
    private RelationQueryService service;

    @Test
    void groupsOutgoingAndIncomingWithPreviewAndPeerTitles() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture person = objectType(ownerId, "person");
        ObjectFixture order = objectType(ownerId, "order");
        insertObject(person.versionId(), person, "1", "Alice", Map.of());
        insertObject(person.versionId(), person, "2", "Bob", Map.of());
        insertObject(order.versionId(), order, "1", "Order 1", Map.of());
        insertObject(order.versionId(), order, "2", "Order 2", Map.of());

        UUID owns = createRelationDefinition(ownerId, person, order, "owns", "one_to_many");
        UUID ownsVersion = addFkVersion(
                ownerId, owns, person, order, person.versionId(), order.versionId()
        );
        insertInstance(ownsVersion, owns, person, order, "1", "1");
        insertInstance(ownsVersion, owns, person, order, "1", "2");

        UUID placedBy = createRelationDefinition(ownerId, order, person, "placed_by", "one_to_many");
        UUID placedByVersion = addFkVersion(
                ownerId, placedBy, order, person, order.versionId(), person.versionId()
        );
        insertInstance(placedByVersion, placedBy, order, person, "1", "1");

        RelationGroupsResponse groups = service.groups(
                person.objectTypeId(), ownerId, "1", "both"
        );

        RelationGroupResponse outgoing = group(groups, owns, "outgoing");
        assertThat(outgoing.name()).isEqualTo("owns");
        assertThat(outgoing.total()).isEqualTo(2);
        assertThat(outgoing.relatedObjects()).extracting(item -> item.primaryKey())
                .containsExactly("1", "2");
        assertThat(outgoing.relatedObjects()).extracting(item -> item.title())
                .containsExactly("Order 1", "Order 2");

        RelationGroupResponse incoming = group(groups, placedBy, "incoming");
        assertThat(incoming.total()).isEqualTo(1);
        assertThat(incoming.relatedObjects()).extracting(item -> item.primaryKey())
                .containsExactly("1");

        RelatedObjectPageResponse outgoingPage = service.related(
                person.objectTypeId(), ownerId, "1", owns, "outgoing", 1, 1
        );
        RelatedObjectPageResponse incomingPage = service.related(
                person.objectTypeId(), ownerId, "1", placedBy, "incoming", 1, 20
        );
        assertThat(outgoingPage.total()).isEqualTo(2);
        assertThat(outgoingPage.items()).extracting(item -> item.primaryKey())
                .containsExactly("1");
        assertThat(outgoingPage.indexVersionId()).isEqualTo(ownsVersion);
        assertThat(incomingPage.items()).extracting(item -> item.primaryKey())
                .containsExactly("1");
    }

    @Test
    void missingPeerRowsAreNotExposed() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture person = objectType(ownerId, "person");
        ObjectFixture order = objectType(ownerId, "order");
        insertObject(person.versionId(), person, "1", "Alice", Map.of());
        insertObject(order.versionId(), order, "1", "Order 1", Map.of());
        UUID owns = createRelationDefinition(ownerId, person, order, "owns", "one_to_many");
        UUID versionId = addFkVersion(
                ownerId, owns, person, order, person.versionId(), order.versionId()
        );
        insertInstance(versionId, owns, person, order, "1", "1");
        insertInstance(versionId, owns, person, order, "1", "999");

        RelationGroupResponse group = group(
                service.groups(person.objectTypeId(), ownerId, "1", "outgoing"),
                owns,
                "outgoing"
        );
        RelatedObjectPageResponse page = service.related(
                person.objectTypeId(), ownerId, "1", owns, "outgoing", 1, 20
        );

        assertThat(group.total()).isEqualTo(1);
        assertThat(group.relatedObjects()).extracting(item -> item.primaryKey())
                .containsExactly("1");
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(item -> item.primaryKey())
                .containsExactly("1");
    }

    @Test
    void relatedRowsUsePeerCurrentVersionInsteadOfTheRelationVersionSnapshot() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture person = objectType(ownerId, "person");
        ObjectFixture order = objectType(ownerId, "order");
        insertObject(person.versionId(), person, "1", "Alice", Map.of());
        insertObject(order.versionId(), order, "1", "Order old", Map.of());
        UUID owns = createRelationDefinition(ownerId, person, order, "owns", "one_to_many");
        UUID relationVersion = addFkVersion(
                ownerId, owns, person, order, person.versionId(), order.versionId()
        );
        insertInstance(relationVersion, owns, person, order, "1", "1");

        UUID currentOrderVersion = addObjectVersion(order, order.attributes());
        RelatedObjectPageResponse missing = service.related(
                person.objectTypeId(), ownerId, "1", owns, "outgoing", 1, 20
        );
        RelationGroupResponse missingGroup = group(
                service.groups(person.objectTypeId(), ownerId, "1", "outgoing"),
                owns,
                "outgoing"
        );

        assertThat(missing.total()).isZero();
        assertThat(missing.items()).isEmpty();
        assertThat(missingGroup.total()).isZero();

        insertObject(currentOrderVersion, order, "1", "Order current", Map.of());
        RelatedObjectPageResponse current = service.related(
                person.objectTypeId(), ownerId, "1", owns, "outgoing", 1, 20
        );
        RelationGroupResponse currentGroup = group(
                service.groups(person.objectTypeId(), ownerId, "1", "outgoing"),
                owns,
                "outgoing"
        );

        assertThat(current.indexVersionId()).isEqualTo(relationVersion);
        assertThat(current.total()).isEqualTo(1);
        assertThat(current.items()).extracting(item -> item.title())
                .containsExactly("Order current");
        assertThat(currentGroup.total()).isEqualTo(1);
        assertThat(currentGroup.relatedObjects()).extracting(item -> item.title())
                .containsExactly("Order current");
    }

    @Test
    void manyToManyReadsOnlyTheBindingPointer() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture person = objectType(ownerId, "person");
        ObjectFixture order = objectType(ownerId, "order");
        insertObject(person.versionId(), person, "1", "Alice", Map.of());
        insertObject(order.versionId(), order, "1", "Order 1", Map.of());
        insertObject(order.versionId(), order, "2", "Order 2", Map.of());
        UUID relationId = createRelationDefinition(
                ownerId, person, order, "watches", "many_to_many"
        );
        M2mFixture m2m = createM2mFixture(
                ownerId,
                relationId,
                person,
                order,
                person.versionId(),
                order.versionId(),
                false
        );
        insertInstance(m2m.versionId(), relationId, person, order, "1", "1");

        RelationGroupsResponse noPointer = service.groups(
                person.objectTypeId(), ownerId, "1", "both"
        );
        assertThat(group(noPointer, relationId, "outgoing").total()).isZero();
        assertThat(service.related(
                person.objectTypeId(), ownerId, "1", relationId, "outgoing", 1, 20
        ).indexVersionId()).isNull();

        UUID newerVersion = insertRelationVersion(
                ownerId,
                relationId,
                "MANY_TO_MANY",
                person,
                order,
                person.versionId(),
                order.versionId(),
                "ACTIVE",
                m2m.bindingId()
        );
        insertInstance(newerVersion, relationId, person, order, "1", "2");
        pointM2mTo(m2m.bindingId(), m2m.versionId());

        RelatedObjectPageResponse current = service.related(
                person.objectTypeId(), ownerId, "1", relationId, "outgoing", 1, 20
        );
        assertThat(current.indexVersionId()).isEqualTo(m2m.versionId());
        assertThat(current.items()).extracting(item -> item.primaryKey())
                .containsExactly("1");
    }

    @Test
    void relationInstancesSortAndFilterOnBothPrimaryKeys() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture person = objectType(ownerId, "person");
        ObjectFixture order = objectType(ownerId, "order");
        insertObject(person.versionId(), person, "1", "Alice", Map.of());
        insertObject(order.versionId(), order, "1", "Order 1", Map.of());
        insertObject(order.versionId(), order, "2", "Order 2", Map.of());
        insertObject(order.versionId(), order, "3", "Order 3", Map.of());
        UUID owns = createRelationDefinition(ownerId, person, order, "owns", "one_to_many");
        UUID versionId = addFkVersion(
                ownerId, owns, person, order, person.versionId(), order.versionId()
        );
        insertInstance(versionId, owns, person, order, "1", "3");
        insertInstance(versionId, owns, person, order, "1", "1");
        insertInstance(versionId, owns, person, order, "1", "2");

        RelationInstancePageResponse first = service.instances(
                owns, ownerId, null, null, 1, 2
        );
        RelationInstancePageResponse filtered = service.instances(
                owns, ownerId, "1", "2", 1, 20
        );

        assertThat(first.total()).isEqualTo(3);
        assertThat(first.items()).extracting(item -> item.toPrimaryKey())
                .containsExactly("1", "2");
        assertThat(first.items()).allMatch(item -> item.indexVersionId().equals(versionId));
        assertThat(filtered.total()).isEqualTo(1);
        assertThat(filtered.items()).extracting(item -> item.toPrimaryKey())
                .containsExactly("2");
    }

    @Test
    void definitionsWithoutCurrentVersionReturnEmptyGroupsAndPages() {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture person = objectType(ownerId, "person");
        ObjectFixture order = objectType(ownerId, "order");
        UUID relationId = createRelationDefinition(
                ownerId, person, order, "never_generated", "many_to_many"
        );

        RelationGroupsResponse groups = service.groups(
                person.objectTypeId(), ownerId, "missing", "both"
        );
        RelatedObjectPageResponse related = service.related(
                person.objectTypeId(), ownerId, "missing", relationId, "outgoing", 1, 20
        );
        RelationInstancePageResponse instances = service.instances(
                relationId, ownerId, null, null, 1, 20
        );

        assertThat(groups.groups()).hasSize(1);
        assertThat(groups.groups().get(0).total()).isZero();
        assertThat(groups.groups().get(0).relatedObjects()).isEmpty();
        assertThat(related.items()).isEmpty();
        assertThat(related.indexVersionId()).isNull();
        assertThat(instances.items()).isEmpty();
    }

    private ObjectFixture objectType(UUID ownerId, String name) {
        return createObjectFixture(ownerId, List.of(
                new AttributeSpec(UUID.randomUUID(), name + "_id", "integer"),
                new AttributeSpec(UUID.randomUUID(), name + "_name", "text")
        ));
    }

    private RelationGroupResponse group(
            RelationGroupsResponse response,
            UUID relationId,
            String direction
    ) {
        return response.groups().stream()
                .filter(group -> group.relationId().equals(relationId))
                .filter(group -> group.direction().equals(direction))
                .findFirst()
                .orElseThrow();
    }
}
