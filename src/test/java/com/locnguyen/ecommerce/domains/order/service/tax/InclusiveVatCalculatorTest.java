package com.locnguyen.ecommerce.domains.order.service.tax;

import com.locnguyen.ecommerce.domains.order.service.tax.InclusiveVatCalculator.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class InclusiveVatCalculatorTest {
    private static final Band EIGHT_PERCENT = new Band(Treatment.TAXABLE, new BigDecimal("0.08"));
    private static final Band TEN_PERCENT = new Band(Treatment.TAXABLE, new BigDecimal("0.10"));

    @Test
    void reconcilesMixedMerchandiseDiscountShippingAndPartialRefund() {
        var allocations = InclusiveVatCalculator.allocate(List.of(
                new Line("A", EIGHT_PERCENT, vnd(1_080_000), vnd(108_000)),
                new Line("B", TEN_PERCENT, vnd(550_000), vnd(55_000)),
                new Line("shipping", EIGHT_PERCENT, vnd(32_400), vnd(10_800))));
        assertThat(allocations).extracting(Allocation::amounts).containsExactly(
                new Amounts(vnd(972_000), vnd(900_000), vnd(72_000)),
                new Amounts(vnd(495_000), vnd(450_000), vnd(45_000)),
                new Amounts(vnd(21_600), vnd(20_000), vnd(1_600)));
        BigDecimal gross = allocations.stream().map(line -> line.amounts().gross()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = allocations.stream().map(line -> line.amounts().net()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = allocations.stream().map(line -> line.amounts().vat()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(gross).isEqualByComparingTo("1488600");
        assertThat(net).isEqualByComparingTo("1370000");
        assertThat(vat).isEqualByComparingTo("118600");
        assertThat(net.add(vat)).isEqualByComparingTo(gross);
        var credit = InclusiveVatCalculator.credit(allocations.get(0).amounts(), vnd(0), vnd(486_000));
        assertThat(credit).isEqualTo(new Amounts(vnd(486_000), vnd(450_000), vnd(36_000)));
        assertThat(gross.subtract(credit.gross())).isEqualByComparingTo("1002600");
    }

    @Test
    void assignsEqualRemaindersUsingStableIdentifierRegardlessOfRetryOrder() {
        var first = new Line("first", EIGHT_PERCENT, vnd(4), vnd(0));
        var second = new Line("second", new Band(Treatment.TAXABLE, new BigDecimal("0.080")), vnd(4), vnd(0));
        var result = InclusiveVatCalculator.allocate(List.of(second, first));
        assertThat(result).isEqualTo(InclusiveVatCalculator.allocate(List.of(first, second)));
        assertThat(result).extracting(allocation -> allocation.amounts().vat()).containsExactly(vnd(1), vnd(0));
        assertThatThrownBy(() -> InclusiveVatCalculator.allocate(List.of(first, first))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesSettlementDateRateWithoutChangingCheckoutGross() {
        var estimate = InclusiveVatCalculator.allocate(List.of(new Line("A", EIGHT_PERCENT, vnd(540_000), vnd(0)))).get(0);
        var finalized = InclusiveVatCalculator.allocate(List.of(new Line("A", TEN_PERCENT, vnd(540_000), vnd(0)))).get(0);
        assertThat(estimate.amounts()).isEqualTo(new Amounts(vnd(540_000), vnd(500_000), vnd(40_000)));
        assertThat(finalized.amounts()).isEqualTo(new Amounts(vnd(540_000), vnd(490_909), vnd(49_091)));
    }

    @Test
    void finalPartialRefundAbsorbsRoundingAndCannotExceedOriginalAmount() {
        var original = new Amounts(vnd(30), vnd(28), vnd(2));
        var first = InclusiveVatCalculator.credit(original, vnd(0), vnd(10));
        var second = InclusiveVatCalculator.credit(original, vnd(10), vnd(10));
        var last = InclusiveVatCalculator.credit(original, vnd(20), vnd(10));
        assertThat(first.vat().add(second.vat()).add(last.vat())).isEqualByComparingTo(original.vat());
        assertThatThrownBy(() -> InclusiveVatCalculator.credit(original, vnd(30), vnd(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distinguishesZeroRateFromNonTaxableSupply() {
        var zeroRate = new Band(Treatment.TAXABLE, vnd(0));
        var exempt = new Band(Treatment.NON_TAXABLE, vnd(0));
        var result = InclusiveVatCalculator.allocate(List.of(
                new Line("exempt", exempt, vnd(100), vnd(0)), new Line("taxable", zeroRate, vnd(200), vnd(0))));
        assertThat(result).extracting(item -> item.line().band().treatment())
                .containsExactly(Treatment.NON_TAXABLE, Treatment.TAXABLE);
        assertThat(result).extracting(item -> item.amounts().vat()).containsExactly(vnd(0), vnd(0));
    }

    private static BigDecimal vnd(long amount) {
        return BigDecimal.valueOf(amount);
    }
}
