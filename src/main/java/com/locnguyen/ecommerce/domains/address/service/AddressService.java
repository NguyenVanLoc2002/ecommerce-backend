package com.locnguyen.ecommerce.domains.address.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.address.dto.AddressResponse;
import com.locnguyen.ecommerce.domains.address.dto.CreateAddressRequest;
import com.locnguyen.ecommerce.domains.address.dto.UpdateAddressRequest;
import com.locnguyen.ecommerce.domains.address.entity.Address;
import com.locnguyen.ecommerce.domains.address.mapper.AddressMapper;
import com.locnguyen.ecommerce.domains.address.repository.AddressRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import java.util.UUID;
/**
 * Address CRUD service — all operations are scoped to the current customer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;
    private final UserService userService;

    // ─── List ─────────────────────────────────────────────────────────────────

    /**
     * Returns all addresses for the current customer.
     * Sorted by default first (DESC), then by newest first.
     */
    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses() {
        Customer customer = userService.getCurrentCustomer();
        List<Address> addresses = addressRepository.findByCustomerIdAndDeletedFalse(
                customer.getId(),
                Sort.by(Sort.Order.desc("defaultAddress"), Sort.Order.desc("createdAt"))
        );
        return addresses.stream().map(addressMapper::toResponse).toList();
    }

    // ─── Get by ID ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AddressResponse getAddressById(UUID addressId) {
        Address address = findOwnedAddress(addressId);
        return addressMapper.toResponse(address);
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    /**
     * Creates a new address for the current customer.
     *
     * <p>If {@code isDefault} is true, clears any existing default address first
     * to enforce the "only one default per customer" rule.
     */
    @Transactional
    public AddressResponse createAddress(CreateAddressRequest request) {
        Customer customer = userService.getCurrentCustomer();

        // Enforce single default per customer
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.clearDefaultByCustomerId(customer.getId());
        }

        Address address = new Address();
        address.setCustomer(customer);
        address.setReceiverName(request.getReceiverName().trim());
        address.setPhoneNumber(request.getPhoneNumber());
        address.setStreetAddress(request.getStreetAddress().trim());
        address.setWard(request.getWard().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setCity(request.getCity().trim());
        address.setPostalCode(request.getPostalCode());
        address.setAddressType(request.getAddressType());
        address.setDefaultAddress(Boolean.TRUE.equals(request.getIsDefault()));
        address.setLabel(request.getLabel());

        address = addressRepository.save(address);
        log.info("Address created: id={} customerId={}", address.getId(), customer.getId());

        return addressMapper.toResponse(address);
    }

    // ─── Update ──────────────────────────────────────────────────────────────

    /**
     * Updates an existing address. Only non-null fields are applied (partial update).
     *
     * <p>If {@code isDefault} is set to true, clears any existing default first.
     * If set to false, simply unsets the default flag on this address.
     */
    @Transactional
    public AddressResponse updateAddress(UUID addressId, UpdateAddressRequest request) {
        Address address = findOwnedAddress(addressId);

        if (request.getReceiverName() != null) {
            address.setReceiverName(request.getReceiverName().trim());
        }
        if (request.getPhoneNumber() != null) {
            address.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getStreetAddress() != null) {
            address.setStreetAddress(request.getStreetAddress().trim());
        }
        if (request.getWard() != null) {
            address.setWard(request.getWard().trim());
        }
        if (request.getDistrict() != null) {
            address.setDistrict(request.getDistrict().trim());
        }
        if (request.getCity() != null) {
            address.setCity(request.getCity().trim());
        }
        if (request.getPostalCode() != null) {
            address.setPostalCode(request.getPostalCode());
        }
        if (request.getAddressType() != null) {
            address.setAddressType(request.getAddressType());
        }
        if (request.getLabel() != null) {
            address.setLabel(request.getLabel());
        }
        if (request.getIsDefault() != null) {
            if (request.getIsDefault()) {
                addressRepository.clearDefaultByCustomerId(address.getCustomer().getId());
            }
            address.setDefaultAddress(request.getIsDefault());
        }

        address = addressRepository.save(address);
        log.info("Address updated: id={}", addressId);

        return addressMapper.toResponse(address);
    }

    // ─── Delete ──────────────────────────────────────────────────────────────

    /**
     * Soft-deletes an address. Verifies ownership before deletion.
     * Does not auto-assign a new default — the customer can set one manually.
     */
    @Transactional
    public void deleteAddress(UUID addressId) {
        Address address = findOwnedAddress(addressId);
        String actor = com.locnguyen.ecommerce.common.utils.SecurityUtils.getCurrentUsernameOrSystem();
        address.softDelete(actor);
        addressRepository.save(address);
        log.info("Address deleted: id={} deletedBy={}", addressId, actor);
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    /**
     * Loads an address and verifies it belongs to the current customer.
     * Returns 404 if the address doesn't exist or belongs to another customer
     * (prevents information leakage about other customers' addresses).
     */
    private Address findOwnedAddress(UUID addressId) {
        Customer customer = userService.getCurrentCustomer();
        Address address = addressRepository.findByIdAndDeletedFalse(addressId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        if (!address.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ADDRESS_NOT_FOUND);
        }

        return address;
    }
}
