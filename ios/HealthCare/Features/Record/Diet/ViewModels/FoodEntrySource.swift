import Foundation

/// 식단 기록하기 — 섭취 음식 식별 모듈.
/// 검색, AI 추정, 직접 등록 세 경로 모두 이 모듈을 통해 DraftFoodEntry를 생산한다.
@MainActor
final class FoodEntrySource: ObservableObject {
    // MARK: - 공개 인터페이스

    /// TextField 양방향 바인딩용 쿼리 문자열.
    @Published var searchQuery: String = ""

    /// 검색·AI 추정·오류를 하나의 타입으로 표현. View는 이 프로퍼티 하나를 관찰한다.
    @Published var state: FoodEntryState = .idle

    /// 직접 등록 폼 시트 표시 여부 (네비게이션 상태).
    @Published var showCustomFoodForm = false

    /// 직접 등록 API 호출 진행 중 여부 (폼 버튼 스피너용).
    @Published var isSubmittingCustomFood = false

    /// 항목이 확정되면 호출. ViewModel이 draft에 추가하고 시트를 닫는다.
    var onEntryProduced: ((DraftFoodEntry) -> Void)?

    // MARK: - Private

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
        state = .idle
        cancelPendingSearches()
    }

    // MARK: - 검색

    func scheduleSearch(apiClient: any DietFoodSearching) {
        let query = normalizedQuery
        guard !query.isEmpty else {
            cancelPendingSearches()
            state = .idle
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
            state = .idle
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
        state = .idle
    }

    func searchAll(apiClient: any DietFoodSearching) async {
        let query = normalizedQuery
        guard !query.isEmpty else {
            state = .idle
            return
        }

        searchTask?.cancel()
        state = .searching(query: query)

        searchTask = Task { [weak self] in
            do {
                let catalog = await Self.fetchCatalog(apiClient: apiClient, query: query)
                try Task.checkCancellation()

                await MainActor.run {
                    guard let self, self.normalizedQuery == query else { return }
                    self.state = .results(query: query, items: catalog.uniqued(by: \.displayName))
                    self.searchTask = nil
                }
            } catch is CancellationError {
                await MainActor.run {
                    guard let self else { return }
                    if case .searching(let q) = self.state, q == query {
                        self.state = .idle
                    }
                    self.searchTask = nil
                }
            } catch {
                await MainActor.run {
                    guard let self, self.normalizedQuery == query else { return }
                    self.state = .failed(
                        query: query,
                        items: [],
                        message: String(localized: "diet.error.foodSearch2")
                    )
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

    // MARK: - AI 영양 추정

    func estimateWithAI(apiClient: APIClient) async {
        let query = searchQuery.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return }
        let currentItems = state.items
        state = .aiEstimating(query: query, items: currentItems)
        do {
            let body = try JSONEncoder().encode(AiNutritionEstimateRequest(foodName: query))
            let result: AiNutritionEstimateResponse = try await apiClient.request(.aiEstimateFood(body: body))
            if result.isFood {
                state = .aiEstimated(query: query, items: currentItems, estimate: result)
            } else {
                let message: String
                if let error = result.error {
                    switch error.code {
                    case "NOT_FOOD_OR_UNKNOWN": message = String(localized: "diet.error.ai.notFood")
                    case "AI_UNAVAILABLE":      message = String(localized: "diet.error.ai.unavailable")
                    default:                    message = error.message
                    }
                } else {
                    message = String(localized: "diet.error.ai.estimate")
                }
                state = .failed(query: query, items: currentItems, message: message)
            }
        } catch {
            state = .failed(query: query, items: currentItems, message: String(localized: "diet.error.ai.estimate"))
        }
    }

    func addAiEstimatedFood(apiClient: APIClient) async {
        guard case .aiEstimated(let query, let items, let estimate) = state,
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
            let body = try JSONEncoder().encode(CreateCustomFoodRequest(
                name: displayName,
                nameKo: displayName,
                category: (item.category ?? .OTHER).rawValue,
                caloriesPer100g:      n.caloriesKcal * factor,
                proteinPer100g:       n.proteinG * factor,
                carbsPer100g:         n.carbohydrateG * factor,
                fatPer100g:           n.fatG * factor,
                sugarsPer100g:        n.sugarsG * factor,
                dietaryFiberPer100g:  n.dietaryFiberG * factor,
                saturatedFatPer100g:  n.saturatedFatG * factor,
                transFatPer100g:      n.transFatG * factor,
                cholesterolPer100gMg: n.cholesterolMg * factor,
                sodiumPer100gMg:      n.sodiumMg * factor
            ))
            let catalogItem: FoodCatalogItem = try await apiClient.request(.createCustomFood(body: body))
            var draft = DraftFoodEntry(food: catalogItem)
            draft.servingGText = String(format: "%.0f", weight)
            state = .results(query: query, items: items)
            onEntryProduced?(draft)
        } catch {
            state = .failed(query: query, items: items, message: String(localized: "diet.error.ai.save"))
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
            let body = try JSONEncoder().encode(CreateCustomFoodRequest(
                name: name,
                nameKo: name,
                category: category.rawValue,
                caloriesPer100g: caloriesPer100g,
                proteinPer100g: proteinPer100g,
                carbsPer100g: carbsPer100g,
                fatPer100g: fatPer100g,
                sugarsPer100g: nil,
                dietaryFiberPer100g: nil,
                saturatedFatPer100g: nil,
                transFatPer100g: nil,
                cholesterolPer100gMg: nil,
                sodiumPer100gMg: nil
            ))
            let saved: FoodCatalogItem = try await apiClient.request(.createCustomFood(body: body))
            state = .results(query: state.query, items: [saved] + state.items)
            showCustomFoodForm = false
            onEntryProduced?(DraftFoodEntry(food: saved))
        } catch {
            // submitCustomFood 오류는 현재 View에 노출 경로 없음 — 추후 개선 필요
        }
    }

    // MARK: - 오류 해제

    func dismissError() {
        if case .failed(let query, let items, _) = state {
            state = items.isEmpty ? .idle : .results(query: query, items: items)
        }
    }

    // MARK: - Private helpers

    private var normalizedQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func cancelPendingSearches() {
        searchDebounceTask?.cancel(); searchDebounceTask = nil
        searchTask?.cancel(); searchTask = nil
    }

    private static func fetchCatalog(apiClient: any DietFoodSearching, query: String) async -> [FoodCatalogItem] {
        (try? await apiClient.searchFoodCatalog(query: query)) ?? []
    }
}
