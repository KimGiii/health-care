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
    @Published var showFeedbackSheet = false
    @Published private(set) var isSendingFeedback = false

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

    func requestRefresh(reason: RecommendationFeedbackReason, apiClient: APIClient) async {
        guard let snapshotId = result?.snapshotId else { return }
        isSendingFeedback = true
        showFeedbackSheet = false
        defer { isSendingFeedback = false }
        do {
            let body = try JSONEncoder().encode(FeedbackRequest(reason: reason))
            try await apiClient.requestVoid(
                .postRecommendationFeedback(snapshotId: snapshotId, body: body)
            )
            await recommend(apiClient: apiClient)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "recommend.error.feedback")
        }
    }

    /// 백엔드 checkTargets와 동일한 목표 허용 범위 (UseCases와 일치).
    private static let calorieLowerRatio = 0.90
    private static let calorieUpperRatio = 1.10
    private static let proteinLowerRatio = 0.90

    /// 대안 식단을 상단 추천으로 적용하고, 기존 추천은 해당 대안 자리로 보낸다(swap).
    /// 합계와 실패 사유를 새 식단 기준으로 재계산한다.
    func applyAlternative(at index: Int) {
        guard let current = result,
              index >= 0, index < current.alternatives.count else { return }

        let chosen = current.alternatives[index]
        var newAlternatives = current.alternatives
        newAlternatives[index] = current.meals

        result = DailyDietRecommendationResponse(
            date: current.date,
            targets: current.targets,
            remainingTargets: current.remainingTargets,
            appliedRestrictions: current.appliedRestrictions,
            meals: chosen,
            totalNutrients: Self.summarize(chosen),
            failureReason: Self.evaluateFailureReason(meals: chosen, targets: current.targets),
            strictAllergyMode: current.strictAllergyMode,
            disclaimer: current.disclaimer,
            alternatives: newAlternatives,
            snapshotId: current.snapshotId
        )
        // 새 식단으로 바뀌었으므로 저장 상태를 초기화한다.
        savedMealTypes = []
    }

    static func summarize(_ meals: [RecommendedMeal]) -> DailyDietRecommendationResponse.NutrientSummary {
        DailyDietRecommendationResponse.NutrientSummary(
            totalCalories: meals.reduce(0) { $0 + $1.totalCalories },
            totalProteinG: meals.reduce(0) { $0 + $1.totalProteinG },
            totalCarbsG: meals.reduce(0) { $0 + $1.totalCarbsG },
            totalFatG: meals.reduce(0) { $0 + $1.totalFatG }
        )
    }

    static func evaluateFailureReason(meals: [RecommendedMeal], targets: NutritionTargets) -> String? {
        let totalCalories = meals.reduce(0) { $0 + $1.totalCalories }
        let totalProtein = meals.reduce(0) { $0 + $1.totalProteinG }
        if totalCalories < targets.calorieTarget * calorieLowerRatio
            || totalCalories > targets.calorieTarget * calorieUpperRatio {
            return String(localized: "recommend.failure.calorie")
        }
        if totalProtein < targets.proteinTargetG * proteinLowerRatio {
            return String(localized: "recommend.failure.protein")
        }
        return nil
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
