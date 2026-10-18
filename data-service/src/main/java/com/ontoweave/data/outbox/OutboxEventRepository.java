package com.ontoweave.data.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

    Optional<OutboxEventEntity> findTopByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            String aggregateType,
            UUID aggregateId
    );
}
