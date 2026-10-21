package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.execution.flink.PipelineExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewErrorMapperTest {

    @Test
    void mapsKnownExecutionFailureWithoutLeakingInternalMessage() {
        ApiException error = PreviewErrorMapper.executionFailure(new PipelineExecutionException(
                "raw driver failure: secret=abc", "node-1", "in", "amount", null
        ));

        assertThat(error.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(error.code()).isEqualTo("PREVIEW_EXECUTION_FAILED");
        assertThat(error.getMessage()).doesNotContain("secret");
        assertThat(error.details())
                .containsEntry("nodeId", "node-1")
                .containsEntry("portId", "in")
                .containsEntry("field", "amount");
    }

    @Test
    void mapsUnknownFailureToInternalErrorWithoutMessagePassthrough() {
        ApiException error = PreviewErrorMapper.executionFailure(
                new IllegalStateException("jdbc password=secret")
        );

        assertThat(error.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.getMessage()).doesNotContain("password");
        assertThat(error.details()).doesNotContainKey("nodeId");
    }
}
