import SwiftUI

private func displayUnitText(from rawUnit: String?) -> String {
    switch rawUnit {
    case "pct":     return "%"
    case "minutes", "seconds": return "분"
    case .some(let rawUnit):
        return rawUnit
    case .none:
        return ""
    }
}

private func displayValue(for rawValue: Double?, unit rawUnit: String?) -> Double? {
    guard let rawValue else { return nil }

    switch rawUnit {
    case "seconds":
        return rawValue / 60.0
    default:
        return rawValue
    }
}

// MARK: - Enums

enum GoalType: String, Codable, CaseIterable, Sendable {
    case WEIGHT_LOSS
    case MUSCLE_GAIN
    case BODY_RECOMPOSITION
    case ENDURANCE
    case GENERAL_HEALTH

    var displayName: String {
        switch self {
        case .WEIGHT_LOSS:        return "체중 감량"
        case .MUSCLE_GAIN:        return "근육 증가"
        case .BODY_RECOMPOSITION: return "체형 개선"
        case .ENDURANCE:          return "지구력 향상"
        case .GENERAL_HEALTH:     return "전반적 건강"
        }
    }

    var emoji: String {
        switch self {
        case .WEIGHT_LOSS:        return "⚖️"
        case .MUSCLE_GAIN:        return "💪"
        case .BODY_RECOMPOSITION: return "🔥"
        case .ENDURANCE:          return "🏃"
        case .GENERAL_HEALTH:     return "❤️"
        }
    }

    var icon: String {
        switch self {
        case .WEIGHT_LOSS:        return "arrow.down.circle.fill"
        case .MUSCLE_GAIN:        return "dumbbell.fill"
        case .BODY_RECOMPOSITION: return "arrow.triangle.2.circlepath"
        case .ENDURANCE:          return "figure.run"
        case .GENERAL_HEALTH:     return "heart.fill"
        }
    }

    var accentColor: Color {
        switch self {
        case .WEIGHT_LOSS:        return .blue
        case .MUSCLE_GAIN:        return Color.brandPrimary
        case .BODY_RECOMPOSITION: return .orange
        case .ENDURANCE:          return .cyan
        case .GENERAL_HEALTH:     return .pink
        }
    }

    var apiUnit: String? {
        switch self {
        case .WEIGHT_LOSS, .MUSCLE_GAIN: return "kg"
        case .BODY_RECOMPOSITION:        return "pct"
        case .ENDURANCE:                 return "minutes"
        case .GENERAL_HEALTH:            return nil
        }
    }

    var displayUnit: String {
        displayUnitText(from: apiUnit)
    }

    var supportsWeeklyRateTarget: Bool {
        switch self {
        case .WEIGHT_LOSS, .MUSCLE_GAIN, .BODY_RECOMPOSITION: return true
        case .ENDURANCE, .GENERAL_HEALTH:                     return false
        }
    }

    var weeklyRateDisplayUnit: String {
        switch self {
        case .WEIGHT_LOSS, .MUSCLE_GAIN: return "kg/주"
        case .BODY_RECOMPOSITION:        return "%/주"
        case .ENDURANCE:                 return "분/주"
        case .GENERAL_HEALTH:            return ""
        }
    }

    var description: String {
        switch self {
        case .WEIGHT_LOSS:        return "체중을 목표치까지 줄여요"
        case .MUSCLE_GAIN:        return "근육량을 늘려 체성분을 개선해요"
        case .BODY_RECOMPOSITION: return "체지방을 줄이고 근육을 키워요"
        case .ENDURANCE:          return "유산소 능력을 향상시켜요"
        case .GENERAL_HEALTH:     return "전반적인 건강과 웰빙을 향상해요"
        }
    }

    var requiresTargetValue: Bool { self != .GENERAL_HEALTH }

    func normalizeWeeklyRate(_ rawValue: Double?) -> Double? {
        guard let rawValue, rawValue > 0 else { return nil }

        switch self {
        case .WEIGHT_LOSS, .BODY_RECOMPOSITION:
            return -abs(rawValue)
        case .MUSCLE_GAIN:
            return abs(rawValue)
        case .ENDURANCE, .GENERAL_HEALTH:
            return nil
        }
    }
}

enum GoalStatus: String, Codable, Sendable {
    case ACTIVE, COMPLETED, ABANDONED

    var displayName: String {
        switch self {
        case .ACTIVE:    return "진행 중"
        case .COMPLETED: return "달성 완료"
        case .ABANDONED: return "포기"
        }
    }

    var badgeColor: Color {
        switch self {
        case .ACTIVE:    return .brandAccent
        case .COMPLETED: return .green
        case .ABANDONED: return .secondary
        }
    }
}

// MARK: - Response Models

struct GoalSummary: Codable, Identifiable, Sendable {
    let goalId: Int
    let goalType: GoalType
    let targetValue: Double?
    let targetUnit: String?
    let targetDate: String?
    let startDate: String?
    let status: GoalStatus
    let percentComplete: Double?

    var id: Int { goalId }

