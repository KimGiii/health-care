import XCTest
@testable import HealthCare

@MainActor
final class AddDietLogViewModelTests: XCTestCase {

    func testScheduleSearch_빠른연속입력시마지막쿼리만실행된다() async throws {
        let searcher = MockDietFoodSearcher()
        let source = FoodEntrySource(debounceDuration: .milliseconds(50))

        source.searchQuery = "닭"
        source.scheduleSearch(apiClient: searcher)

        source.searchQuery = "닭가"
        source.scheduleSearch(apiClient: searcher)

        source.searchQuery = "닭가슴살"
        source.scheduleSearch(apiClient: searcher)

        try await Task.sleep(for: .milliseconds(150))

        let executedQueries = await searcher.executedQueries
        XCTAssertEqual(executedQueries, ["닭가슴살"])
        XCTAssertEqual(source.state.items.map(\.displayName), ["닭가슴살"])
    }

    func testScheduleSearch_디바운스이전새입력이오면이전대기작업이취소된다() async throws {
        let searcher = MockDietFoodSearcher()
        let source = FoodEntrySource(debounceDuration: .milliseconds(80))

        source.searchQuery = "닭"
        source.scheduleSearch(apiClient: searcher)

        try await Task.sleep(for: .milliseconds(30))

        source.searchQuery = "닭가슴살"
        source.scheduleSearch(apiClient: searcher)

        try await Task.sleep(for: .milliseconds(150))

        let executedQueries = await searcher.executedQueries
        XCTAssertEqual(executedQueries, ["닭가슴살"])
    }

    func testTriggerImmediateSearch_디바운스대기없이즉시실행된다() async throws {
        let searcher = MockDietFoodSearcher()
        let source = FoodEntrySource(debounceDuration: .seconds(1))

        source.searchQuery = "닭가슴살"
        source.scheduleSearch(apiClient: searcher)
        source.triggerImmediateSearch(apiClient: searcher)

        try await Task.sleep(for: .milliseconds(80))

        let executedQueries = await searcher.executedQueries
        XCTAssertEqual(executedQueries, ["닭가슴살"])
        XCTAssertFalse(source.state.isSearching)
    }

    func testClearSearch_결과와AI상태를즉시초기화한다() async {
        let source = FoodEntrySource()
        source.searchQuery = "비빔밥"
        source.state = .aiEstimated(
            query: "비빔밥",
            items: [makeCatalogItem(name: "비빔밥")],
            estimate: makeAiEstimate(foodName: "비빔밥")
        )

        source.clearSearch()

        XCTAssertEqual(source.searchQuery, "")
        XCTAssertTrue(source.state.items.isEmpty)
        XCTAssertNil(source.state.aiEstimate)
        XCTAssertFalse(source.state.isSearching)
        guard case .idle = source.state else {
            return XCTFail("Expected .idle after clearSearch, got \(source.state)")
        }
    }

    func testSearchAll_느린이전응답이최신결과를덮어쓰지못한다() async throws {
        let searcher = MockDietFoodSearcher(
            catalogDelay: [
                "닭": .milliseconds(200),
                "닭가슴살": .milliseconds(20)
            ]
        )
        let source = FoodEntrySource(debounceDuration: .milliseconds(10))

        source.searchQuery = "닭"
        Task { await source.searchAll(apiClient: searcher) }

        try await Task.sleep(for: .milliseconds(40))

        source.searchQuery = "닭가슴살"
        source.triggerImmediateSearch(apiClient: searcher)

        try await Task.sleep(for: .milliseconds(120))

        XCTAssertEqual(source.state.items.map(\.displayName), ["닭가슴살"])
    }

    func testEntryBinding_삭제된식품항목을다시읽어도안전한마지막값을반환한다() {
        let viewModel = AddDietLogViewModel()
        let first = DraftFoodEntry(food: makeCatalogItem(name: "닭가슴살"))
        let second = DraftFoodEntry(food: makeCatalogItem(name: "현미밥"))
        viewModel.draft = .manual([first, second])

        let binding = viewModel.entryBinding(for: second)
        viewModel.removeEntry(id: second.id)

        XCTAssertEqual(viewModel.draft.entries.map(\.id), [first.id])
        XCTAssertEqual(binding.wrappedValue.id, second.id)
        XCTAssertEqual(binding.wrappedValue.displayName, "현미밥")
    }

    func testEntryBinding_삭제된식품항목에쓰기를시도해도남은항목을변경하지않는다() {
        let viewModel = AddDietLogViewModel()
        let first = DraftFoodEntry(food: makeCatalogItem(name: "닭가슴살"))
        let second = DraftFoodEntry(food: makeCatalogItem(name: "현미밥"))
        viewModel.draft = .manual([first, second])

        let binding = viewModel.entryBinding(for: second)
        viewModel.removeEntry(id: second.id)
        var staleValue = binding.wrappedValue
        staleValue.servingGText = "250"
        binding.wrappedValue = staleValue

        XCTAssertEqual(viewModel.draft.entries.count, 1)
        XCTAssertEqual(viewModel.draft.entries.first?.id, first.id)
        XCTAssertEqual(viewModel.draft.entries.first?.servingGText, "100")
    }

