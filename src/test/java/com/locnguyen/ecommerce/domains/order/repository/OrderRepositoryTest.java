package com.locnguyen.ecommerce.domains.order.repository;

import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void adminSearchExecutesCombinedFiltersAndCustomSort() {
        var page = orderRepository.adminSearch(
                null,
                OrderStatus.PROCESSING,
                PaymentStatus.FAILED,
                PaymentMethod.ONLINE,
                LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2026-01-31T23:59:59"),
                "ORD-55",
                ShipmentStatus.IN_TRANSIT,
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("createdAt"))));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }
}
