import Foundation

@MainActor
final class DietRecommendationViewModel: ObservableObject {
    @Published var result: DailyDietRecommendationResponse?
    @Published var selectedMeals: Set<MealType> = [.BREAKFAST, .LUNCH, .DINNER]
    @Published var selectedDate: Date = .now
    @Published var strictAllergyMode: Bool = false
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published private(set) var savingMealTypes: Set<MealType> = []
    @Published private(set) var savedMealTypes: Set<MealType> = []

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    var canRecommend: Bool { !selectedMeals.isEmpty }

    var orderedMeals: [MealType] { [.BREAKFAST, .LUNCH, .DINNER, .SNACK] }

    func recommend(apiClient: APIClient) async {
        guard canRecommend else { return }
        isLoading = true
        errorMessage = nil
        result = nil
        savedMealTypes = []
        defer { isLoading = false }
        do {
            let request = DailyDietRecommendationRequest(
                date: dateFormatter.string(from: selectedDate),
                mealTypes: selectedMeals.sorted { $0.rawValue < $1.rawValue }.map(\.rawValue),
                strictAllergyMode: strictAllergyMode,
                alternativeCount: 2
            )
            let body = try JSONEncoder().encode(request)
            result = try await apiClient.request(.getDailyRecommendation(body: body))
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "recommend.error.load")
        }
    }

    func saveMeal(_ meal: RecommendedMeal, apiClient: APIClient) async {
        guard !meal.items.isEmpty, !savingMealTypes.contains(meal.mealType) else { return }

        savingMealTypes.insert(meal.mealType)
        errorMessage = nil
        defer { savingMealTypes.remove(meal.mealType) }

        do {
            let request = Self.makeCreateDietLogRequest(
                for: meal,
                date: result?.date ?? dateFormatter.string(from: selectedDate)
            )
            let body = try JSONEncoder().encode(request)
            let _: CreateDietLogResponse = try await apiClient.request(.createDietLog(body: body))
            savedMealTypes.insert(meal.mealType)
            NotificationCenter.default.post(name: .dietRecordChanged, object: nil)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "recommend.error.save")
        }
    }

    func isSaving(_ meal: RecommendedMeal) -> Bool {
        savingMealTypes.contains(meal.mealType)
    }

    func isSaved(_ meal: RecommendedMeal) -> Bool {
        savedMealTypes.contains(meal.mealType)
    }

    func toggleMeal(_ meal: MealType) {
        if selectedMeals.contains(meal) {
            selectedMeals.remove(meal)
        } else {
            selectedMeals.insert(meal)
        }
    }

    static func makeCreateDietLogRequest(
        for meal: RecommendedMeal,
        date: String,
        note: String = String(localized: "recommend.log.note")
    ) -> CreateDietLogRequest {
        CreateDietLogRequest(
            logDate: date,
            mealType: meal.mealType.rawValue,
            entries: meal.items.map {
                CreateFoodEntryRequest(
                    foodCatalogId: $0.foodCatalogId,
                    servingG: $0.servingG,
                    notes: $0.caution.flatMap { $0.isEmpty ? nil : $0 }
                )
            },
            notes: note
        )
    }
}
