package com.locnguyen.ecommerce.domains.customer.entity;

import com.locnguyen.ecommerce.common.auditing.SoftDeleteEntity;
import com.locnguyen.ecommerce.domains.address.entity.Address;
import com.locnguyen.ecommerce.domains.customer.enums.Gender;
import com.locnguyen.ecommerce.domains.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Customer business profile — linked 1-to-1 with {@link User}.
 *
 * <p><b>User</b> holds authentication/identity data (email, password, status, roles).
 * <b>Customer</b> holds business profile data (gender, birth date, avatar, loyalty).
 *
 * <p>Created automatically on registration. Soft-deletable per database guidelines.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Customer extends SoftDeleteEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "loyalty_points", nullable = false)
    private Integer loyaltyPoints = 0;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<Address> addresses = new HashSet<>();

    public Customer(User user) {
        this.user = user;
    }
}
