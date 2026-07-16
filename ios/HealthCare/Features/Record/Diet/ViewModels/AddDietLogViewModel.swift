import Foundation
import SwiftUI
import UniformTypeIdentifiers

protocol DietFoodSearching: Sendable {
    func searchFoodCatalog(query: String) async throws -> [FoodCatalogItem]
}

extension APIClient: DietFoodSearching {
    func searchFoodCatalog(query: String) async throws -> [FoodCatalogItem] {
        try await request(.getFoodCatalog(query: query))
    }
}

@MainActor
final class AddDietLogViewModel: ObservableObject {
    // MARK: - 핵심 상태

    @Published var selectedMealType: MealType = .BREAKFAST
    @Published var logDate: String = ""
    @Published var notes: String = ""
    @Published var draft: DietLogDraft = .manual([])

    // MARK: - 네비게이션

    @Published var showFoodSearch = false
    @Published var showPremiumPaywall = false
    @Published var isSaving = false
    @Published var isAnalyzingPhoto = false
    @Published var errorMessage: String?

    // MARK: - 모듈

    let foodEntrySource: FoodEntrySource
    private let photoAnalyzer: (any MealPhotoAnalyzing)?

    private(set) var editingLogId: Int?

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = LocaleManager.resolvedLocale
        return f
    }()

    // MARK: - Init

    init(
        initialDate: Date = Date(),
        photoAnalyzer: (any MealPhotoAnalyzing)? = nil,
        debounceDuration: Duration = .milliseconds(500)
    ) {
        self.photoAnalyzer = photoAnalyzer
        self.foodEntrySource = FoodEntrySource(debounceDuration: debounceDuration)
        logDate = dateFormatter.string(from: initialDate)
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 6..<10:  selectedMealType = .BREAKFAST
        case 10..<14: selectedMealType = .LUNCH
        case 14..<20: selectedMealType = .DINNER
        default:      selectedMealType = .SNACK
        }
        bindFoodEntrySource()
    }

    init(
        editing log: DietLogDetailResponse,
        photoAnalyzer: (any MealPhotoAnalyzing)? = nil,
        debounceDuration: Duration = .milliseconds(500)
    ) {
        self.photoAnalyzer = photoAnalyzer
        self.foodEntrySource = FoodEntrySource(debounceDuration: debounceDuration)
        editingLogId     = log.dietLogId
        logDate          = log.logDate
        selectedMealType = log.mealType
        notes            = log.notes ?? ""
        draft            = .manual(log.entries.map(DraftFoodEntry.init(existingEntry:)))
        bindFoodEntrySource()
    }

    private func bindFoodEntrySource() {
        foodEntrySource.onEntryProduced = { [weak self] entry in
            self?.draft.append(entry)
            self?.showFoodSearch = false
        }
    }

    // MARK: - 계산 프로퍼티

    var canSave: Bool {
        !draft.isEmpty && draft.entries.allSatisfy(\.isValid)
    }

    var totalCalories: Double { draft.entries.map(\.calories).reduce(0, +) }
    var totalProtein:  Double { draft.entries.map(\.protein).reduce(0, +) }
    var totalCarbs:    Double { draft.entries.map(\.carbs).reduce(0, +) }
    var totalFat:      Double { draft.entries.map(\.fat).reduce(0, +) }

    // MARK: - 항목 관리

    func entryBinding(for entry: DraftFoodEntry) -> Binding<DraftFoodEntry> {
        let entryId = entry.id
        return Binding(
            get: { self.draft.entry(id: entryId) ?? entry },
            set: { self.draft.updateEntry(id: entryId, with: $0) }
        )
    }

    func entryBinding(at index: Int) -> Binding<DraftFoodEntry> {
        Binding(
            get: {
                guard self.draft.entries.indices.contains(index) else {
                    return Self.fallbackDraftEntry()
                }
                return self.draft.entries[index]
            },
            set: { self.draft.updateEntry(at: index, with: $0) }
        )
    }

    func removeEntry(id: DraftFoodEntry.ID) {
        draft.remove(id: id)
    }

    func removeEntry(at index: Int) {
        guard draft.entries.indices.contains(index) else { return }
        draft.remove(at: IndexSet(integer: index))
    }

    private static func fallbackDraftEntry() -> DraftFoodEntry {
        DraftFoodEntry(food: FoodCatalogItem(
            id: -1,
            name: "",
            nameKo: nil,
            category: .OTHER,
            caloriesPer100g: 0,
            proteinPer100g: 0,
            carbsPer100g: 0,
            fatPer100g: 0,
            sugarsPer100g: 0,
            dietaryFiberPer100g: 0,
            saturatedFatPer100g: 0,
            transFatPer100g: 0,
            cholesterolPer100gMg: 0,
            sodiumPer100gMg: 0,
            custom: false,
            usageCount: nil,
            createdByUserId: nil
        ))
    }

    // MARK: - 네비게이션

    func openFoodSearch() {
        foodEntrySource.reset()
        showFoodSearch = true
    }

    // MARK: - 사진 분석

    func startPhotoAnalysis(imageData: Data, apiClient: APIClient) async {
        isAnalyzingPhoto = true
        errorMessage = nil
        defer { isAnalyzingPhoto = false }
        let analyzer: any MealPhotoAnalyzing = photoAnalyzer ?? APIClientMealPhotoAnalyzer(apiClient: apiClient)
        do {
            draft = try await analyzer.analyze(imageData: imageData, mealType: selectedMealType)
        } catch APIError.premiumRequired {
            showPremiumPaywall = true
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "diet.error.photoAnalyze")
        }
    }

    func resetPhotoDraft() {
        draft = .manual([])
    }

    // MARK: - 저장

    func save(apiClient: APIClient, onSuccess: @escaping () -> Void) async {
        guard canSave else { return }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            switch draft {
            case .photoAnalysis(let analysisId, let entries, _, _):
                let items = entries.map {
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
                let body = try JSONEncoder().encode(ConfirmMealPhotoAnalysisRequest(
                    logDate: logDate,
                    mealType: selectedMealType.rawValue,
                    notes: notes.isEmpty ? nil : notes,
                    items: items
                ))
                let _: ConfirmMealPhotoAnalysisResponse = try await apiClient.request(
                    .confirmMealPhotoAnalysis(id: analysisId, body: body)
                )

            case .manual(let entries):
                let foodEntries = entries.map {
                    CreateFoodEntryRequest(
                        foodCatalogId: $0.food.id,
                        servingG: $0.servingG,
                        notes: $0.notes.isEmpty ? nil : $0.notes
                    )
                }
                if let logId = editingLogId {
                    let body = try JSONEncoder().encode(UpdateDietLogRequest(
                        logDate: logDate,
                        mealType: selectedMealType.rawValue,
                        entries: foodEntries,
                        notes: notes.isEmpty ? nil : notes
                    ))
                    let _: CreateDietLogResponse = try await apiClient.request(.updateDietLog(id: logId, body: body))
                } else {
                    let body = try JSONEncoder().encode(CreateDietLogRequest(
                        logDate: logDate,
                        mealType: selectedMealType.rawValue,
                        entries: foodEntries,
                        notes: notes.isEmpty ? nil : notes
                    ))
                    let _: CreateDietLogResponse = try await apiClient.request(.createDietLog(body: body))
                }
            }
            // 홈 화면(mount 상태인 경우)이 즉시 dashboard를 재로드해 위젯 스냅샷까지 갱신한다.
            // mount 아니면 다음 홈 진입 시 .task가 자동 처리.
            NotificationCenter.default.post(name: .dietRecordChanged, object: nil)
            onSuccess()
        } catch APIError.premiumRequired {
            showPremiumPaywall = true
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "diet.error.save")
        }
    }
}
