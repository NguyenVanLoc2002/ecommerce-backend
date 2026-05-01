package com.locnguyen.ecommerce.domains.brand.specification;

import com.locnguyen.ecommerce.domains.brand.dto.BrandFilter;
import com.locnguyen.ecommerce.domains.brand.entity.Brand;
import com.locnguyen.ecommerce.domains.brand.enums.BrandStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrandSpecificationTest {

    @Mock Root<Brand> root;
    @Mock CriteriaQuery<?> query;
    @Mock CriteriaBuilder cb;

    @Mock Path<Boolean> deletedPath;
    @Mock Path<String> namePath;
    @Mock Path<BrandStatus> statusPath;
    @Mock Expression<String> upperName;

    @Mock Predicate notDeleted;
    @Mock Predicate deletedOnly;
    @Mock Predicate namePredicate;
    @Mock Predicate statusPredicate;
    @Mock Predicate combinedPredicate;

    @Test
    void defaults_to_non_deleted_rows() {
        stubBasePredicates();
        when(cb.and(any(Predicate[].class))).thenReturn(combinedPredicate);

        Predicate result = BrandSpecification.withFilter(new BrandFilter()).toPredicate(root, query, cb);

        assertThat(result).isSameAs(combinedPredicate);
        assertThat(capturedPredicates()).containsExactly(notDeleted);
    }

    @Test
    void supports_deleted_only_filter() {
        BrandFilter filter = new BrandFilter();
        filter.setIsDeleted(true);

        stubBasePredicates();
        when(cb.and(any(Predicate[].class))).thenReturn(combinedPredicate);

        Predicate result = BrandSpecification.withFilter(filter).toPredicate(root, query, cb);

        assertThat(result).isSameAs(combinedPredicate);
        assertThat(capturedPredicates()).containsExactly(deletedOnly);
    }

    @Test
    void includeDeleted_true_returns_all_matching_status_and_name_rows() {
        BrandFilter filter = new BrandFilter();
        filter.setIncludeDeleted(true);
        filter.setName("nike");
        filter.setStatus(BrandStatus.ACTIVE);

        stubBasePredicates();
        when(root.get("name")).thenReturn((Path) namePath);
        when(root.get("status")).thenReturn((Path) statusPath);
        when(cb.upper(namePath)).thenReturn(upperName);
        when(cb.like(upperName, "%NIKE%")).thenReturn(namePredicate);
        when(cb.equal(statusPath, BrandStatus.ACTIVE)).thenReturn(statusPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(combinedPredicate);

        Predicate result = BrandSpecification.withFilter(filter).toPredicate(root, query, cb);

        assertThat(result).isSameAs(combinedPredicate);
        assertThat(capturedPredicates()).containsExactly(namePredicate, statusPredicate);
    }

    @SuppressWarnings("unchecked")
    private void stubBasePredicates() {
        when(root.get("deleted")).thenReturn((Path) deletedPath);
        when(cb.isFalse(deletedPath)).thenReturn(notDeleted);
        when(cb.isTrue(deletedPath)).thenReturn(deletedOnly);
    }

    private Predicate[] capturedPredicates() {
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(captor.capture());
        return captor.getValue();
    }
}
