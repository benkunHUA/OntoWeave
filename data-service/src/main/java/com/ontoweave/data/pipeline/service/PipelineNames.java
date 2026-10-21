package com.ontoweave.data.pipeline.service;

import static com.ontoweave.data.pipeline.service.PipelineErrors.validation;
import static com.ontoweave.data.pipeline.service.PipelineServiceConstants.MAX_NAME_LENGTH;

final class PipelineNames {

    static String require(String value) {
        if (value == null) {
            throw validation("流程名称不能为空");
        }
        String name = value.trim();
        if (name.isEmpty()) {
            throw validation("流程名称不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw validation("流程名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        return name;
    }

    private PipelineNames() {
    }
}
