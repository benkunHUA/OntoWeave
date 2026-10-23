package com.ontoweave.data.ontology.binding.api;

import com.ontoweave.data.ontology.binding.support.BindingErrors;

import java.util.UUID;

final class BindingOwnerResolver {

    private BindingOwnerResolver() {
    }

    static UUID resolve(UUID queryOwnerId, UUID bodyOwnerId) {
        if (queryOwnerId != null && bodyOwnerId != null && !queryOwnerId.equals(bodyOwnerId)) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "ownerId 不一致");
        }
        UUID ownerId = queryOwnerId == null ? bodyOwnerId : queryOwnerId;
        if (ownerId == null) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "ownerId 不能为空");
        }
        return ownerId;
    }
}
