package com.ontoweave.data.pipeline.api;

import com.ontoweave.data.pipeline.schedule.PipelineScheduleService;
import com.ontoweave.data.pipeline.schedule.ScheduleRequest;
import com.ontoweave.data.pipeline.schedule.ScheduleResponse;
import com.ontoweave.data.pipeline.schedule.ScheduleRunResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/v1/pipelines/{pipelineId}/schedule")
public class PipelineScheduleController {

    private final PipelineScheduleService schedules;

    public PipelineScheduleController(PipelineScheduleService schedules) {
        this.schedules = schedules;
    }

    @GetMapping
    public ScheduleResponse get(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId
    ) {
        return schedules.get(pipelineId, ownerId);
    }

    @PutMapping
    public ScheduleResponse put(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId,
            @Valid @RequestBody ScheduleRequest request
    ) {
        return schedules.put(pipelineId, ownerId, request);
    }

    @PostMapping("/pause")
    public ScheduleResponse pause(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId
    ) {
        return schedules.pause(pipelineId, ownerId);
    }

    @PostMapping("/resume")
    public ScheduleResponse resume(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId
    ) {
        return schedules.resume(pipelineId, ownerId);
    }

    @GetMapping("/runs")
    public List<ScheduleRunResponse> runs(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset
    ) {
        return schedules.runs(pipelineId, ownerId, limit, offset);
    }
}
