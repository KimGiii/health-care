import XCTest
@testable import HealthCare

@MainActor
final class AddDietLogViewModelTests: XCTestCase {

    func testScheduleSearch_빠른연속입력시마지막쿼리만실행된다() async throws {
        let searcher = MockDietFoodSearcher()
        let viewModel = AddDietLogViewModel(debounceDuration: .milliseconds(50))

        viewModel.searchQuery = "닭"
        viewModel.scheduleSearch(apiClient: searcher)

        viewModel.searchQuery = "닭가"
        viewModel.scheduleSearch(apiClient: searcher)

        viewModel.searchQuery = "닭가슴살"
        viewModel.scheduleSearch(apiClient: searcher)

        try await Task.sleep(for: .milliseconds(150))

        let executedQueries = await searcher.executedQueries
        XCTAssertEqual(executedQueries, ["닭가슴살"])
        XCTAssertEqual(viewModel.catalogResults.map(\.displayName), ["닭가슴살"])
        XCTAssertEqual(viewModel.externalResults.map(\.displayName), ["닭가슴살 외부"])
    }

    func testScheduleSearch_디바운스이전새입력이오면이전대기작업이취소된다() async throws {
        let searcher = MockDietFoodSearcher()
        let viewModel = AddDietLogViewModel(debounceDuration: .milliseconds(80))

        viewModel.searchQuery = "닭"
        viewModel.scheduleSearch(apiClient: searcher)

        try await Task.sleep(for: .milliseconds(30))

        viewModel.searchQuery = "닭가슴살"
        viewModel.scheduleSearch(apiClient: searcher)

        try await Task.sleep(for: .milliseconds(150))

        let executedQueries = await searcher.executedQueries
        XCTAssertEqual(executedQueries, ["닭가슴살"])
    }

    func testTriggerImmediateSearch_디바운스대기없이즉시실행된다() async throws {
        let searcher = MockDietFoodSearcher()
        let viewModel = AddDietLogViewModel(debounceDuration: .seconds(1))

        viewModel.searchQuery = "닭가슴살"
        viewModel.scheduleSearch(apiClient: searcher)
        viewModel.triggerImmediateSearch(apiClient: searcher)

        try await Task.sleep(for: .milliseconds(80))

        let executedQueries = await searcher.executedQueries
        XCTAssertEqual(executedQueries, ["닭가슴살"])
        XCTAssertFalse(viewModel.isSearching)
    }

    func testClearSearch_결과와AI상태를즉시초기화한다() async {
        let viewModel = AddDietLogViewModel()
        viewModel.searchQuery = "비빔밥"
        viewModel.catalogResults = [makeCatalogItem(name: "비빔밥")]
        viewModel.externalResults = [makeExternalFood(name: "비빔밥 외부")]
        viewModel.aiEstimateResult = makeAiEstimate(foodName: "비빔밥")
        viewModel.isSearching = true

        viewModel.clearSearch()

        XCTAssertEqual(viewModel.searchQuery, "")
        XCTAssertTrue(viewModel.catalogResults.isEmpty)
        XCTAssertTrue(viewModel.externalResults.isEmpty)
        XCTAssertNil(viewModel.aiEstimateResult)
        XCTAssertFalse(viewModel.isSearching)
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
        let viewModel = AddDietLogViewModel(debounceDuration: .milliseconds(10))

        viewModel.searchQuery = "닭"
        Task { await viewModel.searchAll(apiClient: searcher) }

        try await Task.sleep(for: .milliseconds(40))

        viewModel.searchQuery = "닭가슴살"
        viewModel.triggerImmediateSearch(apiClient: searcher)

        try await Task.sleep(for: .milliseconds(120))

        XCTAssertEqual(viewModel.catalogResults.map(\.displayName), ["닭가슴살"])
        XCTAssertEqual(viewModel.externalResults.map(\.displayName), ["닭가슴살 외부"])
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
            custom: false
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
            fatPer100g: 5
        )
    }

    private func makeAiEstimate(foodName: String) -> AiNutritionEstimateResponse {
        AiNutritionEstimateResponse(
            foodName: foodName,
            category: .OTHER,
            caloriesPer100g: 100,
            proteinPer100g: 10,
            carbsPer100g: 10,
            fatPer100g: 5,
            confidence: 0.8,
            disclaimer: "test",
            isAiEstimated: true
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
                custom: false
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
                fatPer100g: 5
            )
        ]
    }
}
