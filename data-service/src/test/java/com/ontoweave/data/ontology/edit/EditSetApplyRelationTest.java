package com.ontoweave.data.ontology.edit;

import com.ontoweave.data.ontology.edit.model.EditSetRequest;
import com.ontoweave.data.ontology.edit.model.EditSetResult;
import com.ontoweave.data.ontology.edit.service.EditSetApplyService;
import com.ontoweave.data.ontology.query.RelationQueryFixtureSupport.M2mFixture;
import com.ontoweave.data.ontology.query.api.dto.RelationInstancePageResponse;
import com.ontoweave.data.ontology.query.service.RelationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EditSetApplyRelationTest extends EditSetFixtureSupport {

    @Autowired
    private EditSetApplyService applyService;

    @Autowired
    private RelationQueryService relationQueryService;

    @Test
    void relationCreateAndDeleteApply() {
        RelationFixture fixture = relationFixture();
        EditSetResult created = applyService.apply(editSetRequest(
                fixture.ownerId(), "idem-relation-create", UUID.randomUUID(),
                List.of(), List.of(), List.of(),
                List.of(new EditSetRequest.RelationCreate(
                        fixture.relation().relationId(), "1", "10")),
                List.of()
        ));
        RelationInstancePageResponse visible = relationQueryService.instances(
                fixture.relation().relationId(), fixture.ownerId(), null, null, 1, 20);
        applyService.apply(editSetRequest(
                fixture.ownerId(), "idem-relation-delete", UUID.randomUUID(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new EditSetRequest.RelationDelete(
                        fixture.relation().relationId(), "1", "10"))
        ));
        RelationInstancePageResponse hidden = relationQueryService.instances(
                fixture.relation().relationId(), fixture.ownerId(), null, null, 1, 20);

        assertThat(created.relations()).singleElement()
                .satisfies(result -> assertThat(result.exists()).isTrue());
        assertThat(visible.items()).hasSize(1);
        assertThat(hidden.items()).isEmpty();
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
        UUID relationId = createRelationDefinition(
                ownerId, person, order, "orders", "many_to_many");
        M2mFixture relation = createM2mFixture(
                ownerId, relationId, person, order,
                person.versionId(), order.versionId(), true);
        return new RelationFixture(ownerId, relation);
    }

    private record RelationFixture(UUID ownerId, M2mFixture relation) {
    }
}
