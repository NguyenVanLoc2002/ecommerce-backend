package com.locnguyen.ecommerce.domains.returns.service;

import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.returns.dto.CreateReturnRequest;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnResponse;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnDecisionRequest;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnHandlingRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderReturnService {
    Page<ReturnResponse> listForAdmin(ReturnStatus status, UUID orderId, Pageable pageable);
    ReturnResponse decide(UUID orderId, UUID returnId, String key, String actor,
                          ReturnStatus next, ReturnDecisionRequest request);

    ReturnResponse handle(UUID orderId, UUID returnId, String key, String actor,
                          ReturnStatus next, ReturnHandlingRequest request);

    ReturnResponse create(UUID orderId, Customer customer, String key, CreateReturnRequest request);
}
