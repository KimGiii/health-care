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

    func testCatalogNutritionSummary_파생제공량옵션이있으면옵션기준으로표시한다() {
        // STANDARD_PROCESSED: servingSizeG(257)는 포장중량이라 무시, 옵션(200g)이 1회 제공량.
        var food = makeCatalogItem(name: "카레 덮밥")  // 100 kcal/100g
        food.servingSizeG = 257
        food.servingReference = "210g"
        food.servingOptions = [
            ServingOption(label: "1 serving", labelKo: "1회 제공량",
                          equivalentG: 200, sortOrder: 0,
                          servingType: "OFFICIAL_SERVING", verified: true),
            ServingOption(label: "0.5 serving", labelKo: "0.5회 제공량",
                          equivalentG: 100, sortOrder: 1,
                          servingType: "OFFICIAL_SERVING", verified: true)
        ]

        // 포장중량(257)도 영양기준량(210)도 아닌 대표 옵션(200g) 기준.
        XCTAssertEqual(food.catalogNutritionSummary, "200 kcal / 200g")
        XCTAssertEqual(DraftFoodEntry(food: food).servingGText, "200")
    }

    func testCatalogNutritionSummary_옵션없는일반식품은신뢰못할servingSizeG를무시하고100g로표시한다() {
        // MFDS_FOOD_NUTRIENT_DB: servingSizeG=100(플레이스홀더)·포장중량은 1회 제공량이 아님.
        var food = makeCatalogItem(name: "카레 덮밥")  // 100 kcal/100g
        food.servingSizeG = 370
        food.servingReference = "210g"
        food.servingOptions = nil

        XCTAssertEqual(food.catalogNutritionSummary, "100 kcal / 100g")
        XCTAssertEqual(DraftFoodEntry(food: food).servingGText, "100")
    }

    func testCatalogNutritionSummary_제공량이없으면100g기준으로표시한다() {
        let food = makeCatalogItem(name: "쌀밥")  // 100 kcal/100g, servingSizeG nil

        XCTAssertEqual(food.catalogNutritionSummary, "100 kcal / 100g")
    }

    func testDisplayBrand_브랜드명이없으면제조사명으로폴백한다() {
        var withBrand = makeCatalogItem(name: "와퍼")
        withBrand.brandName = "버거킹"
        withBrand.maker = "비케이알"
        XCTAssertEqual(withBrand.displayBrand, "버거킹")

        var makerOnly = makeCatalogItem(name: "초코파이")
        makerOnly.maker = "오리온"
        XCTAssertEqual(makerOnly.displayBrand, "오리온")

        XCTAssertNil(makeCatalogItem(name: "사과").displayBrand)
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
        XCTAssertNil(request.recommendationSnapshotId)
    }

    func testRecommendationMeal_스냅샷ID가기록요청에전달된다() {
        let meal = RecommendedMeal(
            mealType: .DINNER,
            targetCalories: 600,
            totalCalories: 550,
            totalProteinG: 35,
            totalCarbsG: 60,
            totalFatG: 15,
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
                )
            ]
        )

        let request = DietRecommendationViewModel.makeCreateDietLogRequest(
            for: meal,
            date: "2026-07-14",
            snapshotId: 77
        )

        XCTAssertEqual(request.recommendationSnapshotId, 77)
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

@MainActor
final class DietRestrictionViewModelTests: XCTestCase {
    override func tearDown() {
        DietFeatureURLProtocolStub.requestHandler = nil
        TokenStore().clearTokens()
        super.tearDown()
    }

