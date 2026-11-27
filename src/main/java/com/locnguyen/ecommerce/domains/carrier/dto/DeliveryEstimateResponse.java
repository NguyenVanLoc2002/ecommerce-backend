package com.locnguyen.ecommerce.domains.carrier.dto;

public record DeliveryEstimateResponse(
        String serviceCode,
        Source source,
        Basis basis,
        int minBusinessDays,
        int maxBusinessDays) {

    public static DeliveryEstimateResponse configuredSla(String serviceCode, int minBusinessDays, int maxBusinessDays) {
        return new DeliveryEstimateResponse(serviceCode, Source.CONFIGURED_SLA, Basis.CARRIER_HANDOVER,
                minBusinessDays, maxBusinessDays);
    }

    public enum Source { CONFIGURED_SLA }
    public enum Basis { CARRIER_HANDOVER }
}
