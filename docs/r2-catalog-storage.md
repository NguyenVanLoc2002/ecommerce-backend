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

1. `POST /api/v1/admin/catalog-uploads` with `purpose: CATALOG_IMAGE`, an original filename, one of `image/jpeg`, `image/png`, `image/webp`, and the exact byte size.
2. Send a browser `PUT` to `uploadUrl` with the returned `requiredContentType`. Do not add credentials.
3. `POST /api/v1/admin/catalog-uploads/complete` with `storageKey`. The backend uses `HeadObject` and checks content type and size.
4. Create or replace product media with `storageKey`, never with the presigned URL. The API returns its public `mediaUrl` through the custom domain.

Legacy HTTP(S) `mediaUrl` values remain supported for existing non-managed assets. A managed object is deleted only after its media relation is removed or replaced; failed deletes remain `DELETE_PENDING` for the scheduled retry. Unattached requested or verified uploads are removed after the configured retention period.
