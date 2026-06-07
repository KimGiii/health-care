import SwiftUI

private func displayUnitText(from rawUnit: String?) -> String {
    switch rawUnit {
    case "pct":     return "%"
    case "minutes", "seconds": return String(localized: "goal.unit.minutes")
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
        case .WEIGHT_LOSS:        return String(localized: "goal.type.weightLoss")
        case .MUSCLE_GAIN:        return String(localized: "goal.type.muscleGain")
        case .BODY_RECOMPOSITION: return String(localized: "goal.type.recomp")
        case .ENDURANCE:          return String(localized: "goal.type.endurance")
        case .GENERAL_HEALTH:     return String(localized: "goal.type.health")
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
        case .WEIGHT_LOSS, .MUSCLE_GAIN: return String(localized: "goal.unit.kgPerWeek")
        case .BODY_RECOMPOSITION:        return String(localized: "goal.unit.pctPerWeek")
        case .ENDURANCE:                 return String(localized: "goal.unit.minPerWeek")
        case .GENERAL_HEALTH:            return ""
        }
    }

    var description: String {
        switch self {
        case .WEIGHT_LOSS:        return String(localized: "goal.type.weightLoss.desc")
        case .MUSCLE_GAIN:        return String(localized: "goal.type.muscleGain.desc")
        case .BODY_RECOMPOSITION: return String(localized: "goal.type.recomp.desc")
        case .ENDURANCE:          return String(localized: "goal.type.endurance.desc")
        case .GENERAL_HEALTH:     return String(localized: "goal.type.health.desc")
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
        case .ACTIVE:    return String(localized: "goal.status.active")
        case .COMPLETED: return String(localized: "goal.status.completed")
        case .ABANDONED: return String(localized: "goal.status.abandoned")
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
        let parser = DateFormatter()
        parser.dateFormat = "yyyy-MM-dd"
        parser.locale = Locale(identifier: "en_US_POSIX")
        guard let date = parser.date(from: td) else { return td }
        let display = DateFormatter()
        display.locale = LocaleManager.resolvedLocale
        display.dateFormat = String(localized: "goal.date.long.format")
        return display.string(from: date)
    }

    var targetText: String {
        let displayUnit = displayUnitText(from: targetUnit)
        guard let v = displayValue(for: targetValue, unit: targetUnit), !displayUnit.isEmpty else {
            return goalType.displayName
        }
        return String(format: String(localized: "goal.value.format"), v, displayUnit)
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
    /// "시작" — 목표 생성 시 자동 생성된 시작 체크포인트, nil — 주간(일요일) 자동 체크포인트.
    let notes: String?

    var isStartingPoint: Bool {
        notes == "시작" || notes == "Start"
    }

    var formattedDate: String {
        let parser = DateFormatter()
        parser.dateFormat = "yyyy-MM-dd"
        parser.locale = Locale(identifier: "en_US_POSIX")
        guard let date = parser.date(from: checkpointDate) else { return checkpointDate }
        let display = DateFormatter()
        display.locale = LocaleManager.resolvedLocale
        display.dateFormat = String(localized: "goal.date.short.format")
        return display.string(from: date)
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
        case "AHEAD":           return String(localized: "goal.tracking.ahead")
        case "ON_TRACK":        return String(localized: "goal.tracking.onTrack")
        case "SLIGHTLY_BEHIND": return String(localized: "goal.tracking.slightlyBehind")
        case "BEHIND":          return String(localized: "goal.tracking.behind")
        default:                return String(localized: "goal.tracking.default")
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
        guard let d = projectedCompletionDate else { return String(localized: "goal.progress.calculating") }
        return Self.formatLongDate(d)
    }

    var formattedTargetDate: String {
        guard let d = targetDate else { return "-" }
        return Self.formatLongDate(d)
    }

    static func formatLongDate(_ s: String) -> String {
        let parser = DateFormatter()
        parser.dateFormat = "yyyy-MM-dd"
        parser.locale = Locale(identifier: "en_US_POSIX")
        guard let date = parser.date(from: s) else { return s }
        let display = DateFormatter()
        display.locale = LocaleManager.resolvedLocale
        display.dateFormat = String(localized: "goal.date.long.format")
        return display.string(from: date)
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
