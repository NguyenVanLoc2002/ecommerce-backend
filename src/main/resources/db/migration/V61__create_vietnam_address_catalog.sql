CREATE TABLE address_provinces (
    code CHAR(2) NOT NULL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_province_code CHECK (code REGEXP '^[0-9]{2}$' AND code <> '00')
);

CREATE TABLE address_wards (
    code CHAR(5) NOT NULL PRIMARY KEY,
    province_code CHAR(2) NOT NULL,
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_ward_code CHECK (code REGEXP '^[0-9]{5}$' AND code <> '00000'),
    CONSTRAINT fk_address_ward_province FOREIGN KEY (province_code) REFERENCES address_provinces (code),
    INDEX idx_address_ward_province_active (province_code, active, code)
);
