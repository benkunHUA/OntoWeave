import { describe, expect, it } from 'vitest'
import type { PipelineBuild, PipelineBuildOutput } from '../../../shared/types'
import {
  buildElapsedLabel,
  buildStageLabel,
  buildStatusLabel,
  expectationSummary,
  outputRowCount,
} from './buildStatus'

describe('build status labels', () => {
  it('maps build stage to user-facing labels', () => {
    expect(buildStageLabel('checking_expectations')).toBe('检查数据期望')
    expect(buildStageLabel('publishing')).toBe('发布版本')
    expect(buildStageLabel(null)).toBe('等待执行')
  })

  it('formats terminal status and elapsed time', () => {
    const build: PipelineBuild = {
      id: 'build-1',
      pipeline_id: 'pipeline-1',
      pipeline_version_id: null,
      status: 'succeeded',
      stage: 'finished',
      trigger: 'manual',
      error_code: null,
      error_message: null,
      started_at: '2026-10-21T10:00:00Z',
      finished_at: '2026-10-21T10:00:02.500Z',
      outputs: [],
    }

    expect(buildStatusLabel('succeeded')).toBe('构建成功')
    expect(buildElapsedLabel(build)).toBe('2.5 秒')
  })
})

describe('build output summaries', () => {
  const output: PipelineBuildOutput = {
    id: 'row-1',
    output_id: 'output-1',
    dataset_id: 'dataset-1',
    dataset_version_id: 'version-1',
    output_type: 'dataset',
    status: 'succeeded',
    stage: 'finished',
    engine_job_id: null,
    error_code: null,
    error_message: null,
    result: {
      expectations: [
        { type: 'ROW_COUNT_RANGE', passed: true, failedRows: 0 },
        { type: 'NOT_NULL', passed: false, failedRows: 3 },
      ],
    },
    stats: { rowCount: 42, elapsedMs: 1200 },
    started_at: null,
    finished_at: null,
  }

  it('extracts row count and expectation failures', () => {
    expect(outputRowCount(output)).toBe(42)
    expect(expectationSummary(output)).toEqual({
      total: 2,
      passed: 1,
      failed: 1,
      failedRows: 3,
    })
  })
})
