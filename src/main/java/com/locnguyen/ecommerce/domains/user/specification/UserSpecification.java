package com.locnguyen.ecommerce.domains.user.specification;

import com.locnguyen.ecommerce.common.specification.SoftDeleteSpecificationHelper;
import com.locnguyen.ecommerce.domains.admin.dto.AdminUserFilter;
import com.locnguyen.ecommerce.domains.user.entity.Role;
import com.locnguyen.ecommerce.domains.user.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    private UserSpecification() {}

    public static Specification<User> withFilter(AdminUserFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            SoftDeleteSpecificationHelper.addDeletedFilter(
                    predicates,
                    root.get("deleted"),
                    cb,
                    filter != null ? filter.getIsDeleted() : null,
                    filter != null ? filter.getIncludeDeleted() : null
            );

            if (filter != null) {
                if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                    String pattern = "%" + filter.getKeyword().trim().toUpperCase() + "%";
                    Predicate emailLike = cb.like(cb.upper(root.get("email")), pattern);
                    Predicate firstNameLike = cb.like(cb.upper(root.get("firstName")), pattern);
                    Predicate lastNameLike = cb.like(cb.upper(root.get("lastName")), pattern);
                    Predicate phoneLike = cb.like(cb.upper(root.get("phoneNumber")), pattern);
                    predicates.add(cb.or(emailLike, firstNameLike, lastNameLike, phoneLike));
                }

                if (filter.getEmail() != null && !filter.getEmail().isBlank()) {
                    predicates.add(cb.like(
                            cb.upper(root.get("email")),
                            "%" + filter.getEmail().trim().toUpperCase() + "%"
                    ));
                }

                if (filter.getPhoneNumber() != null && !filter.getPhoneNumber().isBlank()) {
                    predicates.add(cb.like(
                            root.get("phoneNumber"),
                            "%" + filter.getPhoneNumber().trim() + "%"
                    ));
                }

                if (filter.getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), filter.getStatus()));
                }

                if (filter.getRole() != null) {
                    if (query != null) {
                        query.distinct(true);
                    }
                    Join<User, Role> rolesJoin = root.join("roles", JoinType.INNER);
                    predicates.add(cb.equal(rolesJoin.get("name"), filter.getRole()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
