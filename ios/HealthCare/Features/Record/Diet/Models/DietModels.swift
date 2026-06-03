import Foundation

// MARK: - Enums

enum MealType: String, Codable, CaseIterable {
    case BREAKFAST, LUNCH, DINNER, SNACK

    var displayName: String {
        switch self {
        case .BREAKFAST: return String(localized: "diet.mealType.breakfast")
        case .LUNCH:     return String(localized: "diet.mealType.lunch")
        case .DINNER:    return String(localized: "diet.mealType.dinner")
        case .SNACK:     return String(localized: "diet.mealType.snack")
        }
    }

    var sfSymbol: String {
        switch self {
        case .BREAKFAST: return "sun.horizon.fill"
        case .LUNCH:     return "sun.max.fill"
        case .DINNER:    return "moon.stars.fill"
        case .SNACK:     return "cup.and.saucer.fill"
        }
    }
}

enum FoodCategory: String, Codable, CaseIterable {
    case GRAIN, PROTEIN_SOURCE, VEGETABLE, FRUIT, DAIRY, FAT, BEVERAGE, PROCESSED, OTHER

    var displayName: String {
        switch self {
        case .GRAIN:          return String(localized: "diet.category.grain")
        case .PROTEIN_SOURCE: return String(localized: "diet.category.proteinSource")
        case .VEGETABLE:      return String(localized: "diet.category.vegetable")
        case .FRUIT:          return String(localized: "diet.category.fruit")
        case .DAIRY:          return String(localized: "diet.category.dairy")
        case .FAT:            return String(localized: "diet.category.fat")
        case .BEVERAGE:       return String(localized: "diet.category.beverage")
        case .PROCESSED:      return String(localized: "diet.category.processed")
        case .OTHER:          return String(localized: "diet.category.other")
        }
    }

    var sfSymbol: String {
        switch self {
        case .GRAIN:          return "bowl.fill"
        case .PROTEIN_SOURCE: return "fish.fill"
        case .VEGETABLE:      return "leaf.fill"
        case .FRUIT:          return "tree.fill"
        case .DAIRY:          return "drop.fill"
        case .FAT:            return "drop.halffull"
        case .BEVERAGE:       return "cup.and.saucer.fill"
        case .PROCESSED:      return "cube.fill"
        case .OTHER:          return "fork.knife"
        }
    }
}

enum FoodDataSource: String, Codable {
    case PUBLIC_FOOD_API, ALL

    var displayName: String {
        switch self {
        case .PUBLIC_FOOD_API: return String(localized: "diet.dataSource.publicFoodApi")
        case .ALL:             return String(localized: "diet.dataSource.all")
        }
    }
}

/// 영양표시기준 10종(앱 전체 표준 — 백엔드와 일치).
/// 표시명은 locale 별로 재계산되므로 static computed 으로 노출.
enum NutrientLabel {
    static var calories: String      { String(localized: "diet.nutrient.calories") }
    static var carbs: String         { String(localized: "diet.nutrient.carbs") }
    static var sugars: String        { String(localized: "diet.nutrient.sugars") }
    static var dietaryFiber: String  { String(localized: "diet.nutrient.dietaryFiber") }
    static var protein: String       { String(localized: "diet.nutrient.protein") }
    static var fat: String           { String(localized: "diet.nutrient.fat") }
    static var saturatedFat: String  { String(localized: "diet.nutrient.saturatedFat") }
    static var transFat: String      { String(localized: "diet.nutrient.transFat") }
    static var cholesterol: String   { String(localized: "diet.nutrient.cholesterol") }
    static var sodium: String        { String(localized: "diet.nutrient.sodium") }
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
    let totalSugarsG: Double?
    let totalDietaryFiberG: Double?
    let totalSaturatedFatG: Double?
    let totalTransFatG: Double?
    let totalCholesterolMg: Double?
    let totalSodiumMg: Double?

    var id: Int { dietLogId }

