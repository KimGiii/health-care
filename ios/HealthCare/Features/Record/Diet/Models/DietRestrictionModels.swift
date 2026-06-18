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

struct DailyDietRecommendationRequest: Encodable {
    let date: String           // "yyyy-MM-dd"
    let mealTypes: [String]
    let strictAllergyMode: Bool
    let alternativeCount: Int
}

struct DailyDietRecommendationResponse: Codable {
    let date: String
    let targets: NutritionTargets
    let remainingTargets: NutritionTargets
    let appliedRestrictions: [DietRestriction]
    let meals: [RecommendedMeal]
    let totalNutrients: NutrientSummary
    let failureReason: String?
    let strictAllergyMode: Bool
    let disclaimer: String
    let alternatives: [[RecommendedMeal]]

    var succeeded: Bool { failureReason == nil }

    struct NutrientSummary: Codable {
        let totalCalories: Double
        let totalProteinG: Double
        let totalCarbsG: Double
        let totalFatG: Double
    }
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

    var id: Int { foodCatalogId }

    var displayName: String {
        let prefersKo = (Locale.preferredLanguages.first ?? "").hasPrefix("ko")
        if prefersKo, let ko = nameKo, !ko.isEmpty { return ko }
        return name
    }

    var needsCaution: Bool { caution != nil || allergenConfidenceLevel.cautionRequired }
}
