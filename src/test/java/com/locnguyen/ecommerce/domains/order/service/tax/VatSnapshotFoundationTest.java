package com.locnguyen.ecommerce.domains.order.service.tax;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.service.OrderVoucherAllocation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VatSnapshotFoundationTest {
    private static final LocalDate REDUCTION_START = LocalDate.of(2025, 7, 1);
    private static final LocalDate STANDARD_START = LocalDate.of(2027, 1, 1);
    private static final Currency VND = Currency.getInstance("VND");
    private static final VatRateSchedule.Rate REDUCED = rate("APPAREL", "0.08", REDUCTION_START, STANDARD_START);
    private static final VatRateSchedule.Rate STANDARD = rate("APPAREL", "0.10", STANDARD_START, null);

    @Test
    void resolve_usesExclusiveEndAndIndependentTaxGroups() {
        var exempt = rate("EXAMPLE_ZERO_RATE", "0", REDUCTION_START, null);
        var schedule = new VatRateSchedule(List.of(STANDARD, exempt, REDUCED));

        assertThat(schedule.resolve("APPAREL", REDUCTION_START.minusDays(1))).isEmpty();
        assertThat(schedule.resolve("APPAREL", REDUCTION_START)).contains(REDUCED);
        assertThat(schedule.resolve("APPAREL", STANDARD_START.minusDays(1))).contains(REDUCED);
        assertThat(schedule.resolve("APPAREL", STANDARD_START)).contains(STANDARD);
        assertThat(schedule.resolve("EXAMPLE_ZERO_RATE", STANDARD_START)).contains(exempt);
        assertThat(schedule.resolve("UNKNOWN", STANDARD_START)).isEmpty();
    }

    @Test
    void configure_rejectsOverlapsAndEmptyIntervals() {
        var overlap = rate("APPAREL", "0.10", STANDARD_START.minusDays(1), null);
        assertThatIllegalArgumentException().isThrownBy(() -> new VatRateSchedule(List.of(REDUCED, overlap)));
        assertThatIllegalArgumentException().isThrownBy(() -> new VatRateSchedule(
                List.of(STANDARD, rate("APPAREL", "0.12", STANDARD_START.plusYears(1), null))));
        assertThatIllegalArgumentException().isThrownBy(() -> rate("APPAREL", "0.08", STANDARD_START, STANDARD_START));
    }

    @Test
    void snapshot_appliesOnlyExistingEligibleLineDiscountAndKeepsGross() {
        OrderItem eligible = new OrderItem();
        eligible.setLineTotal(money(540_000));
        OrderItem ineligible = new OrderItem();
        ineligible.setLineTotal(money(110_000));

        Order order = new Order();
        order.getItems().addAll(List.of(eligible, ineligible));
        order.setDiscountAmount(money(54_000));
        var allocated = OrderVoucherAllocation.resolve(order, Set.of(eligible));

        var before = atReducedRate(eligible.getLineTotal(), BigDecimal.ZERO);
        assertThat(before.amounts()).isEqualTo(new MerchandiseVatSnapshot.Amounts(money(540_000), money(500_000), money(40_000)));
        var after = atReducedRate(eligible.getLineTotal(), allocated.get(eligible));
        assertThat(after.amounts()).isEqualTo(new MerchandiseVatSnapshot.Amounts(money(486_000), money(450_000), money(36_000)));

        var differentGroup = rate("OTHER", "0.10", REDUCTION_START, null);
        var other = new MerchandiseVatSnapshot(differentGroup, REDUCTION_START, VND, RoundingMode.HALF_UP,
                0, ineligible.getLineTotal(), allocated.get(ineligible));
        assertThat(allocated.get(ineligible)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(other.amounts()).isEqualTo(new MerchandiseVatSnapshot.Amounts(money(110_000), money(100_000), money(10_000)));
    }

    @Test
    void credit_reconcilesMinorUnitsAcrossRetriesAndPartialCredits() {
        var snapshot = atReducedRate(money(100), BigDecimal.ZERO);
        var first = snapshot.credit(money(0), money(33));
        var second = snapshot.credit(money(33), money(33));
        var last = snapshot.credit(money(66), money(34));

        assertThat(snapshot.credit(money(33), money(33))).isEqualTo(second);
        assertThat(first.vat().add(second.vat()).add(last.vat())).isEqualTo(snapshot.amounts().vat());
        assertThat(first.taxableBase().add(second.taxableBase()).add(last.taxableBase()))
                .isEqualTo(snapshot.amounts().taxableBase());
        assertThat(snapshot.credit(money(0), money(100))).isEqualTo(snapshot.amounts());
        assertThatIllegalArgumentException().isThrownBy(() -> snapshot.credit(money(66), money(35)));
    }

    @Test
    void snapshot_rejectsUnapprovedRoundingOfGrossAndHandlesFullyDiscountedLine() {
        var zero = atReducedRate(money(100), money(100));
        assertThat(zero.credit(money(0), money(0))).isEqualTo(zero.amounts());
        assertThatIllegalArgumentException().isThrownBy(() -> atReducedRate(money(100), money(101)));
        assertThatThrownBy(() -> atReducedRate(new BigDecimal("100.50"), money(0))).isInstanceOf(ArithmeticException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new MerchandiseVatSnapshot(
                REDUCED, STANDARD_START, VND, RoundingMode.HALF_UP, 0, money(100), money(0)));
    }

    private static MerchandiseVatSnapshot atReducedRate(BigDecimal gross, BigDecimal discount) {
        return new MerchandiseVatSnapshot(REDUCED, REDUCTION_START, VND, RoundingMode.HALF_UP, 0, gross, discount);
    }

    private static VatRateSchedule.Rate rate(String group, String fraction, LocalDate start, LocalDate end) {
        return new VatRateSchedule.Rate(group, new BigDecimal(fraction), start, end);
    }

    private static BigDecimal money(long wholeVnd) {
        return BigDecimal.valueOf(wholeVnd);
    }
}