    var formattedDate: String {
        let parts = logDate.split(separator: "-")
        guard parts.count == 3 else { return logDate }
        // locale-aware short date. AppleLanguages override 가 Locale.current 에 반영되므로
        // LocaleManager 직접 참조 없이 Locale.current 만으로 충분 (MainActor 격리 회피).
        let parser = DateFormatter()
        parser.dateFormat = "yyyy-MM-dd"
        parser.locale = Locale(identifier: "en_US_POSIX")
        if let date = parser.date(from: logDate) {
            let display = DateFormatter()
            display.locale = LocaleManager.resolvedLocale
            display.dateFormat = String(localized: "diet.date.format.shortKR")
            return display.string(from: date)
        }
        return "\(parts[1])/\(parts[2])"
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
    let totalSugarsG: Double?
    let totalDietaryFiberG: Double?
    let totalSaturatedFatG: Double?
    let totalTransFatG: Double?
    let totalCholesterolMg: Double?
    let totalSodiumMg: Double?
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
    let sugarsG: Double?
    let dietaryFiberG: Double?
    let saturatedFatG: Double?
    let transFatG: Double?
    let cholesterolMg: Double?
    let sodiumMg: Double?
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
    let sugarsPer100g: Double?
    let dietaryFiberPer100g: Double?
    let saturatedFatPer100g: Double?
    let transFatPer100g: Double?
    let cholesterolPer100gMg: Double?
    let sodiumPer100gMg: Double?
    let custom: Bool
    let usageCount: Int?
    let createdByUserId: Int?

    var displayName: String {
        let prefersKo = (Locale.preferredLanguages.first ?? "").hasPrefix("ko")
        if prefersKo, let ko = nameKo, !ko.isEmpty { return ko }
        return name
    }

    private func amount(_ per100g: Double?, forServing g: Double) -> Double {
        ((per100g ?? 0) * g) / 100
    }

    func calories(forServing g: Double)       -> Double { amount(caloriesPer100g, forServing: g) }
    func protein(forServing g: Double)        -> Double { amount(proteinPer100g, forServing: g) }
    func carbs(forServing g: Double)          -> Double { amount(carbsPer100g, forServing: g) }
    func fat(forServing g: Double)            -> Double { amount(fatPer100g, forServing: g) }
    func sugars(forServing g: Double)         -> Double { amount(sugarsPer100g, forServing: g) }
    func dietaryFiber(forServing g: Double)   -> Double { amount(dietaryFiberPer100g, forServing: g) }
    func saturatedFat(forServing g: Double)   -> Double { amount(saturatedFatPer100g, forServing: g) }
    func transFat(forServing g: Double)       -> Double { amount(transFatPer100g, forServing: g) }
    func cholesterol(forServing g: Double)    -> Double { amount(cholesterolPer100gMg, forServing: g) }
    func sodium(forServing g: Double)         -> Double { amount(sodiumPer100gMg, forServing: g) }
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
    let sugarsPer100g: Double?
    let dietaryFiberPer100g: Double?
    let saturatedFatPer100g: Double?
    let transFatPer100g: Double?
    let cholesterolPer100gMg: Double?
    let sodiumPer100gMg: Double?

    var id: String { "\(source.rawValue)-\(externalId)" }
    var displayName: String {
        let prefersKo = (Locale.preferredLanguages.first ?? "").hasPrefix("ko")
        if prefersKo, let ko = nameKo, !ko.isEmpty { return ko }
        return name
    }

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

struct UpdateDietLogRequest: Codable {
    let logDate: String
    let mealType: String
    let entries: [CreateFoodEntryRequest]
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
    let sugarsPer100g: Double?
    let dietaryFiberPer100g: Double?
    let saturatedFatPer100g: Double?
    let transFatPer100g: Double?
    let cholesterolPer100gMg: Double?
    let sodiumPer100gMg: Double?
}

struct CreateDietLogResponse: Codable {
    let dietLogId: Int
    let logDate: String
    let mealType: MealType
    let totalCalories: Double?
    let totalProteinG: Double?
    let totalCarbsG: Double?
    let totalFatG: Double?
    let totalSugarsG: Double?
    let totalDietaryFiberG: Double?
    let totalSaturatedFatG: Double?
    let totalTransFatG: Double?
    let totalCholesterolMg: Double?
    let totalSodiumMg: Double?
}

struct InitiateMealPhotoAnalysisRequest: Codable {
    let fileName: String
    let contentType: String
    let fileSizeBytes: Int
    let capturedAt: String
}

struct InitiateMealPhotoAnalysisResponse: Codable {
    let analysisId: Int
    let storageKey: String
    let uploadUrl: String
    let previewUrl: String?
    let expiresAt: String
}

struct AnalyzeMealPhotoRequest: Codable {
    let mealType: String
}

struct MealPhotoAnalysisResponse: Codable {
    let analysisId: Int
    let status: String
    let provider: String?
    let analysisVersion: String?
    let previewUrl: String?
    let capturedAt: String
    let needsReview: Bool
    let analysisWarnings: [String]
    let detectedItems: [MealPhotoAnalysisItem]
}

struct MealPhotoAnalysisItem: Codable, Identifiable {
    let analysisItemId: Int
    let label: String
    let matchedFoodCatalogId: Int?
    let estimatedServingG: Double
    let calories: Double?
    let proteinG: Double?
    let carbsG: Double?
    let fatG: Double?
    // 사진 분석 프롬프트 통일(Phase 3) 시 6필드 추가 예정 — 백엔드 사진 분석은 후속 PR.
    let confidence: Double?
    let needsReview: Bool
    let unknownOrUncertain: String?

    var id: Int { analysisItemId }
}

struct ConfirmMealPhotoAnalysisRequest: Codable {
    let logDate: String
    let mealType: String
    let notes: String?
    let items: [ConfirmMealPhotoAnalysisItem]
}

struct ConfirmMealPhotoAnalysisItem: Codable {
    let analysisItemId: Int?
    let label: String
    let matchedFoodCatalogId: Int?
    let estimatedServingG: Double
    let calories: Double
    let proteinG: Double?
    let carbsG: Double?
    let fatG: Double?
    let notes: String?
}

struct ConfirmMealPhotoAnalysisResponse: Codable {
    let analysisId: Int
    let status: String
    let dietLog: CreateDietLogResponse
}

// MARK: - Draft (로컬 상태)

struct DraftFoodEntry: Identifiable {
    let id = UUID()
    var food: FoodCatalogItem
    var servingGText: String = "100"
    var notes: String = ""
    var analysisItemId: Int?
    var sourceLabel: String?
    var aiConfidence: Double?
    var needsReview: Bool = false
    var unknownOrUncertain: String?

    var servingG: Double { Double(servingGText) ?? 100 }
    var calories:     Double { food.calories(forServing: servingG) }
    var protein:      Double { food.protein(forServing: servingG) }
    var carbs:        Double { food.carbs(forServing: servingG) }
    var fat:          Double { food.fat(forServing: servingG) }
    var sugars:       Double { food.sugars(forServing: servingG) }
    var dietaryFiber: Double { food.dietaryFiber(forServing: servingG) }
    var saturatedFat: Double { food.saturatedFat(forServing: servingG) }
    var transFat:     Double { food.transFat(forServing: servingG) }
    var cholesterol:  Double { food.cholesterol(forServing: servingG) }
    var sodium:       Double { food.sodium(forServing: servingG) }
    var matchedFoodCatalogId: Int? { food.id >= 0 ? food.id : nil }
    var displayName: String { sourceLabel ?? food.displayName }

    var isValid: Bool { servingG > 0 }
}

extension DraftFoodEntry {
    init(existingEntry: FoodEntryResponse) {
        let factor = existingEntry.servingG > 0 ? existingEntry.servingG / 100.0 : 1.0
        self.food = FoodCatalogItem(
            id: existingEntry.foodCatalogId,
            name: existingEntry.foodName,
            nameKo: existingEntry.foodNameKo,
            category: existingEntry.category,
            caloriesPer100g:      (existingEntry.calories ?? 0) / factor,
            proteinPer100g:       (existingEntry.proteinG ?? 0) / factor,
            carbsPer100g:         (existingEntry.carbsG ?? 0) / factor,
            fatPer100g:           (existingEntry.fatG ?? 0) / factor,
            sugarsPer100g:        (existingEntry.sugarsG ?? 0) / factor,
            dietaryFiberPer100g:  (existingEntry.dietaryFiberG ?? 0) / factor,
            saturatedFatPer100g:  (existingEntry.saturatedFatG ?? 0) / factor,
            transFatPer100g:      (existingEntry.transFatG ?? 0) / factor,
            cholesterolPer100gMg: (existingEntry.cholesterolMg ?? 0) / factor,
            sodiumPer100gMg:      (existingEntry.sodiumMg ?? 0) / factor,
            custom: false,
            usageCount: nil,
            createdByUserId: nil
        )
        self.servingGText = String(format: "%.0f", existingEntry.servingG)
        self.notes = existingEntry.notes ?? ""
    }

    init(food: FoodCatalogItem) {
        self.food = food
        self.servingGText = "100"
        self.notes = ""
    }

    init(analysisItem: MealPhotoAnalysisItem) {
        let serving = max(analysisItem.estimatedServingG, 1)
        let per100Calories = ((analysisItem.calories ?? 0) / serving) * 100
        let per100Protein  = ((analysisItem.proteinG ?? 0)  / serving) * 100
        let per100Carbs    = ((analysisItem.carbsG ?? 0)    / serving) * 100
        let per100Fat      = ((analysisItem.fatG ?? 0)      / serving) * 100
        let syntheticId = analysisItem.matchedFoodCatalogId ?? -analysisItem.analysisItemId

        self.food = FoodCatalogItem(
            id: syntheticId,
            name: analysisItem.label,
            nameKo: analysisItem.label,
            category: nil,
            caloriesPer100g:      per100Calories,
            proteinPer100g:       per100Protein,
            carbsPer100g:         per100Carbs,
            fatPer100g:           per100Fat,
            // 사진 분석 프롬프트가 아직 6필드를 안 줌 — Phase 3에서 보강.
            sugarsPer100g:        nil,
            dietaryFiberPer100g:  nil,
            saturatedFatPer100g:  nil,
            transFatPer100g:      nil,
            cholesterolPer100gMg: nil,
            sodiumPer100gMg:      nil,
            custom: analysisItem.matchedFoodCatalogId == nil,
            usageCount: nil,
            createdByUserId: nil
        )
        self.servingGText = String(format: "%.0f", analysisItem.estimatedServingG)
        self.notes = ""
        self.analysisItemId = analysisItem.analysisItemId
        self.sourceLabel = analysisItem.label
        self.aiConfidence = analysisItem.confidence
        self.needsReview = analysisItem.needsReview
        self.unknownOrUncertain = analysisItem.unknownOrUncertain
    }

    /// AI 텍스트 추정 결과(EstimatedItem 단일 항목)를 카탈로그 후보로 변환.
    init(aiEstimatedItem item: EstimatedItem) {
        let basisWeight = item.estimatedWeightG > 0 ? item.estimatedWeightG : 100.0
        // PER_ITEM·CUSTOM_WEIGHT는 1개 전체/지정 무게 기준 — per100g로 환산.
        // PER_100G는 이미 100g 기준이므로 환산 불필요.
        let factor: Double = {
            switch item.servingBasis {
            case .PER_100G:      return 1.0
            case .PER_ITEM,
                 .CUSTOM_WEIGHT: return 100.0 / basisWeight
            }
        }()
        let n = item.nutrition
        let displayName = item.normalizedName.isEmpty ? item.name : item.normalizedName

        self.food = FoodCatalogItem(
            id: -abs(displayName.hashValue),  // 음수 합성 ID — 저장 전까지 임시
            name: displayName,
            nameKo: displayName,
            category: item.category,
            caloriesPer100g:      n.caloriesKcal * factor,
            proteinPer100g:       n.proteinG * factor,
            carbsPer100g:         n.carbohydrateG * factor,
            fatPer100g:           n.fatG * factor,
            sugarsPer100g:        n.sugarsG * factor,
            dietaryFiberPer100g:  n.dietaryFiberG * factor,
            saturatedFatPer100g:  n.saturatedFatG * factor,
            transFatPer100g:      n.transFatG * factor,
            cholesterolPer100gMg: n.cholesterolMg * factor,
            sodiumPer100gMg:      n.sodiumMg * factor,
            custom: true,
            usageCount: nil,
            createdByUserId: nil
        )
        // 사용자가 무게를 명시했거나 단위 음식이면 그 무게를 기본값으로.
        self.servingGText = String(format: "%.0f", basisWeight)
        self.notes = ""
        self.sourceLabel = item.name
        self.aiConfidence = item.confidence
        self.unknownOrUncertain = item.estimationNote
    }
}

// MARK: - AI 영양 추정 응답 (백엔드 envelope과 일치)

/// 영양표시기준 10종 — items[].nutrition과 totalNutrition에서 공통 사용.
struct NutritionFacts: Codable, Hashable {
    let caloriesKcal: Double
    let carbohydrateG: Double
    let sugarsG: Double
    let dietaryFiberG: Double
    let proteinG: Double
    let fatG: Double
    let saturatedFatG: Double
    let transFatG: Double
    let cholesterolMg: Double
    let sodiumMg: Double

    static let zero = NutritionFacts(
        caloriesKcal: 0, carbohydrateG: 0, sugarsG: 0, dietaryFiberG: 0,
        proteinG: 0, fatG: 0, saturatedFatG: 0, transFatG: 0,
        cholesterolMg: 0, sodiumMg: 0
    )
}

enum ServingBasis: String, Codable {
    case PER_ITEM, PER_100G, CUSTOM_WEIGHT

    var displayName: String {
        switch self {
        case .PER_ITEM:       return String(localized: "diet.serving.perItem")
        case .PER_100G:       return String(localized: "diet.serving.per100g")
        case .CUSTOM_WEIGHT:  return String(localized: "diet.serving.customWeight")
        }
    }
}

struct EstimatedItem: Codable, Identifiable {
    let name: String
    let normalizedName: String
    let category: FoodCategory?
    let servingBasis: ServingBasis
    let servingDescription: String
    let estimatedWeightG: Double
    let nutrition: NutritionFacts
    let confidence: Double           // 0.0~1.0 (백엔드가 high/medium/low를 0.9/0.6/0.3으로 정규화)
    let estimationNote: String

    var id: String { name + "-" + normalizedName }
    var displayName: String { normalizedName.isEmpty ? name : normalizedName }

    var confidenceLabel: String {
        switch confidence {
        case 0.8...:    return String(localized: "diet.confidence.high")
        case 0.5..<0.8: return String(localized: "diet.confidence.medium")
        default:        return String(localized: "diet.confidence.low")
        }
    }
}

struct EstimationError: Codable {
    let code: String
    let message: String
}

struct AiNutritionEstimateResponse: Codable {
    let isFood: Bool
    let inputText: String
    let items: [EstimatedItem]
    let totalNutrition: NutritionFacts?
    let error: EstimationError?
    let disclaimer: String
    let aiEstimated: Bool

    var firstItem: EstimatedItem? { items.first }
    var isMultiItem: Bool { items.count > 1 }
}

struct AiNutritionEstimateRequest: Encodable {
    let foodName: String
}
