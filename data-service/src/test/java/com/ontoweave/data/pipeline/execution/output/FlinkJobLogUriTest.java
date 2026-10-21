package com.ontoweave.data.pipeline.execution.output;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlinkJobLogUriTest {

    @Test
    void buildsAnAccessibleFlinkJobDiagnosticsUrl() {
        assertThat(FlinkJobLogUri.forJob("http://flink:8081/", "job-1"))
                .isEqualTo("http://flink:8081/#/job/job-1/exceptions");
        assertThat(FlinkJobLogUri.forJob("https://flink.example/base", "job-2"))
                .isEqualTo("https://flink.example/base/#/job/job-2/exceptions");
    }

    @Test
    void returnsNullWhenNoRealEndpointCanBeDerived() {
        assertThat(FlinkJobLogUri.forJob(null, "job-1")).isNull();
        assertThat(FlinkJobLogUri.forJob("not a url", "job-1")).isNull();
        assertThat(FlinkJobLogUri.forJob("ftp://flink", "job-1")).isNull();
        assertThat(FlinkJobLogUri.forJob("http://flink", null)).isNull();
    }
}
