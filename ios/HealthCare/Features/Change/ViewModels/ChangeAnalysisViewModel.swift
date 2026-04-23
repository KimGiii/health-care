import Foundation

@MainActor
final class ChangeAnalysisViewModel: ObservableObject {
    @Published var analysis: ChangeAnalysisResponse?
    @Published var fromDate: Date = Calendar.current.date(byAdding: .month, value: -1, to: Date()) ?? Date()
    @Published var toDate: Date = Date()
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    func load(apiClient: APIClient) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let from = dateFormatter.string(from: fromDate)
        let to = dateFormatter.string(from: toDate)

        do {
            analysis = try await apiClient.request(.getChangeAnalysis(from: from, to: to))
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "변화 분석을 불러오는 중 오류가 발생했습니다."
        }
    }
}
