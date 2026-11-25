package com.locnguyen.ecommerce.domains.returns;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.locnguyen.ecommerce.domains.returns.controller.AdminReturnQueueController;
import com.locnguyen.ecommerce.domains.returns.dto.CreateReturnRequest;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnResponse;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnReason;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.service.OrderReturnService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminReturnQueueControllerTest {
    private static final String PATH = "/api/v1/admin/returns";
    private final OrderReturnService service = mock(OrderReturnService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AdminReturnQueueController(service))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();

    @Test
    void returnsFilteredPageWithRequestedQuantity() throws Exception {
        UUID orderId = UUID.randomUUID();
        var entry = new ReturnResponse(UUID.randomUUID(), orderId, ReturnStatus.REQUESTED,
                ReturnReason.DEFECTIVE, "Damaged seam", LocalDateTime.of(2026, 11, 24, 12, 0),
                List.of(new CreateReturnRequest.Item(UUID.randomUUID(), 2)));
        when(service.listForAdmin(eq(ReturnStatus.REQUESTED), eq(orderId), any()))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(1, 1), 2));

        mvc.perform(get(PATH).param("status", "REQUESTED").param("orderId", orderId.toString())
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page", is(1)))
                .andExpect(jsonPath("$.data.totalItems", is(2)))
                .andExpect(jsonPath("$.data.items[0].orderId", is(orderId.toString())))
                .andExpect(jsonPath("$.data.items[0].items[0].quantity", is(2)));
    }

    @Test
    void rejectsUnknownStatusBeforeQuerying() throws Exception {
        mvc.perform(get(PATH).param("status", "UNKNOWN")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
