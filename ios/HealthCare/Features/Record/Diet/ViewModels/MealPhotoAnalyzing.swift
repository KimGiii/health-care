import Foundation

protocol MealPhotoAnalyzing: Sendable {
    func analyze(imageData: Data, mealType: MealType) async throws -> DietLogDraft
}

final class APIClientMealPhotoAnalyzer: MealPhotoAnalyzing, @unchecked Sendable {
    private let apiClient: APIClient

    private let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func analyze(imageData: Data, mealType: MealType) async throws -> DietLogDraft {
        let contentType = detectContentType(from: imageData)
        let initiateBody = try JSONEncoder().encode(InitiateMealPhotoAnalysisRequest(
            fileName: "meal-photo.jpg",
            contentType: contentType,
            fileSizeBytes: imageData.count,
            capturedAt: isoFormatter.string(from: Date())
        ))
        let initiated: InitiateMealPhotoAnalysisResponse = try await apiClient.request(
            .initiateMealPhotoAnalysis(body: initiateBody)
        )

        try await uploadImage(data: imageData, to: initiated.uploadUrl, contentType: contentType)

        let analyzeBody = try JSONEncoder().encode(
            AnalyzeMealPhotoRequest(mealType: mealType.rawValue)
        )
        let analyzed: MealPhotoAnalysisResponse = try await apiClient.request(
            .analyzeMealPhoto(id: initiated.analysisId, body: analyzeBody)
        )

        return .photoAnalysis(
            id: analyzed.analysisId,
            entries: analyzed.detectedItems.map(DraftFoodEntry.init(analysisItem:)),
            warnings: analyzed.analysisWarnings,
            previewURL: analyzed.previewUrl
        )
    }

    private func detectContentType(from data: Data) -> String {
        let bytes = [UInt8](data.prefix(12))
        if bytes.starts(with: [0x89, 0x50, 0x4E, 0x47]) { return "image/png" }
        if bytes.starts(with: [0xFF, 0xD8, 0xFF]) { return "image/jpeg" }
        if bytes.count >= 12,
           bytes[0...3] == [0x52, 0x49, 0x46, 0x46],
           bytes[8...11] == [0x57, 0x45, 0x42, 0x50] { return "image/webp" }
        return "image/jpeg"
    }

    private func uploadImage(data: Data, to uploadURL: String, contentType: String) async throws {
        guard let url = URL(string: uploadURL) else { throw APIError.invalidURL }
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        let (_, response) = try await URLSession.shared.upload(for: request, from: data)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw APIError.unknown
        }
    }
}
