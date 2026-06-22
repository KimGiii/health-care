package com.healthcare.domain.diet.allergen;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.ValidationException;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.allergen.dto.AddAllergenTagRequest;
import com.healthcare.domain.diet.allergen.dto.BulkAddAllergenTagsRequest;
import com.healthcare.domain.diet.allergen.dto.BulkAllergenTagResult;
import com.healthcare.domain.diet.allergen.dto.FoodAllergenTagResponse;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FoodAllergenTagAdminOperations")
class FoodAllergenTagAdminOperationsTest {

    @Mock private AdminOperationGuard adminOperationGuard;
    @Mock private FoodAllergenTagRepository tagRepository;
    @Mock private FoodCatalogRepository foodCatalogRepository;

    private FoodAllergenTagAdminOperations operations;

    private static final String TOKEN = "test-token";
    private static final Long FOOD_ID = 1L;

    @BeforeEach
    void setUp() {
        operations = new FoodAllergenTagAdminOperations(adminOperationGuard, tagRepository, foodCatalogRepository);
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("유효한 요청은 태그를 저장하고 응답을 반환한다")
        void add_validRequest_savesAndReturnsTag() {
            AddAllergenTagRequest request = new AddAllergenTagRequest(
                    FOOD_ID, AllergenTag.EGG, AllergenConfidenceLevel.LABEL_DERIVED,
                    AllergenDataSource.MFDS_CLASS, null, true
            );
            FoodAllergenTag saved = FoodAllergenTag.builder()
                    .foodCatalogId(FOOD_ID)
                    .allergenTag(AllergenTag.EGG)
                    .confidenceLevel(AllergenConfidenceLevel.LABEL_DERIVED)
                    .source(AllergenDataSource.MFDS_CLASS)
                    .allergenProfileVerified(true)
                    .build();

            given(foodCatalogRepository.existsById(FOOD_ID)).willReturn(true);
            given(tagRepository.existsByFoodCatalogIdAndAllergenTag(FOOD_ID, AllergenTag.EGG)).willReturn(false);
            given(tagRepository.save(any())).willReturn(saved);

            FoodAllergenTagResponse response = operations.add(TOKEN, request);

            assertThat(response.allergenTag()).isEqualTo(AllergenTag.EGG);
            assertThat(response.foodCatalogId()).isEqualTo(FOOD_ID);
            assertThat(response.allergenProfileVerified()).isTrue();
            verify(tagRepository).save(any());
        }

        @Test
        @DisplayName("이미 존재하는 태그는 저장 없이 기존 태그를 반환한다")
        void add_duplicateTag_returnsExistingWithoutSave() {
            AddAllergenTagRequest request = new AddAllergenTagRequest(
                    FOOD_ID, AllergenTag.MILK, AllergenConfidenceLevel.DIRECT_VERIFIED,
                    AllergenDataSource.MFDS_CLASS, null
            );
            FoodAllergenTag existing = FoodAllergenTag.builder()
                    .foodCatalogId(FOOD_ID)
                    .allergenTag(AllergenTag.MILK)
                    .confidenceLevel(AllergenConfidenceLevel.DIRECT_VERIFIED)
                    .source(AllergenDataSource.MFDS_CLASS)
                    .build();

            given(foodCatalogRepository.existsById(FOOD_ID)).willReturn(true);
            given(tagRepository.existsByFoodCatalogIdAndAllergenTag(FOOD_ID, AllergenTag.MILK)).willReturn(true);
            given(tagRepository.findByFoodCatalogId(FOOD_ID)).willReturn(List.of(existing));

            FoodAllergenTagResponse response = operations.add(TOKEN, request);

            assertThat(response.allergenTag()).isEqualTo(AllergenTag.MILK);
        }

        @Test
        @DisplayName("존재하지 않는 식품 ID면 ResourceNotFoundException을 던진다")
        void add_foodNotFound_throwsResourceNotFoundException() {
            AddAllergenTagRequest request = new AddAllergenTagRequest(
                    999L, AllergenTag.EGG, AllergenConfidenceLevel.UNKNOWN,
                    AllergenDataSource.USER_CUSTOM, null
            );
            given(foodCatalogRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() -> operations.add(TOKEN, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("admin token이 없으면 AccessDeniedException을 던진다")
        void add_missingToken_throwsAccessDeniedException() {
            AddAllergenTagRequest request = new AddAllergenTagRequest(
                    FOOD_ID, AllergenTag.EGG, AllergenConfidenceLevel.UNKNOWN,
                    AllergenDataSource.USER_CUSTOM, null
            );
            org.mockito.Mockito.doThrow(new AccessDeniedException("관리자 작업 권한이 없습니다."))
                    .when(adminOperationGuard).assertAllowed(null);

            assertThatThrownBy(() -> operations.add(null, request))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("프로필 검토 완료 플래그는 UNKNOWN 태그에 사용할 수 없다")
        void add_profileVerifiedWithUnknown_throwsValidationException() {
            AddAllergenTagRequest request = new AddAllergenTagRequest(
                    FOOD_ID, AllergenTag.EGG, AllergenConfidenceLevel.UNKNOWN,
                    AllergenDataSource.USER_CUSTOM, null, true
            );

            assertThatThrownBy(() -> operations.add(TOKEN, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("allergenProfileVerified");
        }

        @Test
        @DisplayName("프로필 검토 완료 플래그는 RECIPE_DERIVED 태그에 사용할 수 없다")
        void add_profileVerifiedWithRecipeDerived_throwsValidationException() {
            AddAllergenTagRequest request = new AddAllergenTagRequest(
                    FOOD_ID, AllergenTag.EGG, AllergenConfidenceLevel.RECIPE_DERIVED,
                    AllergenDataSource.KHANES_RECIPE, null, true
            );

            assertThatThrownBy(() -> operations.add(TOKEN, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("DIRECT_VERIFIED");
        }
    }

    @Nested
    @DisplayName("bulkAdd")
    class BulkAdd {

        @Test
        @DisplayName("새 태그는 created, 중복은 skipped로 집계한다")
        void bulkAdd_countsCreatedAndSkipped() {
            AddAllergenTagRequest newTag = new AddAllergenTagRequest(
                    FOOD_ID, AllergenTag.EGG, AllergenConfidenceLevel.LABEL_DERIVED,
                    AllergenDataSource.MFDS_CLASS, null
            );
            AddAllergenTagRequest dup = new AddAllergenTagRequest(
                    FOOD_ID, AllergenTag.MILK, AllergenConfidenceLevel.LABEL_DERIVED,
                    AllergenDataSource.MFDS_CLASS, null
            );
            AddAllergenTagRequest missingFood = new AddAllergenTagRequest(
                    999L, AllergenTag.SOY, AllergenConfidenceLevel.UNKNOWN,
                    AllergenDataSource.USER_CUSTOM, null
            );

            given(foodCatalogRepository.existsById(FOOD_ID)).willReturn(true);
            given(foodCatalogRepository.existsById(999L)).willReturn(false);
            given(tagRepository.existsByFoodCatalogIdAndAllergenTag(FOOD_ID, AllergenTag.EGG)).willReturn(false);
            given(tagRepository.existsByFoodCatalogIdAndAllergenTag(FOOD_ID, AllergenTag.MILK)).willReturn(true);
            given(tagRepository.save(any())).willReturn(FoodAllergenTag.builder()
                    .foodCatalogId(FOOD_ID).allergenTag(AllergenTag.EGG)
                    .confidenceLevel(AllergenConfidenceLevel.LABEL_DERIVED)
                    .source(AllergenDataSource.MFDS_CLASS).build());

            BulkAllergenTagResult result = operations.bulkAdd(TOKEN,
                    new BulkAddAllergenTagsRequest(List.of(newTag, dup, missingFood)));

            assertThat(result.created()).isEqualTo(1);
            assertThat(result.skipped()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getByFoodId")
    class GetByFoodId {

        @Test
        @DisplayName("식품의 알러젠 태그 목록을 반환한다")
        void getByFoodId_returnsTags() {
            FoodAllergenTag tag = FoodAllergenTag.builder()
                    .foodCatalogId(FOOD_ID)
                    .allergenTag(AllergenTag.EGG)
                    .confidenceLevel(AllergenConfidenceLevel.DIRECT_VERIFIED)
                    .source(AllergenDataSource.MFDS_CLASS)
                    .build();

            given(foodCatalogRepository.existsById(FOOD_ID)).willReturn(true);
            given(tagRepository.findByFoodCatalogId(FOOD_ID)).willReturn(List.of(tag));

            List<FoodAllergenTagResponse> result = operations.getByFoodId(TOKEN, FOOD_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).allergenTag()).isEqualTo(AllergenTag.EGG);
        }

        @Test
        @DisplayName("존재하지 않는 식품 ID면 ResourceNotFoundException을 던진다")
        void getByFoodId_foodNotFound_throws() {
            given(foodCatalogRepository.existsById(FOOD_ID)).willReturn(false);

            assertThatThrownBy(() -> operations.getByFoodId(TOKEN, FOOD_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("존재하는 태그를 삭제한다")
        void delete_existingTag_deletesSuccessfully() {
            given(tagRepository.existsById(10L)).willReturn(true);

            operations.delete(TOKEN, 10L);

            verify(tagRepository).deleteById(10L);
        }

        @Test
        @DisplayName("존재하지 않는 태그 ID면 ResourceNotFoundException을 던진다")
        void delete_tagNotFound_throws() {
            given(tagRepository.existsById(99L)).willReturn(false);

            assertThatThrownBy(() -> operations.delete(TOKEN, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // OffsetDateTime 비교를 위한 헬퍼
    private OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}
