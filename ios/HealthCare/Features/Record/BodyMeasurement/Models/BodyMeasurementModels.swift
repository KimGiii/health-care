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

    private static let storageFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter
    }()

    var parsedDate: Date? {
        Self.storageFormatter.date(from: measuredAt)
    }

    var formattedDate: String {
        let parts = measuredAt.split(separator: "-")
        guard parts.count == 3 else { return measuredAt }
        // locale-aware long date format
        let parser = DateFormatter()
        parser.dateFormat = "yyyy-MM-dd"
        parser.locale = Locale(identifier: "en_US_POSIX")
        if let date = parser.date(from: "\(parts[0])-\(parts[1])-\(parts[2])") {
            let display = DateFormatter()
            display.locale = LocaleManager.resolvedLocale
            display.dateFormat = String(localized: "diet.date.format.longKR")
            return display.string(from: date)
        }
        return "\(parts[0])-\(parts[1])-\(parts[2])"
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

enum MeasurementTrendRange: String, CaseIterable, Identifiable {
    case week7 = "7D"
    case month1 = "1M"
    case month3 = "3M"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .week7:  return String(localized: "body.range.1w")
        case .month1: return String(localized: "body.range.1m")
        case .month3: return String(localized: "body.range.3m")
        }
    }

    var days: Int {
        switch self {
        case .week7:  return 7
        case .month1: return 30
        case .month3: return 90
        }
    }
}

enum MeasurementMetric: String, CaseIterable, Identifiable {
    case weight
    case bodyFat
    case muscleMass
    case waist

    var id: String { rawValue }

    var title: String {
        switch self {
        case .weight:     return String(localized: "body.metric.weight")
        case .bodyFat:    return String(localized: "body.metric.bodyFat")
        case .muscleMass: return String(localized: "body.metric.muscleMass")
        case .waist:      return String(localized: "body.metric.waist")
        }
    }

    var unit: String {
        switch self {
        case .bodyFat:
            return "%"
        case .weight, .muscleMass:
            return "kg"
        case .waist:
            return "cm"
        }
    }

    var accentHex: String {
        switch self {
        case .weight:     return "#2563EB"
        case .bodyFat:    return "#7C3AED"
        case .muscleMass: return "#10B981"
        case .waist:      return "#DC2626"
        }
    }

    var fallbackYAxisDomain: ClosedRange<Double> {
        switch self {
        case .weight:     return 30...150
        case .bodyFat:    return 0...70
        case .muscleMass: return 0...70
        case .waist:      return 40...150
        }
    }

    var absoluteYAxisBounds: ClosedRange<Double> {
        switch self {
        case .weight:     return 10...250
        case .bodyFat:    return 0...80
        case .muscleMass: return 5...90
        case .waist:      return 20...200
        }
    }

    var scatterDelta: Double {
        switch self {
        case .weight:     return 25
        case .bodyFat:    return 10
        case .muscleMass: return 10
        case .waist:      return 15
        }
    }

    var goalMargin: Double {
        switch self {
        case .weight:     return 5
        case .bodyFat:    return 3
        case .muscleMass: return 3
        case .waist:      return 5
        }
    }

    func yAxisDomain(base: Double?, goalTarget: Double?) -> ClosedRange<Double> {
        let raw: ClosedRange<Double>
        switch (base, goalTarget) {
        case let (b?, t?):
            raw = (min(b, t) - goalMargin)...(max(b, t) + goalMargin)
        case let (b?, nil):
            raw = (b - scatterDelta)...(b + scatterDelta)
        case let (nil, t?):
            raw = (t - scatterDelta)...(t + scatterDelta)
        case (nil, nil):
            return fallbackYAxisDomain
        }

        let bounds = absoluteYAxisBounds
        let lo = max(raw.lowerBound, bounds.lowerBound)
        let hi = min(raw.upperBound, bounds.upperBound)
        return lo < hi ? lo...hi : fallbackYAxisDomain
    }

    func value(from measurement: MeasurementResponse) -> Double? {
        switch self {
        case .weight:
            return measurement.weightKg
        case .bodyFat:
            return measurement.bodyFatPct
        case .muscleMass:
            return measurement.muscleMassKg
        case .waist:
            return measurement.waistCm
        }
    }
}

struct MeasurementTrendPoint: Identifiable, Sendable {
    let measurementId: Int
    let date: Date
    let label: String
    let value: Double

    var id: Int { measurementId }
}
