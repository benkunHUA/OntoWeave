import type { JsonObject, PipelineBuildOutput } from '../../../../shared/types'
import { expectationSummary } from '../../lib/buildStatus'
import { cellText } from '../../lib/format'

function expectations(output: PipelineBuildOutput): JsonObject[] {
  const raw = output.result?.expectations
  if (!Array.isArray(raw)) return []
  return raw.filter((item): item is JsonObject => Boolean(item) && typeof item === 'object')
}

export function ExpectationResults({ output }: { output: PipelineBuildOutput }) {
  const rows = expectations(output)
  if (!rows.length) return null
  const summary = expectationSummary(output)
  return (
    <section className="pipeline-expectations">
      <header>
        <strong>数据期望</strong>
        <span>{summary.passed}/{summary.total} 通过</span>
      </header>
      {rows.map((item, index) => (
        <div key={`${cellText(item.type)}-${index}`} className={`pipeline-expectation ${item.passed === true ? 'is-ok' : 'is-failed'}`}>
          <div>
            <strong>{cellText(item.type)}</strong>
            <span>{item.passed === true ? '通过' : '失败'}</span>
          </div>
          <p>实际：{cellText(item.actual)} · 期望：{cellText(item.expected)}</p>
          {typeof item.failedRows === 'number' && item.failedRows > 0 ? (
            <small>失败行数：{item.failedRows.toLocaleString()}</small>
          ) : null}
          {typeof item.message === 'string' ? <small>{item.message}</small> : null}
        </div>
      ))}
    </section>
  )
}
