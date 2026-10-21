package com.ontoweave.data.pipeline.execution.output;

import java.net.URI;

/** Builds the browser-accessible Flink job diagnostics entry from the configured REST base. */
public final class FlinkJobLogUri {

    private FlinkJobLogUri() {
    }

    public static String forJob(String restUrl, String jobId) {
        if (restUrl == null || restUrl.isBlank() || jobId == null || jobId.isBlank()) {
            return null;
        }
        String base = restUrl.trim().replaceAll("/+$", "");
        try {
            URI uri = URI.create(base);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                return null;
            }
        } catch (IllegalArgumentException error) {
            return null;
        }
        return base + "/#/job/" + jobId + "/exceptions";
    }
}
