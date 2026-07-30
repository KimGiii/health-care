import Foundation

enum SnackPlacement: String, CaseIterable, Identifiable {
    case morning
    case afternoon
    case evening

    var id: Self { self }

    var displayName: String {
        switch self {
        case .morning:
            return String(localized: "recommend.snackPlacement.morning", defaultValue: "아침~점심")
        case .afternoon:
            return String(localized: "recommend.snackPlacement.afternoon", defaultValue: "점심~저녁")
        case .evening:
            return String(localized: "recommend.snackPlacement.evening", defaultValue: "저녁 이후")
        }
    }
}

@MainActor
final class DietRecommendationViewModel: ObservableObject {
    @Published var result: DailyDietRecommendationResponse?
    @Published var selectedMeals: Set<MealType> = [.BREAKFAST, .LUNCH, .DINNER]
    @Published var selectedDate: Date = .now
    @Published var strictAllergyMode: Bool = false
    @Published var snackPlacements: Set<SnackPlacement> = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published private(set) var savingMealKeys: Set<String> = []
    @Published private(set) var savedMealKeys: Set<String> = []
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

    var selectedMealsInDisplayOrder: [MealType] {
        let effectivePlacements: Set<SnackPlacement> =
            selectedMeals.contains(.SNACK) && snackPlacements.isEmpty ? [.afternoon] : snackPlacements
        return Self.mealOrder(for: effectivePlacements).filter {
            $0 == .SNACK || selectedMeals.contains($0)
        }
    }

    func ordered(_ meals: [RecommendedMeal]) -> [RecommendedMeal] {
        var buckets = Dictionary(grouping: meals, by: \.mealType)
        let effectivePlacements: Set<SnackPlacement> =
            meals.contains { $0.mealType == .SNACK } && snackPlacements.isEmpty ? [.afternoon] : snackPlacements
        return Self.mealOrder(for: effectivePlacements).compactMap { type in
            guard var bucket = buckets[type], !bucket.isEmpty else { return nil }
            let meal = bucket.removeFirst()
            buckets[type] = bucket
            return meal
        }
    }

    static func mealOrder(for placements: Set<SnackPlacement>) -> [MealType] {
        var order: [MealType] = [.BREAKFAST]
        if placements.contains(.morning) { order.append(.SNACK) }
        order.append(.LUNCH)
        if placements.contains(.afternoon) { order.append(.SNACK) }
        order.append(.DINNER)
        if placements.contains(.evening) { order.append(.SNACK) }
        return order
    }

    func recommend(apiClient: APIClient) async {
        guard canRecommend else { return }
        isLoading = true
        errorMessage = nil
        result = nil
        savedMealKeys = []
        defer { isLoading = false }
        do {
            let request = DailyDietRecommendationRequest(
                date: dateFormatter.string(from: selectedDate),
                mealTypes: selectedMealsInDisplayOrder.map(\.rawValue),
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
        let key = mealKey(meal)
        guard !meal.items.isEmpty, !savingMealKeys.contains(key) else { return }

        savingMealKeys.insert(key)
        errorMessage = nil
        defer { savingMealKeys.remove(key) }

        do {
            let request = Self.makeCreateDietLogRequest(
                for: meal,
                date: result?.date ?? dateFormatter.string(from: selectedDate),
                snapshotId: result?.snapshotId
            )
            let body = try JSONEncoder().encode(request)
            let _: CreateDietLogResponse = try await apiClient.request(.createDietLog(body: body))
            savedMealKeys.insert(key)
            NotificationCenter.default.post(name: .dietRecordChanged, object: nil)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "recommend.error.save")
        }
    }

    func isSaving(_ meal: RecommendedMeal) -> Bool {
        savingMealKeys.contains(mealKey(meal))
    }

    func isSaved(_ meal: RecommendedMeal) -> Bool {
        savedMealKeys.contains(mealKey(meal))
    }

    private func mealKey(_ meal: RecommendedMeal) -> String {
        meal.mealType.rawValue + ":" + meal.items.map { String($0.foodCatalogId) }.joined(separator: ",")
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
    /// swap 사실은 서버에 SWAPPED 이벤트로 계측한다(#85 선행) — 계측 실패가 UX를 막으면 안 되므로
    /// best-effort로 보내고 실패는 무시한다.
    func applyAlternative(at index: Int, apiClient: APIClient? = nil) {
        guard let current = result,
              index >= 0, index < current.alternatives.count else { return }

        if let apiClient, let snapshotId = current.snapshotId {
            Task {
                let body = try JSONEncoder().encode(SwapRequest(alternativeIndex: index))
                try? await apiClient.requestVoid(
                    .postRecommendationSwap(snapshotId: snapshotId, body: body)
                )
            }
        }

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
        savedMealKeys = []
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
        if meal == .SNACK {
            if selectedMeals.contains(.SNACK) {
                selectedMeals.remove(.SNACK)
                snackPlacements.removeAll()
            } else {
                selectedMeals.insert(.SNACK)
                snackPlacements = [.afternoon]
            }
            return
        }
        if selectedMeals.contains(meal) {
            selectedMeals.remove(meal)
        } else {
            selectedMeals.insert(meal)
        }
    }

    func toggleSnackPlacement(_ placement: SnackPlacement) {
        if snackPlacements.contains(placement) {
            snackPlacements.remove(placement)
        } else {
            snackPlacements.insert(placement)
        }
        if snackPlacements.isEmpty {
            selectedMeals.remove(.SNACK)
        } else {
            selectedMeals.insert(.SNACK)
        }
    }

    static func makeCreateDietLogRequest(
        for meal: RecommendedMeal,
        date: String,
        snapshotId: Int? = nil,
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
            notes: note,
            recommendationSnapshotId: snapshotId
        )
    }
}
