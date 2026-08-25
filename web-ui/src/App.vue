<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { handleRedirectCallback, isAuthenticated, login, logout, username } from './auth/authClient'
import ApiKeyPanel from './components/ApiKeyPanel.vue'
import RequestBuilder from './components/RequestBuilder.vue'
import ResultsTable from './components/ResultsTable.vue'
import { apiKey } from './store/apiKey'

const ready = ref(false)
const authError = ref<string | null>(null)

onMounted(async () => {
  try {
    await handleRedirectCallback()
  } catch (error) {
    authError.value = error instanceof Error ? error.message : String(error)
  } finally {
    ready.value = true
  }
})
</script>

<template>
  <div class="app">
    <header class="topbar">
      <h1>Gateway Traffic Simulator</h1>
      <div class="session">
        <template v-if="isAuthenticated">
          <span>Signed in as <strong>{{ username }}</strong></span>
          <button type="button" @click="logout">Sign out</button>
        </template>
        <button v-else-if="ready" type="button" @click="login">Sign in with Keycloak</button>
      </div>
    </header>

    <p v-if="authError" class="error-banner">{{ authError }}</p>

    <main v-if="isAuthenticated">
      <ApiKeyPanel />
      <template v-if="apiKey">
        <RequestBuilder />
        <ResultsTable />
      </template>
      <p v-else class="hint">Enter an API key above to start sending requests.</p>
    </main>
    <main v-else-if="ready">
      <p class="hint">Sign in through Keycloak to use the traffic simulator.</p>
    </main>
  </div>
</template>
