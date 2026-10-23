package com.ontoweave.data.ontology.binding.support;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.OntologyErrorCode;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public final class BindingErrors {

    private BindingErrors() {
    }

    public static ApiException datasetNotFound(DatasetKind kind, UUID datasetId) {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                OntologyErrorCode.DATASET_NOT_FOUND,
                "数据集不存在、未发布或不属于当前用户",
                Map.of("datasetKind", kind == null ? "" : kind.name(), "datasetId", datasetId)
        );
    }

    public static ApiException bindingNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                OntologyErrorCode.DATASET_NOT_BOUND,
                "对象类型或关系尚未绑定数据集"
        );
    }

    public static ApiException bindingConflict(DatasetKind kind, UUID datasetId) {
        return new ApiException(
                HttpStatus.CONFLICT,
                OntologyErrorCode.BINDING_CONFLICT,
                "数据集已被其他绑定占用",
                Map.of("datasetKind", kind.name(), "datasetId", datasetId)
        );
    }

    public static ApiException revisionConflict(long currentRevision, MappingSnapshot draft) {
        return new ApiException(
                HttpStatus.CONFLICT,
                OntologyErrorCode.MAPPING_REVISION_CONFLICT,
                "映射版本已变化，请刷新后重试",
                Map.of("currentRevision", currentRevision, "draft", draft == null ? Map.of() : draft)
        );
    }

    public static ApiException mappingInvalid(MappingValidationResult validation) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                OntologyErrorCode.MAPPING_INVALID,
                "映射存在阻断问题",
                Map.of("validation", validation)
        );
    }

    public static ApiException invalidRelationDefinition(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RELATION_DEFINITION", message);
    }

    public static ApiException relationNotBindable() {
        return mappingInvalid(new MappingValidationResult(
                java.util.List.of(new MappingValidationResult.MappingIssue(
                        "RELATION_NOT_BINDABLE",
                        "外键关系不创建独立绑定，只能在对象映射中通过外键生成",
                        null,
                        null
                )),
                java.util.List.of()
        ));
    }

    public static ApiException ownerMismatch() {
        return new ApiException(
                HttpStatus.CONFLICT,
                OntologyErrorCode.BINDING_CONFLICT,
                "资源不属于当前用户"
        );
    }

    public static ApiException indexEnqueuerUnavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "INDEX_ENQUEUER_UNAVAILABLE",
                "索引入队服务不可用"
        );
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