    func testLoad_제한조건목록을대상타입별로분류한다() async throws {
        let viewModel = DietRestrictionViewModel()
        let apiClient = Self.stubbedAPIClient { request in
            XCTAssertEqual(request.httpMethod, "GET")
            XCTAssertEqual(request.url?.path, "/api/v1/diet/restrictions")
            return Self.jsonResponse(path: request.url?.path, body: """
            {
              "success": true,
              "data": [
                {
                  "id": 1,
                  "restrictionType": "ALLERGY",
                  "targetType": "ALLERGEN_TAG",
                  "foodCatalogId": null,
                  "category": null,
                  "keyword": null,
                  "allergenTag": "MILK",
                  "createdAt": "2026-06-17T09:00:00+09:00"
                },
                {
                  "id": 2,
                  "restrictionType": "AVOID",
                  "targetType": "CATEGORY",
                  "foodCatalogId": null,
                  "category": "PROCESSED",
                  "keyword": null,
                  "allergenTag": null,
                  "createdAt": "2026-06-17T09:01:00+09:00"
                },
                {
                  "id": 3,
                  "restrictionType": "AVOID",
                  "targetType": "KEYWORD",
                  "foodCatalogId": null,
                  "category": null,
                  "keyword": "튀김",
                  "allergenTag": null,
                  "createdAt": "2026-06-17T09:02:00+09:00"
                }
              ],
              "message": null
            }
            """)
        }

        await viewModel.load(apiClient: apiClient)

        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNil(viewModel.errorMessage)
        XCTAssertEqual(viewModel.restrictions.count, 3)
        XCTAssertEqual(viewModel.allergenRestrictions().map(\.allergenTag), [.MILK])
        XCTAssertEqual(viewModel.categoryRestrictions().map(\.category), [.PROCESSED])
        XCTAssertEqual(viewModel.keywordRestrictions().map(\.keyword), ["튀김"])
    }

    func testAddAndDelete_제한조건을추가하고삭제한다() async throws {
        let viewModel = DietRestrictionViewModel()
        let requestCounter = LockedDietFeatureCounter()
        let apiClient = Self.stubbedAPIClient { request in
            switch (request.httpMethod, request.url?.path) {
            case ("POST", "/api/v1/diet/restrictions"):
                XCTAssertEqual(requestCounter.increment(), 1)
                let body = try XCTUnwrap(Self.bodyJSON(from: request))
                XCTAssertEqual(body["restrictionType"] as? String, "ALLERGY")
                XCTAssertEqual(body["targetType"] as? String, "ALLERGEN_TAG")
                XCTAssertEqual(body["allergenTag"] as? String, "EGG")
                XCTAssertNil(body["keyword"] as? String)

                return Self.jsonResponse(path: request.url?.path, body: """
                {
                  "success": true,
                  "data": {
                    "id": 10,
                    "restrictionType": "ALLERGY",
                    "targetType": "ALLERGEN_TAG",
                    "foodCatalogId": null,
                    "category": null,
                    "keyword": null,
                    "allergenTag": "EGG",
                    "createdAt": "2026-06-17T09:03:00+09:00"
                  },
                  "message": "식단 제한 조건이 등록되었습니다."
                }
                """)
            case ("DELETE", "/api/v1/diet/restrictions/10"):
                XCTAssertEqual(requestCounter.increment(), 2)
                return Self.jsonResponse(path: request.url?.path, body: """
                {
                  "success": true,
                  "message": "식단 제한 조건이 삭제되었습니다."
                }
                """)
            default:
                XCTFail("예상하지 못한 요청: \(request.httpMethod ?? "nil") \(request.url?.path ?? "nil")")
                return Self.jsonResponse(statusCode: 404, path: request.url?.path, body: "{}")
            }
        }

        await viewModel.addAllergenTag(.EGG, type: .ALLERGY, apiClient: apiClient)

        let created = try XCTUnwrap(viewModel.restrictions.first)
        XCTAssertEqual(created.id, 10)
        XCTAssertEqual(created.allergenTag, .EGG)
        XCTAssertTrue(viewModel.hasAllergenTag(.EGG))

        await viewModel.delete(restriction: created, apiClient: apiClient)

        XCTAssertTrue(viewModel.restrictions.isEmpty)
        XCTAssertNil(viewModel.errorMessage)
        XCTAssertEqual(requestCounter.value, 2)
    }
}

@MainActor
final class DietRecommendationViewModelTests: XCTestCase {
    override func tearDown() {
        DietFeatureURLProtocolStub.requestHandler = nil
        TokenStore().clearTokens()
        super.tearDown()
    }

