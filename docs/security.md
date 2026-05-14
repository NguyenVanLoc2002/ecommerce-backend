# Security Guidelines

This file documents the security behavior implemented in the current backend source and the recommended target architecture for the refresh-token flow.

Source of truth:
- `src/main/java/com/locnguyen/ecommerce/domains/auth/controller/AuthController.java`
- `src/main/java/com/locnguyen/ecommerce/domains/auth/service/impl/AuthServiceImpl.java`
- `src/main/java/com/locnguyen/ecommerce/common/security/JwtTokenProvider.java`
- `src/main/java/com/locnguyen/ecommerce/common/security/JwtAuthenticationFilter.java`
- `src/main/java/com/locnguyen/ecommerce/common/security/TokenBlacklistService.java`
- `src/main/java/com/locnguyen/ecommerce/common/config/SecurityConfig.java`
- `src/main/java/com/locnguyen/ecommerce/common/config/WebMvcConfig.java`
- `src/main/resources/application-dev.properties`
- `src/main/resources/application-prod.properties`

---

## 1. Current implemented auth model

### 1.1 Login and register

- `POST /api/v1/auth/register` returns `ApiResponse<AuthResponse>`
- `POST /api/v1/auth/login` returns `ApiResponse<AuthResponse>`
- `AuthResponse.data.tokens` includes:
  - `accessToken`
  - `tokenType`
  - `expiresIn`
- Register and login also set the refresh token in a `Set-Cookie` header.

### 1.2 Access token usage

- Protected routes use `Authorization: Bearer <accessToken>`.
- `JwtAuthenticationFilter` reads only the Bearer token from the `Authorization` header.
- Refresh tokens are rejected if sent as access tokens.

### 1.3 Refresh token usage

- `POST /api/v1/auth/refresh-token` reads the refresh token from the configured HttpOnly cookie.
- The deprecated request-body fallback (`RefreshTokenRequest`) is **disabled by default**. It is re-enabled only when `app.security.refresh-token-body-fallback-enabled=true` (see section 8).
- The refresh endpoint does not read the refresh token from the `Authorization` header.
- The refresh endpoint returns a new `TokenResponse` containing only a new access token.
- Refresh rotates the refresh token on every success, revokes the previous refresh session, and sets a replacement cookie.
- If the session is missing or the token hash mismatches, the backend revokes the session family when it can identify it and returns `401`.

### 1.4 Logout

- `POST /api/v1/auth/logout` is public/idempotent so it can clear the cookie even when the access token is missing or expired.
- Logout blacklists the presented access token in Redis until that access token expires when a valid Bearer token is supplied.
- Logout revokes the refresh session referenced by the refresh-token cookie when present.
- Logout clears the refresh-token cookie.

### 1.5 Password change / password reset

- No password-change endpoint is implemented in the current source tree.
- No forgot-password or reset-password endpoint is implemented in the current source tree.
- `AuthService.revokeAllRefreshSessions(principalType, principalId)` is now the reusable integration point a future password-change/reset flow must call.

### 1.6 Customer vs admin auth separation

- Customers, staff, admins, and super admins all use the same `/api/v1/auth/login` endpoint.
- Separation happens through roles inside the access token and the Spring Security authorization rules.
- There is no separate admin login controller or separate admin refresh-token mechanism.

---

## 2. Current storage model

### 2.1 Access token and refresh token format

- Both access tokens and refresh tokens are self-contained signed JWTs.
- Access token contains subject and roles.
- Refresh token contains subject and token type, but no roles.

### 2.2 Server-side storage

- Refresh sessions are stored in Redis through `RefreshSessionService`.
- Session records are stored under keys shaped like `auth:refresh:{principalType}:{principalId}:{sessionId}`.
- Principal/session indexes are also maintained in Redis to support family or principal-wide revocation.
- Redis is still used for access-token blacklist entries on logout.

### 2.3 Hashing

- Refresh tokens are hashed with SHA-256 before being stored in Redis session records.
- Access tokens are also not hashed in Redis blacklist; the raw access token string is used as part of the Redis key.

