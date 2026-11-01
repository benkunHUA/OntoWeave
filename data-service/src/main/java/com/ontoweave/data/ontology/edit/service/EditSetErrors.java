package com.ontoweave.data.ontology.edit.service;

import com.ontoweave.data.common.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

final class EditSetErrors {

    private EditSetErrors() {
    }

    static ApiException conflict(String code, UUID subjectId, String primaryKey) {
        return new ApiException(
                HttpStatus.CONFLICT,
                code,
                "edit set 不可应用",
                Map.of(
                        "id", String.valueOf(subjectId),
                        "primaryKey", primaryKey == null ? "" : primaryKey
                )
        );
    }
}
