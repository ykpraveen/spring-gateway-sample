/** Request bodies mirroring api-server's ProductRequest / PriceRequest wire contracts. */

export interface ProductBody {
  sku: string
  name: string
  description?: string
  active?: boolean
}

export interface PriceBody {
  productId: number
  amount: number
  currency: string
}

/** RFC 9457 Problem Details, plus the `code` extension member every service in this system adds. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  [key: string]: unknown
}

/** api-server's cache-fallback envelope for a degraded GET (see PLAN.md "Circuit Breakers and Cached Fallbacks"). */
export interface DegradedMeta {
  degraded: boolean
  source: string
  reason: string
  cachedAt: string
}

export type RequestOutcomeStatus = number | 'network-error' | 'aborted'

/** One row in the live traffic table. */
export interface RequestResult {
  id: number
  timestamp: string
  method: string
  path: string
  mode?: string
  status: RequestOutcomeStatus
  durationMs: number
  degraded: boolean
  problemCode?: string
  retryAfter?: string
  bodySummary: string
}
