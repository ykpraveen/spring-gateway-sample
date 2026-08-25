import { ref, watch } from 'vue'

// Session-only per PLAN.md ("API-key entry stored only in browser session storage").
const STORAGE_KEY = 'gateway-sample.apiKey'

export const apiKey = ref(sessionStorage.getItem(STORAGE_KEY) ?? '')

watch(apiKey, (value) => {
  if (value) sessionStorage.setItem(STORAGE_KEY, value)
  else sessionStorage.removeItem(STORAGE_KEY)
})
