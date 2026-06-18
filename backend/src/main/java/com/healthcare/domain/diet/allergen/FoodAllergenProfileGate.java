package com.healthcare.domain.diet.allergen;

import com.healthcare.domain.diet.allergen.entity.FoodAllergenProfile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

@Component
public class FoodAllergenProfileGate {

    private final Clock clock;

    public FoodAllergenProfileGate() {
        this(Clock.systemUTC());
    }

    public FoodAllergenProfileGate(Clock clock) {
        this.clock = clock;
    }

    public boolean passesGate(
            Long foodId,
            Set<AllergenTag> restrictedTags,
            Map<Long, FoodAllergenProfile> profilesByFoodId
    ) {
        if (restrictedTags.isEmpty()) {
            return true;
        }
        FoodAllergenProfile profile = profilesByFoodId.get(foodId);
        return profile != null && profile.isVerifiedAt(OffsetDateTime.now(clock));
    }
}
