package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.execution.flink.PipelineExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

final class PreviewErrorMapper {

    private static final Logger log = LoggerFactory.getLogger(PreviewErrorMapper.class);
    private static final String KNOWN_EXECUTION_MESSAGE = "预览执行失败，请检查节点配置";

    private PreviewErrorMapper() {
    }

    static ApiException timeout() {
        return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PREVIEW_TIMEOUT", "预览执行超时");
    }

    static ApiException canceled() {
        return new ApiException(HttpStatus.CONFLICT, "PREVIEW_CANCELED", "预览已撤销");
    }

    static ApiException busy() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "PREVIEW_BUSY", "预览资源繁忙，请稍后重试");
    }

    static ApiException executionFailure(Throwable cause) {
        if (cause instanceof PipelineExecutionException failure) {
            Map<String, Object> details = new LinkedHashMap<>();
            put(details, "nodeId", failure.nodeId());
            put(details, "portId", failure.portId());
            put(details, "field", failure.field());
            return new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PREVIEW_EXECUTION_FAILED",
                    KNOWN_EXECUTION_MESSAGE,
                    details
            );
        }
        log.error("unexpected preview execution failure", cause);
        return new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "数据服务内部错误"
        );
    }

    private static void put(Map<String, Object> details, String key, String value) {
        if (value != null) {
            details.put(key, value);
        }
    }
}
