import { HttpError } from './client'

export interface HttpErrorInfo {
  message: string
  code: string | null
  details: Record<string, unknown>
  issues: unknown[]
}

function object(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function bodyOf(error: HttpError): Record<string, unknown> {
  try {
    return object(JSON.parse(error.body) as unknown)
  } catch {
    return {}
  }
}

function issuesOf(body: Record<string, unknown>, details: Record<string, unknown>): unknown[] {
  for (const value of [body.issues, details.issues, details.blockingIssues]) {
    if (Array.isArray(value)) return value
  }
  return []
}

export function httpErrorInfo(reason: unknown, fallback = '请求失败'): HttpErrorInfo {
  if (!(reason instanceof HttpError)) {
    return {
      message: reason instanceof Error && !reason.message.startsWith('{')
        ? reason.message
        : fallback,
      code: null,
      details: {},
      issues: [],
    }
  }
  const body = bodyOf(reason)
  const details = object(body.details)
  const message = typeof body.message === 'string' && body.message
    ? body.message
    : fallback
  return {
    message,
    code: typeof body.code === 'string' ? body.code : null,
    details,
    issues: issuesOf(body, details),
  }
}

export function httpErrorText(reason: unknown, fallback = '请求失败'): string {
  return httpErrorInfo(reason, fallback).message
}
