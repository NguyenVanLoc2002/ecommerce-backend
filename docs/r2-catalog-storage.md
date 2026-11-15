# Catalog image storage with Cloudflare R2

Catalog images are uploaded directly from Admin Web to Cloudflare R2. The API never returns R2 credentials. A signed URL is a bearer credential, so Admin Web must not log, persist, or proxy the `uploadUrl`.

## Required environment

Set these only in the deployment secret store:

```text
APP_STORAGE_R2_ENABLED=true
APP_STORAGE_R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
APP_STORAGE_R2_ACCESS_KEY_ID=<r2-api-token-access-key-id>
APP_STORAGE_R2_SECRET_ACCESS_KEY=<r2-api-token-secret>
APP_STORAGE_R2_BUCKET=<catalog-bucket>
APP_STORAGE_R2_PUBLIC_BASE_URL=https://assets.example.com
```

`APP_STORAGE_R2_REGION` defaults to `auto`. The public base URL must be the dedicated custom domain that serves the catalog bucket, not an S3 endpoint and not a presigned URL. Do not set any of these values in committed property files.

Optional controls are `APP_STORAGE_R2_MAX_IMAGE_SIZE_BYTES` (default `10485760`), `APP_STORAGE_R2_PRESIGN_EXPIRY_SECONDS` (default `300`), `APP_STORAGE_R2_ORPHAN_RETENTION_HOURS` (default `24`), and `APP_STORAGE_R2_CLEANUP_FIXED_DELAY_MS` (default `300000`).

## Bucket setup

Enable public reads only through the custom domain. The bucket must allow browser CORS only from deployed Admin Web origins, with the smallest required policy:

```json
[
  {
    "AllowedOrigins": ["https://admin.example.com"],
    "AllowedMethods": ["PUT"],
    "AllowedHeaders": ["Content-Type"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 300
  }
]
```

Add local Admin origins only to non-production buckets. Do not enable wildcard origins or allow credentials; R2 credentials stay server-side and the browser only receives an expiring `PUT` URL.

## Admin workflow

1. `POST /api/v1/admin/catalog-uploads` with the domain purpose (`CATALOG_IMAGE`, `CATEGORY_IMAGE`, `BRAND_LOGO`, `CUSTOMER_AVATAR`, or `CARRIER_LOGO`), an original filename, one of `image/jpeg`, `image/png`, `image/webp`, and the exact byte size.
2. Send a browser `PUT` to `uploadUrl` with the returned `requiredContentType`. Do not add credentials.
3. `POST /api/v1/admin/catalog-uploads/complete` with `storageKey`. The backend checks object metadata, size, and the actual JPEG/PNG/WebP signature.
4. Attach the verified key using the matching Admin domain request field: `storageKey` for Product Media, `imageStorageKey` for Category, `logoStorageKey` for Brand/Carrier, or `avatarStorageKey` for Customer. Never persist the presigned URL. Domain responses keep returning the public delivery URL and include the managed storage key when present.

Legacy HTTP(S) image URLs remain readable and writable. A managed object is associated with one entity at a time; the purpose must match the domain. Replacing an image updates the domain reference transactionally and detaches the previous association only after the entity update succeeds. Cleanup verifies that no association remains before deleting an object; failures remain `DELETE_PENDING` for scheduled retry. Unattached uploads and attached objects without a live association are removed after the configured retention period. Migrations V38-V41 add nullable legacy-compatible references, optimistic version fields for single-image domains and Product Media, and association records (including a backfill for current Product Media assets); they do not migrate or delete external URLs.
