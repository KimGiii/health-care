import Foundation
import UniformTypeIdentifiers

protocol DietFoodSearching: Sendable {
    func searchFoodCatalog(query: String) async throws -> [FoodCatalogItem]
    func searchExternalFoods(query: String) async throws -> [ExternalFoodResult]
}

extension APIClient: DietFoodSearching {
    func searchFoodCatalog(query: String) async throws -> [FoodCatalogItem] {
        try await request(.getFoodCatalog(query: query))
    }

    func searchExternalFoods(query: String) async throws -> [ExternalFoodResult] {
        try await request(.searchExternalFoods(query: query, source: "ALL", page: 0, size: 20))
    }
}

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

    // MARK: - 직접 등록 상태
    @Published var showCustomFoodForm = false
    @Published var isSubmittingCustomFood = false

    // MARK: - AI 추정 상태
    @Published var aiEstimateResult: AiNutritionEstimateResponse?
    @Published var isAiEstimating = false

    // MARK: - 저장 상태
    @Published var isSaving = false
    @Published var errorMessage: String?
    @Published var isAnalyzingPhoto = false
    @Published var analysisWarnings: [String] = []
    @Published var photoAnalysisId: Int?
    @Published var photoPreviewURL: String?

    // MARK: - 프리미엄 게이팅
    /// 사진 분석 시도 시 백엔드가 403 PREMIUM_REQUIRED로 거부한 경우 true.
    /// 화면이 paywall 시트를 띄우는 트리거.
    @Published var showPremiumPaywall = false

    private(set) var editingLogId: Int?

    private let debounceDuration: Duration
    private var searchDebounceTask: Task<Void, Never>?
    private var searchTask: Task<Void, Never>?

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = LocaleManager.resolvedLocale
        return f
    }()

    private let isoFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    init(initialDate: Date = Date(), debounceDuration: Duration = .milliseconds(500)) {
        self.debounceDuration = debounceDuration
        logDate = dateFormatter.string(from: initialDate)
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 6..<10:  selectedMealType = .BREAKFAST
        case 10..<14: selectedMealType = .LUNCH
        case 14..<20: selectedMealType = .DINNER
        default:      selectedMealType = .SNACK
        }
    }

    init(editing log: DietLogDetailResponse, debounceDuration: Duration = .milliseconds(500)) {
        self.debounceDuration = debounceDuration
        self.editingLogId     = log.dietLogId
        self.logDate          = log.logDate
        self.selectedMealType = log.mealType
        self.notes            = log.notes ?? ""
        self.draftEntries     = log.entries.map(DraftFoodEntry.init(existingEntry:))
    }

    var canSave: Bool {
        !draftEntries.isEmpty && draftEntries.allSatisfy(\.isValid)
    }

    deinit {
        searchDebounceTask?.cancel()
        searchTask?.cancel()
    }

    // MARK: - 영양 합계 (실시간 미리보기)
    var totalCalories: Double { draftEntries.map(\.calories).reduce(0, +) }
    var totalProtein:  Double { draftEntries.map(\.protein).reduce(0, +) }
    var totalCarbs:    Double { draftEntries.map(\.carbs).reduce(0, +) }
    var totalFat:      Double { draftEntries.map(\.fat).reduce(0, +) }

    func scheduleSearch(apiClient: any DietFoodSearching) {
        let query = normalizedSearchQuery
        guard !query.isEmpty else {
            cancelPendingSearches()
            resetSearchResults()
            return
        }

        searchDebounceTask?.cancel()
        searchDebounceTask = Task { [weak self] in
            do {
                try await Task.sleep(for: self?.debounceDuration ?? .zero)
                try Task.checkCancellation()
                guard let self else { return }
                await self.searchAll(apiClient: apiClient)
            } catch is CancellationError {
                return
            } catch {
                return
            }
        }
    }

    func triggerImmediateSearch(apiClient: any DietFoodSearching) {
        searchDebounceTask?.cancel()
        searchDebounceTask = nil

        guard !normalizedSearchQuery.isEmpty else {
            cancelPendingSearches()
            resetSearchResults()
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
        resetSearchResults()
    }

    // MARK: - 카탈로그 검색 (내 DB)
    func searchCatalog(apiClient: APIClient) async {
        let query = normalizedSearchQuery
        guard !query.isEmpty else {
            catalogResults = []
            return
        }
        isSearching = true
        defer { isSearching = false }

        do {
            let results: [FoodCatalogItem] = try await apiClient.request(
                .getFoodCatalog(query: query)
            )
            catalogResults = results.uniqued(by: \.displayName)
            errorMessage = nil
        } catch {
            catalogResults = []
            errorMessage = String(localized: "diet.error.foodSearch")
            print("❌ searchCatalog error: \(error)")
        }
    }

    // MARK: - 카탈로그 + 외부 동시 검색
    func searchAll(apiClient: any DietFoodSearching) async {
        let query = normalizedSearchQuery
        guard !query.isEmpty else {
            resetSearchResults()
            return
        }

        searchTask?.cancel()
        isSearching = true
        aiEstimateResult = nil

        searchTask = Task { [weak self] in
            do {
                async let catalogFetch = Self.fetchCatalog(apiClient: apiClient, query: query)
                async let externalFetch = Self.fetchExternalFoods(apiClient: apiClient, query: query)
                let (catalog, external) = await (catalogFetch, externalFetch)
                try Task.checkCancellation()

                await MainActor.run {
                    guard let self, self.normalizedSearchQuery == query else { return }
                    self.catalogResults = catalog.uniqued(by: \.displayName)
                    self.externalResults = external.uniqued(by: \.displayName)
                    self.errorMessage = nil
                    self.isSearching = false
                    self.searchTask = nil
                }
            } catch is CancellationError {
                await MainActor.run {
                    guard let self else { return }
                    if self.normalizedSearchQuery == query {
                        self.isSearching = false
                    }
                    self.searchTask = nil
                }
            } catch {
                await MainActor.run {
                    guard let self, self.normalizedSearchQuery == query else { return }
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

    private static func fetchCatalog(
        apiClient: any DietFoodSearching,
        query: String
    ) async -> [FoodCatalogItem] {
        do {
            return try await apiClient.searchFoodCatalog(query: query)
        } catch is CancellationError {
            return []
        } catch {
            return []
        }
    }

    private static func fetchExternalFoods(
        apiClient: any DietFoodSearching,
        query: String
    ) async -> [ExternalFoodResult] {
        do {
            return try await apiClient.searchExternalFoods(query: query)
        } catch is CancellationError {
            return []
        } catch {
            return []
        }
    }

    // MARK: - AI 영양 추정 (카탈로그·외부 검색 모두 결과 없을 때 폴백)
    func estimateWithAI(apiClient: APIClient) async {
        let query = searchQuery.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return }

        isAiEstimating = true
        defer { isAiEstimating = false }

        do {
            let request = AiNutritionEstimateRequest(foodName: query)
            let body = try JSONEncoder().encode(request)
            let result: AiNutritionEstimateResponse = try await apiClient.request(
                .aiEstimateFood(body: body)
            )
            aiEstimateResult = result

            // 음식으로 인식되지 않은 경우 / AI 일시 불가 — 사용자 안내
            if !result.isFood, let error = result.error {
                switch error.code {
                case "NOT_FOOD_OR_UNKNOWN":
                    errorMessage = String(localized: "diet.error.ai.notFood")
                case "AI_UNAVAILABLE":
                    errorMessage = String(localized: "diet.error.ai.unavailable")
                default:
                    errorMessage = error.message
                }
            }
        } catch {
            errorMessage = String(localized: "diet.error.ai.estimate")
        }
    }

    // MARK: - AI 추정 결과로 커스텀 식품 생성 후 항목 추가
    /// 다중 음식인 경우에도 일단 첫 item을 카탈로그로 저장 후 draft로 추가.
    /// 추후 다중 추가 UX는 별도 개선.
    func addAiEstimatedFood(apiClient: APIClient) async {
        guard let estimate = aiEstimateResult,
              estimate.isFood,
              let item = estimate.firstItem else { return }

        // PER_ITEM/CUSTOM_WEIGHT는 표시 nutrition이 1단위 기준 — 100g 환산해서 카탈로그 저장.
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
                "name": displayName,
                "nameKo": displayName,
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
            let catalogItem: FoodCatalogItem = try await apiClient.request(
                .createCustomFood(body: body)
            )
            // 사용자가 명시한 무게(있다면) 또는 기본 100g로 draft 진입.
            var draft = DraftFoodEntry(food: catalogItem)
            draft.servingGText = String(format: "%.0f", weight)
            draftEntries.append(draft)
            aiEstimateResult = nil
            // 검색 시트도 함께 닫음 — 일반 식품 추가(addEntry) 동작과 동일하게.
            showFoodSearch = false
        } catch {
            errorMessage = String(localized: "diet.error.ai.save")
        }
    }

    // MARK: - 직접 등록 식품 저장
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
                let name: String
                let nameKo: String
                let category: String
                let caloriesPer100g: Double
                let proteinPer100g: Double?
                let carbsPer100g: Double?
                let fatPer100g: Double?
            }
            let body = try JSONEncoder().encode(CustomFoodBody(
                name: name,
                nameKo: name,
                category: category.rawValue,
                caloriesPer100g: caloriesPer100g,
                proteinPer100g: proteinPer100g,
                carbsPer100g: carbsPer100g,
                fatPer100g: fatPer100g
            ))
            let saved: FoodCatalogItem = try await apiClient.request(.createCustomFood(body: body))
            catalogResults.insert(saved, at: 0)
            addEntry(food: saved)
            showCustomFoodForm = false
        } catch {
            errorMessage = String(localized: "diet.error.foodRegister")
        }
    }

    // MARK: - 외부 식품 검색 (USDA / OFF)
    func searchExternal(apiClient: APIClient) async {
        let query = normalizedSearchQuery
        guard !query.isEmpty else {
            externalResults = []
            return
        }
        isSearching = true
        defer { isSearching = false }

        do {
            let results: [ExternalFoodResult] = try await apiClient.request(
                .searchExternalFoods(query: query, source: "ALL", page: 0, size: 20)
            )
            externalResults = results.uniqued(by: \.displayName)
            errorMessage = nil
            print("✅ searchExternal success: \(results.count) results")
        } catch {
            externalResults = []
            errorMessage = String(localized: "diet.error.foodSearch")
            print("❌ searchExternal error: \(error)")
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
                fatPer100g: external.fatPer100g,
                sugarsPer100g: external.sugarsPer100g,
                dietaryFiberPer100g: external.dietaryFiberPer100g,
                saturatedFatPer100g: external.saturatedFatPer100g,
                transFatPer100g: external.transFatPer100g,
                cholesterolPer100gMg: external.cholesterolPer100gMg,
                sodiumPer100gMg: external.sodiumPer100gMg
            )
            let body = try JSONEncoder().encode(request)
            let catalogItem: FoodCatalogItem = try await apiClient.request(.importExternalFood(body: body))
            addEntry(food: catalogItem)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "diet.error.foodAdd")
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

    func applyPhotoDraft(_ response: MealPhotoAnalysisResponse) {
        photoAnalysisId = response.analysisId
        photoPreviewURL = response.previewUrl
        analysisWarnings = response.analysisWarnings
        draftEntries = response.detectedItems.map(DraftFoodEntry.init(analysisItem:))
    }

    func resetPhotoDraftState() {
        photoAnalysisId = nil
        photoPreviewURL = nil
        analysisWarnings = []
    }

    func startPhotoAnalysis(
        imageData: Data,
        suggestedFileName: String,
        apiClient: APIClient
    ) async {
        isAnalyzingPhoto = true
        errorMessage = nil
        defer { isAnalyzingPhoto = false }

        do {
            let contentType = detectContentType(from: imageData)
            let initiateRequest = InitiateMealPhotoAnalysisRequest(
                fileName: suggestedFileName,
                contentType: contentType,
                fileSizeBytes: imageData.count,
                capturedAt: isoFormatter.string(from: Date())
            )
            let body = try JSONEncoder().encode(initiateRequest)
            let initiated: InitiateMealPhotoAnalysisResponse = try await apiClient.request(
                .initiateMealPhotoAnalysis(body: body)
            )

            try await uploadImage(
                data: imageData,
                to: initiated.uploadUrl,
                contentType: contentType
            )

            let analyzeBody = try JSONEncoder().encode(
                AnalyzeMealPhotoRequest(mealType: selectedMealType.rawValue)
            )
            let analyzed: MealPhotoAnalysisResponse = try await apiClient.request(
                .analyzeMealPhoto(id: initiated.analysisId, body: analyzeBody)
            )
            applyPhotoDraft(analyzed)
        } catch APIError.premiumRequired {
            // 캐시된 isPremium이 false였어도 사전 게이트가 잡지만, 캐시 미스/만료에
            // 대비한 안전망. 사용자에게 paywall로 안내.
            showPremiumPaywall = true
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "diet.error.photoAnalyze")
        }
    }

    // MARK: - 저장
    func save(apiClient: APIClient, onSuccess: @escaping () -> Void) async {
        guard canSave else { return }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            if let photoAnalysisId {
                let items = draftEntries.map {
                    ConfirmMealPhotoAnalysisItem(
                        analysisItemId: $0.analysisItemId,
                        label: $0.displayName,
                        matchedFoodCatalogId: $0.matchedFoodCatalogId,
                        estimatedServingG: $0.servingG,
                        calories: $0.calories,
                        proteinG: $0.protein,
                        carbsG: $0.carbs,
                        fatG: $0.fat,
                        notes: $0.notes.isEmpty ? nil : $0.notes
                    )
                }
                let request = ConfirmMealPhotoAnalysisRequest(
                    logDate: logDate,
                    mealType: selectedMealType.rawValue,
                    notes: notes.isEmpty ? nil : notes,
                    items: items
                )
                let body = try JSONEncoder().encode(request)
                let _: ConfirmMealPhotoAnalysisResponse = try await apiClient.request(
                    .confirmMealPhotoAnalysis(id: photoAnalysisId, body: body)
                )
            } else {
                let entries = draftEntries.map {
                    CreateFoodEntryRequest(
                        foodCatalogId: $0.food.id,
                        servingG: $0.servingG,
                        notes: $0.notes.isEmpty ? nil : $0.notes
                    )
                }
                if let logId = editingLogId {
                    let request = UpdateDietLogRequest(
                        logDate: logDate,
                        mealType: selectedMealType.rawValue,
                        entries: entries,
                        notes: notes.isEmpty ? nil : notes
                    )
                    let body = try JSONEncoder().encode(request)
                    let _: CreateDietLogResponse = try await apiClient.request(.updateDietLog(id: logId, body: body))
                } else {
                    let request = CreateDietLogRequest(
                        logDate: logDate,
                        mealType: selectedMealType.rawValue,
                        entries: entries,
                        notes: notes.isEmpty ? nil : notes
                    )
                    let body = try JSONEncoder().encode(request)
                    let _: CreateDietLogResponse = try await apiClient.request(.createDietLog(body: body))
                }
            }
            onSuccess()
        } catch APIError.premiumRequired {
            // 사진 분석 도중 프리미엄이 만료/취소된 드문 케이스 — 백엔드의 confirm
            // 엔드포인트가 403 PREMIUM_REQUIRED로 거절. analyzePhoto와 동일하게
            // paywall로 안내하고 일반 에러 메시지로 흘리지 않는다.
            showPremiumPaywall = true
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "diet.error.save")
        }
    }

    private func detectContentType(from data: Data) -> String {
        let bytes = [UInt8](data.prefix(12))
        if bytes.starts(with: [0x89, 0x50, 0x4E, 0x47]) { return "image/png" }
        if bytes.starts(with: [0xFF, 0xD8, 0xFF]) { return "image/jpeg" }
        if bytes.count >= 12,
           bytes[0...3] == [0x52, 0x49, 0x46, 0x46],
           bytes[8...11] == [0x57, 0x45, 0x42, 0x50] {
            return "image/webp"
        }
        return UTType.jpeg.preferredMIMEType ?? "image/jpeg"
    }

    private func uploadImage(data: Data, to uploadURL: String, contentType: String) async throws {
        guard let url = URL(string: uploadURL) else {
            throw APIError.invalidURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        let (_, response) = try await URLSession.shared.upload(for: request, from: data)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw APIError.unknown
        }
    }

    private var normalizedSearchQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func cancelPendingSearches() {
        searchDebounceTask?.cancel()
        searchDebounceTask = nil
        searchTask?.cancel()
        searchTask = nil
        isSearching = false
    }

    private func resetSearchResults() {
        catalogResults = []
        externalResults = []
        aiEstimateResult = nil
        isSearching = false
    }
}