    func testMealOrder_기본메인끼니순서를유지하고선택한위치에간식을삽입한다() {
        XCTAssertEqual(
            DietRecommendationViewModel.mealOrder(for: [.morning]),
            [.BREAKFAST, .SNACK, .LUNCH, .DINNER]
        )
        XCTAssertEqual(
            DietRecommendationViewModel.mealOrder(for: [.afternoon]),
            [.BREAKFAST, .LUNCH, .SNACK, .DINNER]
        )
        XCTAssertEqual(
            DietRecommendationViewModel.mealOrder(for: [.evening]),
            [.BREAKFAST, .LUNCH, .DINNER, .SNACK]
        )
        XCTAssertEqual(
            DietRecommendationViewModel.mealOrder(for: [.morning, .afternoon, .evening]),
            [.BREAKFAST, .SNACK, .LUNCH, .SNACK, .DINNER, .SNACK]
        )
    }

    func testSnackPlacement_여러시간대를선택하면간식슬롯을각각생성한다() {
        let viewModel = DietRecommendationViewModel()

        viewModel.toggleMeal(.SNACK)
        viewModel.toggleSnackPlacement(.morning)
        viewModel.toggleSnackPlacement(.evening)

        XCTAssertEqual(
            viewModel.selectedMealsInDisplayOrder,
            [.BREAKFAST, .SNACK, .LUNCH, .SNACK, .DINNER, .SNACK]
        )
    }

    func testRecommend_성공응답을상태에반영하고의료안전단정문구를포함하지않는다() async throws {
        let viewModel = DietRecommendationViewModel()
        viewModel.selectedDate = Self.fixedDate
        viewModel.selectedMeals = [.BREAKFAST, .LUNCH, .DINNER, .SNACK]
        viewModel.strictAllergyMode = false

        let disclaimer = "등록한 제외 조건을 기준으로 추천 식단에서 제외합니다. 실제 제품·매장 원재료는 다를 수 있어 섭취 전 확인하세요."
        let apiClient = Self.stubbedAPIClient { request in
            XCTAssertEqual(request.httpMethod, "POST")
            XCTAssertEqual(request.url?.path, "/api/v1/diet/recommendations/daily")

            let body = try XCTUnwrap(Self.bodyJSON(from: request))
            XCTAssertEqual(body["date"] as? String, "2026-06-17")
            XCTAssertEqual(
                body["mealTypes"] as? [String],
                ["BREAKFAST", "LUNCH", "SNACK", "DINNER"]
            )
            XCTAssertEqual(body["strictAllergyMode"] as? Bool, false)

            return Self.jsonResponse(path: request.url?.path, body: """
            {
              "success": true,
              "data": {
                "date": "2026-06-17",
                "targets": {
                  "calorieTarget": 2000,
                  "proteinTargetG": 150,
                  "carbTargetG": 230,
                  "fatTargetG": 67
                },
                "remainingTargets": {
                  "calorieTarget": 1350,
                  "proteinTargetG": 108,
                  "carbTargetG": 158,
                  "fatTargetG": 49
                },
                "appliedRestrictions": [
                  {
                    "id": 1,
                    "restrictionType": "ALLERGY",
                    "targetType": "ALLERGEN_TAG",
                    "foodCatalogId": null,
                    "category": null,
                    "keyword": null,
                    "allergenTag": "MILK",
                    "createdAt": "2026-06-17T09:00:00+09:00"
                  }
                ],
                "meals": [
                  {
                    "mealType": "LUNCH",
                    "targetCalories": 700,
                    "totalCalories": 650,
                    "totalProteinG": 42,
                    "totalCarbsG": 72,
                    "totalFatG": 18,
                    "items": [
                      {
                        "foodCatalogId": 101,
                        "name": "brown rice",
                        "nameKo": "현미밥",
                        "category": "GRAIN",
                        "servingG": 180,
                        "calories": 270,
                        "proteinG": 5,
                        "carbsG": 58,
                        "fatG": 2,
                        "allergenConfidenceLevel": "DIRECT_VERIFIED",
                        "caution": null
                      }
                    ]
                  }
                ],
                "totalNutrients": {
                  "totalCalories": 650,
                  "totalProteinG": 42,
                  "totalCarbsG": 72,
                  "totalFatG": 18
                },
                "failureReason": null,
                "strictAllergyMode": false,
                "disclaimer": "\(disclaimer)",
                "alternatives": []
              },
              "message": null
            }
            """)
        }

        await viewModel.recommend(apiClient: apiClient)

        let result = try XCTUnwrap(viewModel.result)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNil(viewModel.errorMessage)
        XCTAssertEqual(result.date, "2026-06-17")
        XCTAssertEqual(result.appliedRestrictions.map(\.allergenTag), [.MILK])
        XCTAssertEqual(result.meals.first?.mealType, .LUNCH)
        XCTAssertEqual(result.meals.first?.items.first?.name, "brown rice")
        XCTAssertEqual(result.meals.first?.items.first?.nameKo, "현미밥")
        XCTAssertEqual(result.disclaimer, disclaimer)
        Self.assertNoMedicalSafetyPromise(result.disclaimer)
    }

