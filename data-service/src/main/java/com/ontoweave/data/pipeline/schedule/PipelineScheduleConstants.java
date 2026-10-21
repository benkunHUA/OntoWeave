package com.ontoweave.data.pipeline.schedule;

import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildConstants;

import java.util.List;

public final class PipelineScheduleConstants {

    public static final String REASON_NO_CHANGE = "NO_CHANGE";
    public static final String REASON_SCHEDULE_PAUSED = "SCHEDULE_PAUSED";
    public static final String REASON_PIPELINE_ARCHIVED = "PIPELINE_ARCHIVED";
    public static final String REASON_NOT_PUBLISHED = "SCHEDULE_NOT_PUBLISHED";
    public static final String MESSAGE_NO_CHANGE = "流程版本与输入未发生变化";
    public static final String MESSAGE_PAUSED = "调度已暂停";
    public static final String MESSAGE_PIPELINE_ARCHIVED = "流程已归档";
    public static final String MESSAGE_NOT_PUBLISHED = "流程没有已发布版本";
    public static final String MESSAGE_VERSION_NOT_FOUND = "当前发布版本不存在";
    public static final String ERROR_PIPELINE_NOT_FOUND = "流程不存在";
    public static final String ERROR_SCHEDULE_NOT_FOUND = "调度不存在";
    public static final String ERROR_DISPATCH_INCOMPLETE = "调度运行参数不完整";
    public static final String ERROR_CRON_REQUIRED = "cronExpression 和 timezone 不能为空";
    public static final String DEFAULT_NAME = "schedule";
    public static final int MAX_PAGE_SIZE = 100;
    public static final List<PipelineBuildStatus> TERMINAL_STATUSES = List.of(
            PipelineBuildStatus.SUCCEEDED,
            PipelineBuildStatus.FAILED,
            PipelineBuildStatus.CANCELED
    );
    public static final List<PipelineBuildStatus> ACTIVE_STATUSES =
            PipelineBuildConstants.ACTIVE_STATUSES;

    private PipelineScheduleConstants() {
    }
}