    var daysRemaining: Int? {
        guard let targetDate, let date = Self.parseDate(targetDate) else { return nil }
        let today = Calendar.current.startOfDay(for: .now)
        let days = Calendar.current.dateComponents([.day], from: today, to: date).day ?? 0
        return days >= 0 ? days : nil
    }

    var formattedTargetDate: String {
        guard let td = targetDate else { return "-" }
        let parts = td.split(separator: "-")
        guard parts.count == 3 else { return td }
        return "\(parts[0])년 \(parts[1])월 \(parts[2])일"
    }

    var targetText: String {
        let displayUnit = displayUnitText(from: targetUnit)
        guard let v = displayValue(for: targetValue, unit: targetUnit), !displayUnit.isEmpty else {
            return goalType.displayName
        }
        return String(format: "%.1f %@", v, displayUnit)
    }

    var progressRatio: Double {
        min(max((percentComplete ?? 0) / 100.0, 0), 1.0)
    }

    func withPercentComplete(_ percentComplete: Double?) -> GoalSummary {
        GoalSummary(
            goalId: goalId,
            goalType: goalType,
            targetValue: targetValue,
            targetUnit: targetUnit,
            targetDate: targetDate,
            startDate: startDate,
            status: status,
            percentComplete: percentComplete
        )
    }

    private static func parseDate(_ s: String) -> Date? {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.date(from: s)
    }
}

struct GoalListResponse: Codable {
    let content: [GoalSummary]
    let pageNumber: Int
    let pageSize: Int
    let totalElements: Int
    let first: Bool
    let last: Bool
}

struct GoalResponse: Codable, Identifiable, Sendable {
    let goalId: Int
    let goalType: GoalType
    let targetValue: Double?
    let targetUnit: String?
    let targetDate: String?
    let startValue: Double?
    let startDate: String?
    let status: GoalStatus
    let weeklyRateTarget: Double?
    let impliedWeeksToGoal: Int?
    let targets: MacroTargets?

    var id: Int { goalId }

    struct MacroTargets: Codable, Sendable {
        let calorieTarget: Int?
        let proteinTargetG: Int?
        let carbTargetG: Int?
        let fatTargetG: Int?
    }
}

// MARK: - Progress Response

struct GoalCheckpointItem: Codable, Sendable {
    let checkpointDate: String
    let actualValue: Double?
    let projectedValue: Double?
    let isOnTrack: Bool?

    var formattedDate: String {
        let parts = checkpointDate.split(separator: "-")
        guard parts.count == 3 else { return checkpointDate }
        return "\(parts[1])월 \(parts[2])일"
    }
}

struct GoalProgressResponse: Codable, Sendable {
    let goalId: Int
    let goalType: GoalType
    let targetValue: Double?
    let targetUnit: String?
    let targetDate: String?
    let startDate: String?
    let startValue: Double?
    let currentValue: Double?
    let percentComplete: Double?
    let daysRemaining: Int?
    let projectedCompletionDate: String?
    let weeklyRateTarget: Double?
    let isOnTrack: Bool
    let trackingStatus: String?
    let trackingColor: String?
    let checkpoints: [GoalCheckpointItem]?

    var progressRatio: Double {
        min(max((percentComplete ?? 0) / 100.0, 0), 1.0)
    }

    var trackingStatusLabel: String {
        switch trackingStatus {
        case "AHEAD":           return "목표 초과 달성 중"
        case "ON_TRACK":        return "순조롭게 진행 중"
        case "SLIGHTLY_BEHIND": return "조금 뒤처지고 있어요"
        case "BEHIND":          return "페이스를 높여야 해요"
        default:                return "진행 중"
        }
    }

    var trackingIcon: String {
        switch trackingStatus {
        case "AHEAD":           return "arrow.up.circle.fill"
        case "ON_TRACK":        return "checkmark.circle.fill"
        case "SLIGHTLY_BEHIND": return "exclamationmark.circle.fill"
        case "BEHIND":          return "xmark.circle.fill"
        default:                return "circle.fill"
        }
    }

    func formattedValue(_ v: Double?) -> String {
        guard let v = displayValue(for: v, unit: targetUnit) else { return "-" }
        let unit = displayUnitText(from: targetUnit)
        return String(format: "%.1f%@", v, unit.isEmpty ? "" : " \(unit)")
    }

    var formattedProjectedDate: String {
        guard let d = projectedCompletionDate else { return "계산 중" }
        let parts = d.split(separator: "-")
        guard parts.count == 3 else { return d }
        return "\(parts[0])년 \(parts[1])월 \(parts[2])일"
    }

    var formattedTargetDate: String {
        guard let d = targetDate else { return "-" }
        let parts = d.split(separator: "-")
        guard parts.count == 3 else { return d }
        return "\(parts[0])년 \(parts[1])월 \(parts[2])일"
    }
}

// MARK: - Request DTOs

struct CreateGoalRequest: Encodable {
    let goalType: String
    let targetValue: Double?
    let targetUnit: String?
    let targetDate: String
    let startValue: Double?
    let weeklyRateTarget: Double?
}

struct UpdateGoalRequest: Encodable {
    let targetDate: String?
    let targetValue: Double?
    let weeklyRateTarget: Double?
}
