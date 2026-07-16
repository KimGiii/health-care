import Foundation

// MARK: - Enums

enum AllergenTag: String, Codable, CaseIterable, Identifiable {
    case EGG, MILK, BUCKWHEAT, PEANUT, SOY, WHEAT
    case MACKEREL, CRAB, SHRIMP, PINE_NUT
    case PORK, PEACH, TOMATO, SULFITE
    case WALNUT, CHICKEN, BEEF, SQUID, SHELLFISH
    case GLUTEN

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .EGG:      return String(localized: "allergen.egg")
        case .MILK:     return String(localized: "allergen.milk")
        case .BUCKWHEAT:return String(localized: "allergen.buckwheat")
        case .PEANUT:   return String(localized: "allergen.peanut")
        case .SOY:      return String(localized: "allergen.soy")
        case .WHEAT:    return String(localized: "allergen.wheat")
        case .MACKEREL: return String(localized: "allergen.mackerel")
        case .CRAB:     return String(localized: "allergen.crab")
        case .SHRIMP:   return String(localized: "allergen.shrimp")
        case .PINE_NUT: return String(localized: "allergen.pineNut")
        case .PORK:     return String(localized: "allergen.pork")
        case .PEACH:    return String(localized: "allergen.peach")
        case .TOMATO:   return String(localized: "allergen.tomato")
        case .SULFITE:  return String(localized: "allergen.sulfite")
        case .WALNUT:   return String(localized: "allergen.walnut")
        case .CHICKEN:  return String(localized: "allergen.chicken")
        case .BEEF:     return String(localized: "allergen.beef")
        case .SQUID:    return String(localized: "allergen.squid")
        case .SHELLFISH:return String(localized: "allergen.shellfish")
        case .GLUTEN:   return String(localized: "allergen.gluten")
        }
    }

    var sfSymbol: String {
        switch self {
        case .EGG:                          return "oval.fill"
        case .MILK:                         return "drop.fill"
        case .BUCKWHEAT, .WHEAT, .GLUTEN:   return "leaf.fill"
        case .PEANUT, .PINE_NUT, .WALNUT:   return "circle.fill"
        case .SOY:                          return "bolt.fill"
        case .MACKEREL, .SQUID:             return "fish.fill"
        case .CRAB, .SHRIMP, .SHELLFISH:    return "fish.fill"
        case .PORK, .CHICKEN, .BEEF:        return "fork.knife"
        case .PEACH, .TOMATO:               return "tree.fill"
        case .SULFITE:                      return "exclamationmark.triangle.fill"
        }
    }
}

enum RestrictionType: String, Codable, CaseIterable {
    case ALLERGY, AVOID

    var displayName: String {
        switch self {
        case .ALLERGY: return String(localized: "restriction.type.allergy")
        case .AVOID:   return String(localized: "restriction.type.avoid")
        }
    }
}

enum TargetType: String, Codable {
    case FOOD, CATEGORY, KEYWORD, ALLERGEN_TAG
}

enum AllergenConfidenceLevel: String, Codable {
    case DIRECT_VERIFIED, LABEL_DERIVED, RECIPE_DERIVED, UNKNOWN

    var displayName: String {
        switch self {
        case .DIRECT_VERIFIED: return String(localized: "allergen.confidence.directVerified")
        case .LABEL_DERIVED:   return String(localized: "allergen.confidence.labelDerived")
        case .RECIPE_DERIVED:  return String(localized: "allergen.confidence.recipeDerived")
        case .UNKNOWN:         return String(localized: "allergen.confidence.unknown")
        }
    }

    var cautionRequired: Bool {
        self == .RECIPE_DERIVED || self == .UNKNOWN
    }
}

// MARK: - DietRestriction

struct DietRestriction: Codable, Identifiable {
    let id: Int
    let restrictionType: RestrictionType
    let targetType: TargetType
    let foodCatalogId: Int?
    let category: FoodCategory?
    let keyword: String?
    let allergenTag: AllergenTag?
    let createdAt: String?

    var displayLabel: String {
        switch targetType {
        case .ALLERGEN_TAG: return allergenTag?.displayName ?? ""
        case .CATEGORY:     return category?.displayName ?? ""
        case .KEYWORD:      return keyword ?? ""
        case .FOOD:         return String(format: String(localized: "restriction.food.id"), foodCatalogId ?? 0)
        }
    }

    var typeLabel: String { restrictionType.displayName }
}

struct CreateDietRestrictionRequest: Encodable {
    let restrictionType: String
    let targetType: String
    let foodCatalogId: Int?
    let category: String?
    let keyword: String?
    let allergenTag: String?
}

// MARK: - Recommendation