---

## 3. Current configuration

- Access-token expiry property: `app.jwt.access-token-expiration`
- Refresh-token expiry property: `app.jwt.refresh-token-expiration`
- Refresh-cookie properties:
  - `app.auth.refresh-cookie.name`
  - `app.auth.refresh-cookie.path`
  - `app.auth.refresh-cookie.same-site`
  - `app.auth.refresh-cookie.secure`
  - `app.auth.refresh-cookie.http-only`
  - `app.auth.refresh-cookie.max-age`
- Development defaults:
  - access token: `3600000` ms (1 hour)
  - refresh token: `604800000` ms (7 days)
  - refresh cookie: `fashion-shop.refresh-token`, `Path=/api/v1/auth`, `SameSite=Lax`, `Secure=false`, `HttpOnly=true`, `Max-Age=604800`
- Production defaults:
  - access token: `${JWT_ACCESS_EXPIRY:3600000}`
  - refresh token: `${JWT_REFRESH_EXPIRY:604800000}`
  - refresh cookie secure flag defaults to `${AUTH_REFRESH_COOKIE_SECURE:true}`

---

## 4. Current security limitations

- A deprecated request-body fallback still accepts `refreshToken` JSON on `/api/v1/auth/refresh-token`; this should be removed after frontend migration.
- Cookie-based refresh is used while CSRF remains disabled. Current mitigations are restricted `allowedOrigins`, `allowCredentials(true)`, `SameSite=Lax`, and `Path=/api/v1/auth`.
- Access-token blacklist still stores the raw token string as part of the Redis key.
- No password-change or reset flow exists yet, so principal-wide session revocation is only available as a service method and not yet exposed by an endpoint.

---

## 5. Implemented target architecture

### 5.1 Transport

- Return the access token in the JSON response body.
- Deliver the refresh token only via `Set-Cookie`.
- Cookie attributes in the current implementation:
  - `HttpOnly`
  - `Secure` configurable by environment
  - `SameSite=Lax` by default
  - `Path=/api/v1/auth`
  - `Max-Age` aligned with refresh-session TTL

### 5.2 Server-side storage

- Store refresh-session state server-side.
- Current option: Redis with TTL for each refresh session.
- Fallback option for future deployments: database refresh-session table if Redis session storage is not available.
- Store a token hash server-side, not the raw refresh token.

### 5.3 Rotation and revocation

- Rotate the refresh token on every refresh.
- Revoke the previous refresh-session record during refresh.
- Detect reuse of an already-rotated refresh token and revoke the session family.
- On logout:
  - revoke the server-side refresh session
  - blacklist the current access token if immediate access-token invalidation is still desired
  - clear the refresh-token cookie
- On password change:
  - revoke all active refresh sessions for that user
  - clear the current refresh-token cookie

### 5.4 Frontend contract after migration

- Frontends should use `withCredentials`.
- Frontends should stop storing refresh tokens in LocalStorage, sessionStorage, or other JavaScript-accessible storage.
- Frontends should continue sending the access token in `Authorization: Bearer <accessToken>`.

---

## 6. Current vs target checklist

- [x] Access token uses Bearer header
- [x] Passwords are stored as hashes
- [x] Logout blacklists presented access token in Redis
- [x] JWT expiration is configured via application properties
- [x] Refresh token in HttpOnly cookie
- [x] Refresh session stored server-side
- [x] Server-side hashed refresh token
- [x] Refresh-session revocation on logout
- [x] Refresh-session revocation on password change / reset
- [x] Refresh-token reuse detection
- [x] Refresh-token body fallback gated behind a feature flag (default off)
- [x] CSRF double-submit token pair (gated, default off in dev)

---

## 7. Forgot / reset / change password (Phase 1)

Source files:

