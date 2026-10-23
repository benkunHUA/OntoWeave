package com.ontoweave.data.ontology.repository;

import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;

public interface OntologyIndexRunRepositoryCustom {

    OntologyIndexRunEntity upsertQueued(OntologyIndexRunEntity candidate);
}
