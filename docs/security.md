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
- `POST /api/v1/auth/refresh-token` still accepts `RefreshTokenRequest` in the JSON body as a temporary deprecated fallback.
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
- [ ] Refresh-session revocation on password change
- [x] Refresh-token reuse detection
