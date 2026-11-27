package com.locnguyen.ecommerce.domains.order.service.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/** VAT components of a merchandise line whose payable amount already includes VAT. */
public final class MerchandiseVatSnapshot {
    private final VatRateSchedule.Rate rate;
    private final LocalDate taxDate;
    private final Currency currency;
    private final RoundingMode roundingMode;
    private final int amountScale;
    private final Amounts amounts;

    public MerchandiseVatSnapshot(
            VatRateSchedule.Rate rate,
            LocalDate taxDate,
            Currency currency,
            RoundingMode roundingMode,
            int amountScale,
            BigDecimal merchandiseGross,
            BigDecimal allocatedMerchandiseDiscount) {
        this.rate = Objects.requireNonNull(rate, "Resolved VAT rate is required");
        this.taxDate = Objects.requireNonNull(taxDate, "VAT tax date is required");
        this.currency = Objects.requireNonNull(currency, "Order currency is required");
        this.roundingMode = Objects.requireNonNull(roundingMode, "VAT rounding mode is required");
        if (amountScale < 0 || !rate.appliesOn(taxDate)) {
            throw new IllegalArgumentException("VAT scale or tax date is outside the configured policy");
        }
        this.amountScale = amountScale;
        BigDecimal gross = money(merchandiseGross);
        BigDecimal discount = money(allocatedMerchandiseDiscount);
        if (discount.compareTo(gross) > 0) {
            throw new IllegalArgumentException("Allocated discount exceeds merchandise gross");
        }

        BigDecimal discountedGross = gross.subtract(discount);
        BigDecimal taxableBase = discountedGross.divide(BigDecimal.ONE.add(rate.fraction()), amountScale, roundingMode);
        this.amounts = new Amounts(discountedGross, taxableBase, discountedGross.subtract(taxableBase));
    }

    public VatRateSchedule.Rate rate() {
        return rate;
    }

    public LocalDate taxDate() {
        return taxDate;
    }

    public Currency currency() {
        return currency;
    }

    public RoundingMode roundingMode() {
        return roundingMode;
    }

    public Amounts amounts() {
        return amounts;
    }

    /**
     * Splits an existing merchandise credit quantity calculation using the original VAT.
     * Cumulative credit before this adjustment excludes the current credit.
     */
    public Amounts credit(BigDecimal previousGrossCredit, BigDecimal currentGrossCredit) {
        BigDecimal previous = money(previousGrossCredit);
        BigDecimal current = money(currentGrossCredit);
        BigDecimal cumulative = previous.add(current);
        if (cumulative.compareTo(amounts.gross()) > 0) {
            throw new IllegalArgumentException("Cumulative credit exceeds original merchandise gross");
        }
        BigDecimal tax = cumulativeTax(cumulative).subtract(cumulativeTax(previous));
        return new Amounts(current, current.subtract(tax), tax);
    }

    private BigDecimal cumulativeTax(BigDecimal credited) {
        if (credited.signum() == 0) {
            return BigDecimal.ZERO.setScale(amountScale);
        }
        return amounts.vat().multiply(credited).divide(amounts.gross(), amountScale, roundingMode);
    }

    private BigDecimal money(BigDecimal value) {
        Objects.requireNonNull(value, "Merchandise amount is required");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Merchandise amounts must be nonnegative");
        }
        return value.setScale(amountScale, RoundingMode.UNNECESSARY);
    }

    public record Amounts(BigDecimal gross, BigDecimal taxableBase, BigDecimal vat) {}
}
