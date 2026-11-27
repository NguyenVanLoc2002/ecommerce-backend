package com.locnguyen.ecommerce.domains.order.service.tax;

import com.locnguyen.ecommerce.domains.order.service.tax.InclusiveVatCalculator.Treatment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class TaxInputs {
    private TaxInputs() {}

    public record CategoryInput(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,39}") String code,
                                @NotBlank @Size(max = 150) String name, @NotNull Treatment treatment) {}

    public record CategoryUpdate(@NotBlank @Size(max = 150) String name, @NotNull Boolean active) {}

    public record Category(String code, String name, Treatment treatment, boolean active, int revision) {}

    public record Assignment(@Pattern(regexp = "[A-Z][A-Z0-9_]{1,39}") String categoryCode) {}

    public record RateInput(@NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 8) BigDecimal fraction,
                            @NotNull LocalDate effectiveFrom, LocalDate effectiveUntil, @NotNull Boolean active) {}

    public record ScheduleUpdate(@Min(0) int revision, @NotEmpty @Size(max = 100) List<@NotNull @Valid RateInput> rates) {}

    public record Schedule(Category category, List<RateInput> rates) {}

    public record ConfigAudit(long id, String event, String actor, String details, String changedAt) {}
}
