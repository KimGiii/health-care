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
                // 서버가 기본 버퍼만큼 후보를 미리 생성하므로 클라는 추가 요청량 0으로 서버 기본에 위임한다.
                alternativeCount: 0
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

    /// 대안 해를 상단 추천으로 적용하고, 기존 추천은 해당 대안 자리로 보낸다(swap).
    /// 대안은 서버가 검증한 feasible 해이므로 실패 사유는 nil이며, 근거(rationale)도 함께 교체한다.
    func applyAlternative(at index: Int) {
        guard let current = result,
              index >= 0, index < current.alternatives.count else { return }

        let chosen = current.alternatives[index]
        // 현재 추천을 대안 자리로 보낸다. rationale이 없으면 교체하지 않는다(안전).
        guard let currentRationale = current.rationale else { return }
        var newAlternatives = current.alternatives
        newAlternatives[index] = RecommendationSolution(
            meals: current.meals, rationale: currentRationale)

        result = DailyDietRecommendationResponse(
            date: current.date,
            targets: current.targets,
            remainingTargets: current.remainingTargets,
            appliedRestrictions: current.appliedRestrictions,
            meals: chosen.meals,
            totalNutrients: Self.summarize(chosen.meals),
            failureReason: nil,
            failureCode: nil,
            rationale: chosen.rationale,
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
