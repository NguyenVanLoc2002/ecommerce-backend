-- Backfill non-sensitive legacy AhaMove values into their typed columns.
-- config_json is deliberately retained for the documented compatibility window;
-- webhookToken is not copied because it must be encrypted by application code.

UPDATE store_carrier_configs config
JOIN carriers carrier ON carrier.id = config.carrier_id
SET
    config.provider_account_phone = COALESCE(
            NULLIF(TRIM(config.provider_account_phone), ''),
            NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config.config_json, '$.phone')), '')),
    config.provider_brand_name = COALESCE(
            NULLIF(TRIM(config.provider_brand_name), ''),
            NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config.config_json, '$.brandName')), '')),
    config.pickup_address = COALESCE(
            NULLIF(TRIM(config.pickup_address), ''),
            NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config.config_json, '$.pickupAddress')), '')),
    config.pickup_short_address = COALESCE(
            NULLIF(TRIM(config.pickup_short_address), ''),
            NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config.config_json, '$.pickupShortAddress')), '')),
    config.pickup_name = COALESCE(
            NULLIF(TRIM(config.pickup_name), ''),
            NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config.config_json, '$.pickupName')), '')),
    config.pickup_phone = COALESCE(
            NULLIF(TRIM(config.pickup_phone), ''),
            NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config.config_json, '$.pickupPhone')), '')),
    config.default_service_code = COALESCE(
            NULLIF(TRIM(config.default_service_code), ''),
            NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config.config_json, '$.groupServiceId')), '')),
    config.default_payment_method = COALESCE(
            NULLIF(TRIM(config.default_payment_method), ''),
            NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config.config_json, '$.paymentMethod')), ''))
WHERE carrier.provider_type = 'AHAMOVE'
  AND config.config_json IS NOT NULL
  AND JSON_VALID(config.config_json);
