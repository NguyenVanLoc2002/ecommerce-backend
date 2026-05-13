# Security Rules

Full implementation details: `docs/security.md`

---

## 1. Never Do These

- Never log: passwords, raw JWT, refresh token, OTP, cookie values, payment secrets, full authorization headers, raw reset tokens
- Never commit: `.env`, credentials, private keys, application-prod config
- Never hardcode secrets, tokens, or passwords in source code
- Never disable Spring Security config to temporarily fix something
- Never trust client-provided payment status — always verify server-side
- Never skip HMAC/signature verification on payment callbacks (see §4 below)
- Never expose stacktraces in production API responses

## 2. Authentication and Authorization

### JWT Model
- Access token: short-lived, returned in response body, used as `Authorization: Bearer`
- Refresh token: long-lived, stored ONLY in HttpOnly cookie at path `/api/v1/auth`
- Refresh session: stored server-side in Redis with TTL (key: `auth:refresh:{type}:{principalId}:{sessionId}`)
- Refresh token stored as SHA-256 hash, never raw

### Auth Flow Rules
- Refresh rotates on every successful call — previous session is revoked
- Reuse of an already-rotated token triggers session family revocation
- Logout: blacklists access token in Redis + revokes refresh session + clears cookie
- Password change/reset: revokes ALL active refresh sessions for that user

### Authorization Checks
- Always check authorization at service or endpoint boundary — not only authentication
- Use `@PreAuthorize` for fine-grained role or ownership checks
- Validate resource ownership for customer-facing endpoints (customer cannot access another customer's order)
- Admin endpoints require STAFF+ at URL level, may require ADMIN/SUPER_ADMIN at method level

## 3. Input Validation and Security

- Validate all external input — request DTOs, query params, path variables
- Custom validators: `@PhoneNumber` (format `0xxxxxxxxx` or `+84xxxxxxxxx`)
- Enum binding: case-insensitive, but invalid values return `400 Bad Request`
- File upload validation: whitelist content types (image/jpeg, image/png, image/webp), max size, sanitize filename, store as UUID-named file
- Never use user-provided filename as storage path

## 4. Payment Callback Security

`POST /api/v1/payments/callback` is a public endpoint — no Bearer token.

**Current risk (HIGH):** HMAC signature verification is a TODO in `PaymentServiceImpl.processCallback`.

Current protections (until HMAC is implemented):
- State-machine guards prevent duplicate SUCCESS callbacks
- DB unique constraint on `payment_transactions.provider_txn_id`

**Required before production:** implement `PaymentGatewaySignatureVerifier` per provider. See `docs/security.md §10`.

## 5. Cookie and CORS

- Refresh cookie: `HttpOnly=true`, `Secure=true` (production), `SameSite=Lax`, `Path=/api/v1/auth`
- CORS: configured explicitly in `SecurityConfig`, `allowCredentials(true)` with specific origins
- CSRF: disabled for Bearer-token flows; double-submit cookie filter (`CsrfDoubleSubmitFilter`) protects refresh/logout endpoints when `app.security.csrf-double-submit-enabled=true`

## 6. OTP and Password Reset

- OTP: 6-digit numeric, stored as `SHA-256(purpose + ":" + target + ":" + rawOtp + ":" + pepper)`
- OTP pepper = JWT secret (`app.jwt.secret`)
- Rate limits via Redis: cooldown per target, rolling-window send/verify limits
- Reset token: 256 bits entropy, SHA-256 hashed, single-use, TTL 10 min (configurable)
- Password policy: min 8 chars, uppercase + lowercase + digit, must not match current password

## 7. When Modifying Security Code

Before changing any of these, read the full `docs/security.md` first:
- `SecurityConfig` (filter chain, CORS, public routes)
- `JwtTokenProvider` / `JwtAuthenticationFilter`
- `AuthServiceImpl`, `RefreshSessionService`, `TokenBlacklistService`
- `CsrfDoubleSubmitFilter`
- Cookie configuration

Do not change security behavior without explaining the impact on the auth flow.
