"""Generate a reviewable MariaDB import for a verified Vietnamese address catalog."""

import argparse
import csv
import hashlib
import re
import sys
import unicodedata
from pathlib import Path


def read_rows(file, fields, digits):
    items = {}
    with file.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != fields:
            raise ValueError(f"{file}: expected header {','.join(fields)}")
        for row in reader:
            if set(row) != set(fields) or any(row[field] is None for field in fields):
                raise ValueError(f"{file}:{reader.line_num}: unexpected columns")
            code = row["code"]
            name = unicodedata.normalize("NFC", row["name"].strip())
            if not re.fullmatch(rf"[0-9]{{{digits}}}", code) or int(code) == 0 or code in items:
                raise ValueError(f"{file}:{reader.line_num}: invalid or duplicate code")
            if not name or len(name) > 150 or any(unicodedata.category(char) == "Cc" for char in name):
                raise ValueError(f"{file}:{reader.line_num}: invalid administrative name")
            items[code] = {"name": name}
            if "province_code" in fields:
                items[code]["province_code"] = row["province_code"]
    return items


def sql_text(value):
    return f"CONVERT(0x{value.encode('utf-8').hex()} USING utf8mb4)"


def upsert(table, columns, rows):
    for start in range(0, len(rows), 200):
        values = ",\n".join("(" + ",".join(sql_text(value) for value in row) + ",TRUE)" for row in rows[start:start + 200])
        assignments = ", ".join(f"{column}=VALUES({column})" for column in columns[1:])
        yield f"INSERT INTO {table} ({','.join(columns)},active) VALUES\n{values}\nON DUPLICATE KEY UPDATE {assignments},active=TRUE;"


def generate(args):
    provinces = read_rows(args.provinces, ["code", "name"], 2)
    wards = read_rows(args.wards, ["code", "province_code", "name"], 5)
    if len(provinces) != args.expected_provinces or len(wards) != args.expected_wards:
        raise ValueError("CSV counts differ from the verified source figures")
    if not provinces or {ward["province_code"] for ward in wards.values()} != set(provinces):
        raise ValueError("Every ward must refer to an included province and every province must have wards")

    print(f"-- Provinces SHA-256: {hashlib.sha256(args.provinces.read_bytes()).hexdigest()}")
    print(f"-- Wards SHA-256: {hashlib.sha256(args.wards.read_bytes()).hexdigest()}")
    print("START TRANSACTION;")
    for stmt in upsert("address_provinces", ("code", "name"),
                       [(code, item["name"]) for code, item in sorted(provinces.items())]):
        print(stmt)
    for stmt in upsert("address_wards", ("code", "province_code", "name"),
                       [(code, item["province_code"], item["name"]) for code, item in sorted(wards.items())]):
        print(stmt)
    if args.deactivate_absent:
        for table, items in (("address_wards", wards), ("address_provinces", provinces)):
            identifiers = ",".join(sql_text(code) for code in sorted(items))
            print(f"UPDATE {table} SET active=FALSE WHERE code NOT IN ({identifiers});")
    print("COMMIT;")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--provinces", required=True, type=Path)
    parser.add_argument("--wards", required=True, type=Path)
    parser.add_argument("--expected-provinces", required=True, type=int)
    parser.add_argument("--expected-wards", required=True, type=int)
    parser.add_argument("--deactivate-absent", action="store_true",
                        help="Use only with a verified complete replacement catalog")
    try:
        generate(parser.parse_args())
    except (ValueError, OSError, csv.Error) as error:
        sys.exit(str(error))
