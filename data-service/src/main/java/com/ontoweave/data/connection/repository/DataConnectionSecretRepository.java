package com.ontoweave.data.connection.repository;

import com.ontoweave.data.connection.domain.DataConnectionSecretEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DataConnectionSecretRepository extends JpaRepository<DataConnectionSecretEntity, UUID> {
}
