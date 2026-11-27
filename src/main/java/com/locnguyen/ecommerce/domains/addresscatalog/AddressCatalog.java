package com.locnguyen.ecommerce.domains.addresscatalog;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddressCatalog {
    public record Province(String code, String name) {}

    public record Ward(String code, String name, String provinceCode) {}

    public record AddressUnits(String countryCode, String provinceCode, String provinceName,
                               String wardCode, String wardName) {}

    private final JdbcClient jdbc;

    public List<Province> provinces() {
        return jdbc.sql("SELECT code, name FROM address_provinces WHERE active = TRUE ORDER BY code")
                .query((row, index) -> new Province(row.getString("code"), row.getString("name")))
                .list();
    }

    public List<Ward> wards(String provinceCode) {
        var rows = jdbc.sql("""
                SELECT ward.code, ward.name
                FROM address_provinces province
                LEFT JOIN address_wards ward ON ward.province_code = province.code AND ward.active = TRUE
                WHERE province.code = :province AND province.active = TRUE
                ORDER BY ward.code
                """)
                .param("province", provinceCode)
                .query((row, index) -> new Ward(row.getString("code"), row.getString("name"), provinceCode))
                .list();
        if (rows.isEmpty()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Unknown or inactive province");
        }
        return rows.stream().filter(ward -> ward.code() != null).toList();
    }

    public AddressUnits resolve(String provinceCode, String wardCode) {
        return jdbc.sql("""
                SELECT province.code AS province_code, province.name AS province_name,
                    ward.code AS ward_code, ward.name AS ward_name
                FROM address_wards ward JOIN address_provinces province ON province.code = ward.province_code
                WHERE ward.code = :ward AND province.code = :province
                  AND ward.active = TRUE AND province.active = TRUE
                """)
                .param("province", provinceCode)
                .param("ward", wardCode)
                .query((row, index) -> new AddressUnits("VN", row.getString("province_code"),
                        row.getString("province_name"), row.getString("ward_code"), row.getString("ward_name")))
                .optional()
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR,
                        "Select an active ward within the selected province"));
    }
}
