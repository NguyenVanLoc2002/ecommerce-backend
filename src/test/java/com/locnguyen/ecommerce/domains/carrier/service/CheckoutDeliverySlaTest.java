package com.locnguyen.ecommerce.domains.carrier.service;

import com.locnguyen.ecommerce.common.config.CheckoutDeliverySlaProperties;
import com.locnguyen.ecommerce.common.config.CheckoutDeliverySlaProperties.Rule;
import com.locnguyen.ecommerce.domains.carrier.dto.DeliveryEstimateResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CheckoutDeliverySlaTest {
    private static final UUID WAREHOUSE = new UUID(0L, 1L);
    private static final Rule RULE = new Rule("AHAMOVE", "SGN-BIKE", WAREHOUSE, "Hồ Chí Minh", "Quận 1", 1, 2);

    @Test
    void resolve_returnsRangeOnlyForMatchingWarehouseServiceAndDestination() {
        var sla = new CheckoutDeliverySla(new CheckoutDeliverySlaProperties(true, List.of(RULE)));

        assertThat(sla.resolve("ahamove", "sgn-bike", WAREHOUSE, " HỒ CHÍ MINH ", "QUẬN 1"))
                .contains(DeliveryEstimateResponse.configuredSla("SGN-BIKE", 1, 2));
        assertThat(sla.resolve("AHAMOVE", "SGN-OTHER", WAREHOUSE, "Hồ Chí Minh", "Quận 1")).isEmpty();
        assertThat(sla.resolve("AHAMOVE", "SGN-BIKE", new UUID(0L, 2L), "Hồ Chí Minh", "Quận 1")).isEmpty();
        assertThat(sla.resolve("AHAMOVE", "SGN-BIKE", WAREHOUSE, "Hồ Chí Minh", "Quận 2")).isEmpty();
        assertThat(sla.resolve("AHAMOVE", "SGN-BIKE", WAREHOUSE, "Other", "Quận 1")).isEmpty();
        assertThat(sla.resolve("AHAMOVE", "SGN-BIKE", null, null, null)).isEmpty();
    }

    @Test
    void resolve_disabledOrEmptyConfigurationOmitsEstimate() {
        var disabled = new CheckoutDeliverySla(new CheckoutDeliverySlaProperties(false, List.of(RULE)));
        var empty = new CheckoutDeliverySla(new CheckoutDeliverySlaProperties(true, null));

        assertThat(disabled.resolve("AHAMOVE", "SGN-BIKE", WAREHOUSE, "Hồ Chí Minh", "Quận 1")).isEmpty();
        assertThat(empty.resolve("AHAMOVE", "SGN-BIKE", WAREHOUSE, "Hồ Chí Minh", "Quận 1")).isEmpty();
    }

    @Test
    void configure_rejectsAmbiguousCoverageAndIncompleteRanges() {
        var duplicate = new Rule(" ahamove ", "sgn-bike", WAREHOUSE, "Hồ Chí Minh", "Quận 1", 1, 3);
        var invalidRange = new Rule("AHAMOVE", "SGN-BIKE", WAREHOUSE, "Hồ Chí Minh", "Quận 1", 2, 1);
        var incomplete = new Rule("AHAMOVE", "SGN-BIKE", null, "Hồ Chí Minh", "Quận 1", 1, 3);

        assertThatIllegalArgumentException().isThrownBy(
                () -> new CheckoutDeliverySla(new CheckoutDeliverySlaProperties(true, List.of(RULE, duplicate))));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CheckoutDeliverySla(new CheckoutDeliverySlaProperties(true, List.of(invalidRange))));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CheckoutDeliverySla(new CheckoutDeliverySlaProperties(true, List.of(incomplete))));
    }
}
