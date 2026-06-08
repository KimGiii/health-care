import Foundation

/// 식단 기록하기 — 섭취 음식 식별 모듈.
/// 검색, AI 추정, 직접 등록 세 경로 모두 이 모듈을 통해 DraftFoodEntry를 생산한다.
@MainActor
final class FoodEntrySource: ObservableObject {
    @Published var searchQuery: String = ""
    @Published var catalogResults: [FoodCatalogItem] = []
    @Published var externalResults: [ExternalFoodResult] = []
    @Published var isSearching = false
    @Published var showCustomFoodForm = false
    @Published var isSubmittingCustomFood = false
    @Published var aiEstimateResult: AiNutritionEstimateResponse?
    @Published var isAiEstimating = false
    @Published var errorMessage: String?

    /// 항목이 확정되면 호출. ViewModel이 draft에 추가하고 시트를 닫는다.
    var onEntryProduced: ((DraftFoodEntry) -> Void)?

    private let debounceDuration: Duration
    private var searchDebounceTask: Task<Void, Never>?
    private var searchTask: Task<Void, Never>?

    init(debounceDuration: Duration = .milliseconds(500)) {
        self.debounceDuration = debounceDuration
    }

    deinit {
        searchDebounceTask?.cancel()
        searchTask?.cancel()
    }

    // MARK: - 초기화

    func reset() {
        searchQuery = ""
        catalogResults = []
        externalResults = []
        aiEstimateResult = nil
        errorMessage = nil
        cancelPendingSearches()
    }

    // MARK: - 검색

    func scheduleSearch(apiClient: any DietFoodSearching) {
        let query = normalizedQuery
        guard !query.isEmpty else {
            cancelPendingSearches()
            resetResults()
            return
        }
        searchDebounceTask?.cancel()
        searchDebounceTask = Task { [weak self] in
            do {
                try await Task.sleep(for: self?.debounceDuration ?? .zero)
                try Task.checkCancellation()
                guard let self else { return }
                await self.searchAll(apiClient: apiClient)
            } catch { }
        }
    }

    func triggerImmediateSearch(apiClient: any DietFoodSearching) {
        searchDebounceTask?.cancel()
        searchDebounceTask = nil
        guard !normalizedQuery.isEmpty else {
            cancelPendingSearches()
            resetResults()
            return
        }
        Task { [weak self] in
            guard let self else { return }
            await self.searchAll(apiClient: apiClient)
        }
    }

    func clearSearch() {
        searchQuery = ""
        cancelPendingSearches()
        resetResults()
    }

    func searchAll(apiClient: any DietFoodSearching) async {
        let query = normalizedQuery
        guard !query.isEmpty else { resetResults(); return }

        searchTask?.cancel()
        isSearching = true
        aiEstimateResult = nil

        searchTask = Task { [weak self] in
            do {
                async let catalogFetch = Self.fetchCatalog(apiClient: apiClient, query: query)
                async let externalFetch = Self.fetchExternal(apiClient: apiClient, query: query)
                let (catalog, external) = await (catalogFetch, externalFetch)
                try Task.checkCancellation()

                await MainActor.run {
                    guard let self, self.normalizedQuery == query else { return }
                    self.catalogResults = catalog.uniqued(by: \.displayName)
                    self.externalResults = external.uniqued(by: \.displayName)
                    self.errorMessage = nil
                    self.isSearching = false
                    self.searchTask = nil
                }
            } catch is CancellationError {
                await MainActor.run {
                    guard let self else { return }
                    if self.normalizedQuery == query { self.isSearching = false }
                    self.searchTask = nil
                }
            } catch {
                await MainActor.run {
                    guard let self, self.normalizedQuery == query else { return }
                    self.catalogResults = []
                    self.externalResults = []
                    self.errorMessage = String(localized: "diet.error.foodSearch2")
                    self.isSearching = false
                    self.searchTask = nil
                }
            }
        }
        await searchTask?.value
    }

    // MARK: - 항목 선택 (카탈로그 직접 선택)

    func select(food: FoodCatalogItem) {
        onEntryProduced?(DraftFoodEntry(food: food))
    }

    // MARK: - 외부 식품 임포트 후 추가

