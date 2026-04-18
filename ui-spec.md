# UI/UX Specification — Fashion Shop Platform

> **Source**: Derived strictly from backend codebase (controllers, services, entities, enums, security config, Flyway migrations).
> **Revised**: 2026-04-17 — added UX patterns, loading/empty/error states, edge cases, admin table UX, checkout improvements, lifecycle visualization.
> **Backend Base URL**: `/api/v1`
> **Auth**: `Authorization: Bearer <accessToken>` on all protected endpoints.

---

## Table of Contents

1. [User Roles & Access Model](#1-user-roles--access-model)
2. [Global UI Conventions](#2-global-ui-conventions)
3. [Customer — Flows & Screens](#3-customer--flows--screens)
4. [Admin — Flows & Screens](#4-admin--flows--screens)
5. [State Machines Reference](#5-state-machines-reference)
6. [Edge Cases & Race Conditions](#6-edge-cases--race-conditions)
7. [Error Codes Reference](#7-error-codes-reference)

---

## 1. User Roles & Access Model

### 1.1 Role Hierarchy

```
SUPER_ADMIN
    └── ADMIN
            └── STAFF
                    └── CUSTOMER
```

Each higher role **inherits all permissions** of roles below it.

| Role | Description | Access Surface |
|---|---|---|
| `CUSTOMER` | Registered shopper | Customer-facing app (web / mobile) |
| `STAFF` | Store staff | Admin panel — view + manage orders, shipments, reviews |
| `ADMIN` | Store admin | All STAFF + voucher/promotion management |
| `SUPER_ADMIN` | Platform owner | All ADMIN + system config, actuator endpoints |

### 1.2 Endpoint Access Matrix

| Pattern | Public | Customer | STAFF+ | ADMIN+ |
|---|---|---|---|---|
| `POST /auth/**` | ✅ | — | — | — |
| `GET /products/**`, `/categories/**`, `/brands/**` | ✅ | — | — | — |
| `POST /payments/callback` | ✅ | — | — | — |
| `/me/**`, `/addresses/**` | — | ✅ | — | — |
| `/cart/**`, `/orders/**` | — | ✅ | — | — |
| `/payments/**` (customer) | — | ✅ | — | — |
| `/shipments/order/**`, `/invoices/order/**` | — | ✅ | — | — |
| `/reviews/**`, `/notifications/**` | — | ✅ | — | — |
| `/vouchers/{code}/validate` | — | ✅ | — | — |
| `/admin/**` | — | — | ✅ | — |
| `/admin/vouchers` (write), `/admin/promotions` (write) | — | — | — | ✅ |
| `/actuator/health`, `/actuator/info` | ✅ | — | — | — |
| `/actuator/**` (other) | — | — | — | ✅ |

---

## 2. Global UI Conventions

### 2.1 Response Wrapper

```json
// Success
{ "success": true, "code": "SUCCESS", "message": "...", "data": { ... }, "timestamp": "..." }

// Error
{ "success": false, "code": "ORDER_NOT_FOUND", "message": "...", "path": "...", "timestamp": "...", "fieldErrors": [] }
```

### 2.2 Pagination Parameters

| Param | Type | Default |
|---|---|---|
| `page` | int | 0 (zero-based) |
| `size` | int | 20 |
| `sort` | string | `createdAt,desc` |

Response includes `totalElements`, `totalPages`, `number`, `size`.

---

### 2.3 Skeleton Variants

Define once, reference per screen by type:

| Skeleton Type | Used For | Description |
|---|---|---|
| `skeleton-card` | Product grid, order cards, notification items | Rectangular placeholder with image area + 2–3 text lines |
| `skeleton-table` | All admin list screens | Header row + 5–8 placeholder rows at full column width |
| `skeleton-detail` | Order detail, product detail, payment detail | Mixed image/text block layout |
| `skeleton-form` | Create/edit product, checkout address | Label + input field pairs stacked |
| `skeleton-timeline` | Shipment events, audit log | Vertical dot-line with alternating text blocks |
| `skeleton-stat` | Dashboard KPI cards | Square card with large number placeholder |

**Rules**:
- Show skeleton on **initial load** and on **hard refresh** of a route.
- Show **spinner** (not skeleton) on subsequent user-triggered actions (button clicks, filter changes).
- Never show both skeleton and real content simultaneously.
- Animate skeletons with a left-to-right shimmer.

---

### 2.4 Toast / Alert System

| Type | Trigger | Auto-dismiss | Position |
|---|---|---|---|
| **Success** (green) | Mutation completed (save, confirm, approve) | 4 s | Top-right (desktop), bottom (mobile) |
| **Error** (red) | API error on mutation | No — requires manual dismiss | Top-right / bottom |
| **Warning** (amber) | Soft warning (e.g., publishing product with no variants) | 6 s | Top-right / bottom |
| **Info** (blue) | Neutral status update | 4 s | Top-right / bottom |

**Rules**:
- Maximum 3 toasts visible at once; queue subsequent ones.
- Errors from `fieldErrors` array go **inline** (not toast).
- Network timeout errors always include a **Retry** action in the toast.
- Do not show success toasts for read-only (GET) operations.

---

### 2.5 Confirmation Dialogs

All destructive or irreversible actions require a confirmation dialog before calling the API.

| Action | Dialog Title | Confirm Label | Style |
|---|---|---|---|
| Cancel order | "Cancel this order?" | "Yes, cancel" | Destructive (red) |
| Delete product (soft) | "Remove product?" | "Remove" | Destructive |
| Delete variant | "Delete variant?" | "Delete" | Destructive |
| Clear cart | "Clear your cart?" | "Clear cart" | Destructive |
| Delete address | "Remove address?" | "Remove" | Destructive |
| Void invoice | "Void this invoice?" + reason textarea | "Void invoice" | Destructive |
| Soft-delete voucher | "Deactivate voucher?" | "Deactivate" | Destructive |
| Reject review | "Reject this review?" + note textarea (required) | "Reject" | Destructive |

**Rules**:
- Confirmation button is disabled until any required input (e.g., rejection note) is filled.
- Cancel (dismiss) is always the leftmost / less prominent button.
- Pressing Escape closes the dialog without action.

---

### 2.6 Form State Management

- Track dirty state on all forms (any field modified from server value).
- On route navigation away from a dirty form: show browser-native "Leave page?" prompt or a custom modal.
- On successful save: clear dirty flag; do not prompt on next navigation.
- On validation error (422): keep form open with inline errors; do not navigate away.
- Auto-save (drafts) is **out of scope for Phase 1** — forms require explicit save.

---

### 2.7 HTTP Layer Patterns

#### Token Refresh Interceptor

```
Request →
  attach Authorization header →
    if 401 response:
      call POST /auth/refresh-token with refreshToken
        if refresh succeeds:
          store new tokens
          retry original request once
        if refresh fails (401 again):
          clear all tokens
          redirect to /login with ?redirect=<current-path>
          show toast: "Session expired. Please sign in again."
```

- The interceptor handles **all** 401s transparently — individual screens do not need to handle token expiry.
- Implement request queue: if a token refresh is already in-flight, queue subsequent 401ed requests and replay them all after refresh.

#### Network Retry

- Retry **GET** requests up to **2 times** with 1 s delay on network failure (no response, timeout).
- Do **not** auto-retry **POST/PATCH/DELETE** — mutations must be user-triggered to avoid double-submit.
- After 2 failed GETs: show inline error with a manual "Retry" button.

#### Search / Filter Debounce

- Debounce keyword search inputs: **300 ms** before firing the API request.
- Debounce numeric filter inputs (price range): **500 ms**.
- Show a spinner inside the search input field while the debounced request is in-flight.

---

### 2.8 Optimistic Updates

Apply optimistic UI only where the failure rate is negligible and rollback is trivial:

| Action | Optimistic behaviour | Rollback on error |
|---|---|---|
| Mark notification as read | Immediately dim unread dot; decrement badge count | Re-add dot; increment badge; show toast |
| Mark all notifications read | Immediately clear all dots; set badge to 0 | Restore previous state; show toast |
| Cart item remove | Immediately remove row from list | Re-insert item at previous position; show toast |

Do **not** use optimistic updates for: order placement, payment initiation, stock operations, status transitions — these require confirmed server state.

---

### 2.9 Common UI States (every screen)

| State | Trigger | UI Behaviour |
|---|---|---|
| **Initial load** | First render | Appropriate skeleton variant (§2.3) |
| **Action loading** | Button-triggered mutation | Button shows spinner, label changes to "Saving…"; button disabled |
| **Empty** | `data = []` or `data = null` | Illustrated empty state with contextual CTA (specified per screen) |
| **Error** | Non-2xx or network failure on GET | Inline error card with message + "Retry" button |
| **Validation error** | 422 `fieldErrors` | Inline per-field message below input; red border on field |
| **Forbidden** | 403 | "You don't have permission to view this." with back button |
| **Unauthorised** | 401 (after refresh fails) | Interceptor clears tokens → redirect to login |
| **Not found** | 404 on detail page | Dedicated 404 illustration + "Go back" link |
| **Server error** | 500 | Toast: "Something went wrong. Please try again." |
| **Stale data** | `ORDER_STATUS_INVALID` or similar concurrency error | Toast: "This record was updated. Refreshing…" + auto-reload |

---

### 2.10 Money Formatting

- All money is `DECIMAL(18,2)`. Display with locale currency symbol, two decimal places.
- **Never compute** discounts, totals, or line totals client-side — display values as returned by API.
- Show `salePrice` in accent colour; show `price` with strikethrough when `salePrice` is set.
- Zero discount: hide the discount row entirely (do not show "- $0.00").

### 2.11 Business Code Display

Monospace font, copy-to-clipboard icon on hover.

| Entity | Example |
|---|---|
| Order | `ORD202604060001` |
| Payment | `PAY...` |
| Invoice | `INV...` |
| Shipment | `SHP...` |

---

### 2.12 Admin Table UX (Standard Pattern)

Applied to **every** admin list screen. Reference this section rather than repeating it.

#### Toolbar layout
```
[ Search input            ] [ Filter ▾ ] [ Columns ▾ ]     [ + New ]
[ Active filter chips × × × ]                       [ Reset filters ]
────────────────────────────────────────────────────────────────────
[ ☐ ] Col A ↕  Col B ↕  Col C ↕  ...                      Actions
────────────────────────────────────────────────────────────────────
  Showing 1–20 of 347 results                [ < 1 2 3 … 18 > ] [20 ▾]
```

#### Features

**Sorting**: Click column header to sort ascending; click again for descending. Active sort shows `↑`/`↓` arrow. One sort at a time.

**Filtering**:
- Filter panel opens as a side drawer or dropdown.
- Active filters shown as dismissible chips below the toolbar.
- "Reset filters" button clears all active filters and re-runs query.
- Filter state persisted in URL query params so browser back/forward works.

**Page size selector**: Options 10 / 20 / 50 / 100. Default 20. Persisted in localStorage.

**Results count**: "Showing {from}–{to} of {totalElements} results". Updates on filter/sort/page change.

**Column visibility toggle**: Dropdown of checkboxes. At least one column always visible.

**Row actions**:
- Primary action visible on row hover (e.g., "View").
- Secondary actions in a `⋯` kebab menu.
- Destructive actions in kebab only (never prominent).

**Bulk actions**:
- Checkbox in each row + header checkbox (select all on page).
- Bulk action bar appears above table when ≥ 1 row selected: "X selected | [Action A] [Action B] [Deselect all]".
- Bulk actions require confirmation dialog before execution.
- After bulk action: refresh table; deselect all; show toast with count ("3 orders confirmed").

**Empty state** (no results): illustration + "No {entity} found" + "Clear filters" if filters are active, or "Create your first {entity}" if no data at all.

**Loading state**: `skeleton-table` on initial load; spinner in table body on filter/sort/page change (keep headers visible).

**Error state**: Inline error card inside table area with "Retry" button.

---

## 3. Customer — Flows & Screens

### 3.1 Authentication

#### Full Auth Flow

```
Any Protected Route
       │
       ▼
  Is authenticated? ──No──▶ /login?redirect=<path>
       │ Yes
       ▼
  Has required role? ──No──▶ 403 page
       │ Yes
       ▼
  Render screen
       │
  On 401 mid-session:
       │
       ▼
  Interceptor: POST /auth/refresh-token
       │
  ┌────┴────────┐
  │             │
Success      Failure
  │             │
Retry        Clear tokens
request      → /login?redirect=<path>
```

---

#### Screen: Login

**Purpose**: Authenticate and obtain JWT tokens.

**Components**:
- Email input (`type="email"`, required)
- Password input (`type="password"`, required, show/hide toggle)
- "Forgot password?" link (Phase 2 — render but disable)
- Submit button: "Sign In"
- Link: "Don't have an account? Register"

**API**:
```
POST /api/v1/auth/login
Body: { email, password }
Response: { user: { id, email, firstName, lastName, roles }, tokens: { accessToken, refreshToken, expiresIn } }
```

**On success**:
1. Store `accessToken` + `refreshToken` (HttpOnly cookie preferred; memory fallback for SPA).
2. Extract `roles` from response for UI routing.
3. Redirect: `STAFF`/`ADMIN`/`SUPER_ADMIN` → `/admin`; `CUSTOMER` → `?redirect` param or `/`.

**States**:

| State | UI |
|---|---|
| Initial load | Form ready (no skeleton needed) |
| Submitting | Button spinner + "Signing in…"; form disabled |
| `INVALID_CREDENTIALS` (401) | Inline error below password: "Email or password is incorrect" |
| `ACCOUNT_DISABLED` (403) | Inline error: "Your account has been disabled. Contact support." |
| Network failure | Toast error + Retry |
| Token refresh (auto) | Transparent — user does not see this |

**Edge cases**:
- User lands on `/login` while already authenticated → redirect to home immediately.
- `?redirect` param present after session expiry → after login, redirect to saved path.
- Login while checkout is in-progress → redirect back to checkout with cart intact.

**Business rules**:
- No password-strength validation on login (only on register).
- Access token is short-lived; interceptor handles transparent refresh (§2.7).

---

#### Screen: Register

**Purpose**: Create a new customer account.

**Components**:
- First name (optional), Last name (optional)
- Email (required), Phone number (optional)
- Password (required, show/hide toggle)
- Confirm password (client-side match validation only)
- Submit button: "Create Account"
- Link: "Already have an account? Sign in"

**API**:
```
POST /api/v1/auth/register
Body: { email, password, firstName, lastName, phoneNumber }
Response: same as login (tokens + user)
```

**States**:

| State | UI |
|---|---|
| Submitting | Button spinner + "Creating account…" |
| Passwords don't match (client) | Inline: "Passwords do not match" — before submit |
| `ACCOUNT_ALREADY_EXISTS` (409) | Field error on email: "An account with this email already exists" |
| Phone conflict (409) | Field error on phone: "This phone number is already registered" |
| 422 `fieldErrors` | Inline per-field messages |
| Success | Auto-authenticated (tokens stored) → redirect to home |

**Business rules**:
- Email and phoneNumber uniqueness enforced by DB; surfaces as 409.
- Successful registration returns tokens — no separate login step needed.

---

### 3.2 Product Discovery

#### Flow

```
[Screen: Product Listing]
  │
  ├── search / filter (debounced 300ms)
  │
  └── tap product card
         │
         ▼
  [Screen: Product Detail]
         │
         ├── select variant attributes (Color → Size)
         │       │
         │       ├── variant ACTIVE + in stock → enable "Add to Cart"
         │       └── variant INACTIVE or out of stock → disable "Add to Cart"
         │
         └── "Add to Cart" → POST /cart/items
                │
                ├── Success → cart icon badge +1, toast "Added to cart"
                └── Error → inline error (stock, inactive)
```

---

#### Screen: Product Listing

**Purpose**: Browse published products with filtering and pagination.

**Components**:
- Search bar — debounced 300 ms, triggers `?keyword=` param
- Filter sidebar / bottom sheet:
  - Category multi-select (source: `GET /api/v1/categories`)
  - Brand multi-select (source: `GET /api/v1/brands`)
  - Price range slider (min/max inputs)
  - Sort select: Newest | Price: Low → High | Price: High → Low | Featured
- Active filter chips (above grid, each with ×)
- Product grid — each card:
  - Primary image (fallback: placeholder illustration on `onerror`)
  - Product name
  - Brand name
  - Price display: sale price in accent + strikethrough original, or base price only
  - "Featured" badge if `featured = true`
  - "Sale" badge if any variant has `salePrice < price`
- Pagination or infinite scroll
- Results count: "X products found"

**API**:
```
GET /api/v1/products?page=0&size=20&sort=createdAt,desc
    &categoryId={}&brandId={}&minPrice={}&maxPrice={}&keyword={}
GET /api/v1/categories
GET /api/v1/brands
```

**States**:

| State | UI |
|---|---|
| Initial load | `skeleton-card` × 12 grid |
| Filter/search change | Spinner in search bar; table body replaced with spinner; keep filter panel open |
| Results returned | Grid renders; "X products found" updates |
| Empty (no results with active filters) | Illustration + "No products match your filters" + "Clear filters" button |
| Empty (no results, no filters) | "No products available yet" |
| API error | Inline error card + "Retry" button |
| Image load failure | Grey placeholder with product initial |

**Business rules**:
- Only `PUBLISHED` products returned — no client-side filtering needed.
- Filter state reflected in URL params (shareable links).
- Price range displayed uses lowest `salePrice` or `price` across all active variants.

---

#### Screen: Product Detail

**Purpose**: View product info and add a variant to cart.

**Components**:
- Image gallery with thumbnail strip (fallback: placeholder on `onerror`)
- Breadcrumb: Home > Category > Product name
- Product name, brand (link to brand filter), category chips
- Short description; "Show more" to expand full description
- **Variant selector** (two-step: first dimension then second):
  - Step 1: Color swatches (or first attribute group)
  - Step 2: Size buttons (or second attribute group)
  - Combinations with `status = INACTIVE`: greyed out, cursor `not-allowed`, tooltip "Unavailable"
  - Out-of-stock combinations: shown with `○` indicator and "Out of stock" tooltip
- Selected variant panel:
  - SKU (monospace, copy-on-click)
  - Price: `salePrice` (accent) + strikethrough `price`, or `price` alone
  - Stock indicator: In Stock (green) / Low Stock ≤ 5 (amber) / Out of Stock (red)
  - Quantity stepper: min 1, max guided by stock indicator; server is authoritative
- "Add to Cart" button — disabled states: no variant selected | `INACTIVE` | out of stock
- Disabled state tooltips:
  - No variant: "Select a size and colour first"
  - Out of stock: "This combination is out of stock"
  - `INACTIVE`: "This variant is currently unavailable"
- Reviews section (lazy-loaded, paginated — see §3.9)

**API**:
```
GET /api/v1/products/{id}
Response: ProductDetailResponse {
  id, name, slug, brand, categories, shortDescription, description,
  status, featured, variants: ProductVariantResponse[], media: []
}

POST /api/v1/cart/items
Body: { variantId, quantity }
```

**States**:

| State | UI |
|---|---|
| Initial load | `skeleton-detail` |
| `PRODUCT_NOT_FOUND` (404) | Illustrated 404 + "Back to products" |
| All variants `INACTIVE` | Banner: "This product is currently unavailable" |
| No variant selected | "Add to Cart" disabled |
| Variant selected, out of stock | "Add to Cart" disabled + "Out of stock" badge |
| Add to cart — loading | Button spinner, disabled |
| Add to cart — `INVENTORY_NOT_ENOUGH` | Inline error: "Only {n} left in stock" |
| Add to cart — `VARIANT_OUT_OF_STOCK` | Inline error: "This item is out of stock" |
| Add to cart — success | Toast: "Added to cart" + badge increment |

**Edge cases**:
- Variant was in stock when page loaded but sold out while user browsed: error surfaces on "Add to Cart" click.
- Product page visited directly via URL while product is `ARCHIVED`: show 404 (backend returns `PRODUCT_NOT_FOUND` for non-published).

**Business rules**:
- Only `ACTIVE` variants are selectable.
- Stock indicator is informational only; the server validates actual availability on cart add.
- `salePrice` is never shown higher than `price` (server enforces; display unconditionally trusts API values).

---

### 3.3 Cart

#### Flow

```
[Product Detail] ──Add to Cart──▶ optimistic badge +1
                                        │
                                 POST /cart/items
                                        │
                          ┌─────────────┴──────────────┐
                          │                             │
                       Success                       Error
                          │                             │
                   Refresh cart                 Show inline error
                          │
                   [Screen: Cart]
                          │
               ┌──────────┴──────────┐
               │                     │
          Edit qty               Remove item
               │                     │
        PATCH /cart/items        DELETE /cart/items/{id}
               │                     │
          (optimistic            (optimistic
           update,               remove,
           rollback on err)      rollback on err)
               │
          [Checkout] ──────▶ [Screen: Checkout]
```

---

#### Screen: Cart

**Purpose**: Review and edit items before checkout.

**Components**:
- Stale items warning banner (see edge cases below) — amber, dismissible
- Cart item list — each row:
  - Variant image (fallback: placeholder)
  - Product name (link to product detail) + variant name
  - SKU
  - Unit price (sale or base)
  - Quantity stepper (−/qty/+); disable `+` when quantity = available stock (informational)
  - Line total (from API)
  - "Remove" button (trash icon, optimistic)
- Order summary panel (sticky on desktop):
  - Item count
  - Subtotal (from API)
  - Voucher entry with "Apply" button (collapsed by default; expand on click)
  - Voucher discount row (only if applied)
  - "Proceed to Checkout" CTA — disabled if cart empty or has stale items blocking checkout
- "Continue Shopping" link
- "Clear Cart" button with confirmation dialog (§2.5)
- Empty state: cart illustration + "Your cart is empty" + "Start Shopping" → product listing

**API**:
```
GET    /api/v1/cart
POST   /api/v1/cart/items           Body: { variantId, quantity }
PATCH  /api/v1/cart/items/{itemId}  Body: { quantity }
DELETE /api/v1/cart/items/{itemId}
DELETE /api/v1/cart
```

**States**:

| State | UI |
|---|---|
| Initial load | `skeleton-card` × 3 list |
| Empty | Empty state (see above) |
| Qty stepper — `INVENTORY_NOT_ENOUGH` | Inline error on item row: "Only {n} left in stock"; revert stepper |
| Qty stepper — `VARIANT_OUT_OF_STOCK` | Inline error: "Out of stock"; disable stepper; show "Remove" highlight |
| `CART_ITEM_QUANTITY_INVALID` | Inline error: "Quantity must be at least 1" |
| Item removal — loading | Row fades to 40% opacity; spinner in remove button |
| Cart cleared | Replace with empty state |
| API error (GET) | Inline error card + "Retry" |

**Edge cases**:

**Stale cart items** — a variant may have become `INACTIVE` or out of stock since the item was added:
- On `GET /cart`, check each item for signals of unavailability (server returns variant status in response).
- If any item is `INACTIVE`: show warning banner "Some items in your cart are no longer available and must be removed before checkout." Highlight affected rows in amber with "Remove" button; disable "Proceed to Checkout".
- If item goes out of stock (detected on qty change): show per-row inline error; do not block checkout outright (stock check happens at order creation).

**Cart after successful checkout**:
- Cart status becomes `CHECKED_OUT` server-side.
- On next `GET /cart`, the server creates a fresh empty cart automatically.
- UI treats `CHECKED_OUT` cart as an empty cart; redirect user to empty state.

**Business rules**:
- `lineTotal` comes from API — never computed client-side.
- The cart has exactly one `ACTIVE` instance per customer; `GET /cart` creates it if absent.
- Quantity stepper max is informational; authoritative check is server-side on order creation.

---

### 3.4 Checkout & Order Creation

#### Full Checkout Flow

```
[Screen: Cart] → [Proceed to Checkout]
                          │
                          ▼
              ┌───────────────────────┐
              │  Step 1: Delivery     │
              │  Address              │◀─── validation gate (address required)
              └───────────┬───────────┘
                          │ Next
                          ▼
              ┌───────────────────────┐
              │  Step 2: Payment      │
              │  Method               │◀─── validation gate (method required)
              └───────────┬───────────┘
                          │ Next
                          ▼
              ┌───────────────────────┐
              │  Step 3: Voucher      │
              │  (optional)           │ ← no gate; skip allowed
              └───────────┬───────────┘
                          │ Next
                          ▼
              ┌───────────────────────┐
              │  Step 4: Review &     │
              │  Place Order          │◀─── final summary; POST /orders
              └───────────┬───────────┘
                          │
               ┌──────────┴────────────┐
               │                       │
            Success                 Error
               │                       │
  [Screen: Order Confirmation]    Handle per error code
```

**Step indicator**: horizontal stepper at top — "Delivery | Payment | Voucher | Review". Completed steps show checkmark; current step highlighted; future steps greyed. Clicking a completed step navigates back (with dirty-state guard).

---

#### Screen: Checkout — Step 1: Delivery Address

**Components**:
- Saved address list (`GET /api/v1/addresses`):
  - Radio cards; default address pre-selected
  - Each card: receiver name, phone, full address, type badge
  - "Default" chip on default address
- "Add new address" link → inline form (same page, no navigation):
  - Street (required), Ward (required), District (required), City (required)
  - Postal code (optional)
  - Address type (HOME / OFFICE / OTHER)
  - "Set as default" checkbox
  - "Save address" button → `POST /api/v1/addresses`
  - On success: new address auto-selected; form collapses
- "Next: Payment Method" button — disabled until an address is selected

**States**:

| State | UI |
|---|---|
| Loading addresses | `skeleton-card` × 2 |
| No saved addresses | Skip list; show inline address form open by default |
| Address save — loading | "Saving address…" button spinner |
| Address save — conflict | Inline error per field (409 → generic "Could not save address. Try again.") |
| Address save — success | New card appears, auto-selected; form closes |

**Edge cases**:
- Session expired while filling address form → interceptor redirects to `/login?redirect=/checkout` → after login, return to checkout (cart preserved; address form re-opens empty).

---

#### Screen: Checkout — Step 2: Payment Method

**Components**:
- Radio group:
  - **Cash on Delivery (COD)** — description: "Pay when your order arrives"
  - **Online Payment** — description: "Pay securely now via payment gateway"
- "Back" link (returns to Step 1, keeps selections)
- "Next: Voucher" button

**States**: No API call on this step. Always ready.

---

#### Screen: Checkout — Step 3: Voucher (Optional)

**Components**:
- Voucher input + "Apply" button
- Applied voucher chip: `[SUMMER20 — -$15.00 ×]`
- Voucher preview: promotion name, discount amount, validity window, remaining uses (from validate response)
- "Skip" / "Next: Review" button

**API**:
```
POST /api/v1/vouchers/{code}/validate
Body: { orderAmount, orderItems: [{ variantId, quantity }] }
Response: { code, promotionName, discountAmount, validityWindow, remainingUsages }
```

**States**:

| State | UI |
|---|---|
| Validate — loading | "Apply" button spinner |
| `VOUCHER_NOT_FOUND` | Field error: "Voucher code not found" |
| `VOUCHER_EXPIRED` | Field error: "This voucher has expired" |
| `VOUCHER_USAGE_LIMIT_EXCEEDED` | Field error: "This voucher has reached its usage limit" |
| Valid | Green chip with discount amount; "Applied!" micro-animation |
| Voucher removed (×) | Clear chip; re-show input; discount removed from summary |

**Business rules**:
- `POST /vouchers/{code}/validate` is a **preview only** — no usage recorded.
- Order amount passed to validate must match the cart subtotal.
- If user edits cart quantity after applying voucher, re-run validate automatically with updated amount.

---

#### Screen: Checkout — Step 4: Review & Place Order

**Components**:
- Read-only order summary:
  - Delivery address (name, phone, full address)
  - Payment method
  - Item list (product name, variant, qty, line total)
  - Subtotal
  - Voucher discount (code shown; hidden if no voucher)
  - Shipping fee ("Calculated by carrier" until confirmed by server)
  - **Total** (from server response after order creation)
- "Edit" links next to each section (navigate back to that step)
- Customer note textarea (optional, max 500 chars)
- "Place Order" button — single-use (disabled after first click to prevent double-submit)

**API**:
```
POST /api/v1/orders
Body: { shippingAddressId, paymentMethod, customerNote, voucherCode }
Response: OrderResponse
```

**States**:

| State | Trigger | UI |
|---|---|---|
| Place order — loading | POST /orders in-flight | Full-screen overlay spinner; "Placing your order…"; button disabled |
| `INVENTORY_NOT_ENOUGH` | Stock sold between cart load and order create | Dismiss overlay; inline error banner: "Some items are no longer available. Please review your cart." + "Return to Cart" button |
| `STOCK_RESERVATION_FAILED` | Same as above | Same UI |
| `VOUCHER_EXPIRED` | Voucher expired between validate and order create | Inline error on voucher row: "Your voucher has expired. Remove it and try again." |
| `VOUCHER_USAGE_LIMIT_EXCEEDED` | Voucher limit hit between validate and order create | Inline error: "This voucher is no longer available. Remove it and try again." |
| `VOUCHER_NOT_FOUND` | Voucher deleted between validate and order create | Inline error: "Voucher no longer valid. Remove it and try again." |
| `ORDER_STATUS_INVALID` | Edge concurrency case | Toast: "Something went wrong. Please try again." |
| Network timeout | Request exceeds timeout | Toast: "Connection lost. Your order may not have been placed. Check My Orders before retrying." |
| Success | 200 OrderResponse | Navigate to Order Confirmation screen |

**Business rules**:
- "Place Order" button becomes disabled immediately on first click (prevent double-submit).
- `shippingFee`, `totalAmount` displayed on confirmation screen come from the server response — do not show a total on the review step that could differ from the confirmed total.
- On timeout/network error: **do not auto-retry** the order POST. Show warning and direct user to check My Orders. If order appears there, it succeeded.

---

#### Screen: Order Confirmation

**Purpose**: Confirm order placed; guide next action.

**Components**:
- Animated success checkmark
- Order code (`ORD...`) with copy icon
- Summary: items count, total amount, payment method
- **COD path**: "Pay on delivery when your order arrives." → "View Order" button
- **Online path**: "Complete your payment to confirm the order." → "Pay Now" button (primary CTA)
- "Continue Shopping" link

**Business rules**:
- COD order starts `PENDING`; no immediate action required from customer.
- ONLINE order starts `PENDING`; must initiate payment to progress.

---

### 3.5 Order Management

#### Order Lifecycle — Customer Perspective

```
Who       Action                                   Order Status
──────────────────────────────────────────────────────────────────
Customer  Places order                             PENDING
                                                      │
Customer  [ONLINE] Initiates payment                  ├──▶ AWAITING_PAYMENT
                                                      │
Customer  [Can cancel here] ──────────────────────────┼──▶ CANCELLED
                                                      │
Admin     Confirms order                              ├──▶ CONFIRMED
                                                      │
Admin     Marks processing                            ├──▶ PROCESSING
                                                      │
System    Admin creates shipment (auto)               ├──▶ SHIPPED
                                                      │
Customer  Can track shipment                          │
                                                      │
System    Admin sets shipment DELIVERED (auto)        ├──▶ DELIVERED
                                                      │
Admin     Marks complete                              ├──▶ COMPLETED
                                                      │
Customer  Can write reviews now                       │
```

---

#### Screen: My Orders

**Purpose**: List all customer orders.

**Components**:
- Status filter tabs: All | To Pay | Processing | Shipped | Completed | Cancelled
  - "To Pay" = `status ∈ {PENDING (ONLINE), AWAITING_PAYMENT}`
  - Badge counts on each tab (approximate, from list response)
- Order cards — each shows:
  - Order code + date
  - Status badge (colour per §5.1)
  - Item thumbnails (first 3; "+N more" chip)
  - Total amount
  - Primary CTA (per table below)
- Load more / pagination
- Empty state per tab: e.g., "No shipped orders yet"

**API**:
```
GET /api/v1/orders?page=0&size=10&sort=createdAt,desc&status={}
```

**Status → Badge Colour → Primary CTA**:

| Status | Colour | CTA | Condition |
|---|---|---|---|
| `PENDING` | Amber | "Pay Now" | `paymentMethod = ONLINE` |
| `PENDING` | Amber | "Cancel" | `paymentMethod = COD` |
| `AWAITING_PAYMENT` | Orange | "Pay Now" | always |
| `CONFIRMED` | Blue | "View" | — |
| `PROCESSING` | Blue | "View" | — |
| `SHIPPED` | Teal | "Track" | — |
| `DELIVERED` | Green | "View" | — |
| `COMPLETED` | Green | "Review" | — |
| `CANCELLED` | Red | "View" | — |
| `REFUNDED` | Grey | "View" | — |

**States**:

| State | UI |
|---|---|
| Initial load | `skeleton-card` × 4 |
| Empty (all tab) | "You haven't placed any orders yet" + "Start Shopping" |
| Empty (filtered tab) | "No {status} orders" |
| Load more | Spinner at bottom of list |

---

#### Screen: Order Detail

**Purpose**: Full order information + actions.

**Components**:
- **Status stepper** (horizontal, shows all lifecycle stages):
  ```
  Placed ✓ → Confirmed ✓ → Processing ✓ → Shipped → Delivered → Completed
  ```
  - Completed stages: filled green circle + checkmark
  - Current stage: pulsing blue circle
  - Future stages: empty grey circle
  - Cancelled/Refunded: stepper replaced by red "CANCELLED" / "REFUNDED" banner
- Order code + placed date
- Items table: product name, variant name, SKU, unit price, qty, line total (all snapshot data)
- Pricing breakdown:
  - Subtotal
  - Voucher discount (show `voucherCode` in label; hide row if `discountAmount = 0`)
  - Shipping fee
  - **Total** (bold)
- Delivery address snapshot (name, phone, street, ward, district, city)
- Payment summary: method, status badge, amount, paid at
- Shipment mini-card: carrier, tracking number (copy icon), estimated delivery, current status → "Track shipment" link
- Invoice link (opens invoice screen)
- **Action buttons** (visibility by status):
  - "Cancel Order" — only if `status ∈ {PENDING, AWAITING_PAYMENT}`; guarded by confirmation dialog
  - "Pay Now" — only if `paymentMethod = ONLINE` and `paymentStatus ∈ {PENDING, INITIATED}`
  - "Write a Review" — only if `status = COMPLETED`

**API**:
```
GET  /api/v1/orders/{id}
POST /api/v1/orders/my/{id}/cancel
GET  /api/v1/shipments/order/{id}
GET  /api/v1/invoices/order/{id}
```

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-detail` |
| `ORDER_NOT_FOUND` | 404 page with "My Orders" link |
| Cancel — loading | Button spinner + "Cancelling…" |
| `ORDER_CANNOT_CANCEL` | Toast: "This order can no longer be cancelled" |
| Cancel success | Status stepper replaced with CANCELLED banner; action buttons removed |
| `AWAITING_PAYMENT` + payment expired | Amber banner: "Payment window has expired. Please contact support." (see §6.3) |

**Edge cases**:
- Customer opens order detail while admin is confirming it: status badge updates on next GET (manual refresh or next page load).
- Order is `AWAITING_PAYMENT` but `payment.expiredAt` has passed: show expired payment banner (see §6.3).
- Shipment not yet created: shipment mini-card shows "Shipment not yet assigned" with no link.

**Business rules**:
- All item data from immutable `OrderItem` snapshots — values never change post-creation.
- Cancellation releases inventory server-side automatically.
- `discountAmount = 0`: hide discount row entirely.

---

### 3.6 Payment

#### Online Payment Flow (detailed)

```
Order Confirmation / Order Detail
         │
    [Pay Now]
         │
         ▼
POST /payments/order/{id}/initiate
         │
    Order → AWAITING_PAYMENT
    Payment → INITIATED
         │
         ▼
Redirect to payment gateway (URL from backend / provider)
         │
    ┌────┴─────────────┐
    │                  │
  User pays       User cancels /
    │              closes tab
    │                  │
    ▼                  ▼
Gateway callback    Return URL with
POST /payments/     failed/cancelled
  callback          status param
(server-side,
 idempotent)
    │
    ▼
[Return URL → Screen: Payment Result]
         │
  GET /payments/order/{id}
  Poll until status ≠ INITIATED
  (max 30 s, 3 s interval, then show "Check status" button)
```

---

#### Screen: Payment Initiation

**Purpose**: Confirm and start the online payment process.

**Components**:
- Order code + total amount
- Payment method: "Online Payment"
- "Proceed to Payment" button

**API**:
```
POST /api/v1/payments/order/{orderId}/initiate
Body: {}
Response: PaymentResponse { id, paymentCode, method, status, amount, ... }
```

**States**:

| State | UI |
|---|---|
| Loading | Button spinner + "Redirecting to payment…" |
| `PAYMENT_ALREADY_PROCESSED` (409) | Toast: "This payment has already been processed." + "View Order" link |
| Success | Redirect to gateway URL |
| Gateway URL missing / redirect failure | Error: "Could not connect to payment provider. Try again later." |

---

#### Screen: Payment Result

**Purpose**: Show outcome after returning from gateway.

**Components**:

**PAID**:
- Green checkmark animation
- "Payment Successful"
- Amount paid + timestamp
- "View Order" button

**FAILED**:
- Red warning icon
- "Payment Failed"
- Gateway error message (if provided)
- "Try Again" button → re-initiate (re-calls `POST /initiate`)
- Note: "Your order is saved. You can retry payment from My Orders."

**PENDING / INITIATED** (gateway returned but callback not yet received):
- Spinner + "Confirming your payment…"
- Auto-poll `GET /payments/order/{id}` every 3 s, max 10 attempts (30 s)
- After timeout: "Payment confirmation is taking longer than expected." + "Check Order Status" button

**API**:
```
GET /api/v1/payments/order/{orderId}
Response: PaymentResponse { status, paidAt, amount }
```

**States**:

| Payment Status | UI |
|---|---|
| `PAID` | Success screen |
| `FAILED` | Failure screen with retry |
| `INITIATED` (polling) | Loading + countdown hint |
| Polling timeout | "Still processing" screen with manual refresh |

**Edge cases**:
- Browser tab closed during gateway redirect → customer returns later via My Orders → "Pay Now" still available if `AWAITING_PAYMENT`.
- Payment fails but `payment.expiredAt` has not passed → retry allowed.
- Payment fails and `payment.expiredAt` has passed → show "Payment window closed. Contact support." (see §6.3).

**Business rules**:
- A failed payment does **not** cancel the order; `AWAITING_PAYMENT` persists.
- Gateway callback is server-to-server and idempotent on `providerTxnId`.
- `paidAt` populated only on `status = PAID`.

---

### 3.7 Shipment Tracking

#### Screen: Shipment Tracking

**Purpose**: Real-time delivery tracking.

**Components**:
- Shipment code (copy icon) + carrier
- Tracking number (copy icon) — show as external link if carrier URL template is configured
- Status badge (colour per §5.4)
- Estimated delivery date; if `status = DELIVERED`, show actual delivery date (`deliveredAt`)
- Delivery status progress bar: `PENDING → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`
- Event timeline (most recent at top):
  - Each event: status label, location, description, timestamp
  - Latest event: highlighted with coloured left border
- "Back to Order" link
- Alert banners (status-dependent):
  - `FAILED`: amber alert — "Delivery attempt failed. Your package will be re-attempted or returned."
  - `RETURNED`: red alert — "Your package has been returned. Contact support for next steps."
  - Estimated delivery passed + not delivered: amber — "Delivery is taking longer than expected."

**API**:
```
GET /api/v1/shipments/order/{orderId}
Response: ShipmentResponse { id, shipmentCode, carrier, trackingNumber,
  status, estimatedDeliveryDate, deliveredAt, events: ShipmentEventResponse[] }
```

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-timeline` |
| `SHIPMENT_NOT_FOUND` (404) | "Tracking information is not yet available. Check back after your order is shipped." |
| `events = []` | Timeline shows "Awaiting carrier scan" placeholder |
| `DELIVERED` | Replace progress bar with "Delivered" banner; show `deliveredAt` |
| `FAILED` | Amber alert (see above) |
| `RETURNED` | Red alert (see above) |
| Overdue | Amber "delayed" banner (client compares `estimatedDeliveryDate` to today) |

**Business rules**:
- Events are immutable — append-only by admin.
- Do not show tracking screen entry point if order status < `SHIPPED`.
- "Track" CTA on My Orders only visible when `status = SHIPPED`.

**ShipmentStatus colour map**:

| Status | Colour |
|---|---|
| `PENDING` | Grey |
| `IN_TRANSIT` | Blue |
| `OUT_FOR_DELIVERY` | Amber |
| `DELIVERED` | Green |
| `FAILED` | Red |
| `RETURNED` | Red |

---

### 3.8 Invoice

#### Screen: Invoice View

**Purpose**: View formal invoice — all snapshot data.

**Components**:
- Invoice header: code, issued date, due date
- Status badge: `ISSUED` (blue) / `PAID` (green) / `VOIDED` (red)
- `VOIDED` watermark overlay on all content
- Seller info (static store name/address)
- Customer snapshot: name, email, phone (immutable)
- Billing address snapshot (immutable)
- Line items: product name, variant, SKU, qty, unit price, line total
- Pricing: subtotal, discount (voucher code), shipping fee, **total**
- "Download PDF" (Phase 2)

**API**:
```
GET /api/v1/invoices/order/{orderId}
```

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-detail` |
| `INVOICE_NOT_FOUND` | "Invoice not yet available for this order." |
| `VOIDED` | Full-page red "VOIDED" watermark; alert banner "This invoice has been voided" |
| `PAID` | Green "PAID" stamp |

**Business rules**:
- Invoice data is a snapshot taken at order creation — never changes.
- Invoice auto-generated at order creation; always present after a successful order.

---

### 3.9 Reviews

#### Review Eligibility Gate

The "Write a Review" entry point must be guarded strictly:

```
Order status = COMPLETED?
       │ Yes
       ▼
Show "Write a Review" button per order item
       │
Customer taps → Review form pre-filled with productId + orderId
       │
POST /reviews
       │
Server re-validates: customer has COMPLETED order containing productId
       │
Success → PENDING (awaiting moderation)
```

---

#### Screen: Write Review

**Components**:
- Product image + name (pre-filled, read-only)
- Star rating selector (1–5, required; tap to select, hover preview)
- Review title input (optional, max 100 chars)
- Review content textarea (required, min 10 chars, max 1000 chars; character counter)
- Submit button: "Submit Review"

**API**:
```
POST /api/v1/reviews
Body: { productId, orderId, rating, title, content }
Response: ReviewResponse { id, status: "PENDING" }
```

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-form` |
| Submit — loading | Button spinner + "Submitting…" |
| `REVIEW_NOT_ELIGIBLE` (403) | Error banner: "You can only review products from completed orders." |
| Duplicate review (409) | Error: "You have already reviewed this product." |
| 422 `fieldErrors` | Inline per-field messages |
| Success | Toast: "Review submitted — pending approval." Navigate back to order detail |

**Edge cases**:
- Customer navigates to write review URL directly without COMPLETED order: server returns `REVIEW_NOT_ELIGIBLE`; show error and redirect to My Orders.
- Customer submits review while another tab has already submitted it: server returns 409 conflict; show "You have already reviewed this product."

**Business rules**:
- Review enters `PENDING` after submission — **not visible** on product page until admin approves.
- Duplicate prevention is server-enforced.

---

#### Screen: My Reviews

**Components**:
- Review list:
  - Product image + name (link)
  - Star rating
  - Title + content (truncated to 3 lines, "Show more" expand)
  - Submission date
  - Status badge: `PENDING` (amber) / `APPROVED` (green) / `REJECTED` (red)
  - If `REJECTED`: moderation note in amber callout box
- Empty state: "You haven't written any reviews yet"

**API**:
```
GET /api/v1/reviews/my?page=0&size=10
```

---

#### Section: Product Reviews (on Product Detail)

**Components**:
- Summary bar: average rating (★ 4.2) + count ("128 reviews")
- Rating distribution: 5★ ██████ 60%, 4★ ████ 20% … (bar chart)
- Review list (APPROVED only):
  - Customer name (first name + last initial, e.g., "Nguyen V.")
  - Rating + title + content
  - Date
- "Load more" button (pagination)
- Empty state: "No reviews yet — be the first to review!"

**API**:
```
GET /api/v1/reviews/product/{productId}?page=0&size=10
```

**Business rules**:
- Endpoint returns only `APPROVED` reviews — no client filtering needed.

---

### 3.10 Vouchers (Checkout-Inline)

Voucher entry is inline in the checkout flow (Step 3). No standalone voucher screen in Phase 1.

**Complete voucher error reference for the checkout context**:

| Error Code | Field error message |
|---|---|
| `VOUCHER_NOT_FOUND` | "Voucher code not found" |
| `VOUCHER_EXPIRED` | "This voucher has expired" |
| `VOUCHER_USAGE_LIMIT_EXCEEDED` | "This voucher has reached its usage limit" |
| Re-validation fail at order create | Banner error per specific code (see §3.4 Step 4 states) |

**Race condition rule**: Voucher is validated at Step 3 (preview) and re-validated server-side at `POST /orders`. If it fails at order creation due to a race, surface the specific error and let the user remove the voucher and retry.

---

### 3.11 Notifications

#### Screen: Notifications

**Components**:
- Global unread badge on nav icon (fetched from `GET /notifications/unread-count` on app load)
- Page header: "Notifications" + badge count
- Filter tabs: All | Unread
- "Mark all as read" button (shown only when unread count > 0)
- Notification list:
  - Icon by `type`: ORDER (box), PAYMENT (credit card), SHIPMENT (truck), REVIEW (star), PROMO (tag), SYSTEM (bell)
  - Title + message (truncated, expandable on tap)
  - Relative timestamp ("2h ago", "Yesterday", full date if > 7 days)
  - Unread: left blue border + bold text
  - Read: normal weight, no border
  - Tap → mark as read (optimistic) + navigate to `relatedEntityType` / `relatedEntityId`
- Load more pagination
- Empty state: illustration + "No notifications yet"

**Deep-link targets by `relatedEntityType`**:

| `relatedEntityType` | Navigates to |
|---|---|
| `ORDER` | Order Detail (`/orders/{relatedEntityId}`) |
| `PAYMENT` | Order Detail > Payment tab |
| `SHIPMENT` | Shipment Tracking (`/orders/{orderId}/tracking`) |
| `REVIEW` | My Reviews |

**API**:
```
GET   /api/v1/notifications?page=0&size=20
GET   /api/v1/notifications/unread-count
PATCH /api/v1/notifications/{id}/read      (optimistic)
PATCH /api/v1/notifications/read-all       (optimistic)
```

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-card` × 5 |
| Empty (all) | Illustrated empty state |
| Empty (unread tab) | "You're all caught up!" with checkmark illustration |
| Mark read — error | Rollback unread state; toast error |

---

### 3.12 Profile & Addresses

#### Screen: My Profile

**Components**:
- Avatar placeholder (upload Phase 2)
- First name, last name (inputs)
- Email (read-only field with lock icon)
- Phone number (unique)
- Gender select: MALE | FEMALE | OTHER
- Birth date picker (no future dates)
- "Save Changes" button — disabled until form is dirty

**API**:
```
GET   /api/v1/me
PATCH /api/v1/me
Body: { firstName, lastName, phoneNumber, gender, birthDate }
```

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-form` |
| Unchanged form | "Save Changes" disabled |
| Saving | Button spinner + "Saving…" |
| Phone conflict (409) | Field error: "This phone number is already in use" |
| Success | Toast: "Profile updated" + clear dirty flag |

---

#### Screen: Address Book

**Components**:
- Address cards:
  - Full address (street, ward, district, city)
  - Address type badge (HOME / OFFICE / OTHER)
  - "Default" badge if `isDefault = true`
  - "Edit" button → inline edit form
  - "Set as Default" button (hidden on default address)
  - "Delete" button → confirmation dialog
- "Add Address" button → inline form (same pattern as checkout Step 1)
- Empty state: "No saved addresses" + "Add your first address"

**API**:
```
GET    /api/v1/addresses
POST   /api/v1/addresses
PATCH  /api/v1/addresses/{id}
DELETE /api/v1/addresses/{id}
```

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-card` × 2 |
| Delete — loading | Card fades to 40% opacity; spinner on delete button |
| Delete success | Card removed; if deleted was default and other addresses exist, none set as default |
| Limit (if any) | Server returns 422 if implementation caps addresses |

**Business rules**:
- Soft-deleted addresses hidden from UI.
- Address snapshot on order is independent of address record — safe to delete.
- Only one default at a time (server handles unset on new default set).

---

## 4. Admin — Flows & Screens

> All admin screens require `STAFF`/`ADMIN`/`SUPER_ADMIN`.
> All admin list screens implement the **standard table UX** from §2.12.
> Role-gated write actions (`ADMIN` only) are disabled (greyed, tooltip "Requires Admin role") for `STAFF` users.

---

### 4.1 Dashboard

#### Screen: Admin Dashboard

**Purpose**: KPI overview + actionable queues.

**Layout**: 2-column grid (KPI cards top) + 2-column panels (recent orders / queues bottom).

**KPI Cards** (assembled from standard API endpoints with filters):

| Card | API | Filter |
|---|---|---|
| Orders today | `GET /admin/orders` | `createdAt=today` |
| Revenue today | `GET /admin/payments` | `status=PAID&paidAt=today` |
| Pending confirmation | `GET /admin/orders` | `status=PENDING,AWAITING_PAYMENT` |
| Pending reviews | `GET /admin/reviews/pending` | — |
| Low stock variants | `GET /admin/inventories/reservations` | `available≤5` |
| Shipments out for delivery | `GET /admin/shipments` | `status=OUT_FOR_DELIVERY` |

**Panels**:
- Recent orders (last 10): order code, customer, status, amount, "Confirm" quick-action if PENDING
- Pending reviews queue: product name, rating, customer, "Approve" / "Reject" inline buttons
- Low stock alerts: variant name, SKU, warehouse, available count, "Import Stock" link

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-stat` × 6 + `skeleton-table` × 2 |
| API error on any card | Card shows "—" with refresh icon |

**Business rules**:
- No dedicated dashboard API — aggregate from standard endpoints.
- KPI cards are informational only; tapping navigates to the respective filtered list screen.

---

### 4.2 Product Management

#### Lifecycle (Admin)

```
DRAFT ──[Publish]──▶ PUBLISHED ──[Archive]──▶ ARCHIVED
  ▲                     │
  └────[Revert draft]───┘ (if no active orders)
```

Only `PUBLISHED` products appear to customers. `ARCHIVED` products are visible in admin only.

---

#### Screen: Admin Product List

**Standard table UX** (§2.12) plus:

**Columns**: Thumbnail | Name | Brand | Categories | Status | Variant count | Created | Actions

**Filters**: Status (multi: DRAFT/PUBLISHED/ARCHIVED), Category, Brand, Featured (yes/no)

**Sort**: Name, Created date, Updated date

**Bulk actions**:
- "Publish selected" — sets status to `PUBLISHED` for all selected DRAFT items
- "Archive selected" — sets status to `ARCHIVED` for all selected PUBLISHED items
- "Delete selected" — soft-delete with confirmation dialog

**Row actions** (kebab):
- Edit, View (public), Manage Variants, Delete

**API**:
```
GET    /api/v1/admin/products?page=0&size=20&status={}&categoryId={}&brandId={}&keyword={}
PATCH  /api/v1/admin/products/{id}   (bulk status via individual calls)
DELETE /api/v1/admin/products/{id}
```

---

#### Screen: Create / Edit Product

**Components**:
- Product name (required, unique; slug auto-generated, editable)
- Brand select
- Categories multi-select
- Short description (textarea, max 300 chars)
- Full description (rich text editor)
- Status select: DRAFT / PUBLISHED / ARCHIVED
- Featured toggle
- "Save" button; "Save & Publish" shortcut button (sets status=PUBLISHED then saves)
- Unsaved changes guard (§2.6)

**Warning banner** (client-side check):
- If setting `status = PUBLISHED` and product has 0 active variants: amber warning "This product has no active variants. Customers will not be able to purchase it."
- Allow publish anyway (server does not block this).

**API**:
```
POST  /api/v1/admin/products
PATCH /api/v1/admin/products/{id}
Body: { name, slug, brandId, categoryIds, shortDescription, description, status, featured }
DELETE /api/v1/admin/products/{id}
```

**States**:

| State | UI |
|---|---|
| Loading (edit) | `skeleton-form` |
| Name/slug conflict (409) | Field error: "A product with this name/slug already exists" |
| Saving | Button spinner |
| Save success (create) | Navigate to product edit page with success toast |
| Save success (edit) | Toast: "Product saved"; stay on page |

---

#### Screen: Variant Management (sub-panel)

**Components**:
- Variant table: SKU | Name | Price | Sale Price | Status | Actions
- Inline "Add Variant" form (toggle open/closed)
- Edit variant: inline row edit or modal
- Delete variant: confirmation dialog

**Variant form fields**:
- SKU (required, globally unique)
- Variant name (required)
- Price (`DECIMAL(18,2)`, required, > 0)
- Sale price (optional; client validates `salePrice ≤ price`)
- Weight, dimensions (optional)
- Status: ACTIVE / INACTIVE
- Attributes: dynamic key-value pairs (Color=White, Size=M)

**States**:

| State | UI |
|---|---|
| SKU conflict (409) | Field error: "This SKU is already in use" |
| `salePrice > price` (client) | Field error: "Sale price must not exceed regular price" |
| Delete with active reservations (409/422) | Error: "Cannot delete variant with active inventory reservations" |

**Business rules**:
- `INACTIVE` variant hidden from customer product detail.
- SKU globally unique across all products/variants.

---

### 4.3 Category & Brand Management

Both screens follow the same pattern; documented together.

**Standard table UX** (§2.12).

**Category columns**: Name | Slug | Status | Created | Actions
**Brand columns**: Name | Slug | Status | Created | Actions

**Inline create/edit modal** (not a full page):
- Name (required), Slug (auto-generated, editable), Status (ACTIVE/INACTIVE)
- Brand-only: Logo URL field

**Soft-delete**: confirmation dialog; shows "This will hide the {category/brand} from all listings."

**API**:
```
GET    /api/v1/categories | /api/v1/brands
POST   /api/v1/admin/categories | /api/v1/admin/brands
PATCH  /api/v1/admin/categories/{id} | /api/v1/admin/brands/{id}
DELETE /api/v1/admin/categories/{id} | /api/v1/admin/brands/{id}
```

---

### 4.4 Inventory & Warehouse Management

#### Warehouse Screen

**Standard table UX** (§2.12).

**Columns**: Name | Location | Status | Created | Actions

**Inline create/edit modal**: Name (required), Location (required), Status toggle.

**API**:
```
GET    /api/v1/admin/warehouses
POST   /api/v1/admin/warehouses
PATCH  /api/v1/admin/warehouses/{id}
DELETE /api/v1/admin/warehouses/{id}
```

---

#### Screen: Inventory by Variant

**Components**:
- Product → Variant selector (cascade: select product first, then variant)
- Inventory grid (one row per warehouse):

| Warehouse | On Hand | Reserved | Available | Actions |
|---|---|---|---|---|
| Main WH | 50 | 8 | **42** | Import / Adjust |

  - `available = onHand - reserved` — computed display, never persisted
  - Available < 5: highlight red; Available 5–20: highlight amber
- Import Stock modal:
  - Quantity (positive int, required)
  - Note (optional)
  - Confirm button
- Adjust Stock modal:
  - Delta (positive = increase, negative = decrease)
  - Reason (required, select: DAMAGE / RETURN / CORRECTION / OTHER)
  - Note (optional)
  - Confirm button — disabled if `onHand + delta < reserved`
- Stock Movements tab (paginated log):

**Stock Movements table**: Date | Type | Qty | Warehouse | Order ref | Admin | Note

**API**:
```
GET  /api/v1/admin/inventories/variant/{variantId}
POST /api/v1/admin/inventories/import   Body: { variantId, warehouseId, quantity, note }
POST /api/v1/admin/inventories/adjust   Body: { variantId, warehouseId, quantityDelta, reason }
GET  /api/v1/admin/inventories/stock-movements?variantId={}&warehouseId={}&page=0&size=20
```

**States**:

| State | UI |
|---|---|
| No variant selected | Empty state: "Select a product and variant to view inventory" |
| Import success | Toast: "Stock imported"; refresh inventory grid |
| Adjust — would go below reserved | Confirm button disabled; inline: "Cannot reduce below {reserved} reserved units" |
| `INVENTORY_NOT_ENOUGH` (server) | Toast error |

**StockMovementType labels**:

| Type | Label | Icon |
|---|---|---|
| `IMPORT` | Stock Received | ↓ green |
| `ADJUSTMENT` | Manual Adjustment | ↺ grey |
| `RESERVATION` | Reserved for Order | 🔒 amber |
| `RELEASE` | Reservation Released | 🔓 blue |
| `FULFILLMENT` | Order Fulfilled | ✓ green |

---

#### Screen: Inventory Reservations

**Standard table UX** + filter by status (ACTIVE / RELEASED).

**Columns**: Variant | SKU | Warehouse | Qty | Order code (link) | Status | Created

**API**:
```
GET /api/v1/admin/inventories/reservations?page=0&size=20&status={}
```

**Business rules**:
- `reserved` column is modified only by order creation (increase) and order cancellation (decrease).
- Admin cannot directly edit reservations — read-only screen.

---

### 4.5 Order Management

#### Admin Order Lifecycle — Swimlane

```
         Customer              System (auto)           Admin
────────────────────────────────────────────────────────────────
Places order ──────────────────────────────────────▶ PENDING
                                                         │
[ONLINE] Initiates payment ──────────────────▶ AWAITING_PAYMENT
                                                         │
Cancels order (if PENDING/AWAITING) ─────────▶ CANCELLED ◀── terminal
                                                         │
                                              Admin confirms ▶ CONFIRMED
                                                         │
                                            Admin processes ▶ PROCESSING
                                                         │
                               Admin creates shipment ──▶ SHIPPED (auto)
                                                         │
                        Admin sets shipment DELIVERED ──▶ DELIVERED (auto)
                                                         │
                                            Admin completes ▶ COMPLETED
                                                         │
                             Customer writes reviews now  │
────────────────────────────────────────────────────────────────
```

---

#### Screen: Admin Order List

**Standard table UX** (§2.12) plus:

**Columns**: Order Code | Customer | Status | Payment Method | Payment Status | Total | Date | Actions

**Filters**: Status (multi-select), Payment Method (COD/ONLINE), Payment Status, Date range (from/to)

**Search**: by order code, customer name, customer email

**Sort**: created date, total amount

**Bulk actions**:
- "Confirm selected" — bulk `POST /confirm` for `PENDING`/`AWAITING_PAYMENT` orders
- Confirmation dialog: "Confirm {n} orders?"
- After bulk confirm: refresh table; toast "3 orders confirmed"

**Row quick actions**:
- "Confirm" button (inline, visible only if status = PENDING or AWAITING_PAYMENT)
- "Process" button (inline, visible only if status = CONFIRMED)
- "View" link

**API**:
```
GET  /api/v1/admin/orders?page=0&size=20&status={}&paymentMethod={}&...
GET  /api/v1/admin/orders/code/{orderCode}
POST /api/v1/admin/orders/{id}/confirm
POST /api/v1/admin/orders/{id}/process
```

---

#### Screen: Admin Order Detail

**Components**:
- Status stepper (same visual as customer, but shows admin-action labels):
  ```
  Placed → Confirmed → Processing → Shipped → Delivered → Completed
  ```
- All order data (items, pricing, address, payment summary, shipment mini-card, invoice link)
- Admin note textarea (editable, `PATCH /admin/orders/{id}` or inline save via existing PATCH)
- **Action button panel** (dynamic per status):

| Status | Primary Button | Secondary |
|---|---|---|
| `PENDING` | "Confirm Order" (blue) | — |
| `AWAITING_PAYMENT` | "Confirm Order" (blue) | — |
| `CONFIRMED` | "Mark as Processing" (blue) | — |
| `PROCESSING` | "Create Shipment" (blue → opens shipment form) | — |
| `SHIPPED` | *(no button — driven by shipment status update)* | "View Shipment" |
| `DELIVERED` | "Mark as Completed" (green) | — |
| `COMPLETED` | *(no actions)* | — |
| `CANCELLED` | *(terminal — no actions)* | — |

**API**:
```
GET  /api/v1/admin/orders/{id}
POST /api/v1/admin/orders/{id}/confirm
POST /api/v1/admin/orders/{id}/process
POST /api/v1/admin/orders/{id}/deliver
POST /api/v1/admin/orders/{id}/complete
```

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-detail` |
| Action — loading | Button spinner + "Updating…" |
| `ORDER_STATUS_INVALID` (concurrency) | Toast: "Order status changed by another user. Refreshing…" + auto-reload |
| Transition success | Status stepper advances; action buttons update; toast: "Order confirmed" |

**Edge cases**:
- Two admins confirming the same order simultaneously: second confirm returns `ORDER_STATUS_INVALID`; show stale-data toast and reload.
- Admin tries "Create Shipment" but order is already `SHIPPED` (another admin created shipment): same stale-data toast.

**Business rules**:
- Admin cannot cancel orders — cancellation is customer-initiated only.
- `DELIVERED` transition is driven automatically by shipment status update; "Mark Delivered" button is a fallback only.
- `COMPLETED` enables the customer's "Write a Review" button.

---

### 4.6 Payment Management

#### Screen: Admin Payment List

**Standard table UX** (§2.12).

**Columns**: Payment Code | Order Code | Customer | Method | Status | Amount | Paid At | Actions

**Filters**: Method (COD/ONLINE), Status (multi-select), Date range

**Search**: payment code, order code, customer name

**Row actions**: View | Mark as Paid (COD + PENDING only)

**API**:
```
GET /api/v1/admin/payments?page=0&size=20&method={}&status={}
```

---

#### Screen: Admin Payment Detail

**Components**:
- Payment code, order link, method, status badge, amount, paidAt
- **"Mark as Paid" button** — shown only when `method = COD` AND `status = PENDING`
  - Confirmation dialog: "Mark this COD payment as paid?"
  - On confirm: `POST /admin/payments/order/{orderId}/complete`
- Transaction History table (immutable, read-only):

**Columns**: Code | Status | Amount | Method | Provider | Provider Tx ID | Reference | Note | Created By | Created At

**API**:
```
GET  /api/v1/admin/payments/{id}
GET  /api/v1/admin/payments/{id}/transactions
POST /api/v1/admin/payments/order/{orderId}/complete
```

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-detail` |
| Mark paid — loading | Button spinner |
| `PAYMENT_ALREADY_PROCESSED` (409) | Toast: "This payment has already been processed" |
| Mark paid success | Status badge → PAID; button disappears; toast: "Payment marked as paid" |

**Business rules**:
- `PaymentTransaction` records are immutable — no edit/delete in UI.
- Online payments cannot be manually marked PAID — admin button only shows for COD.

---

### 4.7 Shipment Management

#### Shipment Status Machine — Admin Controls

```
PENDING ──[Mark In Transit]──▶ IN_TRANSIT ──[Mark Out for Delivery]──▶ OUT_FOR_DELIVERY
   │                                │                                          │
   └──[Fail]──▶ FAILED              └──[Fail]──▶ FAILED                       ├──[Fail]──▶ FAILED
                   │                                                           │
                   └──▶ RETURNED (terminal)                    [Mark Delivered]▼
                                                               DELIVERED (terminal)
                                                               ↓ auto: Order → DELIVERED
```

---

#### Screen: Admin Shipment List

**Standard table UX** (§2.12).

**Columns**: Code | Order Code | Carrier | Tracking | Status | Est. Delivery | Created | Actions

**Filters**: Status (multi), Carrier, Date range

**Row actions**: View | Update Status

**API**:
```
GET /api/v1/admin/shipments?page=0&size=20&status={}
```

---

#### Screen: Create Shipment

**Components**:
- Order selector (pre-filled from order detail; shows order code + customer name)
- Carrier (required, text input)
- Tracking number (text input; copy icon after save)
- Estimated delivery date (date picker; must be ≥ today)
- Shipping fee (`DECIMAL(18,2)`)
- Note (textarea)
- "Create Shipment" button

**API**:
```
POST /api/v1/admin/shipments
Body: { orderId, carrier, trackingNumber, estimatedDeliveryDate, shippingFee, note }
```

**States**:

| State | UI |
|---|---|
| Submitting | Button spinner + "Creating…" |
| `ORDER_NOT_FOUND` | Field error on order: "Order not found" |
| `ORDER_STATUS_INVALID` | Error: "Order must be in PROCESSING state to create a shipment" |
| Shipment already exists (409/conflict) | Error: "A shipment already exists for this order" |
| Success | Navigate to Shipment Detail; toast: "Shipment created — order is now SHIPPED" |

---

#### Screen: Admin Shipment Detail

**Components**:
- Shipment code, order link, carrier, tracking number (copy icon)
- Status badge
- Estimated delivery date; `deliveredAt` (if DELIVERED)
- Shipping fee + note
- "Edit Shipment" button → modal (update carrier, tracking, estimate — does not change status)
- **Status Update panel** (hidden when terminal):
  - Current status label
  - "Update Status" button → modal:
    - Status select (only valid next states, others disabled with tooltip "Not a valid transition")
    - Location (text, optional)
    - Description (text, optional)
    - Event time (datetime picker, defaults to now)
    - Confirm button
- Event Timeline (immutable, append-only):
  - Each entry: status label + location + description + timestamp

**API**:
```
GET   /api/v1/admin/shipments/{id}
PATCH /api/v1/admin/shipments/{id}
PATCH /api/v1/admin/shipments/{id}/status
Body: { status, location, description, eventTime }
```

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-detail` |
| Terminal status (DELIVERED / RETURNED) | Status Update panel hidden; "No further updates available" note |
| Status update — loading | Button spinner |
| Status update success | Timeline appends new event; status badge updates; if DELIVERED: toast "Order automatically marked as DELIVERED" |
| `ORDER_STATUS_INVALID` (auto-transition conflict) | Toast: "Order status updated by another user. Refresh to see current state." |

**Valid next-state map** (drives the status select dropdown):

| Current | Valid next states |
|---|---|
| `PENDING` | `IN_TRANSIT`, `FAILED` |
| `IN_TRANSIT` | `OUT_FOR_DELIVERY`, `FAILED` |
| `OUT_FOR_DELIVERY` | `DELIVERED`, `FAILED` |
| `FAILED` | `RETURNED` |
| `DELIVERED` | *(none — terminal)* |
| `RETURNED` | *(none — terminal)* |

---

### 4.8 Invoice Management

#### Screen: Admin Invoice (per Order)

**Components**:
- Same content as customer Invoice View + admin-only status actions
- **Status action panel** (hidden when terminal):
  - Current status badge
  - "Mark as Paid" button (ISSUED → PAID) — visible only when `status = ISSUED`
  - "Void Invoice" button (ISSUED → VOIDED) — visible only when `status = ISSUED`; requires confirmation dialog with mandatory note field

**API**:
```
POST  /api/v1/admin/invoices              Body: { orderId }   (manual fallback)
GET   /api/v1/admin/invoices/{id}
PATCH /api/v1/admin/invoices/{id}/status  Body: { status: "PAID"|"VOIDED", note }
```

**States**:

| State | UI |
|---|---|
| `VOIDED` | "VOIDED" watermark; all action buttons hidden |
| `PAID` | "PAID" stamp; all action buttons hidden |
| Void — loading | Button spinner; overlay on content |
| Void success | Toast: "Invoice voided"; VOIDED watermark appears |

---

### 4.9 Promotion & Voucher Management

> Write operations (`POST`/`PATCH`/`DELETE`) require `ADMIN`+. STAFF sees read-only views.

#### Screen: Promotion List

**Standard table UX** (§2.12).

**Columns**: Name | Scope | Discount | Usage | Date Range | Active | Actions

**Filters**: Active status, Scope, Discount type

**Row actions**: View/Edit (ADMIN), Delete (ADMIN)

**API**:
```
GET /api/v1/admin/promotions?page=0&size=20
```

---

#### Screen: Create / Edit Promotion

**Components**:
- Name (required), Description
- Discount type: `PERCENTAGE` | `FIXED_AMOUNT`
- Discount value:
  - PERCENTAGE: number input 0–100; client validates range
  - FIXED_AMOUNT: decimal input > 0
- Max discount amount (PERCENTAGE only; optional cap)
- Minimum order amount (optional)
- Scope: ORDER | PRODUCT | CATEGORY | BRAND
- Start date + End date (date-time pickers; end > start)
- Usage limit (blank = unlimited)
- Active toggle
- **Rules section**:
  - Rule list with Type | Value | Description
  - "Add Rule" → inline form:
    - Type: `MIN_ORDER_AMOUNT` | `SPECIFIC_PRODUCTS` | `SPECIFIC_CATEGORIES` | `SPECIFIC_BRANDS` | `FIRST_ORDER`
    - Value: comma-separated IDs (SPECIFIC_*) or amount (MIN_ORDER_AMOUNT) or empty (FIRST_ORDER)
    - Description
  - Delete rule button (with confirmation)

**API**:
```
POST  /api/v1/admin/promotions
PATCH /api/v1/admin/promotions/{id}
DELETE /api/v1/admin/promotions/{id}
POST  /api/v1/admin/promotions/{id}/rules
PATCH /api/v1/admin/promotions/{id}/rules/{ruleId}
DELETE /api/v1/admin/promotions/{id}/rules/{ruleId}
```

**States**:

| State | UI |
|---|---|
| PERCENTAGE > 100 (client) | Field error: "Discount percentage must be between 0 and 100" |
| End before start (client) | Field error: "End date must be after start date" |
| Saving | Button spinner |

---

#### Screen: Voucher List

**Standard table UX** (§2.12).

**Columns**: Code | Promotion | Usage (used/limit) | Per-User Limit | Date Range | Active | Actions

**Filters**: Active, Promotion, Date range (active/expired)

**Search**: by code

**Row actions**: View (STAFF+), Edit (ADMIN), Delete (ADMIN), View Usages

**API**:
```
GET /api/v1/admin/vouchers?page=0&size=20
```

---

#### Screen: Create / Edit Voucher

**Components**:
- Code (required, unique; "Generate" button auto-creates random code)
- Linked promotion (required — select from active promotions)
- Usage limit (blank = unlimited; positive int)
- Per-user usage limit (blank = unlimited; positive int)
- Start date + End date
- Active toggle

**API**:
```
POST  /api/v1/admin/vouchers
PATCH /api/v1/admin/vouchers/{id}
DELETE /api/v1/admin/vouchers/{id}
```

---

#### Screen: Voucher Usage History

**Standard table UX** (§2.12, read-only).

**Header**: Voucher code, promotion name, total usages used / limit.

**Columns**: Customer ID | Order Code | Discount Amount | Used At

**API**:
```
GET /api/v1/admin/vouchers/{id}/usages?page=0&size=20
```

---

### 4.10 Review Moderation

#### Screen: Review Moderation Queue

**Standard table UX** (§2.12) plus:

**Layout option**: Dual-pane (list left, review detail right) for faster throughput.

**Columns**: Product | Customer | Rating | Snippet | Submitted | Status | Actions

**Filters**: Status (PENDING / APPROVED / REJECTED)

**Sort**: Submitted date (default: oldest first — process in order)

**Bulk actions**:
- "Approve selected" (bulk approve PENDING reviews)
- No bulk reject — rejection requires a per-review note

**Row actions** (and keyboard shortcuts when review is focused):
- "Approve" — green button (keyboard: `A`)
- "Reject" — red button → modal with required note (keyboard: `R` → opens modal)

**Reject modal**:
- Moderation note textarea (required, min 10 chars)
- "Reject Review" button (disabled until note filled)
- "Cancel" button

**API**:
```
GET   /api/v1/admin/reviews/pending?page=0&size=20
GET   /api/v1/admin/reviews/{id}
PATCH /api/v1/admin/reviews/{id}/moderate
Body: { status: "APPROVED"|"REJECTED", moderationNote }
```

**States**:

| State | UI |
|---|---|
| Loading | `skeleton-table` |
| Empty (PENDING) | "No reviews pending — you're all caught up!" |
| Approve — loading | Row spinner; disable both buttons |
| Approve success | Remove row from PENDING list; toast: "Review approved" + customer notified |
| Reject success | Remove row from PENDING list; toast: "Review rejected" |

**Business rules**:
- `moderationNote` is **required** on rejection — visible to customer in My Reviews.
- Moderation action triggers `REVIEW_MODERATED` notification to customer (server-side).
- Bulk approve sends individual requests per review; show progress bar for large batches.

---

### 4.11 Audit Log

#### Screen: Audit Log

**Standard table UX** (§2.12, read-only — no bulk actions, no create button).

**Columns**: Timestamp | Admin | Action | Entity Type | Entity ID | IP Address | Changes

**Filters**: Action type (multi-select from `AuditAction` enum), Entity type, Admin user, Date range

**Sort**: Timestamp (default: newest first)

**Changes column**: collapsed JSON diff ("2 fields changed") → expand on click to show full old/new values.

**Entity ID**: rendered as link to the relevant admin screen (e.g., ORDER → order detail).

**API**:
```
GET /api/v1/admin/audit-logs?page=0&size=20&action={}&entityType={}&userId={}&...
```

*(Endpoint path assumed consistent with admin prefix — verify with implementation.)*

**Logged Actions** (`AuditAction` enum):

| Category | Actions |
|---|---|
| Order | `ORDER_CREATED`, `ORDER_CONFIRMED`, `ORDER_CANCELLED`, `ORDER_COMPLETED` |
| Inventory | `STOCK_IMPORTED`, `STOCK_ADJUSTED` |
| Product | `PRODUCT_CREATED`, `PRODUCT_UPDATED`, `PRODUCT_DELETED` |
| Voucher | `VOUCHER_CREATED`, `VOUCHER_UPDATED`, `VOUCHER_DELETED` |
| Payment | `PAYMENT_COMPLETED` |
| Admin | `REVIEW_MODERATED`, `USER_DISABLED` |

**Business rules**:
- Audit log entries are immutable — no edit, delete, or export buttons (Phase 1).
- `SUPER_ADMIN` access recommended for the most sensitive entries (`USER_DISABLED`, `PAYMENT_COMPLETED`).

---

## 5. State Machines Reference

### 5.1 Order Status — Full Machine

```
                         ┌──────────────┐
                         │   PENDING    │◀── initial state (all orders)
                         └──────┬───────┘
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
              │          [ONLINE payment          │
              │           initiated]              │
              ▼                 ▼                 │
         ┌──────────┐  ┌──────────────────┐      │
         │CANCELLED │  │ AWAITING_PAYMENT │      │
         │(terminal)│  └────────┬─────────┘      │
         └──────────┘           │                 │
              ▲                 │ [customer        │
              │                 │  cancels]        │
              └─────────────────┘                 │
              ▲                                   │
              │ [customer cancels]                 │
              │                                   │
              └──────── ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
                                │
                          [Admin confirms]
                                │
                                ▼
                         ┌────────────┐
                         │ CONFIRMED  │
                         └─────┬──────┘
                               │ [Admin processes]
                               ▼
                        ┌────────────┐
                        │ PROCESSING │
                        └─────┬──────┘
                              │ [Admin creates shipment → auto]
                              ▼
                         ┌─────────┐
                         │ SHIPPED │
                         └────┬────┘
                              │ [Shipment → DELIVERED → auto]
                              ▼
                        ┌───────────┐
                        │ DELIVERED │
                        └─────┬─────┘
                              │ [Admin completes]
                              ▼
                        ┌───────────┐
                        │ COMPLETED │◀── enables customer reviews
                        └─────┬─────┘
                              │ [Phase 2: refund]
                              ▼
                        ┌──────────┐
                        │ REFUNDED │ (terminal)
                        └──────────┘
```

**Transition authority**:

| Transition | Who |
|---|---|
| → `AWAITING_PAYMENT` | Customer (initiates payment) |
| → `CANCELLED` | Customer (from PENDING/AWAITING_PAYMENT) |
| → `CONFIRMED` | Admin |
| → `PROCESSING` | Admin |
| → `SHIPPED` | System (auto on shipment creation) |
| → `DELIVERED` | System (auto on shipment status = DELIVERED) |
| → `COMPLETED` | Admin |

---

### 5.2 Payment Status (Order-level `paymentStatus` field)

```
PENDING → PAID
        → FAILED
        → REFUNDED  (Phase 2)
```

### 5.3 Payment Record Status (`Payment.status`)

```
PENDING ──[initiate]──▶ INITIATED ──[callback: success]──▶ PAID
                                  └──[callback: fail]────▶ FAILED
        ──[COD mark paid]──────────────────────────────▶ PAID
        ──[Phase 2: refund]────────────────────────────▶ REFUNDED / PARTIALLY_REFUNDED
```

---

### 5.4 Shipment Status

```
PENDING ─┬──[In Transit]──▶ IN_TRANSIT ─┬──[Out for Delivery]──▶ OUT_FOR_DELIVERY
         │                              │                                 │
         └──[Fail]──▶ FAILED            └──[Fail]──▶ FAILED              ├──[Fail]──▶ FAILED
                         │                               │                │
                         └───────────┬───────────────────┘                │
                                     ▼                                     │
                                 RETURNED (terminal)            [Delivered]▼
                                                               DELIVERED (terminal)
                                                               → auto: Order → DELIVERED
```

---

### 5.5 Invoice Status

```
ISSUED ──[Admin: mark paid]──▶ PAID   (terminal)
       ──[Admin: void]───────▶ VOIDED (terminal)
```

### 5.6 Review Status

```
PENDING ──[Admin approve]──▶ APPROVED (terminal — public)
        ──[Admin reject]───▶ REJECTED (terminal — customer-visible with note)
```

### 5.7 Product Status

```
DRAFT ──[Publish]──▶ PUBLISHED ──[Archive]──▶ ARCHIVED
```

Only `PUBLISHED` products visible to customers.

---

## 6. Edge Cases & Race Conditions

### 6.1 Inventory Race — Item Sells Out Between Cart and Checkout

**Scenario**: Customer adds item to cart (stock = 3), then another customer purchases the last 3 units before checkout.

**Detection point**: `POST /orders` → `INVENTORY_NOT_ENOUGH` or `STOCK_RESERVATION_FAILED`.

**UI response**:
1. Dismiss the "Placing order…" overlay.
2. Show error banner on checkout review step: "Some items in your cart are no longer available."
3. "Return to Cart" button → navigate to cart.
4. On cart screen, per-item stock check shows the item as out of stock.
5. Customer must remove the item before proceeding.

**Do not**: auto-remove items from cart; auto-retry the order; show generic error.

---

### 6.2 Voucher Race — Voucher Expires Between Validate and Order

**Scenario**: Customer validates voucher (succeeds), then before placing the order, the voucher expires or hits its usage limit.

**Detection point**: `POST /orders` → `VOUCHER_EXPIRED` / `VOUCHER_USAGE_LIMIT_EXCEEDED`.

**UI response**:
1. Stay on checkout review step (do not navigate away).
2. Show inline error on voucher row: contextual message per error code.
3. "Remove voucher" button highlighted.
4. After removal: customer can proceed without the voucher.

**Do not**: silently ignore the voucher and place the order without the discount.

---

### 6.3 Payment Window Expiry

**Scenario**: Online payment has `payment.expiredAt` set (payment provider sets a time limit). Customer returns to the order after the window has closed.

**Detection**: Client compares `payment.expiredAt` to current time when loading Order Detail.

**UI response** (Order Detail + Payment Result screens):
- Amber banner: "The payment window for this order has expired."
- If `status = AWAITING_PAYMENT`: "Please contact support to arrange payment or place a new order."
- Remove "Pay Now" button (initiating payment on an expired order will be rejected by the gateway).

**Do not**: attempt to re-initiate payment after expiry.

---

### 6.4 Concurrent Admin Order Transitions

**Scenario**: Two admins have the same order open. Admin A confirms it; Admin B then tries to confirm (now invalid).

**Detection**: `ORDER_STATUS_INVALID` from `POST /confirm`.

**UI response** (Admin Order Detail):
- Toast: "This order was updated by another user. Refreshing…"
- Auto-reload order detail after 1 s.
- Updated state replaces stale state.

**Same pattern applies for**: shipment status updates, invoice status changes.

---

### 6.5 Stale Cart Items on Cart Load

**Scenario**: Customer added a variant to cart earlier; variant has since been set to `INACTIVE` or deleted.

**Detection**: Cart item response includes variant status, or variant detail call returns `INACTIVE`/`NOT_FOUND`.

**UI response** (Cart Screen):
- Amber banner at top of cart: "One or more items in your cart are no longer available."
- Affected rows highlighted in amber with "Remove" button.
- "Proceed to Checkout" disabled until stale items removed.

**Do not**: silently remove items from cart without customer action.

---

### 6.6 Session Expiry During Checkout

**Scenario**: Customer is filling out checkout form; session token expires mid-flow.

**Detection**: Any API call in checkout returns 401; interceptor attempts refresh; refresh also fails (token fully expired).

**UI response**:
1. Interceptor clears tokens.
2. Redirect to `/login?redirect=/checkout`.
3. Show toast: "Session expired. Please sign in to continue your order."
4. After login: redirect back to `/checkout`.
5. Cart is still intact (server-side, `ACTIVE` cart persists).
6. Address selections and form inputs are lost (not persisted client-side) — customer re-fills.

**Mitigation** (UX): Consider saving checkout step and form state to `sessionStorage` before redirect so it can be restored on return.

---

### 6.7 Duplicate Review Submission

**Scenario**: Customer submits review; tap/click fires twice (double submit); or customer navigates back and re-submits.

**Detection**: Server returns 409 on duplicate review for same product+order.

**UI response**:
- "Place Order" button disabled after first click (§3.4 business rule).
- For review: server 409 → show toast: "You have already submitted a review for this product."
- Navigate to My Reviews to show the existing review.

---

### 6.8 Overdue Estimated Delivery

**Scenario**: `shipment.estimatedDeliveryDate` has passed but shipment status is not yet `DELIVERED`.

**Detection**: Client compares `estimatedDeliveryDate` to today.

**UI response** (Shipment Tracking screen):
- Amber banner: "Your delivery was expected by {date}. We're following up with the carrier."
- No action required from customer; informational only.

---

### 6.9 Product Archived After Cart Add

**Scenario**: Product is `ARCHIVED` after customer adds it to cart. Product is no longer visible in listing but the cart item still references the variant.

**Detection**: `POST /orders` will fail at inventory reservation if variant is inactive.

**UI response**: Same as §6.1 (inventory race) — "Some items are no longer available."

---

## 7. Error Codes Reference

| Error Code | HTTP | Screen Context | User-facing Message |
|---|---|---|---|
| `INVALID_CREDENTIALS` | 401 | Login | "Email or password is incorrect" |
| `TOKEN_EXPIRED` | 401 | Any | Silent refresh → if fails: "Session expired. Please sign in again." |
| `TOKEN_INVALID` | 401 | Any | "Session invalid. Please sign in again." |
| `ACCOUNT_DISABLED` | 403 | Login | "Your account has been disabled. Contact support." |
| `ACCOUNT_ALREADY_EXISTS` | 409 | Register | "An account with this email already exists" |
| `PRODUCT_NOT_FOUND` | 404 | Product Detail | "Product not found" |
| `PRODUCT_INACTIVE` | 422 | Cart add | "This product is currently unavailable" |
| `INVENTORY_NOT_ENOUGH` | 422 | Cart, Checkout, Inventory adjust | "Insufficient stock. Only {n} units available." |
| `VARIANT_OUT_OF_STOCK` | 422 | Cart, Product Detail | "This item is out of stock" |
| `STOCK_RESERVATION_FAILED` | 422 | Checkout | "Unable to reserve stock. Please return to your cart." |
| `CART_ITEM_QUANTITY_INVALID` | 422 | Cart | "Quantity must be at least 1" |
| `ORDER_NOT_FOUND` | 404 | Order Detail | "Order not found" |
| `ORDER_STATUS_INVALID` | 422 | Order actions | "This action cannot be performed — order status has changed. Refresh and try again." |
| `ORDER_CANNOT_CANCEL` | 422 | Order cancel | "This order can no longer be cancelled" |
| `PAYMENT_NOT_FOUND` | 404 | Payment screens | "Payment record not found" |
| `PAYMENT_FAILED` | 422 | Payment result | "Payment could not be processed. Please try again." |
| `PAYMENT_ALREADY_PROCESSED` | 409 | Admin payment | "This payment has already been processed" |
| `VOUCHER_NOT_FOUND` | 404 | Checkout voucher | "Voucher code not found" |
| `VOUCHER_EXPIRED` | 422 | Checkout voucher | "This voucher has expired" |
| `VOUCHER_USAGE_LIMIT_EXCEEDED` | 422 | Checkout voucher | "This voucher has reached its usage limit" |
| `SHIPMENT_NOT_FOUND` | 404 | Tracking | "Shipment information not yet available" |
| `INVOICE_NOT_FOUND` | 404 | Invoice | "Invoice not yet available for this order" |
| `REVIEW_NOT_FOUND` | 404 | Review | "Review not found" |
| `REVIEW_NOT_ELIGIBLE` | 403 | Write Review | "You can only review products from completed orders" |
| `CONFLICT` | 409 | Admin forms | "A record with this value already exists" |
| `INTERNAL_SERVER_ERROR` | 500 | Any | "Something went wrong. Please try again later." |

---

*End of UI/UX Specification — Fashion Shop Platform*
*Revised 2026-04-17. Source: T:/Project/ecommerce-backend*
