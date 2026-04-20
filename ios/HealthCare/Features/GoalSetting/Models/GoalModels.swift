import SwiftUI

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

    var defaultUnit: String {
        switch self {
        case .WEIGHT_LOSS, .MUSCLE_GAIN, .BODY_RECOMPOSITION: return "kg"
        case .ENDURANCE:      return "km"
        case .GENERAL_HEALTH: return ""
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
        let days = Calendar.current.dateComponents([.day], from: .now, to: date).day ?? 0
        return days >= 0 ? days : nil
    }

    var formattedTargetDate: String {
        guard let td = targetDate else { return "-" }
        let parts = td.split(separator: "-")
        guard parts.count == 3 else { return td }
        return "\(parts[0])년 \(parts[1])월 \(parts[2])일"
    }

    var targetText: String {
        guard let v = targetValue, let u = targetUnit, !u.isEmpty else {
            return goalType.displayName
        }
        return String(format: "%.1f %@", v, u)
    }

    var progressRatio: Double {
        min(max((percentComplete ?? 0) / 100.0, 0), 1.0)
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
    let isOnTrack: Bool
    let trackingStatus: String?
    let trackingColor: String?
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
