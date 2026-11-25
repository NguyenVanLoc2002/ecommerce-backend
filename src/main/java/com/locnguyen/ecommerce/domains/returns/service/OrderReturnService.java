package com.locnguyen.ecommerce.domains.returns.service;

import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.returns.dto.CreateReturnRequest;
import com.locnguyen.ecommerce.domains.returns.dto.CustomerReturnDetail;
import com.locnguyen.ecommerce.domains.returns.dto.CustomerReturnsResponse;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnResponse;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnDecisionRequest;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnHandlingRequest;
import java.util.UUID;
import com.locnguyen.ecommerce.domains.returns.dto.AdminReturnDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderReturnService {
    CustomerReturnsResponse listForCustomer(UUID orderId, Customer customer);

    CustomerReturnDetail getForCustomer(UUID orderId, UUID returnId, Customer customer);

    AdminReturnDetail getForAdmin(UUID orderId, UUID returnId);

    Page<ReturnResponse> listForAdmin(ReturnStatus status, UUID orderId, Pageable pageable);
    ReturnResponse decide(UUID orderId, UUID returnId, String key, String actor,
                          ReturnStatus next, ReturnDecisionRequest request);

    ReturnResponse handle(UUID orderId, UUID returnId, String key, String actor,
                          ReturnStatus next, ReturnHandlingRequest request);

    ReturnResponse create(UUID orderId, Customer customer, String key, CreateReturnRequest request);
}
