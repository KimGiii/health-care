package com.healthcare.domain.diet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "food_serving_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class FoodServingOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "food_catalog_id", nullable = false)
    private Long foodCatalogId;

    /**
     * 표시 레이블 (e.g. "1회 제공량", "1개", "100g").
     * iOS 프리셋 UX에서 직접 표시한다.
     */
    @Column(nullable = false, length = 60)
    private String label;

    @Column(name = "label_ko", length = 60)
    private String labelKo;

    /** 영양 계산에 사용하는 환산 g */
    @Column(name = "equivalent_g", nullable = false)
    private Double equivalentG;

    /** 0 = 기본(primary) 옵션. 오름차순 정렬로 표시 순서를 결정한다. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "serving_type", nullable = false, length = 30)
    private ServingType servingType;

    /** null이면 미검증 옵션 */
    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public enum ServingType {
        /** 공식 1회 제공량 기반 (0.5×, 1×, 1.5× …) */
        OFFICIAL_SERVING,
        /** 개수 단위 (1개, 2개) */
        COUNT_UNIT,
        /** 검증된 중량 step (50g, 100g …) */
        WEIGHT_STEP
    }
}