    func importAndAdd(external: ExternalFoodResult, apiClient: APIClient) async {
        do {
            let body = try JSONEncoder().encode(ImportFoodRequest(
                source: external.source.rawValue,
                externalId: external.externalId,
                name: external.name,
                nameKo: external.nameKo,
                brand: external.brand,
                category: external.category?.rawValue ?? "OTHER",
                caloriesPer100g: external.caloriesPer100g ?? 0,
                proteinPer100g: external.proteinPer100g,
                carbsPer100g: external.carbsPer100g,
                fatPer100g: external.fatPer100g,
                sugarsPer100g: external.sugarsPer100g,
                dietaryFiberPer100g: external.dietaryFiberPer100g,
                saturatedFatPer100g: external.saturatedFatPer100g,
                transFatPer100g: external.transFatPer100g,
                cholesterolPer100gMg: external.cholesterolPer100gMg,
                sodiumPer100gMg: external.sodiumPer100gMg
            ))
            let item: FoodCatalogItem = try await apiClient.request(.importExternalFood(body: body))
            onEntryProduced?(DraftFoodEntry(food: item))
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "diet.error.foodAdd")
        }
    }

    // MARK: - AI 영양 추정

    func estimateWithAI(apiClient: APIClient) async {
        let query = searchQuery.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return }
        isAiEstimating = true
        defer { isAiEstimating = false }
        do {
            let body = try JSONEncoder().encode(AiNutritionEstimateRequest(foodName: query))
            let result: AiNutritionEstimateResponse = try await apiClient.request(.aiEstimateFood(body: body))
            aiEstimateResult = result
            if !result.isFood, let error = result.error {
                switch error.code {
                case "NOT_FOOD_OR_UNKNOWN": errorMessage = String(localized: "diet.error.ai.notFood")
                case "AI_UNAVAILABLE":      errorMessage = String(localized: "diet.error.ai.unavailable")
                default:                    errorMessage = error.message
                }
            }
        } catch {
            errorMessage = String(localized: "diet.error.ai.estimate")
        }
    }

    func addAiEstimatedFood(apiClient: APIClient) async {
        guard let estimate = aiEstimateResult,
              estimate.isFood,
              let item = estimate.firstItem else { return }

        let weight = item.estimatedWeightG > 0 ? item.estimatedWeightG : 100.0
        let factor: Double = {
            switch item.servingBasis {
            case .PER_100G:      return 1.0
            case .PER_ITEM,
                 .CUSTOM_WEIGHT: return 100.0 / weight
            }
        }()
        let n = item.nutrition
        let displayName = item.normalizedName.isEmpty ? item.name : item.normalizedName

        do {
            let payload: [String: Any] = [
                "name": displayName, "nameKo": displayName,
                "category": (item.category ?? .OTHER).rawValue,
                "caloriesPer100g":      n.caloriesKcal * factor,
                "proteinPer100g":       n.proteinG * factor,
                "carbsPer100g":         n.carbohydrateG * factor,
                "fatPer100g":           n.fatG * factor,
                "sugarsPer100g":        n.sugarsG * factor,
                "dietaryFiberPer100g":  n.dietaryFiberG * factor,
                "saturatedFatPer100g":  n.saturatedFatG * factor,
                "transFatPer100g":      n.transFatG * factor,
                "cholesterolPer100gMg": n.cholesterolMg * factor,
                "sodiumPer100gMg":      n.sodiumMg * factor
            ]
            let body = try JSONSerialization.data(withJSONObject: payload)
            let catalogItem: FoodCatalogItem = try await apiClient.request(.createCustomFood(body: body))
            var draft = DraftFoodEntry(food: catalogItem)
            draft.servingGText = String(format: "%.0f", weight)
            aiEstimateResult = nil
            onEntryProduced?(draft)
        } catch {
            errorMessage = String(localized: "diet.error.ai.save")
        }
    }

    // MARK: - 직접 등록

    func submitCustomFood(
        name: String,
        category: FoodCategory,
        caloriesPer100g: Double,
        proteinPer100g: Double?,
        carbsPer100g: Double?,
        fatPer100g: Double?,
        apiClient: APIClient
    ) async {
        isSubmittingCustomFood = true
        defer { isSubmittingCustomFood = false }
        do {
            struct CustomFoodBody: Encodable {
                let name, nameKo, category: String
                let caloriesPer100g: Double
                let proteinPer100g, carbsPer100g, fatPer100g: Double?
            }
            let body = try JSONEncoder().encode(CustomFoodBody(
                name: name, nameKo: name, category: category.rawValue,
                caloriesPer100g: caloriesPer100g,
                proteinPer100g: proteinPer100g,
                carbsPer100g: carbsPer100g,
                fatPer100g: fatPer100g
            ))
            let saved: FoodCatalogItem = try await apiClient.request(.createCustomFood(body: body))
            catalogResults.insert(saved, at: 0)
            showCustomFoodForm = false
            onEntryProduced?(DraftFoodEntry(food: saved))
        } catch {
            errorMessage = String(localized: "diet.error.foodRegister")
        }
    }

    // MARK: - Private helpers

    private var normalizedQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func cancelPendingSearches() {
        searchDebounceTask?.cancel(); searchDebounceTask = nil
        searchTask?.cancel(); searchTask = nil
        isSearching = false
    }

    private func resetResults() {
        catalogResults = []
        externalResults = []
        aiEstimateResult = nil
        isSearching = false
    }

    private static func fetchCatalog(apiClient: any DietFoodSearching, query: String) async -> [FoodCatalogItem] {
        (try? await apiClient.searchFoodCatalog(query: query)) ?? []
    }

    private static func fetchExternal(apiClient: any DietFoodSearching, query: String) async -> [ExternalFoodResult] {
        (try? await apiClient.searchExternalFoods(query: query)) ?? []
    }
}
