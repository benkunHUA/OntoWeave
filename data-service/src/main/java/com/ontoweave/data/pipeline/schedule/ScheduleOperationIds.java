package com.ontoweave.data.pipeline.schedule;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public final class ScheduleOperationIds {

    private ScheduleOperationIds() {
    }

    public static UUID forRun(UUID scheduleId, Instant scheduledFor) {
        String seed = "ontoweave:data-pipeline:schedule:" + scheduleId + ":"
                + scheduledFor.toEpochMilli();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