- `src/main/java/com/locnguyen/ecommerce/domains/verification/...`
- `src/main/java/com/locnguyen/ecommerce/domains/auth/controller/AuthController.java`
- `src/main/java/com/locnguyen/ecommerce/domains/auth/controller/AccountController.java`
- `src/main/java/com/locnguyen/ecommerce/domains/auth/service/PasswordResetService.java`
- `src/main/java/com/locnguyen/ecommerce/domains/auth/service/ChangePasswordService.java`
- `src/main/java/com/locnguyen/ecommerce/infrastructure/email/EmailSender.java`
- `src/main/resources/db/migration/V18__create_verification_tokens.sql`

### 7.1 Endpoints

| Method | Path                                  | Auth          | Purpose |
|--------|---------------------------------------|---------------|---------|
| POST   | `/api/v1/auth/password/forgot`        | none          | Issue OTP for reset (always returns 200, never leaks email existence). |
| POST   | `/api/v1/auth/password/forgot/verify` | none          | Verify OTP, return one-shot reset token. |
| POST   | `/api/v1/auth/password/reset`         | none          | Consume reset token, update password, revoke all refresh sessions. |
| POST   | `/api/v1/account/password/change`     | Bearer JWT    | Authenticated change-password: verifies current, updates, revokes all sessions. |

### 7.2 OTP storage rule

- OTP is a 6-digit numeric value generated with `SecureRandom`.
- The DB stores only `SHA-256(purpose + ":" + target + ":" + rawOtp + ":" + pepper)`. The pepper is the JWT secret (`app.jwt.secret`).
- The raw OTP is delivered through `EmailSender` and is never logged.
- Each row has `expires_at`, `attempt_count` / `max_attempts`, `verified_at`, `used_at`.

### 7.3 Reset token

- Issued only after a successful OTP verification.
- 256 bits of entropy (concatenated UUIDs), SHA-256 hashed and stored on the same `verification_tokens` row in `reset_token_hash`.
- Single-use: marked with `used_at` after the reset succeeds.
- TTL configurable via `app.reset-token.expires-minutes` (default 10 min).

### 7.4 Rate limits (Redis)

Configured via `app.otp.*`:

| Key prefix                 | Purpose                            | TTL                                |
|----------------------------|------------------------------------|------------------------------------|
| `otp:send:cooldown:*`      | Per-target cooldown after each send | `resend-cooldown-seconds` (60s)    |
| `otp:send:window:*`        | Rolling-window count of sends       | `send-limit-window-minutes` (15m)  |
| `otp:verify:window:*`      | Rolling-window count of verifies    | 15 minutes                         |

Exceeding the cooldown or window throws `ErrorCode.OTP_RATE_LIMITED` (HTTP 429).

### 7.5 Password policy

Server-side enforced by `PasswordPolicyValidator`:

- length >= 8
- at least one uppercase, one lowercase, one digit
- new password must not match the current password (BCrypt comparison)

Violations throw `ErrorCode.PASSWORD_POLICY_VIOLATED` / `PASSWORD_REUSED`.

### 7.6 Session revocation

Both `password/reset` and `account/password/change` call `AuthService.revokeAllRefreshSessions(...)` so every existing device must log in again.

### 7.7 Email sender

The default `LoggingEmailSender` only logs that an email *would have been sent* (no OTP value in logs). A real provider implementation can be wired in by registering an `EmailSender` bean named `smtpEmailSender` — the logging fallback is `@ConditionalOnMissingBean(name = "smtpEmailSender")`.

---

## 8. Refresh-token body fallback removal

`POST /api/v1/auth/refresh-token` no longer reads the refresh token from the request body by default. The deprecated fallback is now gated behind a property:

```properties
app.security.refresh-token-body-fallback-enabled=false  # default
```

Set to `true` only as a temporary measure during a client migration. When `false`, requests without the cookie return `REFRESH_TOKEN_INVALID` (401).

---

## 9. CSRF double-submit cookie

Stateless JWT does not need CSRF for the Bearer-token flows, but the cookie-based refresh endpoints (`/api/v1/auth/refresh-token`, `/api/v1/auth/logout`) are protected by a double-submit-cookie filter (`CsrfDoubleSubmitFilter`).

