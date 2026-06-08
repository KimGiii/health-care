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
        XCTAssertEqual(source.catalogResults.map(\.displayName), ["닭가슴살"])
        XCTAssertEqual(source.externalResults.map(\.displayName), ["닭가슴살 외부"])
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
        XCTAssertFalse(source.isSearching)
    }

    func testClearSearch_결과와AI상태를즉시초기화한다() async {
        let source = FoodEntrySource()
        source.searchQuery = "비빔밥"
        source.catalogResults = [makeCatalogItem(name: "비빔밥")]
        source.externalResults = [makeExternalFood(name: "비빔밥 외부")]
        source.aiEstimateResult = makeAiEstimate(foodName: "비빔밥")
        source.isSearching = true

        source.clearSearch()

        XCTAssertEqual(source.searchQuery, "")
        XCTAssertTrue(source.catalogResults.isEmpty)
        XCTAssertTrue(source.externalResults.isEmpty)
        XCTAssertNil(source.aiEstimateResult)
        XCTAssertFalse(source.isSearching)
    }

    func testSearchAll_느린이전응답이최신결과를덮어쓰지못한다() async throws {
        let searcher = MockDietFoodSearcher(
            catalogDelay: [
                "닭": .milliseconds(200),
                "닭가슴살": .milliseconds(20)
            ],
            externalDelay: [
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

        XCTAssertEqual(source.catalogResults.map(\.displayName), ["닭가슴살"])
        XCTAssertEqual(source.externalResults.map(\.displayName), ["닭가슴살 외부"])
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

    private func makeExternalFood(name: String) -> ExternalFoodResult {
        ExternalFoodResult(
            source: .PUBLIC_FOOD_API,
            externalId: name,
            name: name,
            nameKo: name,
            brand: nil,
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
            sodiumPer100gMg: nil
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
    private let externalDelay: [String: Duration]

    init(
        catalogDelay: [String: Duration] = [:],
        externalDelay: [String: Duration] = [:]
    ) {
        self.catalogDelay = catalogDelay
        self.externalDelay = externalDelay
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

    func searchExternalFoods(query: String) async throws -> [ExternalFoodResult] {
        if let delay = externalDelay[query] {
            try await Task.sleep(for: delay)
        }
        try Task.checkCancellation()
        return [
            ExternalFoodResult(
                source: .PUBLIC_FOOD_API,
                externalId: query,
                name: "\(query) 외부",
                nameKo: "\(query) 외부",
                brand: nil,
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
                sodiumPer100gMg: nil
            )
        ]
    }
}
