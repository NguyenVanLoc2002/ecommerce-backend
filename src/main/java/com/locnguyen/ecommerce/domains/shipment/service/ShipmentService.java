package com.locnguyen.ecommerce.domains.shipment.service;

import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.shipment.dto.CancelProviderShipmentRequest;
import com.locnguyen.ecommerce.domains.shipment.dto.CreateShipmentRequest;
import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentFilter;
import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentResponse;
import com.locnguyen.ecommerce.domains.shipment.dto.UpdateShipmentRequest;
import com.locnguyen.ecommerce.domains.shipment.dto.UpdateShipmentStatusRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ShipmentService {

    ShipmentResponse createShipment(CreateShipmentRequest request);

    ShipmentResponse updateShipment(UUID shipmentId, UpdateShipmentRequest request);

    ShipmentResponse updateStatus(UUID shipmentId, UpdateShipmentStatusRequest request);

    ShipmentResponse getById(UUID shipmentId);

    ShipmentResponse getByOrderId(UUID orderId);

    PagedResponse<ShipmentResponse> getShipments(ShipmentFilter filter, Pageable pageable);

    ShipmentResponse getShipmentForCustomer(UUID orderId, Customer customer);

    ShipmentResponse syncProviderTracking(UUID shipmentId);

    ShipmentResponse cancelProviderShipment(UUID shipmentId, CancelProviderShipmentRequest request);

    ShipmentResponse applyProviderUpdate(ShipmentProviderUpdate update);
}
