import SwiftUI

// MARK: - CalorieExplanationView
//
// "왜 N kcal인가요?" — 백엔드 NutritionCalculator와 동일한 식으로
// 사용자의 실제 BMR·TDEE·목표 보정 단계를 보여 준다.
// 프로필이 불완전하면 식 설명만 일반론으로 노출한다.

struct CalorieExplanationView: View {
    let profile: UserProfile
    let goalType: GoalType?

    @Environment(\.dismiss) private var dismiss

    private var breakdown: CalorieBreakdown? {
        CalorieBreakdown(profile: profile, goalType: goalType)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    headerCard
                    if let breakdown {
                        stepCard(
                            index: "1",
                            title: String(localized: "calorie.step.bmr.title"),
                            formula: "Mifflin-St Jeor (1990)\n\(breakdown.bmrFormula)",
                            result: "\(breakdown.bmrText) " + String(localized: "calorie.unit.perDay"),
                            note: String(localized: "calorie.step.bmr.note")
                        )
                        stepCard(
                            index: "2",
                            title: String(localized: "calorie.step.tdee.title"),
                            formula: String(format: String(localized: "calorie.step.tdee.formula"), breakdown.bmrText, breakdown.activityFactorText, breakdown.activityLabel),
                            result: "\(breakdown.tdeeText) " + String(localized: "calorie.unit.perDay"),
                            note: String(localized: "calorie.step.tdee.note")
                        )
                        stepCard(
                            index: "3",
                            title: String(localized: "calorie.step.adjust.title"),
                            formula: breakdown.goalAdjustmentFormula,
                            result: "\(breakdown.adjustedText) " + String(localized: "calorie.unit.perDay"),
                            note: breakdown.goalNote
                        )
                        if breakdown.safetyFloorApplied {
                            stepCard(
                                index: "4",
                                title: String(localized: "calorie.step.safety.title"),
                                formula: String(format: String(localized: "calorie.step.safety.formula"), breakdown.sexIsFemale ? String(localized: "calorie.safety.female") : String(localized: "calorie.safety.male")),
                                result: "\(breakdown.finalText) " + String(localized: "calorie.unit.perDay"),
                                note: nil
                            )
                        }
                        macroCard(breakdown)
                    } else {
                        incompleteProfileCard
                    }
                    sourcesCard
                    disclaimerCard
                    Spacer(minLength: 24)
                }
                .padding(Spacing.xl) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.backgroundPage)
            .navigationTitle(Text("calorie.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(String(localized: "common.close")) { dismiss() }
                        .foregroundStyle(Color.brandPrimary)
                }
            }
        }
    }

    // MARK: - Cards

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "flame.fill")
                    .foregroundStyle(Color.brandAccent)
                Text(String(localized: "calorie.section.title"))
                    .font(.bodyLarge).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
            }
            if let target = profile.calorieTarget, target > 0 {
                HStack(alignment: .lastTextBaseline, spacing: 4) {
                    Text("\(target)")
                        .font(.system(size: 34, weight: .heavy, design: .rounded)) // design-lint:ignore — hero numeric
                        .foregroundStyle(Color.brandAccent)
                    Text(String(localized: "calorie.unit.perDay"))
                        .font(.bodySmall)
                        .foregroundStyle(Color.textSecondary)
                }
            }
            Text(String(localized: "calorie.intro"))
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
                .lineSpacing(3)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }

    private func stepCard(index: String, title: String, formula: String, result: String, note: String?) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Text(index)
                    .font(.captionBold)
                    .foregroundStyle(.white)
                    .frame(width: 22, height: 22)
                    .background(Color.brandAccent)
                    .clipShape(Circle())
                Text(title)
                    .font(.headingSmall)
                    .foregroundStyle(Color.textPrimary)
            }
            Text(formula)
                .font(.bodySmall.monospacedDigit())
                .foregroundStyle(Color.textSecondary)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 6) {
                Image(systemName: "arrow.turn.down.right")
                    .font(.captionXSmall)
                    .foregroundStyle(Color.textTertiary)
                Text(result)
                    .font(.bodyLarge).fontWeight(.bold)
                    .foregroundStyle(Color.brandAccent)
            }
            if let note {
                Text(note)
                    .font(.captionXSmall)
                    .foregroundStyle(Color.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }

    private func macroCard(_ breakdown: CalorieBreakdown) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(String(localized: "calorie.macro.title"))
                .font(.headingSmall)
                .foregroundStyle(Color.textPrimary)
            Text(String(format: String(localized: "calorie.macro.body"), breakdown.proteinPerKgText, breakdown.fatRatioText))
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 12) {
                macroChip(String(localized: "calorie.macro.protein"), value: profile.proteinTargetG, color: .blue)
                macroChip(String(localized: "calorie.macro.carbs"), value: profile.carbTargetG, color: .orange)
                macroChip(String(localized: "calorie.macro.fat"), value: profile.fatTargetG, color: .pink)
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }

    private func macroChip(_ label: String, value: Int?, color: Color) -> some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.captionXSmall)
                .foregroundStyle(Color.textSecondary)
            Text(value.map { "\($0)g" } ?? "-")
                .font(.bodyLarge).fontWeight(.bold)
                .foregroundStyle(color)
        }
        .frame(maxWidth: .infinity)
    }

    private var incompleteProfileCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "person.crop.circle.badge.exclamationmark")
                    .foregroundStyle(Color.brandAccent)
                Text(String(localized: "calorie.profile.title"))
                    .font(.headingSmall)
                    .foregroundStyle(Color.textPrimary)
            }
            Text(String(localized: "calorie.profile.body"))
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
                .lineSpacing(3)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }

    private var sourcesCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(String(localized: "calorie.sources.title"))
                .font(.headingSmall)
                .foregroundStyle(Color.textPrimary)
            Text(String(localized: "calorie.sources.body"))
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }

    private var disclaimerCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Color(hex: "#D97706"))
                Text(String(localized: "calorie.disclaimer.title"))
                    .font(.headingSmall).fontWeight(.bold)
                    .foregroundStyle(Color(hex: "#78350F"))
            }
            Text(String(localized: "calorie.disclaimer.body"))
                .font(.bodySmall)
                .foregroundStyle(Color(hex: "#92400E"))
                .lineSpacing(3)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(hex: "#FEF3C7"))
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
    }
}