Behavior when `app.security.csrf-double-submit-enabled=true`:

1. Server issues a non-HttpOnly `XSRF-TOKEN` cookie to every response that doesn't already carry one.
2. Frontend JS reads the cookie and echoes it in the `X-XSRF-TOKEN` header on mutating requests.
3. The filter validates `X-XSRF-TOKEN == XSRF-TOKEN` cookie for the protected paths.
4. Mismatch / missing → 403 `CSRF_TOKEN_INVALID`.

The flag is **disabled by default in dev** to avoid breaking existing clients during rollout. Enable in production once the front end has been updated to echo the header.

---

## 10. Payment callback security (HMAC — PENDING)

`POST /api/v1/payments/callback` is a public endpoint called server-to-server by the payment gateway. It is **not** protected by a Bearer token.

### 10.1 Current protection

Without a gateway-specific HMAC implementation, the only guards are:

- **State-machine guards**: duplicate SUCCESS callbacks are no-ops (payment already PAID); stale callbacks cannot move a PAID/REFUNDED payment backward.
- **Duplicate `providerTxnId` check**: application-level deduplication before any mutation.
- **DB unique constraint on `payment_transactions.provider_txn_id`**: DB-level duplicate protection complementing the application check.

### 10.2 Missing protection (TODO before production)

Each payment gateway (VNPay, MoMo, ZaloPay, etc.) signs its callbacks with an HMAC or RSA signature. Without verifying this signature, any party that knows the callback URL can submit a spoofed `status=SUCCESS` for any order code.

**Required implementation before production:**

```java
// In PaymentServiceImpl.processCallback — the TODO is already present:
// TODO(phase-2): HMAC/signature verification must be added here before any
// business mutation. Each gateway uses a different signing algorithm and
// secret key. Wire a PaymentGatewaySignatureVerifier when the gateway
// integration is implemented. Failing signature verification must throw
// AppException(ErrorCode.PAYMENT_CALLBACK_INVALID) before the order is touched.
```

### 10.3 MoMo IPN signature infrastructure (Session 1 complete)

`MomoPaymentProvider` and `MomoSignatureService` are implemented and wired into the `PaymentProvider` strategy. `MomoSignatureService.verifyIpnSignature()` computes the HMAC-SHA256 over the required IPN fields (accessKey, amount, extraData, message, orderId, orderInfo, orderType, partnerCode, payType, requestId, responseTime, resultCode, transId) and compares it to the received signature.

**Remaining gap**: The existing `PaymentWebhookServiceImpl.processWebhook()` does not yet call `provider.verifySignature()` before mutating order/payment state. This is Session 2 work (IPN state machine).

**MoMo local testing requirement**: the IPN URL must be reachable by MoMo's servers. For local dev:
1. Start a public tunnel: `cloudflared tunnel --url http://localhost:8080`
2. Set `APP_PAYMENT_MOMO_IPN_URL=https://<tunnel-domain>/api/v1/payments/webhooks/MOMO`
3. Set `APP_PAYMENT_MOMO_ENABLED=true` with TEST credentials

### 10.4 Implementation guidance (remaining gateways)

1. Create a `PaymentGatewaySignatureVerifier` interface with a `verify(provider, payload, signature)` method.
2. Implement one class per gateway (e.g., `VnPaySignatureVerifier`, `MoMoSignatureVerifier`).
3. Inject via a `Map<String, PaymentGatewaySignatureVerifier>` keyed by provider name.
4. Call before any read of `order` or `payment` state.
5. Failing verification must throw `AppException(ErrorCode.PAYMENT_CALLBACK_INVALID)` — never log raw payload or secrets.

### 10.5 Risk level

**HIGH** — Until `processWebhook()` calls `verifySignature()` for each provider, any attacker who discovers the webhook URL can confirm payments without actually paying. Do not expose this endpoint to the public internet on a production environment without completing HMAC verification in Session 2.
