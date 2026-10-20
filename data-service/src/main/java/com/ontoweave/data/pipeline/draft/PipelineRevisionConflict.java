package com.ontoweave.data.pipeline.draft;

import com.ontoweave.data.common.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class PipelineRevisionConflict extends ApiException {

    public static final String CODE = "REVISION_CONFLICT";

    public PipelineRevisionConflict() {
        this("草稿版本已变化，请重新读取后再提交", Map.of());
    }

    public PipelineRevisionConflict(String message) {
        this(message, Map.of());
    }

    public PipelineRevisionConflict(String message, Map<String, Object> details) {
        super(HttpStatus.CONFLICT, CODE, message, details);
    }
}
