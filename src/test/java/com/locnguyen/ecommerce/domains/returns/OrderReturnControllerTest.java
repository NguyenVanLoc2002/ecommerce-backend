package com.locnguyen.ecommerce.domains.returns;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.returns.controller.OrderReturnController;
import com.locnguyen.ecommerce.domains.returns.dto.CustomerReturnDetail;
import com.locnguyen.ecommerce.domains.returns.dto.CustomerReturnsResponse;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnResponse;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnReason;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.service.OrderReturnService;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderReturnControllerTest {
    private static final String PATH = "/api/v1/orders/{orderId}/returns";
    private final OrderReturnService returns = mock(OrderReturnService.class);
    private final UserService users = mock(UserService.class);
    private final Customer customer = new Customer();
    private final UUID orderId = UUID.randomUUID();
    private final UUID returnId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 25, 10, 0);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new OrderReturnController(returns, users)).build();

    @Test
    void readsEligibilityForCurrentCustomer() throws Exception {
        when(users.getCurrentCustomer()).thenReturn(customer);
        when(returns.listForCustomer(orderId, customer)).thenReturn(new CustomerReturnsResponse(List.of(
                new CustomerReturnsResponse.EligibleItem(itemId, 1, List.of(ReturnReason.DEFECTIVE), false)), List.of()));

        mvc.perform(get(PATH, orderId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligibleItems[0].orderItemId").value(itemId.toString()))
                .andExpect(jsonPath("$.data.eligibleItems[0].availableQuantity").value(1))
                .andExpect(jsonPath("$.data.eligibleItems[0].allowedReasons[0]").value("DEFECTIVE"))
                .andExpect(jsonPath("$.data.requests").isEmpty());
    }

    @Test
    void readsTimelineAndTrackingForCurrentCustomer() throws Exception {
        when(users.getCurrentCustomer()).thenReturn(customer);
        var request = new ReturnResponse(returnId, orderId, ReturnStatus.RECEIVED,
                ReturnReason.DEFECTIVE, "Damaged seam", now, List.of());
        when(returns.getForCustomer(orderId, returnId, customer)).thenReturn(new CustomerReturnDetail(request,
                List.of(new CustomerReturnDetail.Event(ReturnStatus.RECEIVED, now)),
                new CustomerReturnDetail.Tracking("Carrier", "TRACK-1", now), List.of(), null, List.of()));

        mvc.perform(get(PATH + "/{returnId}", orderId, returnId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.id").value(returnId.toString()))
                .andExpect(jsonPath("$.data.timeline[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.tracking.trackingNumber").value("TRACK-1"))
                .andExpect(jsonPath("$.data.tracking.actor").doesNotExist());
    }
}
