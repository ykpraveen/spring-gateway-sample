/** RFC 7636 PKCE helpers, built on Web Crypto (requires a secure context — localhost qualifies). */

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function randomBytes(length: number): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(length))
}

export function generateState(): string {
  return base64UrlEncode(randomBytes(16))
}

export function generateCodeVerifier(): string {
  return base64UrlEncode(randomBytes(32))
}

export async function deriveCodeChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  return base64UrlEncode(new Uint8Array(digest))
}
