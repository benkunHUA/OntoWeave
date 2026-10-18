package com.ontoweave.data.connection.repository;

import com.ontoweave.data.connection.domain.DataConnectionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DataConnectionRepository extends JpaRepository<DataConnectionEntity, UUID> {

    Optional<DataConnectionEntity> findByConversationId(UUID conversationId);

    Optional<DataConnectionEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<DataConnectionEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    boolean existsByOwnerIdAndName(UUID ownerId, String name);
}
