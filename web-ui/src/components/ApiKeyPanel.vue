<script setup lang="ts">
import { ref } from 'vue'
import { apiKey } from '../store/apiKey'

const revealed = ref(false)
</script>

<template>
  <section class="panel">
    <div class="row">
      <label for="api-key">API key</label>
      <input
        id="api-key"
        v-model="apiKey"
        :type="revealed ? 'text' : 'password'"
        placeholder="Paste the client API key issued by the gateway"
        autocomplete="off"
      />
      <button type="button" @click="revealed = !revealed">{{ revealed ? 'Hide' : 'Show' }}</button>
    </div>
    <p class="hint">Sent as the X-API-Key header; kept only in this tab's session storage.</p>
  </section>
</template>