    func testRecommend_Strict토글을요청본문에반영한다() async throws {
        let viewModel = DietRecommendationViewModel()
        viewModel.selectedDate = Self.fixedDate
        viewModel.selectedMeals = [.BREAKFAST, .DINNER]
        viewModel.strictAllergyMode = true

        let apiClient = Self.stubbedAPIClient { request in
            let body = try XCTUnwrap(Self.bodyJSON(from: request))
            XCTAssertEqual(body["strictAllergyMode"] as? Bool, true)
            XCTAssertEqual(Set(body["mealTypes"] as? [String] ?? []), ["BREAKFAST", "DINNER"])

            return Self.emptyRecommendationResponse(
                path: request.url?.path,
                strictAllergyMode: true,
                disclaimer: "교차오염은 보장하지 못합니다. 포장 원재료를 직접 확인하세요."
            )
        }

        await viewModel.recommend(apiClient: apiClient)

        let result = try XCTUnwrap(viewModel.result)
        XCTAssertTrue(result.strictAllergyMode)
        Self.assertNoMedicalSafetyPromise(result.disclaimer)
    }

    func testRecommend_후보부족실패메시지를표시한다() async throws {
        let viewModel = DietRecommendationViewModel()
        viewModel.selectedDate = Self.fixedDate
        viewModel.selectedMeals = [.BREAKFAST, .LUNCH, .DINNER]

        let message = "Not enough recommendation candidates after applying restrictions."
        let apiClient = Self.stubbedAPIClient { request in
            XCTAssertEqual(request.httpMethod, "POST")
            XCTAssertEqual(request.url?.path, "/api/v1/diet/recommendations/daily")
            return Self.jsonResponse(statusCode: 422, path: request.url?.path, body: """
            {
              "success": false,
              "code": "BUSINESS_RULE_VIOLATION",
              "message": "\(message)"
            }
            """)
        }

        await viewModel.recommend(apiClient: apiClient)

        XCTAssertNil(viewModel.result)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertEqual(viewModel.errorMessage, message)
    }

    private static let fixedDate: Date = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar.date(from: DateComponents(year: 2026, month: 6, day: 17, hour: 12))!
    }()

    private static func emptyRecommendationResponse(
        path: String?,
        strictAllergyMode: Bool,
        disclaimer: String
    ) -> (HTTPURLResponse, Data) {
        jsonResponse(path: path, body: """
        {
          "success": true,
          "data": {
            "date": "2026-06-17",
            "targets": {
              "calorieTarget": 2000,
              "proteinTargetG": 150,
              "carbTargetG": 230,
              "fatTargetG": 67
            },
            "remainingTargets": {
              "calorieTarget": 2000,
              "proteinTargetG": 150,
              "carbTargetG": 230,
              "fatTargetG": 67
            },
            "appliedRestrictions": [],
            "meals": [],
            "totalNutrients": {
              "totalCalories": 0,
              "totalProteinG": 0,
              "totalCarbsG": 0,
              "totalFatG": 0
            },
            "failureReason": null,
            "strictAllergyMode": \(strictAllergyMode),
            "disclaimer": "\(disclaimer)",
            "alternatives": []
          },
          "message": null
        }
        """)
    }

    private static func assertNoMedicalSafetyPromise(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        let forbiddenPhrases = [
            "알러지 위험이 없는",
            "알레르기 위험이 없는",
            "안전한 식단",
            "완전히 안전",
            "진단",
            "치료",
            "처방"
        ]

        for phrase in forbiddenPhrases {
            XCTAssertFalse(text.contains(phrase), "'\(phrase)' 문구를 포함하면 의료 안전 단정으로 보일 수 있습니다.", file: file, line: line)
        }
    }
}

