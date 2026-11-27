package com.locnguyen.ecommerce.domains.order.service.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Extracts whole-VND VAT from confirmed VAT-inclusive merchandise and shipping amounts. */
public final class InclusiveVatCalculator {
    private InclusiveVatCalculator() {}

    public enum Treatment { TAXABLE, NON_TAXABLE }

    public record Band(Treatment treatment, BigDecimal fraction) {
        public Band {
            Objects.requireNonNull(treatment);
            fraction = Objects.requireNonNull(fraction).stripTrailingZeros();
            if (fraction.signum() < 0 || fraction.compareTo(BigDecimal.ONE) > 0
                    || (treatment == Treatment.NON_TAXABLE && fraction.signum() != 0)) {
                throw new IllegalArgumentException("Invalid VAT treatment or fraction");
            }
        }
    }

    public record Line(String key, Band band, BigDecimal gross, BigDecimal discount) {
        public Line {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("A stable VAT line key is required");
            }
            Objects.requireNonNull(band);
            gross = wholeVnd(gross);
            discount = wholeVnd(discount);
            if (discount.compareTo(gross) > 0) {
                throw new IllegalArgumentException("Discount exceeds the VAT-inclusive price");
            }
        }
    }

    public record Amounts(BigDecimal gross, BigDecimal net, BigDecimal vat) {}

    public record Allocation(Line line, Amounts amounts) {}

    public static List<Allocation> allocate(List<Line> lines) {
        Map<Band, List<Share>> bands = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (Line line : lines) {
            if (!keys.add(line.key())) {
                throw new IllegalArgumentException("Duplicate VAT line key");
            }
            var netOfDiscount = line.gross().subtract(line.discount());
            var numerator = netOfDiscount.multiply(line.band().fraction());
            var denominator = BigDecimal.ONE.add(line.band().fraction());
            var quotientRemainder = numerator.divideAndRemainder(denominator);
            bands.computeIfAbsent(line.band(), ignored -> new ArrayList<>()).add(
                    new Share(line, netOfDiscount, numerator, quotientRemainder[0].setScale(0),
                            quotientRemainder[1]));
        }
        return bands.entrySet().stream()
                .flatMap(entry -> allocateBand(entry.getKey(), entry.getValue()).stream())
                .sorted(Comparator.comparing(allocation -> allocation.line().key()))
                .toList();
    }

    public static Amounts credit(Amounts original, BigDecimal priorGross, BigDecimal currentGross) {
        BigDecimal before = wholeVnd(priorGross);
        BigDecimal current = wholeVnd(currentGross);
        BigDecimal after = before.add(current);
        if (after.compareTo(original.gross()) > 0) {
            throw new IllegalArgumentException("Cumulative credit exceeds the original line");
        }
        BigDecimal vat = proratedVat(original, after).subtract(proratedVat(original, before));
        return new Amounts(current, current.subtract(vat), vat);
    }

    private static List<Allocation> allocateBand(Band band, List<Share> shares) {
        BigDecimal numerators = BigDecimal.ZERO;
        BigDecimal wholeShares = BigDecimal.ZERO;
        for (Share share : shares) {
            numerators = numerators.add(share.numerator());
            wholeShares = wholeShares.add(share.whole());
        }

        // The denominator is shared by a band. Comparing remainders is exact and avoids
        // changing the winning line by prematurely rounding a recurring fraction.
        BigDecimal vat = numerators.divide(BigDecimal.ONE.add(band.fraction()), 0, RoundingMode.HALF_UP);
        int extra = vat.subtract(wholeShares).intValueExact();
        shares.sort(Comparator.comparing(Share::remainder).reversed().thenComparing(share -> share.line().key()));
        List<Allocation> allocations = new ArrayList<>();
        for (int index = 0; index < shares.size(); index++) {
            Share share = shares.get(index);
            BigDecimal tax = index < extra ? share.whole().add(BigDecimal.ONE) : share.whole();
            allocations.add(new Allocation(share.line(), new Amounts(share.gross(), share.gross().subtract(tax), tax)));
        }
        return allocations;
    }

    private static BigDecimal proratedVat(Amounts original, BigDecimal credited) {
        return credited.signum() == 0 ? BigDecimal.ZERO
                : original.vat().multiply(credited).divide(original.gross(), 0, RoundingMode.HALF_UP);
    }

    private static BigDecimal wholeVnd(BigDecimal value) {
        if (Objects.requireNonNull(value).signum() < 0) {
            throw new IllegalArgumentException("VAT amounts must be nonnegative");
        }
        return value.setScale(0, RoundingMode.UNNECESSARY);
    }

    private record Share(Line line, BigDecimal gross, BigDecimal numerator, BigDecimal whole, BigDecimal remainder) {}
}
