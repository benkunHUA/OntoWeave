import { DEFAULT_MYSQL_PORT, MAX_PORT, MIN_PORT } from './constants'

export interface ConnectionFormValues {
  name: string
  host: string
  port: string
  database: string
  username: string
  password: string
  useSsl: boolean
}

export type ConnectionFormErrors = Partial<Record<keyof ConnectionFormValues, string>>

export function emptyConnectionForm(name = ''): ConnectionFormValues {
  return {
    name,
    host: '',
    port: String(DEFAULT_MYSQL_PORT),
    database: '',
    username: '',
    password: '',
    useSsl: false,
  }
}

export function validateConnectionForm(
  values: ConnectionFormValues,
  hasStoredSecret: boolean,
): ConnectionFormErrors {
  const errors: ConnectionFormErrors = {}
  if (!values.name.trim()) errors.name = '请填写连接名称'
  if (!values.host.trim()) errors.host = '请填写主机'
  if (!values.database.trim()) errors.database = '请填写数据库名'
  if (!values.username.trim()) errors.username = '请填写用户名'
  if (!values.password && !hasStoredSecret) errors.password = '请填写密码'
  const port = Number(values.port)
  if (!Number.isInteger(port) || port < MIN_PORT || port > MAX_PORT) {
    errors.port = `端口需在 ${MIN_PORT}-${MAX_PORT} 之间`
  }
  return errors
}

export function hasErrors(errors: ConnectionFormErrors): boolean {
  return Object.keys(errors).length > 0
}