// MARK: - CalorieBreakdown (백엔드 NutritionCalculator 미러)

private struct CalorieBreakdown {
    let sexIsFemale: Bool
    let bmr: Double
    let activityFactor: Double
    let activityLabel: String
    let tdee: Double
    let adjusted: Double
    let finalKcal: Int
    let goalType: GoalType?
    let weightKg: Double

    /// 프로필이 불완전(필수 필드 누락)하면 nil.
    init?(profile: UserProfile, goalType: GoalType?) {
        guard
            let sex = profile.sex,
            let dob = profile.dateOfBirth,
            let height = profile.heightCm,
            let weight = profile.weightKg,
            let activity = profile.activityLevel,
            let age = Self.age(from: dob)
        else { return nil }

        let female = sex.uppercased() == "FEMALE"
        let base = 10.0 * weight + 6.25 * height - 5.0 * Double(age)
        let bmr: Double = switch sex.uppercased() {
        case "MALE":   base + 5
        case "FEMALE": base - 161
        default:       base - 78
        }
        let (factor, label) = Self.activity(activity)
        let tdee = bmr * factor
        let adjusted = tdee + Self.goalDelta(goalType)
        let floor = female ? 1_200.0 : 1_500.0
        let finalKcal = Int(max(adjusted.rounded(), floor))

        self.sexIsFemale = female
        self.bmr = bmr
        self.activityFactor = factor
        self.activityLabel = label
        self.tdee = tdee
        self.adjusted = adjusted
        self.finalKcal = finalKcal
        self.goalType = goalType
        self.weightKg = weight
    }

    // MARK: 표시 텍스트

    var bmrText: String { "\(Int(bmr.rounded()))" }
    var tdeeText: String { "\(Int(tdee.rounded()))" }
    var adjustedText: String { "\(Int(adjusted.rounded()))" }
    var finalText: String { "\(finalKcal)" }
    var activityFactorText: String { String(format: "%.3f", activityFactor) }

    var bmrFormula: String {
        sexIsFemale
            ? String(localized: "calorie.bmrFormula.female")
            : String(localized: "calorie.bmrFormula.male")
    }

    var goalAdjustmentFormula: String {
        let delta = Self.goalDelta(goalType)
        guard delta != 0 else {
            return String(format: String(localized: "calorie.adjust.maintain"), goalType?.displayName ?? String(localized: "calorie.goal.default"))
        }
        let sign = delta > 0 ? "+" : "−"
        return "\(goalType?.displayName ?? String(localized: "calorie.goal.label")) → \(tdeeText) \(sign) \(Int(abs(delta)))"
    }

    var goalNote: String {
        switch goalType {
        case .WEIGHT_LOSS:        String(localized: "calorie.goal.note.weightLoss")
        case .MUSCLE_GAIN:        String(localized: "calorie.goal.note.muscleGain")
        case .BODY_RECOMPOSITION: String(localized: "calorie.goal.note.recomp")
        case .ENDURANCE:          String(localized: "calorie.goal.note.endurance")
        case .GENERAL_HEALTH, .none: String(localized: "calorie.goal.note.health")
        }
    }

    var safetyFloorApplied: Bool { Int(adjusted.rounded()) < finalKcal }

    var proteinPerKgText: String {
        let v: Double = switch goalType {
        case .WEIGHT_LOSS:                          2.0
        case .MUSCLE_GAIN, .BODY_RECOMPOSITION:     1.8
        case .ENDURANCE:                            1.4
        case .GENERAL_HEALTH, .none:                1.2
        }
        return String(format: "%g", v)
    }

    var fatRatioText: String {
        switch goalType {
        case .GENERAL_HEALTH, .none: "30"
        default:                     "25"
        }
    }

    // MARK: 헬퍼

    private static func age(from dob: String) -> Int? {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        guard let date = formatter.date(from: dob) else { return nil }
        return Calendar.current.dateComponents([.year], from: date, to: Date()).year
    }

    private static func activity(_ level: String) -> (Double, String) {
        switch level.uppercased() {
        case "SEDENTARY":         (1.2, String(localized: "calorie.activity.sedentary"))
        case "LIGHTLY_ACTIVE":    (1.375, String(localized: "calorie.activity.lightly"))
        case "MODERATELY_ACTIVE": (1.55, String(localized: "calorie.activity.moderately"))
        case "VERY_ACTIVE":       (1.725, String(localized: "calorie.activity.very"))
        case "EXTRA_ACTIVE":      (1.9, String(localized: "calorie.activity.extra"))
        default:                  (1.2, String(localized: "calorie.activity.sedentary"))
        }
    }

    private static func goalDelta(_ goalType: GoalType?) -> Double {
        switch goalType {
        case .WEIGHT_LOSS:        -500
        case .MUSCLE_GAIN:        300
        case .BODY_RECOMPOSITION: -200
        case .ENDURANCE:          200
        case .GENERAL_HEALTH, .none: 0
        }
    }
}
