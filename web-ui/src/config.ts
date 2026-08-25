/** Central runtime config, sourced from Vite env vars (see .env.example). */

export const gatewayBaseUrl: string = import.meta.env.VITE_GATEWAY_BASE_URL ?? 'http://localhost:8080'

export const keycloak = {
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'gateway-sample',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'web-ui',
}

export const keycloakEndpoints = {
  authorization: `${keycloak.url}/realms/${keycloak.realm}/protocol/openid-connect/auth`,
  token: `${keycloak.url}/realms/${keycloak.realm}/protocol/openid-connect/token`,
  logout: `${keycloak.url}/realms/${keycloak.realm}/protocol/openid-connect/logout`,
}

/** Redirect target for both the auth-code callback and post-logout: the app's own base URL. */
export function redirectUri(): string {
  return `${window.location.origin}${window.location.pathname}`
}
