package com.locnguyen.ecommerce.domains.returns.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.returns.dto.AdminReturnDetail;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnResponse;
import org.springframework.web.bind.annotation.PathVariable;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.service.OrderReturnService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequestMapping(AppConstants.API_V1 + "/admin/returns")
public class AdminReturnQueueController {
    private final OrderReturnService returnService;

    @GetMapping("/{orderId}/{returnId}")
    public ApiResponse<AdminReturnDetail> detail(@PathVariable UUID orderId, @PathVariable UUID returnId) {
        return ApiResponse.success(returnService.getForAdmin(orderId, returnId));
    }

    @GetMapping
    public ApiResponse<PagedResponse<ReturnResponse>> list(
            @RequestParam(required = false) ReturnStatus status,
            @RequestParam(required = false) UUID orderId,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.success(PagedResponse.of(returnService.listForAdmin(status, orderId, pageable)));
    }
}
