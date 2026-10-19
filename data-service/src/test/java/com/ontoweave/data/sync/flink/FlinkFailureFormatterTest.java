package com.ontoweave.data.sync.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlinkFailureFormatterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void keepsRootCauseTaskAndStackForAgent() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "root-exception": "org.apache.flink.runtime.JobException: Recovery is suppressed\\n\\tat x",
                  "all-exceptions": [
                    {
                      "taskName": "Source: Flink CDC Event Source",
                      "location": "(unassigned)",
                      "exception": "java.util.concurrent.CompletionException: NoResourceAvailableException: Could not acquire resources\\n\\tat y\\npassword=secret"
                    }
                  ]
                }
                """);

        String summary = FlinkFailureFormatter.format(body);

        assertThat(summary)
                .contains("NoResourceAvailableException")
                .contains("失败任务：Source: Flink CDC Event Source（(unassigned)）")
                .contains("作业级异常：org.apache.flink.runtime.JobException")
                .contains("password=***")
                .doesNotContain("secret");
    }

    @Test
    void fallsBackWhenFlinkReturnsNoException() {
        assertThat(FlinkFailureFormatter.format(null)).contains("未返回异常详情");
    }
}
