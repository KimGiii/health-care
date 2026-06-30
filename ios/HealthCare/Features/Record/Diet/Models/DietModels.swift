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

/// 백엔드가 파생한 제공량 옵션 스냅샷(`food_serving_options`). 신뢰할 수 없는
/// `servingSizeG`(포장중량/플레이스홀더) 대신 1회 제공량의 권위 있는 출처다.
struct ServingOption: Codable, Hashable {
    let label: String?
    let labelKo: String?
    let equivalentG: Double
    let sortOrder: Int
    let servingType: String?
    let verified: Bool?
}

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
    var foodCode: String? = nil
    var source: String? = nil
    var sourceDetail: String? = nil
    var brandName: String? = nil
    var maker: String? = nil
    var servingSizeG: Double? = nil
    var servingReference: String? = nil
    var servingOptions: [ServingOption]? = nil

    var displayName: String {
        let prefersKo = (Locale.preferredLanguages.first ?? "").hasPrefix("ko")
        if prefersKo, let ko = nameKo, !ko.isEmpty { return ko }
        return name
    }

    var isBrandOfficialMenu: Bool {
        source == "BRAND_OFFICIAL"
    }

    /// 대표 1회 제공량 옵션(sort_order가 가장 작은 OFFICIAL_SERVING).
    /// 백엔드가 `serving_reference`에서 파생한 깨끗한 값으로, 제공량 판단의 1순위.
    var primaryServingOption: ServingOption? {
        (servingOptions ?? [])
            .filter { $0.equivalentG > 0 }
            .min { $0.sortOrder < $1.sortOrder }
    }

    /// 신뢰할 수 있는 1회 제공량이 존재하는지 여부.
    var hasServingSize: Bool {
        if primaryServingOption != nil { return true }
        if isBrandOfficialMenu, let servingSizeG, servingSizeG > 0 { return true }
        return false
    }

    /// 검색 결과에 표시할 브랜드/제조사명. 둘 다 없으면 nil.
    var displayBrand: String? {
        if let brandName, !brandName.isEmpty { return brandName }
        if let maker, !maker.isEmpty { return maker }
        return nil
    }

    /// 표시·기록의 기본 제공량(g).
    /// 1) 파생 제공량 옵션 → 2) 브랜드 공식 메뉴의 큐레이션된 제공량 → 3) 100g.
    /// `servingSizeG`는 MFDS에서 포장중량/플레이스홀더(100)인 경우가 많아 직접 쓰지 않는다.
    var defaultServingG: Double {
        if let option = primaryServingOption {
            return option.equivalentG
        }
        if isBrandOfficialMenu, let servingSizeG, servingSizeG > 0 {
            return servingSizeG
        }
        return 100
    }

    var displayServingReference: String {
        servingLabel(forGrams: defaultServingG)
    }

    var catalogNutritionSummary: String {
        guard let caloriesPer100g else { return "-" }
        if hasServingSize {
            let serving = defaultServingG
            let calories = calories(forServing: serving)
            return String(format: "%.0f kcal / %@", calories, servingLabel(forGrams: serving))
        }
        return String(format: "%.0f kcal / 100g", caloriesPer100g)
    }

    /// 제공량 라벨. `servingReference` 텍스트(예: "1봉지(120g)")가 실제 제공량 그람수와
    /// 일치할 때만 사용하고, 어긋나면(식품중량 ≠ 영양성분 기준량) 숫자 그람으로 표시해
    /// 표시 칼로리와 그람수가 모순되지 않게 한다.
    private func servingLabel(forGrams grams: Double) -> String {
        if let servingReference, !servingReference.isEmpty,
           let parsed = Self.parseGrams(servingReference),
           abs(parsed - grams) < 0.5 {
            return servingReference
        }
        return formatGrams(grams)
    }

    /// "210g", "1봉지(120g)" 등에서 g 앞 숫자를 그람수로 추출한다.
    private static func parseGrams(_ text: String) -> Double? {
        guard let range = text.range(
            of: #"[0-9]+(\.[0-9]+)?\s*[gG]"#, options: .regularExpression
        ) else { return nil }
        let digits = text[range].filter { $0.isNumber || $0 == "." }
        return Double(digits)
    }

    private func amount(_ per100g: Double?, forServing g: Double) -> Double {
        ((per100g ?? 0) * g) / 100
    }

    private func formatGrams(_ grams: Double) -> String {
        grams.truncatingRemainder(dividingBy: 1) == 0
            ? String(format: "%.0fg", grams)
            : String(format: "%.1fg", grams)
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
        self.servingGText = Self.formatServingG(food.defaultServingG)
        self.notes = ""
    }

    private static func formatServingG(_ grams: Double) -> String {
        grams.truncatingRemainder(dividingBy: 1) == 0
            ? String(format: "%.0f", grams)
            : String(format: "%.1f", grams)
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

struct CreateCustomFoodRequest: Encodable {
    let name: String
    let nameKo: String
    let category: String
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
