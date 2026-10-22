import { Button } from '@mantine/core'
import { useState } from 'react'
import {
  CheckboxField,
  PasswordField,
  TextField,
} from '../../../../shared/components/form/controls'
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
        <TextField
          className="form-field form-field--wide"
          label="连接名称"
          value={values.name}
          error={errors.name}
          onChange={(event) => update('name', event.target.value)}
        />
        <TextField
          className="form-field"
          label="主机"
          value={values.host}
          error={errors.host}
          placeholder="127.0.0.1"
          onChange={(event) => update('host', event.target.value)}
        />
        <TextField
          className="form-field"
          label="端口"
          value={values.port}
          error={errors.port}
          inputMode="numeric"
          onChange={(event) => update('port', event.target.value)}
        />
        <TextField
          className="form-field form-field--wide"
          label="数据库"
          value={values.database}
          error={errors.database}
          placeholder="retail_sales"
          onChange={(event) => update('database', event.target.value)}
        />
        <TextField
          className="form-field"
          label="用户名"
          value={values.username}
          error={errors.username}
          onChange={(event) => update('username', event.target.value)}
        />
        <PasswordField
          className="form-field"
          label="密码"
          value={values.password}
          error={errors.password}
          placeholder={connection.secret_configured ? '留空表示不修改' : '必填'}
          onChange={(event) => update('password', event.target.value)}
        />
        <CheckboxField
          className="form-field--wide"
          label="使用加密连接（目标 MySQL 需支持 SSL）"
          checked={values.useSsl}
          onChange={(event) => update('useSsl', event.currentTarget.checked)}
        />
      </div>

      <div className="context-footer">
        {dirty || connection.status === 'draft' ? (
          <Button type="button" variant="default" disabled={isBusy} onClick={() => void submit(false)}>
            仅保存
          </Button>
        ) : null}
        <Button
          type="button"
          disabled={isBusy}
          loading={isBusy}
          onClick={() => void submit(dirty || connection.status === 'draft')}
        >
          {isBusy ? '处理中…' : dirty || connection.status === 'draft' ? '保存并测试' : '测试连接'}
        </Button>
      </div>
    </div>
  )
}
