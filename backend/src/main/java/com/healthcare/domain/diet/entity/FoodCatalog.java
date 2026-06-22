package com.healthcare.domain.diet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "food_catalog")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class FoodCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "name_ko", length = 150)
    private String nameKo;

    /**
     * 검색 보조 문자열. MFDS 원본 어순({@code 경단_깨})과 정규화 표시명({@code 깨경단})을 함께 담아
     * 사용자가 어느 어순으로 검색해도 매칭되게 한다. 정규화 대상이 아니면 null이다.
     */
    @Column(name = "search_alias", length = 400)
    private String searchAlias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodCategory category;

    @Column(name = "calories_per_100g", nullable = false)
    private Double caloriesPer100g;

    @Column(name = "protein_per_100g")
    private Double proteinPer100g;

    @Column(name = "carbs_per_100g")
    private Double carbsPer100g;

    @Column(name = "fat_per_100g")
    private Double fatPer100g;

    @Column(name = "sugars_per_100g")
    private Double sugarsPer100g;

    @Column(name = "dietary_fiber_per_100g")
    private Double dietaryFiberPer100g;

    @Column(name = "saturated_fat_per_100g")
    private Double saturatedFatPer100g;

    @Column(name = "trans_fat_per_100g")
    private Double transFatPer100g;

    @Column(name = "cholesterol_per_100g_mg")
    private Double cholesterolPer100gMg;

    @Column(name = "sodium_per_100g_mg")
    private Double sodiumPer100gMg;

    @Column(name = "food_code", length = 60)
    private String foodCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private FoodCatalogSource source = FoodCatalogSource.SEED;

    @Column(name = "source_detail", length = 120)
    private String sourceDetail;

    @Column(name = "brand_name", length = 150)
    private String brandName;

    @Column(length = 150)
    private String maker;

    @Column(name = "serving_size_g")
    private Double servingSizeG;

    @Column(name = "serving_reference", length = 80)
    private String servingReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_status", nullable = false, length = 40)
    @Builder.Default
    private RecommendationStatus recommendationStatus = RecommendationStatus.RECOMMENDABLE;

    @Column(name = "recommendation_reason", length = 255)
    private String recommendationReason;

    @Column(name = "data_version", length = 80)
    private String dataVersion;

    @Column(name = "last_verified_at")
    private OffsetDateTime lastVerifiedAt;

    @Column(name = "is_custom", nullable = false)
    private Boolean isCustom;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Long usageCount = 0L;

    /**
     * 정규(canonical) 행을 가리키는 supersede 포인터. NULL = 그룹 대표(검색·추천 노출),
     * NOT NULL = 이 행을 흡수한 canonical 행 id(추천 제외, provenance 보존). (V35 재사용)
     */
    @Column(name = "canonical_group_id")
    private Long canonicalGroupId;

    /** 출처 간 dedup 클러스터 키. = food_code(공통 코드 공간). 코드 없는 행은 NULL(dedup 비대상). */
    @Column(name = "dedup_group", length = 60)
    private String dedupGroup;

    /** dedup 클러스터 분리용 이름키. {@code FoodCatalogIdentity.duplicateNameKey(nameKo)}. */
    @Column(name = "dedup_name_key", length = 160)
    private String dedupNameKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "dedup_state", nullable = false, length = 20)
    @Builder.Default
    private DedupState dedupState = DedupState.CANONICAL;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    public void incrementUsage() {
        this.usageCount = (this.usageCount == null ? 0L : this.usageCount) + 1;
    }

    public void decrementUsage() {
        this.usageCount = Math.max((this.usageCount == null ? 0L : this.usageCount) - 1, 0L);
    }

    /**
     * 표시명 정규화 결과를 적용한다. MFDS 원본명을 보유한 행의 백필에 사용한다.
     * name/nameKo가 동일한 원본 어순이므로 둘 다 표시명으로 갱신한다.
     */
    public void applyDisplayNormalization(String displayName, String searchAlias) {
        this.name = displayName;
        this.nameKo = displayName;
        this.searchAlias = searchAlias;
    }

    public void updateSourceFactsFromImportedCatalog(FoodCatalog imported) {
        this.name = imported.name;
        this.nameKo = imported.nameKo;
        this.searchAlias = imported.searchAlias;
        this.category = imported.category;
        this.caloriesPer100g = imported.caloriesPer100g;
        this.proteinPer100g = imported.proteinPer100g;
        this.carbsPer100g = imported.carbsPer100g;
        this.fatPer100g = imported.fatPer100g;
        this.sugarsPer100g = imported.sugarsPer100g;
        this.dietaryFiberPer100g = imported.dietaryFiberPer100g;
        this.saturatedFatPer100g = imported.saturatedFatPer100g;
        this.transFatPer100g = imported.transFatPer100g;
        this.cholesterolPer100gMg = imported.cholesterolPer100gMg;
        this.sodiumPer100gMg = imported.sodiumPer100gMg;
        this.sourceDetail = imported.sourceDetail;
        this.brandName = imported.brandName;
        this.maker = imported.maker;
        this.servingSizeG = imported.servingSizeG;
        this.servingReference = imported.servingReference;
        this.dataVersion = imported.dataVersion;
        this.lastVerifiedAt = imported.lastVerifiedAt;
        this.isCustom = imported.isCustom;
    }

    /**
     * source 사실 변경으로 추천 자격을 자동 회수했을 때 남기는 사유. 운영자 재검증 큐 식별에 쓰인다.
     */
    public static final String STALE_FACTS_REVALIDATION_REASON = "source 사실 변경으로 재검증 필요";

    /**
     * 유의미한 변화로 판단하는 상대 변화율 임계값. 연속 재적재의 미세 변동(반올림·소폭 갱신)은
     * 회수 대상에서 제외하고, 실제 사실 변경만 회수하기 위한 운영 기준이다.
     */
    private static final double SIGNIFICANT_RELATIVE_CHANGE = 0.05;

    /**
     * 추천 자격 판정에 직접 영향을 주는 source 사실(4대 매크로, 제공량, 주의 판정 영양소)이
     * 가져온 항목과 <em>유의미하게</em> 다른지 비교한다. 상대 5%를 초과하는 변화이거나
     * 데이터 완전성이 바뀌는 경우(null↔값)만 변경으로 본다.
     * 이름·브랜드·카테고리 등 안전성과 무관한 변경은 포함하지 않는다.
     */
    public boolean recommendationFactsDifferFrom(FoodCatalog imported) {
        return significantlyDiffers(caloriesPer100g, imported.caloriesPer100g)
                || significantlyDiffers(proteinPer100g, imported.proteinPer100g)
                || significantlyDiffers(carbsPer100g, imported.carbsPer100g)
                || significantlyDiffers(fatPer100g, imported.fatPer100g)
                || significantlyDiffers(servingSizeG, imported.servingSizeG)
                || significantlyDiffers(sodiumPer100gMg, imported.sodiumPer100gMg)
                || significantlyDiffers(sugarsPer100g, imported.sugarsPer100g)
                || significantlyDiffers(saturatedFatPer100g, imported.saturatedFatPer100g);
    }

    private static boolean significantlyDiffers(Double oldValue, Double newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return false;
        }
        if (oldValue == null || newValue == null) {
            return true; // 데이터 완전성 변화는 항상 유의미
        }
        double denominator = Math.max(Math.abs(oldValue), Math.abs(newValue));
        if (denominator == 0.0) {
            return false;
        }
        return Math.abs(oldValue - newValue) / denominator > SIGNIFICANT_RELATIVE_CHANGE;
    }

    /**
     * source 사실이 바뀐 추천 후보의 자격을 회수해 재검증 대상으로 강등한다.
     * 추천 후보가 아닌 항목에는 영향을 주지 않는다.
     */
    public void revokeRecommendationForStaleFacts() {
        if (!recommendationStatus.isRecommendationCandidate()) {
            return;
        }
        this.recommendationStatus = RecommendationStatus.SEARCH_ONLY;
        this.recommendationReason = STALE_FACTS_REVALIDATION_REASON;
    }

    public RecommendationCuration curation() {
        return RecommendationCuration.of(recommendationStatus, recommendationReason);
    }

    public void updateCuration(RecommendationCuration curation) {
        this.recommendationStatus = curation.status();
        this.recommendationReason = curation.reasonForStorage();
    }

    /** 출처 간 dedup 클러스터 키를 부여한다(설계 §4-1). */
    public void assignDedupKeys(String dedupGroup, String dedupNameKey) {
        this.dedupGroup = dedupGroup;
        this.dedupNameKey = dedupNameKey;
    }

    /** 그룹 대표(canonical)로 표시한다. supersede 포인터를 끊고 상태를 CANONICAL로 둔다. */
    public void markCanonical() {
        this.canonicalGroupId = null;
        this.dedupState = DedupState.CANONICAL;
    }

    /** 우선순위에 밀린 패자로 표시한다. canonical 행을 가리키고 추천·검색에서 제외된다. */
    public void supersedeBy(FoodCatalog canonical) {
        if (canonical.id == null) {
            throw new IllegalArgumentException("canonical must be persisted before supersede");
        }
        this.canonicalGroupId = canonical.id;
        this.dedupState = DedupState.SUPERSEDED;
    }

    /** 같은 코드·다른 이름키 충돌을 검토 큐로 표시한다. 대표 지위는 유지(canonical_group_id 불변). */
    public void markCollision() {
        this.dedupState = DedupState.COLLISION;
    }

    /** 그룹 대표 여부(검색·추천 노출 게이트와 동일 의미). */
    public boolean isCanonicalRow() {
        return canonicalGroupId == null;
    }

    public enum FoodCategory {
        GRAIN, PROTEIN_SOURCE, VEGETABLE, FRUIT, DAIRY, FAT, BEVERAGE, PROCESSED, OTHER
    }

    /**
     * 출처 우선순위 dedup 상태(설계 §3).
     * CANONICAL = 그룹 대표 / SUPERSEDED = 내용 일치, 우선순위에 밀림 / COLLISION = 동일 코드·다른 이름(검토 필요).
     */
    public enum DedupState {
        CANONICAL, SUPERSEDED, COLLISION
    }
}
