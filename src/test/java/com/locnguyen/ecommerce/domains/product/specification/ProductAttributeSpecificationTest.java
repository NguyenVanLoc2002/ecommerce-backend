package com.locnguyen.ecommerce.domains.product.specification;

import com.locnguyen.ecommerce.domains.product.dto.attribute.ProductAttributeFilter;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttribute;
import com.locnguyen.ecommerce.domains.product.enums.AttributeType;
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
class ProductAttributeSpecificationTest {

    @Mock Root<ProductAttribute> root;
    @Mock CriteriaQuery<?> query;
    @Mock CriteriaBuilder cb;

    @Mock Path<Boolean> deletedPath;
    @Mock Path<AttributeType> typePath;
    @Mock Path<String> namePath;
    @Mock Path<String> codePath;
    @Mock Expression<String> upperName;
    @Mock Expression<String> upperCode;

    @Mock Predicate notDeleted;
    @Mock Predicate deletedOnly;
    @Mock Predicate typePredicate;
    @Mock Predicate namePredicate;
    @Mock Predicate codePredicate;
    @Mock Predicate keywordPredicate;
    @Mock Predicate combinedPredicate;

    @Test
    void null_filter_defaults_to_non_deleted_predicate() {
        stubBasePredicates();
        when(cb.and(any(Predicate[].class))).thenReturn(combinedPredicate);

        Predicate result = ProductAttributeSpecification.withFilter(null)
                .toPredicate(root, query, cb);

        assertThat(result).isSameAs(combinedPredicate);
        assertThat(capturedPredicates()).containsExactly(notDeleted);
    }

    @Test
    void isDeleted_true_returns_deleted_only_predicate() {
        ProductAttributeFilter filter = new ProductAttributeFilter();
        filter.setIsDeleted(true);
        stubBasePredicates();
        when(cb.and(any(Predicate[].class))).thenReturn(combinedPredicate);

        Predicate result = ProductAttributeSpecification.withFilter(filter)
                .toPredicate(root, query, cb);

        assertThat(result).isSameAs(combinedPredicate);
        assertThat(capturedPredicates()).containsExactly(deletedOnly);
    }

    @Test
    void includeDeleted_true_skips_soft_delete_predicate() {
        ProductAttributeFilter filter = new ProductAttributeFilter();
        filter.setIncludeDeleted(true);
        filter.setType(AttributeType.VARIANT);

        stubBasePredicates();
        when(root.get("type")).thenReturn((Path) typePath);
        when(cb.equal(typePath, AttributeType.VARIANT)).thenReturn(typePredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(combinedPredicate);

        Predicate result = ProductAttributeSpecification.withFilter(filter)
                .toPredicate(root, query, cb);

        assertThat(result).isSameAs(combinedPredicate);
        assertThat(capturedPredicates()).containsExactly(typePredicate);
    }

    @Test
    void combines_default_non_deleted_predicate_with_type_and_keyword_filters() {
        ProductAttributeFilter filter = new ProductAttributeFilter();
        filter.setType(AttributeType.VARIANT);
        filter.setKeyword("color");

        stubBasePredicates();
        when(root.get("type")).thenReturn((Path) typePath);
        when(root.get("name")).thenReturn((Path) namePath);
        when(root.get("code")).thenReturn((Path) codePath);
        when(cb.equal(typePath, AttributeType.VARIANT)).thenReturn(typePredicate);
        when(cb.upper(namePath)).thenReturn(upperName);
        when(cb.upper(codePath)).thenReturn(upperCode);
        when(cb.like(upperName, "%COLOR%")).thenReturn(namePredicate);
        when(cb.like(upperCode, "%COLOR%")).thenReturn(codePredicate);
        when(cb.or(namePredicate, codePredicate)).thenReturn(keywordPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(combinedPredicate);

        Predicate result = ProductAttributeSpecification.withFilter(filter)
                .toPredicate(root, query, cb);

        assertThat(result).isSameAs(combinedPredicate);
        assertThat(capturedPredicates()).containsExactly(notDeleted, typePredicate, keywordPredicate);
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
