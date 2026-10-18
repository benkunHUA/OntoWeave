import { useState } from 'react'
import type { DataConnection } from '../../../../shared/types'
import { emptyConnectionForm, hasErrors, validateConnectionForm, type ConnectionFormValues } from '../../lib/validation'

interface ConnectionFormProps {
  connection: DataConnection
  isBusy: boolean
  onSave: (values: ConnectionFormValues, testAfter: boolean) => Promise<void>
}

function valuesFrom(connection: DataConnection): ConnectionFormValues {
  const base = emptyConnectionForm(connection.name)
  return {
    ...base,
    host: String(connection.config.host ?? ''),
    port: String(connection.config.port ?? base.port),
    database: String(connection.config.database ?? ''),
    username: String(connection.config.username ?? ''),
    useSsl: Boolean(connection.config.useSsl),
  }
}

export function ConnectionForm({ connection, isBusy, onSave }: ConnectionFormProps) {
  const [values, setValues] = useState(() => valuesFrom(connection))
  const [errors, setErrors] = useState<ReturnType<typeof validateConnectionForm>>({})

  function update<K extends keyof ConnectionFormValues>(key: K, value: ConnectionFormValues[K]) {
    setValues((current) => ({ ...current, [key]: value }))
    setErrors((current) => ({ ...current, [key]: undefined }))
  }

  async function submit(testAfter: boolean) {
    const nextErrors = validateConnectionForm(values, connection.secret_configured)
    setErrors(nextErrors)
    if (hasErrors(nextErrors)) return
    await onSave(values, testAfter)
  }

  const dirty =
    values.name !== connection.name ||
    values.host !== String(connection.config.host ?? '') ||
    values.port !== String(connection.config.port ?? 3306) ||
    values.database !== String(connection.config.database ?? '') ||
    values.username !== String(connection.config.username ?? '') ||
    values.useSsl !== Boolean(connection.config.useSsl) ||
    Boolean(values.password)

  return (
    <div className="connection-form">
      <div className="context-section">
        <h2>连接配置</h2>
        <p className="context-hint">密码只用于建立连接，不会写入对话或日志。</p>
      </div>

      {connection.last_test_status === 'failed' ? (
        <div className="inline-alert is-error">
          <strong>上次连接测试失败</strong>
          <span>{connection.last_test_error ?? '请检查主机、端口和账号信息。'}</span>
        </div>
      ) : null}

      <div className="form-grid">
        <label className="form-field form-field--wide">
          <span>连接名称</span>
          <input value={values.name} onChange={(event) => update('name', event.target.value)} />
          {errors.name ? <em>{errors.name}</em> : null}
        </label>
        <label className="form-field">
          <span>主机</span>
          <input value={values.host} onChange={(event) => update('host', event.target.value)} placeholder="127.0.0.1" />
          {errors.host ? <em>{errors.host}</em> : null}
        </label>
        <label className="form-field">
          <span>端口</span>
          <input value={values.port} onChange={(event) => update('port', event.target.value)} inputMode="numeric" />
          {errors.port ? <em>{errors.port}</em> : null}
        </label>
        <label className="form-field form-field--wide">
          <span>数据库</span>
          <input value={values.database} onChange={(event) => update('database', event.target.value)} placeholder="demo" />
          {errors.database ? <em>{errors.database}</em> : null}
        </label>
        <label className="form-field">
          <span>用户名</span>
          <input value={values.username} onChange={(event) => update('username', event.target.value)} />
          {errors.username ? <em>{errors.username}</em> : null}
        </label>
        <label className="form-field">
          <span>密码</span>
          <input
            type="password"
            value={values.password}
            onChange={(event) => update('password', event.target.value)}
            placeholder={connection.secret_configured ? '留空表示不修改' : '必填'}
          />
          {errors.password ? <em>{errors.password}</em> : null}
        </label>
        <label className="form-check form-field--wide">
          <input
            type="checkbox"
            checked={values.useSsl}
            onChange={(event) => update('useSsl', event.target.checked)}
          />
          <span>使用加密连接（目标 MySQL 需支持 SSL）</span>
        </label>
      </div>

      <div className="context-footer">
        {dirty || connection.status === 'draft' ? (
          <button type="button" className="secondary-button" disabled={isBusy} onClick={() => void submit(false)}>
            仅保存
          </button>
        ) : null}
        <button
          type="button"
          className="primary-button"
          disabled={isBusy}
          onClick={() => void submit(dirty || connection.status === 'draft')}
        >
          {isBusy ? '处理中…' : dirty || connection.status === 'draft' ? '保存并测试' : '测试连接'}
        </button>
      </div>
    </div>
  )
}
