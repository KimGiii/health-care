import Foundation

// MARK: - Enums

enum MealType: String, Codable, CaseIterable {
    case BREAKFAST, LUNCH, DINNER, SNACK

    var displayName: String {
        switch self {
        case .BREAKFAST: return "아침"
        case .LUNCH:     return "점심"
        case .DINNER:    return "저녁"
        case .SNACK:     return "간식"
        }
    }

    var emoji: String {
        switch self {
        case .BREAKFAST: return "🌅"
        case .LUNCH:     return "☀️"
        case .DINNER:    return "🌙"
        case .SNACK:     return "🍎"
        }
    }
}

enum FoodCategory: String, Codable {
    case GRAIN, PROTEIN, VEGETABLE, FRUIT, DAIRY, FAT, BEVERAGE, PROCESSED, OTHER

    var displayName: String {
        switch self {
        case .GRAIN:     return "곡류"
        case .PROTEIN:   return "단백질"
        case .VEGETABLE: return "채소"
        case .FRUIT:     return "과일"
        case .DAIRY:     return "유제품"
        case .FAT:       return "지방"
        case .BEVERAGE:  return "음료"
        case .PROCESSED: return "가공식품"
        case .OTHER:     return "기타"
        }
    }

    var emoji: String {
        switch self {
        case .GRAIN:     return "🍚"
        case .PROTEIN:   return "🥩"
        case .VEGETABLE: return "🥦"
        case .FRUIT:     return "🍎"
        case .DAIRY:     return "🥛"
        case .FAT:       return "🥑"
        case .BEVERAGE:  return "🧃"
        case .PROCESSED: return "🍱"
        case .OTHER:     return "🍽"
        }
    }
}

enum FoodDataSource: String, Codable {
    case PUBLIC_FOOD_API, ALL

    var displayName: String {
        switch self {
        case .PUBLIC_FOOD_API: return "공공데이터"
        case .ALL:             return "전체"
        }
    }
}

// MARK: - DietLog

struct DietLogSummary: Codable, Identifiable {
    let dietLogId: Int
    let logDate: String           // "yyyy-MM-dd"
    let mealType: MealType
    let totalCalories: Double?
    let totalProteinG: Double?
    let totalCarbsG: Double?
    let totalFatG: Double?

    var id: Int { dietLogId }

    var formattedDate: String {
        let parts = logDate.split(separator: "-")
        guard parts.count == 3 else { return logDate }
        return "\(parts[1])월 \(parts[2])일"
    }

    var caloriesText: String {
        guard let kcal = totalCalories else { return "-" }
        return String(format: "%.0f kcal", kcal)
    }
}

struct DietLogListResponse: Codable {
    let content: [DietLogSummary]
    let page: Int
    let size: Int
    let totalElements: Int
    let totalPages: Int
    let first: Bool
    let last: Bool
}

struct DietLogDetailResponse: Codable, Identifiable {
    let dietLogId: Int
    let logDate: String
    let mealType: MealType
    let totalCalories: Double?
    let totalProteinG: Double?
    let totalCarbsG: Double?
    let totalFatG: Double?
    let notes: String?
    let entries: [FoodEntryResponse]

    var id: Int { dietLogId }
}

struct FoodEntryResponse: Codable, Identifiable {
    let id: Int
    let foodCatalogId: Int
    let foodName: String
    let foodNameKo: String?
    let category: FoodCategory?
    let servingG: Double
    let calories: Double?
    let proteinG: Double?
    let carbsG: Double?
    let fatG: Double?
    let notes: String?

    var displayName: String { foodNameKo ?? foodName }
}

// MARK: - FoodCatalog

struct FoodCatalogItem: Codable, Identifiable {
    let id: Int
    let name: String
    let nameKo: String?
    let category: FoodCategory?
    let caloriesPer100g: Double?
    let proteinPer100g: Double?
    let carbsPer100g: Double?
    let fatPer100g: Double?
    let custom: Bool

    var displayName: String { nameKo ?? name }

    func calories(forServing g: Double) -> Double {
        ((caloriesPer100g ?? 0) * g) / 100
    }
    func protein(forServing g: Double) -> Double {
        ((proteinPer100g ?? 0) * g) / 100
    }
    func carbs(forServing g: Double) -> Double {
        ((carbsPer100g ?? 0) * g) / 100
    }
    func fat(forServing g: Double) -> Double {
        ((fatPer100g ?? 0) * g) / 100
    }
}

// MARK: - External Food

struct ExternalFoodResult: Codable, Identifiable {
    let source: FoodDataSource
    let externalId: String
    let name: String
    let nameKo: String?
    let brand: String?
    let category: FoodCategory?
    let caloriesPer100g: Double?
    let proteinPer100g: Double?
    let carbsPer100g: Double?
    let fatPer100g: Double?

    var id: String { "\(source.rawValue)-\(externalId)" }
    var displayName: String { nameKo ?? name }

    var nutritionSummary: String {
        let kcal = caloriesPer100g.map { String(format: "%.0f kcal", $0) } ?? "-"
        let p    = proteinPer100g.map  { String(format: "P %.1fg", $0) }   ?? ""
        let c    = carbsPer100g.map    { String(format: "C %.1fg", $0) }   ?? ""
        let f    = fatPer100g.map      { String(format: "F %.1fg", $0) }   ?? ""
        return [kcal, p, c, f].filter { !$0.isEmpty }.joined(separator: " · ")
    }
}

// MARK: - Request DTOs

struct CreateDietLogRequest: Codable {
    let logDate: String           // "yyyy-MM-dd"
    let mealType: String          // MealType.rawValue
    let entries: [CreateFoodEntryRequest]
    let notes: String?
}

struct CreateFoodEntryRequest: Codable {
    let foodCatalogId: Int
    let servingG: Double
    let notes: String?
}

struct ImportFoodRequest: Codable {
    let source: String            // FoodDataSource.rawValue
    let externalId: String
    let name: String
    let nameKo: String?
    let brand: String?
    let category: String          // FoodCategory.rawValue
    let caloriesPer100g: Double
    let proteinPer100g: Double?
    let carbsPer100g: Double?
    let fatPer100g: Double?
}

struct CreateDietLogResponse: Codable {
    let dietLogId: Int
    let logDate: String
    let mealType: MealType
    let totalCalories: Double?
    let totalProteinG: Double?
    let totalCarbsG: Double?
    let totalFatG: Double?
}

// MARK: - Draft (로컬 상태)

struct DraftFoodEntry: Identifiable {
    let id = UUID()
    var food: FoodCatalogItem
    var servingGText: String = "100"
    var notes: String = ""

    var servingG: Double { Double(servingGText) ?? 100 }
    var calories: Double { food.calories(forServing: servingG) }
    var protein:  Double { food.protein(forServing: servingG) }
    var carbs:    Double { food.carbs(forServing: servingG) }
    var fat:      Double { food.fat(forServing: servingG) }

    var isValid: Bool { servingG > 0 }
}
