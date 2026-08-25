import { computed, reactive } from 'vue'
import { keycloakEndpoints, keycloak, redirectUri } from '../config'
import { deriveCodeChallenge, generateCodeVerifier, generateState } from './pkce'

interface TokenState {
  accessToken: string | null
  refreshToken: string | null
  idToken: string | null
  expiresAt: number | null // epoch millis
  username: string | null
  roles: string[]
}

const STORAGE_KEY = 'gateway-sample.auth.tokens'
const PKCE_STORAGE_KEY = 'gateway-sample.auth.pkce'

// Refresh this long before actual expiry so a burst in flight never gets caught mid-request.
const REFRESH_SKEW_MS = 30_000

function emptyState(): TokenState {
  return { accessToken: null, refreshToken: null, idToken: null, expiresAt: null, username: null, roles: [] }
}

function decodeJwtPayload(token: string): Record<string, unknown> {
  const [, payload] = token.split('.')
  const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
  return JSON.parse(json)
}

function loadPersistedState(): TokenState {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return emptyState()
  try {
    return { ...emptyState(), ...JSON.parse(raw) }
  } catch {
    return emptyState()
  }
}

const state = reactive<TokenState>(loadPersistedState())

function persist() {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state))
}

function clear() {
  Object.assign(state, emptyState())
  sessionStorage.removeItem(STORAGE_KEY)
}

function applyTokenResponse(response: {
  access_token: string
  refresh_token?: string
  id_token?: string
  expires_in: number
}) {
  state.accessToken = response.access_token
  state.refreshToken = response.refresh_token ?? state.refreshToken
  state.idToken = response.id_token ?? state.idToken
  state.expiresAt = Date.now() + response.expires_in * 1000

  const claims = decodeJwtPayload(response.access_token)
  state.username = typeof claims.preferred_username === 'string' ? claims.preferred_username : null
  const realmAccess = claims.realm_access as { roles?: string[] } | undefined
  state.roles = realmAccess?.roles ?? []

  persist()
}

export const isAuthenticated = computed(() => state.accessToken !== null && state.expiresAt !== null)
export const username = computed(() => state.username)
export const roles = computed(() => state.roles)

export async function login(): Promise<void> {
  const verifier = generateCodeVerifier()
  const challenge = await deriveCodeChallenge(verifier)
  const authState = generateState()
  sessionStorage.setItem(PKCE_STORAGE_KEY, JSON.stringify({ verifier, state: authState }))

  const params = new URLSearchParams({
    client_id: keycloak.clientId,
    response_type: 'code',
    redirect_uri: redirectUri(),
    scope: 'openid',
    code_challenge: challenge,
    code_challenge_method: 'S256',
    state: authState,
  })
  window.location.href = `${keycloakEndpoints.authorization}?${params.toString()}`
}

export function logout(): void {
  const idTokenHint = state.idToken
  clear()

  const params = new URLSearchParams({ client_id: keycloak.clientId, post_logout_redirect_uri: redirectUri() })
  if (idTokenHint) params.set('id_token_hint', idTokenHint)
  window.location.href = `${keycloakEndpoints.logout}?${params.toString()}`
}

/**
 * Checks the current URL for an authorization-code callback (`code`/`state` query params) and,
 * if present, exchanges the code for tokens and strips them from the URL. No-op otherwise.
 */
export async function handleRedirectCallback(): Promise<void> {
  const url = new URL(window.location.href)
  const code = url.searchParams.get('code')
  const returnedState = url.searchParams.get('state')
  if (!code || !returnedState) return

  const pkceRaw = sessionStorage.getItem(PKCE_STORAGE_KEY)
  sessionStorage.removeItem(PKCE_STORAGE_KEY)
  url.searchParams.delete('code')
  url.searchParams.delete('state')
  url.searchParams.delete('session_state')
  url.searchParams.delete('iss')
  window.history.replaceState({}, document.title, url.toString())

  if (!pkceRaw) return
  const { verifier, state: expectedState } = JSON.parse(pkceRaw) as { verifier: string; state: string }
  if (returnedState !== expectedState) {
    throw new Error('OAuth state mismatch — possible CSRF; discarding callback.')
  }

  const response = await fetch(keycloakEndpoints.token, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: keycloak.clientId,
      code,
      redirect_uri: redirectUri(),
      code_verifier: verifier,
    }),
  })
  if (!response.ok) {
    throw new Error(`Token exchange failed: ${response.status} ${await response.text()}`)
  }
  applyTokenResponse(await response.json())
}

async function refresh(): Promise<void> {
  if (!state.refreshToken) {
    clear()
    throw new Error('Session expired — please sign in again.')
  }
  const response = await fetch(keycloakEndpoints.token, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: keycloak.clientId,
      refresh_token: state.refreshToken,
    }),
  })
  if (!response.ok) {
    clear()
    throw new Error('Session expired — please sign in again.')
  }
  applyTokenResponse(await response.json())
}

/** Returns a currently-valid access token, transparently refreshing it if it's near expiry. */
export async function getAccessToken(): Promise<string> {
  if (!state.accessToken || !state.expiresAt) {
    throw new Error('Not signed in.')
  }
  if (Date.now() >= state.expiresAt - REFRESH_SKEW_MS) {
    await refresh()
  }
  if (!state.accessToken) {
    throw new Error('Not signed in.')
  }
  return state.accessToken
}
