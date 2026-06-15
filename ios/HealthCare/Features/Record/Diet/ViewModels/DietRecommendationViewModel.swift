import Foundation

@MainActor
final class DietRecommendationViewModel: ObservableObject {
    @Published var result: DailyDietRecommendationResponse?
    @Published var selectedMeals: Set<MealType> = [.BREAKFAST, .LUNCH, .DINNER]
    @Published var selectedDate: Date = .now
    @Published var strictAllergyMode: Bool = false
    @Published var isLoading = false
    @Published var errorMessage: String?

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
        defer { isLoading = false }
        do {
            let request = DailyDietRecommendationRequest(
                date: dateFormatter.string(from: selectedDate),
                mealTypes: selectedMeals.sorted { $0.rawValue < $1.rawValue }.map(\.rawValue),
                strictAllergyMode: strictAllergyMode
            )
            let body = try JSONEncoder().encode(request)
            result = try await apiClient.request(.getDailyRecommendation(body: body))
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "recommend.error.load")
        }
    }

    func toggleMeal(_ meal: MealType) {
        if selectedMeals.contains(meal) {
            selectedMeals.remove(meal)
        } else {
            selectedMeals.insert(meal)
        }
    }
}