    func testDraftFoodEntry_브랜드공식메뉴는제공량을기본기록량으로사용한다() {
        var food = makeCatalogItem(name: "와퍼")
        food.source = "BRAND_OFFICIAL"
        food.servingSizeG = 290
        food.servingReference = "290g"

        let draft = DraftFoodEntry(food: food)

        XCTAssertEqual(draft.servingGText, "290")
        XCTAssertEqual(draft.calories, 290)
        XCTAssertEqual(food.catalogNutritionSummary, "290 kcal / 290g")
    }

    func testRecommendationMeal_식단기록요청으로변환된다() {
        let meal = RecommendedMeal(
            mealType: .LUNCH,
            targetCalories: 700,
            totalCalories: 650,
            totalProteinG: 42,
            totalCarbsG: 72,
            totalFatG: 18,
            items: [
                RecommendedFoodEntry(
                    foodCatalogId: 101,
                    name: "brown rice",
                    nameKo: "현미밥",
                    category: .GRAIN,
                    servingG: 180,
                    calories: 270,
                    proteinG: 5,
                    carbsG: 58,
                    fatG: 2,
                    allergenConfidenceLevel: .DIRECT_VERIFIED,
                    caution: nil
                ),
                RecommendedFoodEntry(
                    foodCatalogId: 202,
                    name: "grilled chicken breast",
                    nameKo: "닭가슴살구이",
                    category: .PROTEIN_SOURCE,
                    servingG: 150,
                    calories: 240,
                    proteinG: 37,
                    carbsG: 0,
                    fatG: 8,
                    allergenConfidenceLevel: .UNKNOWN,
                    caution: "알러젠 정보 확인 필요"
                )
            ]
        )

        let request = DietRecommendationViewModel.makeCreateDietLogRequest(
            for: meal,
            date: "2026-06-16",
            note: "추천 식단"
        )

        XCTAssertEqual(request.logDate, "2026-06-16")
        XCTAssertEqual(request.mealType, MealType.LUNCH.rawValue)
        XCTAssertEqual(request.notes, "추천 식단")
        XCTAssertEqual(request.entries.count, 2)
        XCTAssertEqual(request.entries[0].foodCatalogId, 101)
        XCTAssertEqual(request.entries[0].servingG, 180)
        XCTAssertNil(request.entries[0].notes)
        XCTAssertEqual(request.entries[1].foodCatalogId, 202)
        XCTAssertEqual(request.entries[1].servingG, 150)
        XCTAssertEqual(request.entries[1].notes, "알러젠 정보 확인 필요")
    }

    private func makeCatalogItem(name: String) -> FoodCatalogItem {
        FoodCatalogItem(
            id: abs(name.hashValue),
            name: name,
            nameKo: name,
            category: .OTHER,
            caloriesPer100g: 100,
            proteinPer100g: 10,
            carbsPer100g: 10,
            fatPer100g: 5,
            sugarsPer100g: nil,
            dietaryFiberPer100g: nil,
            saturatedFatPer100g: nil,
            transFatPer100g: nil,
            cholesterolPer100gMg: nil,
            sodiumPer100gMg: nil,
            custom: false,
            usageCount: 0,
            createdByUserId: nil
        )
    }

    private func makeAiEstimate(foodName: String) -> AiNutritionEstimateResponse {
        let item = EstimatedItem(
            name: foodName,
            normalizedName: foodName,
            category: .OTHER,
            servingBasis: .PER_100G,
            servingDescription: "100g 기준",
            estimatedWeightG: 100,
            nutrition: NutritionFacts(
                caloriesKcal: 100, carbohydrateG: 10, sugarsG: 0, dietaryFiberG: 0,
                proteinG: 10, fatG: 5, saturatedFatG: 0, transFatG: 0,
                cholesterolMg: 0, sodiumMg: 0
            ),
            confidence: 0.8,
            estimationNote: ""
        )
        return AiNutritionEstimateResponse(
            isFood: true,
            inputText: foodName,
            items: [item],
            totalNutrition: item.nutrition,
            error: nil,
            disclaimer: "test",
            aiEstimated: true
        )
    }
}

private actor MockDietFoodSearcher: DietFoodSearching {
    private(set) var executedQueries: [String] = []

    private let catalogDelay: [String: Duration]

    init(catalogDelay: [String: Duration] = [:]) {
        self.catalogDelay = catalogDelay
    }

    func searchFoodCatalog(query: String) async throws -> [FoodCatalogItem] {
        executedQueries.append(query)
        if let delay = catalogDelay[query] {
            try await Task.sleep(for: delay)
        }
        try Task.checkCancellation()
        return [
            FoodCatalogItem(
                id: abs(query.hashValue),
                name: query,
                nameKo: query,
                category: .OTHER,
                caloriesPer100g: 100,
                proteinPer100g: 10,
                carbsPer100g: 10,
                fatPer100g: 5,
                sugarsPer100g: nil,
                dietaryFiberPer100g: nil,
                saturatedFatPer100g: nil,
                transFatPer100g: nil,
                cholesterolPer100gMg: nil,
                sodiumPer100gMg: nil,
                custom: false,
                usageCount: 0,
                createdByUserId: nil
            )
        ]
    }
}
