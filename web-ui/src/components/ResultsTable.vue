<script setup lang="ts">
import type { RequestOutcomeStatus } from '../api/types'
import { clearResults, results } from '../store/results'
import RateLimitExplainer from './RateLimitExplainer.vue'

function statusClass(status: RequestOutcomeStatus): string {
  if (status === 'network-error' || status === 'aborted') return 'status-error'
  if (status === 429) return 'status-rate-limited'
  if (status >= 500) return 'status-error'
  if (status >= 400) return 'status-client-error'
  return 'status-ok'
}
</script>

<template>
  <section class="panel results">
    <div class="results-header">
      <h2>Live results ({{ results.length }})</h2>
      <button type="button" @click="clearResults">Clear</button>
    </div>
    <p v-if="results.length === 0" class="hint">No requests sent yet.</p>
    <div v-else class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Time</th>
            <th>Method</th>
            <th>Path</th>
            <th>Mode</th>
            <th>Status</th>
            <th>Duration</th>
            <th>Degraded</th>
            <th>Body</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="result in results" :key="result.id">
            <tr :class="statusClass(result.status)">
              <td>{{ new Date(result.timestamp).toLocaleTimeString() }}</td>
              <td>{{ result.method }}</td>
              <td>{{ result.path }}</td>
              <td>{{ result.mode ?? '—' }}</td>
              <td>{{ result.status }}</td>
              <td>{{ result.durationMs.toFixed(0) }} ms</td>
              <td>{{ result.degraded ? 'yes' : '—' }}</td>
              <td class="body-cell">{{ result.bodySummary || '—' }}</td>
            </tr>
            <tr v-if="result.status === 429" class="explainer-row">
              <td colspan="8">
                <RateLimitExplainer :code="result.problemCode" :retry-after="result.retryAfter" />
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </section>
</template>
