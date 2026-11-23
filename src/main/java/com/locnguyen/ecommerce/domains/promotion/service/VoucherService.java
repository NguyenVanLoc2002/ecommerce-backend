package com.locnguyen.ecommerce.domains.promotion.service;

import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.promotion.dto.CheckoutVoucher;
import com.locnguyen.ecommerce.domains.promotion.dto.CreateVoucherRequest;
import com.locnguyen.ecommerce.domains.promotion.dto.VoucherLine;
import com.locnguyen.ecommerce.domains.promotion.dto.UpdateVoucherRequest;
import com.locnguyen.ecommerce.domains.promotion.dto.ValidateVoucherRequest;
import com.locnguyen.ecommerce.domains.promotion.dto.ValidateVoucherResponse;
import com.locnguyen.ecommerce.domains.promotion.dto.VoucherFilter;
import com.locnguyen.ecommerce.domains.promotion.dto.VoucherResponse;
import com.locnguyen.ecommerce.domains.promotion.dto.VoucherUsageResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface VoucherService {

    VoucherResponse createVoucher(CreateVoucherRequest request);

    VoucherResponse updateVoucher(UUID voucherId, UpdateVoucherRequest request);

    void deleteVoucher(UUID voucherId);

    VoucherResponse getById(UUID voucherId);

    VoucherResponse getByCode(String code);

    PagedResponse<VoucherResponse> getVouchers(VoucherFilter filter, Pageable pageable);

    PagedResponse<VoucherUsageResponse> getUsages(UUID voucherId, Pageable pageable);

    ValidateVoucherResponse validateVoucher(String code, Customer customer, ValidateVoucherRequest request);

    BigDecimal applyVoucher(String voucherCode, Customer customer, UUID orderId, BigDecimal orderAmount, List<UUID> productIds, List<UUID> categoryIds, List<UUID> brandIds);

    CheckoutVoucher quoteCheckoutVoucher(String code, Customer customer, List<VoucherLine> lines);

    CheckoutVoucher redeemCheckoutVoucher(String code, Customer customer, UUID orderId, List<VoucherLine> lines);

    void releaseVoucher(String voucherCode, UUID orderId);
}