struct FeedbackRequest: Encodable {
    let reason: RecommendationFeedbackReason
}

struct DailyDietRecommendationRequest: Encodable {
    let date: String           // "yyyy-MM-dd"
    let mealTypes: [String]
    let strictAllergyMode: Bool
    let alternativeCount: Int
}

enum RecommendationFeedbackReason: String, Codable, CaseIterable, Identifiable {
    case NOT_HUNGRY, RECENTLY_ATE, PORTION_WRONG, HARD_TO_PREPARE, NUTRITION_DISLIKE, NO_REASON

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .NOT_HUNGRY:       return String(localized: "recommend.feedback.notHungry")
        case .RECENTLY_ATE:     return String(localized: "recommend.feedback.recentlyAte")
        case .PORTION_WRONG:    return String(localized: "recommend.feedback.portionWrong")
        case .HARD_TO_PREPARE:  return String(localized: "recommend.feedback.hardToPrepare")
        case .NUTRITION_DISLIKE:return String(localized: "recommend.feedback.nutritionDislike")
        case .NO_REASON:        return String(localized: "recommend.feedback.noReason")
        }
    }
}

struct DailyDietRecommendationResponse: Codable {
    let date: String
    let targets: NutritionTargets
    let remainingTargets: NutritionTargets
    let appliedRestrictions: [DietRestriction]
    let meals: [RecommendedMeal]
    let totalNutrients: NutrientSummary
    let failureReason: String?
    /// 구조화된 실패 사유 코드(§8.3). 성공·이미달성 시 nil.
    let failureCode: String?
    /// 추천 근거(§10). primary 해 기준. 실패 시 nil.
    let rationale: RecommendationRationale?
    let strictAllergyMode: Bool
    let disclaimer: String
    /// 대안 해 — 각자 끼니 구성과 근거를 함께 보유한다.
    let alternatives: [RecommendationSolution]
    let snapshotId: Int?

    var succeeded: Bool { failureReason == nil }

    struct NutrientSummary: Codable {
        let totalCalories: Double
        let totalProteinG: Double
        let totalCarbsG: Double
        let totalFatG: Double
    }
}

/// 추천 근거(§10). raw 점수 대신 무엇을 얼마나 채웠는지, 상·하한과의 차이를 보여 준다.
struct RecommendationRationale: Codable {
    let filledCalories: Double
    let filledProteinG: Double
    let filledCarbsG: Double
    /// 목표 열량 상한과의 여유. 상한이 없으면 nil.
    let calorieHeadroom: Double?
    /// 목표 단백질 하한과의 차이. 하한이 없으면 nil.
    let proteinGap: Double?
    let policyVersion: String
    let note: String
}

/// 대안 해 하나. 끼니 구성과 그 해의 근거를 함께 묶는다.
struct RecommendationSolution: Codable {
    let meals: [RecommendedMeal]
    let rationale: RecommendationRationale
}

struct NutritionTargets: Codable {
    let calorieTarget: Double
    let proteinTargetG: Double
    let carbTargetG: Double
    let fatTargetG: Double

    var targetKcal: Double { calorieTarget }
    var proteinG: Double { proteinTargetG }
    var carbsG: Double { carbTargetG }
    var fatG: Double { fatTargetG }
}

struct RecommendedMeal: Codable, Identifiable {
    let mealType: MealType
    let targetCalories: Double
    let totalCalories: Double
    let totalProteinG: Double
    let totalCarbsG: Double
    let totalFatG: Double
    let items: [RecommendedFoodEntry]

    var id: String { mealType.rawValue }
}

struct RecommendedFoodEntry: Codable, Identifiable {
    let foodCatalogId: Int
    let name: String
    let nameKo: String?
    let category: FoodCategory?
    let servingG: Double
    let calories: Double
    let proteinG: Double
    let carbsG: Double
    let fatG: Double
    let allergenConfidenceLevel: AllergenConfidenceLevel
    let caution: String?
    /// 낱개 식품의 개수 라벨("2개") 또는 제공량 라벨. 서버(백엔드 servingLabel) 미제공 시 nil.
    var servingLabel: String? = nil

    var id: Int { foodCatalogId }

    var displayName: String {
        let prefersKo = (Locale.preferredLanguages.first ?? "").hasPrefix("ko")
        if prefersKo, let ko = nameKo, !ko.isEmpty { return ko }
        return name
    }

    /// 낱개 식품이면 "2개" 같은 개수 라벨, 아니면 "100g"으로 표시한다(추천 항목 표시용).
    var displayServing: String {
        if let label = servingLabel, !label.isEmpty { return label }
        return String(format: "%.0fg", servingG)
    }

    var needsCaution: Bool { caution != nil || allergenConfidenceLevel.cautionRequired }
}
