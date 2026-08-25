<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { sendRequest } from '../api/gatewayClient'
import { endpoints } from '../api/endpoints'
import type { BurstConfig } from '../simulator/engine'
import { runBurst } from '../simulator/engine'
import { apiKey } from '../store/apiKey'
import { addResult } from '../store/results'

const selectedEndpointId = ref(endpoints[0].id)
const selectedEndpoint = computed(() => endpoints.find((e) => e.id === selectedEndpointId.value) ?? endpoints[0])

const idValue = ref('1')
const mode = ref<'normal' | 'fail' | 'slow'>('normal')
const bodyText = ref('')
const bodyError = ref<string | null>(null)

const requestsPerSecond = ref(5)
const concurrency = ref(3)
const totalCount = ref(10)
const unlimited = ref(false)
const busy = ref(false)

let controller: AbortController | null = null

watch(
  selectedEndpoint,
  (endpoint) => {
    bodyText.value = endpoint.defaultBody ? JSON.stringify(endpoint.defaultBody, null, 2) : ''
    bodyError.value = null
  },
  { immediate: true },
)

function validateBody(): boolean {
  if (!selectedEndpoint.value.requiresBody) return true
  try {
    JSON.parse(bodyText.value)
    bodyError.value = null
    return true
  } catch {
    bodyError.value = 'Body is not valid JSON.'
    return false
  }
}

async function sendOne(): Promise<void> {
  const result = await sendRequest(selectedEndpoint.value, {
    idValue: idValue.value,
    mode: mode.value,
    bodyText: bodyText.value,
    apiKey: apiKey.value,
    signal: controller?.signal,
  })
  addResult(result)
}

async function sendSingle(): Promise<void> {
  if (busy.value || !validateBody()) return
  busy.value = true
  controller = new AbortController()
  try {
    await sendOne()
  } finally {
    busy.value = false
    controller = null
  }
}

async function startBurst(): Promise<void> {
  if (busy.value || !validateBody()) return
  busy.value = true
  controller = new AbortController()
  const config: BurstConfig = {
    totalCount: unlimited.value ? Number.POSITIVE_INFINITY : totalCount.value,
    requestsPerSecond: requestsPerSecond.value,
    concurrency: concurrency.value,
  }
  try {
    await runBurst(config, sendOne, controller.signal)
  } finally {
    busy.value = false
    controller = null
  }
}

function stop(): void {
  controller?.abort()
}
</script>

<template>
  <section class="panel">
    <div class="row">
      <label for="endpoint">Endpoint</label>
      <select id="endpoint" v-model="selectedEndpointId" :disabled="busy">
        <option v-for="endpoint in endpoints" :key="endpoint.id" :value="endpoint.id">{{ endpoint.label }}</option>
      </select>
    </div>

    <div v-if="selectedEndpoint.requiresId" class="row">
      <label for="resource-id">{{ selectedEndpoint.idLabel }}</label>
      <input id="resource-id" v-model="idValue" :disabled="busy" />
    </div>

    <div v-if="selectedEndpoint.supportsMode" class="row">
      <label for="mode">Downstream mode</label>
      <select id="mode" v-model="mode" :disabled="busy">
        <option value="normal">normal</option>
        <option value="fail">fail — forces a controlled 500</option>
        <option value="slow">slow — exceeds the api-server time limiter</option>
      </select>
    </div>

    <div v-if="selectedEndpoint.requiresBody" class="row body-row">
      <label for="body">Request body (JSON)</label>
      <textarea id="body" v-model="bodyText" rows="6" :disabled="busy"></textarea>
      <p v-if="bodyError" class="error-text">{{ bodyError }}</p>
    </div>

    <fieldset class="burst-config">
      <legend>Burst settings</legend>
      <div class="row">
        <label for="rate">Requests / second</label>
        <input id="rate" v-model.number="requestsPerSecond" type="number" min="0.1" step="0.5" :disabled="busy" />
      </div>
      <div class="row">
        <label for="concurrency">Max concurrent</label>
        <input id="concurrency" v-model.number="concurrency" type="number" min="1" step="1" :disabled="busy" />
      </div>
      <div class="row">
        <label for="total">Total requests</label>
        <input
          id="total"
          v-model.number="totalCount"
          type="number"
          min="1"
          step="1"
          :disabled="busy || unlimited"
        />
        <label class="checkbox"><input v-model="unlimited" type="checkbox" :disabled="busy" /> Run until stopped</label>
      </div>
    </fieldset>

    <div class="actions">
      <button type="button" :disabled="busy" @click="sendSingle">Send once</button>
      <button type="button" :disabled="busy" @click="startBurst">Start burst</button>
      <button type="button" :disabled="!busy" @click="stop">Stop</button>
    </div>
  </section>
</template>
