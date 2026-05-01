package com.locnguyen.ecommerce.domains.user.repository;

import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndDeletedFalse(String email);

    Optional<User> findByIdAndDeletedFalse(UUID id);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Count active (non-deleted) users that have the given role assigned.
     * Used to guard the "last SUPER_ADMIN" safety rule.
     */
    @Query("""
            select count(u) from User u join u.roles r
            where r.name = :roleName
              and u.deleted = false
              and u.status = com.locnguyen.ecommerce.domains.user.enums.UserStatus.ACTIVE
            """)
    long countActiveByRoleName(@Param("roleName") RoleName roleName);
}
