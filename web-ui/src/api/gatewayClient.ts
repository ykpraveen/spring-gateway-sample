import { getAccessToken } from '../auth/authClient'
import { gatewayBaseUrl } from '../config'
import type { EndpointDef } from './endpoints'
import type { RequestResult } from './types'

let nextResultId = 1

export interface SendOptions {
  idValue?: string
  mode?: string
  bodyText?: string
  apiKey: string
  signal?: AbortSignal
}

/** Sends one request through the gateway and captures everything the live results table shows. */
export async function sendRequest(endpoint: EndpointDef, options: SendOptions): Promise<RequestResult> {
  const path = endpoint.buildPath({ id: options.idValue ?? '', mode: options.mode ?? 'normal' })
  const url = new URL(path, gatewayBaseUrl)
  if (endpoint.supportsMode && options.mode && options.mode !== 'normal') {
    url.searchParams.set('mode', options.mode)
  }

  const startedAt = performance.now()
  const timestamp = new Date().toISOString()

  try {
    const accessToken = await getAccessToken()
    const headers: Record<string, string> = {
      Authorization: `Bearer ${accessToken}`,
      'X-API-Key': options.apiKey,
    }
    if (endpoint.requiresBody) headers['Content-Type'] = 'application/json'

    const response = await fetch(url, {
      method: endpoint.method,
      headers,
      body: endpoint.requiresBody ? options.bodyText : undefined,
      signal: options.signal,
    })
    const durationMs = performance.now() - startedAt

    const contentType = response.headers.get('content-type') ?? ''
    let parsedBody: unknown = null
    let bodySummary = ''
    if (contentType.includes('json')) {
      const text = await response.text()
      if (text) {
        parsedBody = JSON.parse(text)
        bodySummary = text.length > 500 ? `${text.slice(0, 500)}…` : text
      }
    }

    const bodyRecord = parsedBody as Record<string, unknown> | null
    const meta = bodyRecord?.meta as Record<string, unknown> | undefined

    return {
      id: nextResultId++,
      timestamp,
      method: endpoint.method,
      path: url.pathname + url.search,
      mode: endpoint.supportsMode ? (options.mode ?? 'normal') : undefined,
      status: response.status,
      durationMs,
      degraded: meta?.degraded === true,
      problemCode: response.ok ? undefined : (bodyRecord?.code as string | undefined),
      retryAfter: response.headers.get('retry-after') ?? undefined,
      bodySummary,
    }
  } catch (error) {
    const durationMs = performance.now() - startedAt
    const aborted = error instanceof DOMException && error.name === 'AbortError'
    return {
      id: nextResultId++,
      timestamp,
      method: endpoint.method,
      path: url.pathname + url.search,
      mode: endpoint.supportsMode ? (options.mode ?? 'normal') : undefined,
      status: aborted ? 'aborted' : 'network-error',
      durationMs,
      degraded: false,
      bodySummary: error instanceof Error ? error.message : String(error),
    }
  }
}
