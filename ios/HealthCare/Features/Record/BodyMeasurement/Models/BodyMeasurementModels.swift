import Foundation

// MARK: - Response Models

struct MeasurementResponse: Codable, Identifiable, Sendable {
    let id: Int
    let measuredAt: String
    let weightKg: Double?
    let bodyFatPct: Double?
    let muscleMassKg: Double?
    let bmi: Double?
    let chestCm: Double?
    let waistCm: Double?
    let hipCm: Double?
    let thighCm: Double?
    let armCm: Double?
    let notes: String?

    var formattedDate: String {
        let parts = measuredAt.split(separator: "-")
        guard parts.count == 3 else { return measuredAt }
        return "\(parts[0])년 \(parts[1])월 \(parts[2])일"
    }

    var shortDate: String {
        let parts = measuredAt.split(separator: "-")
        guard parts.count == 3 else { return measuredAt }
        return "\(parts[1])/\(parts[2])"
    }
}

struct MeasurementListResponse: Codable {
    let content: [MeasurementResponse]
    let pageNumber: Int
    let pageSize: Int
    let totalElements: Int
    let first: Bool
    let last: Bool
}

// MARK: - Request DTO

struct CreateMeasurementRequest: Encodable {
    let measuredAt: String
    let weightKg: Double?
    let bodyFatPct: Double?
    let muscleMassKg: Double?
    let bmi: Double?
    let chestCm: Double?
    let waistCm: Double?
    let hipCm: Double?
    let thighCm: Double?
    let armCm: Double?
    let notes: String?
}
