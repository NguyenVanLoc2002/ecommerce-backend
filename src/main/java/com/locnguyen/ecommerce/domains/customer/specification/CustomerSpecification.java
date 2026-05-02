package com.locnguyen.ecommerce.domains.customer.specification;

import com.locnguyen.ecommerce.common.specification.SoftDeleteSpecificationHelper;
import com.locnguyen.ecommerce.domains.admin.dto.AdminCustomerFilter;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.user.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CustomerSpecification {

    private CustomerSpecification() {}

    public static Specification<Customer> withFilter(AdminCustomerFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            SoftDeleteSpecificationHelper.addDeletedFilter(
                    predicates,
                    root.get("deleted"),
                    cb,
                    filter != null ? filter.getIsDeleted() : null,
                    filter != null ? filter.getIncludeDeleted() : null
            );

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // Join with linked User; INNER because Customer.user is non-optional.
            Join<Customer, User> userJoin = root.join("user", JoinType.INNER);

            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String pattern = "%" + filter.getKeyword().trim().toUpperCase() + "%";
                Predicate emailLike = cb.like(cb.upper(userJoin.get("email")), pattern);
                Predicate firstNameLike = cb.like(cb.upper(userJoin.get("firstName")), pattern);
                Predicate lastNameLike = cb.like(cb.upper(userJoin.get("lastName")), pattern);
                Predicate phoneLike = cb.like(cb.upper(userJoin.get("phoneNumber")), pattern);
                predicates.add(cb.or(emailLike, firstNameLike, lastNameLike, phoneLike));
            }

            if (filter.getEmail() != null && !filter.getEmail().isBlank()) {
                predicates.add(cb.like(
                        cb.upper(userJoin.get("email")),
                        "%" + filter.getEmail().trim().toUpperCase() + "%"
                ));
            }

            if (filter.getPhoneNumber() != null && !filter.getPhoneNumber().isBlank()) {
                predicates.add(cb.like(
                        userJoin.get("phoneNumber"),
                        "%" + filter.getPhoneNumber().trim() + "%"
                ));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(userJoin.get("status"), filter.getStatus()));
            }

            if (filter.getGender() != null) {
                predicates.add(cb.equal(root.get("gender"), filter.getGender()));
            }

            if (filter.getMinLoyaltyPoints() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("loyaltyPoints"), filter.getMinLoyaltyPoints()));
            }

            if (filter.getMaxLoyaltyPoints() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("loyaltyPoints"), filter.getMaxLoyaltyPoints()));
            }

            if (filter.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"), filter.getDateFrom().atStartOfDay()));
            }

            if (filter.getDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"), filter.getDateTo().atTime(LocalTime.MAX)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
