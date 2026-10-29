package com.locnguyen.ecommerce.infrastructure.external.ahamove;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.dto.AhamoveCreateOrderRequest;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.dto.AhamoveEstimateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AhamoveMapperTest {

    private final AhamoveMapper mapper = new AhamoveMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toEstimateRequest_mapsExpectedFields() {
        Order order = buildOrder();

        AhamoveEstimateRequest request = mapper.toEstimateRequest(order, buildConfig());

        assertThat(request.path()).hasSize(2);
        assertThat(request.path().get(0).getAddress()).isEqualTo("12 Nguyen Hue, District 1, Ho Chi Minh City");
        assertThat(request.path().get(1).getAddress()).contains("221B Baker Street");
        assertThat(request.path().get(0).getMobile()).isEqualTo("84338710667");
        assertThat(request.path().get(1).getMobile()).isEqualTo("84909000001");
        assertThat(request.paymentMethod()).isEqualTo("CASH");
        assertThat(request.groupServices()).hasSize(1);
        assertThat(request.groupServices().get(0).id()).isEqualTo("BIKE");
        assertThat(request.items()).isNull();
        assertThat(request.packageDetail()).isNull();
    }

    @Test
    void toEstimateRequest_omitsNullFieldsWhenSerialized() {
        AhamoveEstimateRequest request = mapper.toEstimateRequest(buildOrder(), buildConfig());

        JsonNode json = objectMapper.valueToTree(request);
        JsonNode pickupPoint = json.path("path").get(0);
        JsonNode dropoffPoint = json.path("path").get(1);
        JsonNode groupService = json.path("group_services").get(0);

        assertThat(json.path("payment_method").asText()).isEqualTo("CASH");
        assertThat(json.has("remarks")).isFalse();
        assertThat(json.has("items")).isFalse();
        assertThat(json.has("package_detail")).isFalse();
        assertThat(pickupPoint.has("lat")).isFalse();
        assertThat(pickupPoint.has("lng")).isFalse();
        assertThat(pickupPoint.has("status")).isFalse();
        assertThat(pickupPoint.has("fail_comment")).isFalse();
        assertThat(dropoffPoint.has("tracking_number")).isFalse();
        assertThat(dropoffPoint.has("cod")).isFalse();
        assertThat(dropoffPoint.has("item_value")).isFalse();
        assertThat(dropoffPoint.has("complete_time")).isFalse();
        assertThat(dropoffPoint.has("return_time")).isFalse();
        assertThat(groupService.has("group_requests")).isFalse();
    }

    @Test
    void toCreateOrderRequest_mapsCodAmountAndTrackingNumber() {
        Order order = buildOrder();
        Shipment shipment = new Shipment();
        shipment.setShipmentCode("SHP0001");
        shipment.setNote("Fragile");

        AhamoveCreateOrderRequest request = mapper.toCreateOrderRequest(shipment, order, buildConfig());

        assertThat(request.groupServiceId()).isEqualTo("BIKE");
        assertThat(request.path()).hasSize(2);
        assertThat(request.path().get(1).getMobile()).isEqualTo("84909000001");
        assertThat(request.path().get(1).getCod()).isEqualByComparingTo("125000");
        assertThat(request.path().get(1).getTrackingNumber()).isEqualTo("SHP0001");
        assertThat(request.remarks()).contains("Fragile");
    }

    @Test
    void toCreateOrderRequest_missingRecipientPhone_throwsValidationError() {
        Order order = buildOrder();
        order.setReceiverPhone(" ");

        assertThatThrownBy(() -> mapper.toCreateOrderRequest(new Shipment(), order, buildConfig()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void toCreateOrderRequest_missingAddress_throwsValidationError() {
        Order order = buildOrder();
        order.setShippingStreet(" ");
        order.setShippingWard(" ");
        order.setShippingDistrict(" ");
        order.setShippingCity(" ");

        assertThatThrownBy(() -> mapper.toCreateOrderRequest(new Shipment(), order, buildConfig()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void toCreateOrderRequest_nonCodOrderOmitsCod() {
        Order order = buildOrder();
        order.setPaymentMethod(PaymentMethod.ONLINE);

        AhamoveCreateOrderRequest request = mapper.toCreateOrderRequest(new Shipment(), order, buildConfig());

        assertThat(request.path().get(1).getCod()).isNull();
    }

    private Order buildOrder() {
        Order order = new Order();
        order.setOrderCode("ORD-1001");
        order.setReceiverName("Sherlock Holmes");
        order.setReceiverPhone("0909000001");
        order.setShippingStreet("221B Baker Street");
        order.setShippingWard("Ward 1");
        order.setShippingDistrict("District 1");
        order.setShippingCity("Ho Chi Minh City");
        order.setPaymentMethod(PaymentMethod.COD);
        order.setSubTotal(new BigDecimal("100000"));
        order.setTotalAmount(new BigDecimal("125000"));
        order.setCustomerNote("Call before arrival");

        ProductVariant variant = new ProductVariant();
        variant.setWeightGram(750);

        OrderItem item = new OrderItem();
        item.setVariant(variant);
        item.setProductName("Linen Shirt");
        item.setVariantName("Blue / M");
        item.setSku("SKU-1");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("100000"));

        order.setItems(List.of(item));
        return order;
    }

    private AhamoveResolvedConfig buildConfig() {
        return new AhamoveResolvedConfig(
                "https://partner-apistg.ahamove.com",
                "api-key",
                "84338710667",
                "Locen Studio",
                "webhook-token",
                "12 Nguyen Hue, District 1, Ho Chi Minh City",
                "District 1",
                "Locen Studio",
                "84338710667",
                null,
                null,
                "BIKE",
                "CASH",
                List.of()
        );
    }
}
