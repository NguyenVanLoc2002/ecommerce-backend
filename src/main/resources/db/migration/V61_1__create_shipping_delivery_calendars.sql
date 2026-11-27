CREATE TABLE shipping_calendars (
    id CHAR(36) PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(150) NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    workday_mask SMALLINT NOT NULL,
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    cutoff_at TIME NOT NULL,
    holidays JSON NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT chk_shipping_calendar_purpose CHECK (purpose IN ('WAREHOUSE_PROCESSING','CARRIER_TRANSPORT')),
    CONSTRAINT chk_shipping_calendar_days CHECK (workday_mask BETWEEN 1 AND 127),
    CONSTRAINT chk_shipping_calendar_hours CHECK (opens_at < cutoff_at AND cutoff_at <= closes_at AND opens_at < closes_at)
);

CREATE TABLE shipping_calendar_revisions (
    calendar_id CHAR(36) NOT NULL,
    revision BIGINT NOT NULL,
    changed_by CHAR(36) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    snapshot JSON NOT NULL,
    PRIMARY KEY (calendar_id, revision),
    FOREIGN KEY (calendar_id) REFERENCES shipping_calendars (id)
);
