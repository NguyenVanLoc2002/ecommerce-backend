package com.locnguyen.ecommerce.domains.promotion.mapper;

import com.locnguyen.ecommerce.domains.promotion.dto.VoucherResponse;
import com.locnguyen.ecommerce.domains.promotion.dto.VoucherUsageResponse;
import com.locnguyen.ecommerce.domains.promotion.entity.Voucher;
import com.locnguyen.ecommerce.domains.promotion.entity.VoucherUsage;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VoucherMapper {

    default VoucherResponse toResponse(Voucher voucher) {
        if (voucher == null) return null;

        return VoucherResponse.builder()
                .id(voucher.getId())
                .code(voucher.getCode())
                .promotionId(voucher.getPromotion().getId())
                .promotionName(voucher.getPromotion().getName())
                .discountType(voucher.getPromotion().getDiscountType().name())
                .discountValue(voucher.getPromotion().getDiscountValue())
                .maxDiscountAmount(voucher.getPromotion().getMaxDiscountAmount())
                .minimumOrderAmount(voucher.getPromotion().getMinimumOrderAmount())
                .usageLimit(voucher.getUsageLimit())
                .usageCount(voucher.getUsageCount())
                .usageLimitPerUser(voucher.getUsageLimitPerUser())
                .startDate(voucher.getStartDate())
                .endDate(voucher.getEndDate())
                .active(voucher.isActive())
                .createdAt(voucher.getCreatedAt())
                .build();
    }

    default VoucherUsageResponse toUsageResponse(VoucherUsage usage) {
        if (usage == null) return null;

        return VoucherUsageResponse.builder()
                .id(usage.getId())
                .voucherId(usage.getVoucher().getId())
                .voucherCode(usage.getVoucher().getCode())
                .customerId(usage.getCustomer().getId())
                .orderId(usage.getOrderId())
                .discountAmount(usage.getDiscountAmount())
                .usedAt(usage.getCreatedAt())
                .build();
    }
}
