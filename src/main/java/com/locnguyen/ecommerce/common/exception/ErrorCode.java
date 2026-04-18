package com.locnguyen.ecommerce.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Centralised error codes for the entire application.
 * Each code maps to an HTTP status and a default user-facing message.
 */
@Getter
public enum ErrorCode {

    // ─── General ────────────────────────────────────────────────────────────
    SUCCESS(HttpStatus.OK, "SUCCESS", "Request processed successfully"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Bad request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"),
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Validation failed"),
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "Resource conflict"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
            "An internal error occurred. Please try again later."),

    // ─── Auth ────────────────────────────────────────────────────────────────
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
            "Invalid email or password"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Token has expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Token is invalid"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID",
            "Refresh token is invalid or expired"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
            "Your account has been disabled"),
    ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS",
            "An account with this email already exists"),

    // ─── User / Customer ────────────────────────────────────────────────────
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS",
            "Email is already registered"),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS",
            "Phone number is already registered"),

    // ─── Address ────────────────────────────────────────────────────────────
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "Address not found"),

    // ─── Catalog ────────────────────────────────────────────────────────────
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category not found"),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "Brand not found"),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "SLUG_ALREADY_EXISTS",
            "Slug is already in use"),

    // ─── Product ────────────────────────────────────────────────────────────
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found"),
    PRODUCT_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_INACTIVE",
            "Product is currently inactive"),

    // ─── Product Variant ────────────────────────────────────────────────────
    PRODUCT_VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_VARIANT_NOT_FOUND",
            "Product variant not found"),
    PRODUCT_VARIANT_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_VARIANT_INACTIVE",
            "Product variant is currently inactive"),
    SKU_ALREADY_EXISTS(HttpStatus.CONFLICT, "SKU_ALREADY_EXISTS",
            "SKU is already in use"),

    // ─── Inventory ──────────────────────────────────────────────────────────
    INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "INVENTORY_NOT_FOUND",
            "Inventory record not found"),
    INVENTORY_NOT_ENOUGH(HttpStatus.UNPROCESSABLE_ENTITY, "INVENTORY_NOT_ENOUGH",
            "Insufficient inventory for this variant"),
    VARIANT_OUT_OF_STOCK(HttpStatus.UNPROCESSABLE_ENTITY, "VARIANT_OUT_OF_STOCK",
            "This variant is out of stock"),
    STOCK_RESERVATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "STOCK_RESERVATION_FAILED",
            "Failed to reserve stock — please try again"),
    WAREHOUSE_NOT_FOUND(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND",
            "Warehouse not found"),

    // ─── Cart ────────────────────────────────────────────────────────────────
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_NOT_FOUND", "Cart not found"),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND",
            "Cart item not found"),
    CART_ITEM_QUANTITY_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "CART_ITEM_QUANTITY_INVALID",
            "Cart item quantity must be greater than 0"),

    // ─── Order ──────────────────────────────────────────────────────────────
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"),
    ORDER_STATUS_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_STATUS_INVALID",
            "Invalid order status transition"),
    ORDER_CANNOT_CANCEL(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_CANNOT_CANCEL",
            "Order cannot be cancelled at its current status"),
    ORDER_CANNOT_COMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_CANNOT_COMPLETE",
            "Order cannot be completed at its current status"),
    ORDER_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_EMPTY",
            "Order must contain at least one item"),

    // ─── Payment ────────────────────────────────────────────────────────────
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"),
    PAYMENT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_FAILED",
            "Payment processing failed"),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "PAYMENT_ALREADY_PROCESSED",
            "Payment has already been processed"),
    PAYMENT_CALLBACK_INVALID(HttpStatus.BAD_REQUEST, "PAYMENT_CALLBACK_INVALID",
            "Invalid payment callback received"),

    // ─── Promotion / Voucher ────────────────────────────────────────────────
    VOUCHER_NOT_FOUND(HttpStatus.NOT_FOUND, "VOUCHER_NOT_FOUND", "Voucher not found"),
    VOUCHER_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "VOUCHER_INVALID",
            "Voucher is invalid"),
    VOUCHER_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "VOUCHER_EXPIRED",
            "Voucher has expired"),
    VOUCHER_USAGE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "VOUCHER_USAGE_LIMIT_EXCEEDED",
            "Voucher usage limit has been reached"),
    VOUCHER_NOT_APPLICABLE(HttpStatus.UNPROCESSABLE_ENTITY, "VOUCHER_NOT_APPLICABLE",
            "Voucher is not applicable to this order"),
    VOUCHER_MIN_ORDER_NOT_MET(HttpStatus.UNPROCESSABLE_ENTITY, "VOUCHER_MIN_ORDER_NOT_MET",
            "Order amount does not meet the minimum required for this voucher"),
    VOUCHER_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT, "VOUCHER_CODE_ALREADY_EXISTS",
            "Voucher code is already in use"),
    VOUCHER_USER_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "VOUCHER_USER_LIMIT_EXCEEDED",
            "You have reached the per-user usage limit for this voucher"),
    PROMOTION_NOT_FOUND(HttpStatus.NOT_FOUND, "PROMOTION_NOT_FOUND",
            "Promotion not found"),
    PROMOTION_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROMOTION_RULE_NOT_FOUND",
            "Promotion rule not found"),

    // ─── Shipment / Invoice ─────────────────────────────────────────────────
    SHIPMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", "Shipment not found"),
    SHIPMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "SHIPMENT_ALREADY_EXISTS",
            "A shipment already exists for this order"),
    SHIPMENT_STATUS_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "SHIPMENT_STATUS_INVALID",
            "Invalid shipment status transition"),
    INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "Invoice not found"),
    INVOICE_ALREADY_EXISTS(HttpStatus.CONFLICT, "INVOICE_ALREADY_EXISTS",
            "An invoice already exists for this order"),
    INVOICE_STATUS_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "INVOICE_STATUS_INVALID",
            "Invalid invoice status or transition"),

    // ─── Review ─────────────────────────────────────────────────────────────
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review not found"),
    REVIEW_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_NOT_ELIGIBLE",
            "You can only review products from completed orders"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS",
            "You have already reviewed this product"),
    REVIEW_ALREADY_MODERATED(HttpStatus.CONFLICT, "REVIEW_ALREADY_MODERATED",
            "Review has already been moderated"),

    // ─── Notification ───────────────────────────────────────────────────────
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
            "Notification not found");

    // ────────────────────────────────────────────────────────────────────────

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
