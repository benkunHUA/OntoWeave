package com.ontoweave.data.pipeline.service;

import com.ontoweave.data.common.web.ApiException;
import org.springframework.http.HttpStatus;

import static com.ontoweave.data.pipeline.service.PipelineServiceConstants.CODE_BINDING_CONFLICT;
import static com.ontoweave.data.pipeline.service.PipelineServiceConstants.CODE_NOT_FOUND;
import static com.ontoweave.data.pipeline.service.PipelineServiceConstants.CODE_OUTPUT_INCOMPLETE;
import static com.ontoweave.data.pipeline.service.PipelineServiceConstants.CODE_VALIDATION_ERROR;

final class PipelineErrors {

    static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_VALIDATION_ERROR, message);
    }

    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, message);
    }

    static ApiException bindingConflict() {
        return new ApiException(HttpStatus.CONFLICT, CODE_BINDING_CONFLICT, "流程绑定冲突");
    }

    static ApiException outputIncomplete() {
        return new ApiException(
                HttpStatus.CONFLICT,
                CODE_OUTPUT_INCOMPLETE,
                "输出缺少数据集配置"
        );
    }

    private PipelineErrors() {
    }
}
