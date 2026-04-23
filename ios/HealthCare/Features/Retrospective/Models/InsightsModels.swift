import Foundation

// MARK: - Weekly Summary

struct WeeklySummaryResponse: Codable, Sendable {
    let weekStart: String
    let weekEnd: String
    let weekOffset: Int

    let exerciseSessionCount: Int
    let totalExerciseMinutes: Int
    let totalCaloriesBurned: Double?

    let dietLogCount: Int
    let avgDailyCalories: Double?
    let avgDailyProteinG: Double?

    let latestWeightKg: Double?
    let latestBodyFatPct: Double?
    let weightChangeKg: Double?

    let activeGoalPercentComplete: Double?
    let activeGoalTrackingStatus: String?

    var formattedWeekRange: String {
        let parts1 = weekStart.split(separator: "-")
        let parts2 = weekEnd.split(separator: "-")
        guard parts1.count == 3, parts2.count == 3 else { return "\(weekStart) ~ \(weekEnd)" }
        return "\(parts1[1])월 \(parts1[2])일 ~ \(parts2[1])월 \(parts2[2])일"
    }

    var weightChangeSummary: String {
        guard let change = weightChangeKg else { return "-" }
        let sign = change >= 0 ? "+" : ""
        return String(format: "%@%.1f kg", sign, change)
    }

    var weightChangeColor: String {
        guard let change = weightChangeKg else { return "neutral" }
        return change < 0 ? "positive" : (change > 0 ? "negative" : "neutral")
    }
}

// MARK: - Change Analysis

struct ChangeAnalysisResponse: Codable, Sendable {
    let fromDate: String
    let toDate: String

    let weightChangeKg: Double?
    let bodyFatPctChange: Double?
    let muscleMassChangeKg: Double?
    let bmiChange: Double?
    let waistChangeCm: Double?
    let chestChangeCm: Double?

    let exerciseSessionCount: Int
    let totalExerciseMinutes: Int

    let fromSnapshot: BodySnapshot?
    let toSnapshot: BodySnapshot?

    struct BodySnapshot: Codable, Sendable {
        let measuredAt: String
        let weightKg: Double?
        let bodyFatPct: Double?
        let muscleMassKg: Double?
        let bmi: Double?
        let waistCm: Double?
        let chestCm: Double?
    }

    func formattedDelta(_ value: Double?, unit: String, positiveIsGood: Bool) -> (text: String, isPositive: Bool?) {
        guard let v = value else { return ("-", nil) }
        let sign = v >= 0 ? "+" : ""
        let text = String(format: "%@%.1f %@", sign, v, unit)
        if v == 0 { return (text, nil) }
        let isPositive = positiveIsGood ? v > 0 : v < 0
        return (text, isPositive)
    }
}
