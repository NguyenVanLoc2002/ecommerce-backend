package com.locnguyen.ecommerce.domains.invoice.repository;

import com.locnguyen.ecommerce.domains.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>,
        JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByOrderId(Long orderId);

    Optional<Invoice> findByInvoiceCode(String invoiceCode);

    boolean existsByOrderId(Long orderId);
}
