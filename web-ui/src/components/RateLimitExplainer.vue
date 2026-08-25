<script setup lang="ts">
defineProps<{ code?: string; retryAfter?: string }>()

const explanations: Record<string, string> = {
  ROUTE_LIMIT_EXCEEDED:
    "This route's shared capacity (across every client) is exhausted. It refills continuously at the route's configured rate, independently of who is calling it.",
  CLIENT_LIMIT_EXCEEDED:
    'Your authenticated user/API-client bucket is exhausted, independently of other clients calling this same route.',
  IP_LIMIT_EXCEEDED:
    'Your client IP bucket is exhausted, independently of which user or API client you authenticated as.',
}
</script>

<template>
  <p class="rate-limit-explainer">
    <strong>429 — {{ code ?? 'rate limited' }}.</strong>
    {{ explanations[code ?? ''] ?? 'A rate-limit bucket was exhausted.' }}
    <span v-if="retryAfter"
      >Buckets refill token-by-token continuously — retry after ~{{ retryAfter }}s and it should succeed.</span
    >
  </p>
</template>
