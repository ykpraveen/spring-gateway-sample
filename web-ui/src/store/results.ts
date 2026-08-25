import { reactive } from 'vue'
import type { RequestResult } from '../api/types'

// Bounds table growth during long unattended bursts; oldest rows are dropped, newest first.
const MAX_RESULTS = 300

export const results = reactive<RequestResult[]>([])

export function addResult(result: RequestResult): void {
  results.unshift(result)
  if (results.length > MAX_RESULTS) results.length = MAX_RESULTS
}

export function clearResults(): void {
  results.length = 0
}
