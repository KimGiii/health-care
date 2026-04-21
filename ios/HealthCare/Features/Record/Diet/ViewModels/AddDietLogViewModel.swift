import Foundation
import UniformTypeIdentifiers

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
    @Published var isAnalyzingPhoto = false
    @Published var analysisWarnings: [String] = []
    @Published var photoAnalysisId: Int?
    @Published var photoPreviewURL: String?

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "ko_KR")
        return f
    }()

    private let isoFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
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
            errorMessage = nil
        } catch {
            catalogResults = []
            errorMessage = "카탈로그 검색 실패: \(error.localizedDescription)"
            print("❌ searchCatalog error: \(error)")
        }
    }

    // MARK: - 카탈로그 + 외부 동시 검색
    func searchAll(apiClient: APIClient) async {
        guard !searchQuery.trimmingCharacters(in: .whitespaces).isEmpty else {
            catalogResults = []
            externalResults = []
            return
        }
        isSearching = true
        defer { isSearching = false }

        async let catalogFetch: [FoodCatalogItem] = {
            (try? await apiClient.request(.getFoodCatalog(query: searchQuery))) ?? []
        }()
        async let externalFetch: [ExternalFoodResult] = {
            (try? await apiClient.request(.searchExternalFoods(query: searchQuery, source: "ALL", page: 0, size: 20))) ?? []
        }()

        let (c, e) = await (catalogFetch, externalFetch)
        catalogResults = c
        externalResults = e
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
            errorMessage = nil
            print("✅ searchExternal success: \(results.count) results")
        } catch {
            externalResults = []
            errorMessage = "외부 검색 실패: \(error.localizedDescription)"
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
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "사진 분석 중 오류가 발생했습니다."
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
                let request = CreateDietLogRequest(
                    logDate: logDate,
                    mealType: selectedMealType.rawValue,
                    entries: entries,
                    notes: notes.isEmpty ? nil : notes
                )
                let encoder = JSONEncoder()
                let body = try encoder.encode(request)
                let _: CreateDietLogResponse = try await apiClient.request(.createDietLog(body: body))
            }
            onSuccess()
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "저장 중 오류가 발생했습니다."
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
}
