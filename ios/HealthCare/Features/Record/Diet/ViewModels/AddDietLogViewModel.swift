import Foundation

@MainActor
final class AddDietLogViewModel: ObservableObject {
    // MARK: - 식사 입력 상태
    @Published var selectedMealType: MealType = .BREAKFAST
    @Published var logDate: String = ""
    @Published var notes: String = ""
    @Published var draftEntries: [DraftFoodEntry] = []

    // MARK: - 식품 검색 상태
    @Published var searchQuery: String = ""
    @Published var catalogResults: [FoodCatalogItem] = []
    @Published var externalResults: [ExternalFoodResult] = []
    @Published var isSearching = false
    @Published var showFoodSearch = false

    // MARK: - 저장 상태
    @Published var isSaving = false
    @Published var errorMessage: String?

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "ko_KR")
        return f
    }()

    init() {
        logDate = dateFormatter.string(from: Date())
        // 현재 시간에 따라 기본 식사 타입 설정
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 6..<10:  selectedMealType = .BREAKFAST
        case 10..<14: selectedMealType = .LUNCH
        case 14..<20: selectedMealType = .DINNER
        default:      selectedMealType = .SNACK
        }
    }

    var canSave: Bool {
        !draftEntries.isEmpty && draftEntries.allSatisfy(\.isValid)
    }

    // MARK: - 영양 합계 (실시간 미리보기)
    var totalCalories: Double { draftEntries.map(\.calories).reduce(0, +) }
    var totalProtein:  Double { draftEntries.map(\.protein).reduce(0, +) }
    var totalCarbs:    Double { draftEntries.map(\.carbs).reduce(0, +) }
    var totalFat:      Double { draftEntries.map(\.fat).reduce(0, +) }

    // MARK: - 카탈로그 검색 (내 DB)
    func searchCatalog(apiClient: APIClient) async {
        guard !searchQuery.trimmingCharacters(in: .whitespaces).isEmpty else {
            catalogResults = []
            return
        }
        isSearching = true
        defer { isSearching = false }

        do {
            let results: [FoodCatalogItem] = try await apiClient.request(
                .getFoodCatalog(query: searchQuery)
            )
            catalogResults = results
        } catch {
            catalogResults = []
        }
    }

    // MARK: - 외부 식품 검색 (USDA / OFF)
    func searchExternal(apiClient: APIClient) async {
        guard !searchQuery.trimmingCharacters(in: .whitespaces).isEmpty else {
            externalResults = []
            return
        }
        isSearching = true
        defer { isSearching = false }

        do {
            let results: [ExternalFoodResult] = try await apiClient.request(
                .searchExternalFoods(query: searchQuery, source: "ALL", page: 0, size: 20)
            )
            externalResults = results
        } catch {
            externalResults = []
        }
    }

    // MARK: - 외부 식품 → 카탈로그 임포트 후 추가
    func importAndAdd(external: ExternalFoodResult, apiClient: APIClient) async {
        do {
            let request = ImportFoodRequest(
                source: external.source.rawValue,
                externalId: external.externalId,
                name: external.name,
                nameKo: external.nameKo,
                brand: external.brand,
                category: external.category?.rawValue ?? "OTHER",
                caloriesPer100g: external.caloriesPer100g ?? 0,
                proteinPer100g: external.proteinPer100g,
                carbsPer100g: external.carbsPer100g,
                fatPer100g: external.fatPer100g
            )
            let body = try JSONEncoder().encode(request)
            let catalogItem: FoodCatalogItem = try await apiClient.request(.importExternalFood(body: body))
            addEntry(food: catalogItem)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "식품 추가 중 오류가 발생했습니다."
        }
    }

    // MARK: - 항목 관리
    func addEntry(food: FoodCatalogItem) {
        draftEntries.append(DraftFoodEntry(food: food))
        showFoodSearch = false
    }

    func removeEntry(at offsets: IndexSet) {
        draftEntries.remove(atOffsets: offsets)
    }

    // MARK: - 저장
    func save(apiClient: APIClient, onSuccess: @escaping () -> Void) async {
        guard canSave else { return }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            let entries = draftEntries.map {
                CreateFoodEntryRequest(
                    foodCatalogId: $0.food.id,
                    servingG: $0.servingG,
                    notes: $0.notes.isEmpty ? nil : $0.notes
                )
            }
            let request = CreateDietLogRequest(
                logDate: logDate,
                mealType: selectedMealType.rawValue,
                entries: entries,
                notes: notes.isEmpty ? nil : notes
            )
            let encoder = JSONEncoder()
            let body = try encoder.encode(request)
            let _: CreateDietLogResponse = try await apiClient.request(.createDietLog(body: body))
            onSuccess()
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "저장 중 오류가 발생했습니다."
        }
    }
}