private extension DietRestrictionViewModelTests {
    static func stubbedAPIClient(
        handler: @escaping (URLRequest) throws -> (HTTPURLResponse, Data)
    ) -> APIClient {
        DietFeatureTestNetwork.makeAPIClient(handler: handler)
    }

    static func jsonResponse(statusCode: Int = 200, path: String?, body: String) -> (HTTPURLResponse, Data) {
        DietFeatureTestNetwork.jsonResponse(statusCode: statusCode, path: path, body: body)
    }

    static func bodyJSON(from request: URLRequest) -> [String: Any]? {
        DietFeatureTestNetwork.bodyJSON(from: request)
    }
}

private extension DietRecommendationViewModelTests {
    static func stubbedAPIClient(
        handler: @escaping (URLRequest) throws -> (HTTPURLResponse, Data)
    ) -> APIClient {
        DietFeatureTestNetwork.makeAPIClient(handler: handler)
    }

    static func jsonResponse(statusCode: Int = 200, path: String?, body: String) -> (HTTPURLResponse, Data) {
        DietFeatureTestNetwork.jsonResponse(statusCode: statusCode, path: path, body: body)
    }

    static func bodyJSON(from request: URLRequest) -> [String: Any]? {
        DietFeatureTestNetwork.bodyJSON(from: request)
    }
}

private enum DietFeatureTestNetwork {
    static let baseURL = URL(string: "https://unit.test")!

    static func makeAPIClient(
        handler: @escaping (URLRequest) throws -> (HTTPURLResponse, Data)
    ) -> APIClient {
        DietFeatureURLProtocolStub.requestHandler = handler
        let store = TokenStore()
        store.clearTokens()
        store.save(accessToken: jwt(expiringIn: 3600), refreshToken: "refresh")

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [DietFeatureURLProtocolStub.self]
        return APIClient(
            baseURL: baseURL,
            tokenStore: store,
            session: URLSession(configuration: configuration)
        )
    }

    static func jsonResponse(
        statusCode: Int = 200,
        path: String?,
        body: String
    ) -> (HTTPURLResponse, Data) {
        let url = URL(string: path ?? "/", relativeTo: baseURL)!
        let response = HTTPURLResponse(
            url: url,
            statusCode: statusCode,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        return (response, Data(body.utf8))
    }

    static func bodyJSON(from request: URLRequest) -> [String: Any]? {
        guard let data = bodyData(from: request) else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    private static func bodyData(from request: URLRequest) -> Data? {
        if let httpBody = request.httpBody {
            return httpBody
        }

        guard let stream = request.httpBodyStream else {
            return nil
        }

        stream.open()
        defer { stream.close() }

        var data = Data()
        let bufferSize = 1024
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }

        while stream.hasBytesAvailable {
            let bytesRead = stream.read(buffer, maxLength: bufferSize)
            if bytesRead < 0 {
                return nil
            }
            if bytesRead == 0 {
                break
            }
            data.append(buffer, count: bytesRead)
        }
        return data
    }

    private static func jwt(expiringIn seconds: TimeInterval) -> String {
        let header = ["alg": "none", "typ": "JWT"]
        let payload = ["exp": Date().timeIntervalSince1970 + seconds]
        return [
            base64URLEncodedJSON(header),
            base64URLEncodedJSON(payload),
            "signature"
        ].joined(separator: ".")
    }

    private static func base64URLEncodedJSON(_ object: Any) -> String {
        let data = try! JSONSerialization.data(withJSONObject: object)
        return data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

private final class DietFeatureURLProtocolStub: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let requestHandler = Self.requestHandler else {
            client?.urlProtocol(self, didFailWithError: APIError.unknown)
            return
        }

        do {
            let (response, data) = try requestHandler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {
    }
}

private final class LockedDietFeatureCounter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0

    var value: Int {
        lock.withLock { count }
    }

    @discardableResult
    func increment() -> Int {
        lock.withLock {
            count += 1
            return count
        }
    }
}
