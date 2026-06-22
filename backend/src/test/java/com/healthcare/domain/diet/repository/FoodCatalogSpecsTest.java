package com.healthcare.domain.diet.repository;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FoodCatalogSpecs 단위 테스트")
class FoodCatalogSpecsTest {

    @Mock private Root<FoodCatalog> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder cb;
    @SuppressWarnings("rawtypes")
    @Mock private Path statusPath;
    @Mock private Predicate predicate;

    @Test
    @DisplayName("hasRecommendationCandidateStatus: 추천 후보 상태만 포함한다")
    @SuppressWarnings("unchecked")
    void hasRecommendationCandidateStatus_includesOnlyCandidateStatuses() {
        given(root.get("recommendationStatus")).willReturn(statusPath);
        given(statusPath.in(any(Collection.class))).willReturn(predicate);

        Specification<FoodCatalog> spec = FoodCatalogSpecs.hasRecommendationCandidateStatus();
        spec.toPredicate(root, query, cb);

        ArgumentCaptor<Collection<RecommendationStatus>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(statusPath).in(captor.capture());

        assertThat(captor.getValue())
                .containsExactlyInAnyOrder(
                        RecommendationStatus.RECOMMENDABLE,
                        RecommendationStatus.RECOMMENDABLE_WITH_CAUTION);
    }

    @Test
    @DisplayName("hasRecommendationCandidateStatus: SEARCH_ONLY는 포함하지 않는다")
    @SuppressWarnings("unchecked")
    void hasRecommendationCandidateStatus_excludesSearchOnly() {
        given(root.get("recommendationStatus")).willReturn(statusPath);
        given(statusPath.in(any(Collection.class))).willReturn(predicate);

        Specification<FoodCatalog> spec = FoodCatalogSpecs.hasRecommendationCandidateStatus();
        spec.toPredicate(root, query, cb);

        ArgumentCaptor<Collection<RecommendationStatus>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(statusPath).in(captor.capture());

        assertThat(captor.getValue()).doesNotContain(RecommendationStatus.SEARCH_ONLY);
    }

    @Test
    @DisplayName("hasRecommendationCandidateStatus: DISABLED는 포함하지 않는다")
    @SuppressWarnings("unchecked")
    void hasRecommendationCandidateStatus_excludesDisabled() {
        given(root.get("recommendationStatus")).willReturn(statusPath);
        given(statusPath.in(any(Collection.class))).willReturn(predicate);

        Specification<FoodCatalog> spec = FoodCatalogSpecs.hasRecommendationCandidateStatus();
        spec.toPredicate(root, query, cb);

        ArgumentCaptor<Collection<RecommendationStatus>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(statusPath).in(captor.capture());

        assertThat(captor.getValue()).doesNotContain(RecommendationStatus.DISABLED);
    }

    @Test
    @DisplayName("isCanonicalCandidate: canonical_group_id IS NULL 조건을 생성한다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void isCanonicalCandidate_generatesIsNullPredicateOnCanonicalGroupId() {
        Path canonicalGroupIdPath = mock(Path.class);
        given(root.get("canonicalGroupId")).willReturn(canonicalGroupIdPath);
        given(cb.isNull(canonicalGroupIdPath)).willReturn(predicate);

        Specification<FoodCatalog> spec = FoodCatalogSpecs.isCanonicalCandidate();
        Predicate result = spec.toPredicate(root, query, cb);

        verify(cb).isNull(canonicalGroupIdPath);
        assertThat(result).isEqualTo(predicate);
    }
}
