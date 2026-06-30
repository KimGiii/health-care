package com.healthcare.domain.diet.allergen;

import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

/**
 * 알러젠 안전 판단 게이트.
 *
 * <p>식품 하나에 대해 AllergenContext를 받아 SafetyDecision을 반환한다.
 * 판단 순서:
 * <ol>
 *   <li>제한 알러젠 없음 → 통과 (신뢰도만 해석)</li>
 *   <li>제한 알러젠 포함 → 차단. {@code GLUTEN} 제한은 {@code WHEAT} 포함 태그도 차단한다.</li>
 *   <li>완결 알러젠 프로필 미검증 → 차단</li>
 *   <li>strict 모드: 완결 프로필의 신뢰도가 프로필 등급이 아니면 차단</li>
 *   <li>모두 통과 → 허용 (신뢰도 포함)</li>
 * </ol>
 *
 * <p>신뢰도 해석: 프로필 레코드가 있으면 프로필 신뢰도, 없으면 태그 중 최고 신뢰도.
 */
@Component
public class AllergenSafetyGate {

    private final Clock clock;

    public AllergenSafetyGate() {
        this(Clock.systemUTC());
    }

    public AllergenSafetyGate(Clock clock) {
        this.clock = clock;
    }

    public SafetyDecision decide(AllergenContext context) {
        if (context.restrictedTags().isEmpty()) {
            return SafetyDecision.allow(resolvedConfidence(context));
        }
        if (containsRestrictedAllergen(context)) {
            return SafetyDecision.block();
        }
        if (!profileIsVerified(context)) {
            return SafetyDecision.block();
        }
        if (context.strictAllergyMode() && !profileHasProfileGradeConfidence(context)) {
            return SafetyDecision.block();
        }
        return SafetyDecision.allow(resolvedConfidence(context));
    }

    private boolean containsRestrictedAllergen(AllergenContext ctx) {
        Set<AllergenTag> restrictedTags = expandedRestrictedTags(ctx.restrictedTags());
        return ctx.tags().stream()
                .anyMatch(t -> restrictedTags.contains(t.getAllergenTag()));
    }

    private Set<AllergenTag> expandedRestrictedTags(Set<AllergenTag> restrictedTags) {
        if (!restrictedTags.contains(AllergenTag.GLUTEN)) {
            return restrictedTags;
        }
        EnumSet<AllergenTag> expanded = EnumSet.copyOf(restrictedTags);
        expanded.add(AllergenTag.WHEAT);
        return expanded;
    }

    private boolean profileIsVerified(AllergenContext ctx) {
        return ctx.profile() != null && ctx.profile().isVerifiedAt(OffsetDateTime.now(clock));
    }

    private boolean profileHasProfileGradeConfidence(AllergenContext ctx) {
        return ctx.profile() != null && ctx.profile().getConfidenceLevel().isProfileGrade();
    }

    private AllergenConfidenceLevel resolvedConfidence(AllergenContext ctx) {
        if (ctx.profile() != null) {
            return ctx.profile().getConfidenceLevel();
        }
        return ctx.tags().stream()
                .map(FoodAllergenTag::getConfidenceLevel)
                .min(Comparator.comparingInt(AllergenConfidenceLevel::ordinal))
                .orElse(AllergenConfidenceLevel.UNKNOWN);
    }
}
