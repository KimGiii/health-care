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
                            title: "기초대사량 (BMR)",
                            formula: "Mifflin-St Jeor (1990)\n\(breakdown.bmrFormula)",
                            result: "\(breakdown.bmrText) kcal/일",
                            note: "가만히 있어도 생명 유지에 쓰이는 최소 에너지입니다."
                        )
                        stepCard(
                            index: "2",
                            title: "활동대사량 (TDEE)",
                            formula: "BMR × 활동계수\n\(breakdown.bmrText) × \(breakdown.activityFactorText) (\(breakdown.activityLabel))",
                            result: "\(breakdown.tdeeText) kcal/일",
                            note: "활동량까지 반영한 하루 총 소비 에너지입니다."
                        )
                        stepCard(
                            index: "3",
                            title: "목표 보정",
                            formula: breakdown.goalAdjustmentFormula,
                            result: "\(breakdown.adjustedText) kcal/일",
                            note: breakdown.goalNote
                        )
                        if breakdown.safetyFloorApplied {
                            stepCard(
                                index: "4",
                                title: "안전 하한 적용",
                                formula: "건강을 위한 최소 섭취량(\(breakdown.sexIsFemale ? "여성 1,200" : "남성·기타 1,500") kcal) 미만으로 내려가지 않도록 보정했습니다.",
                                result: "\(breakdown.finalText) kcal/일",
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
            .navigationTitle("권장 칼로리 계산")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") { dismiss() }
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
                Text("권장 칼로리 산출 방식")
                    .font(.bodyLarge).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
            }
            if let target = profile.calorieTarget, target > 0 {
                HStack(alignment: .lastTextBaseline, spacing: 4) {
                    Text("\(target)")
                        .font(.system(size: 34, weight: .heavy, design: .rounded)) // design-lint:ignore — hero numeric
                        .foregroundStyle(Color.brandAccent)
                    Text("kcal/일")
                        .font(.bodySmall)
                        .foregroundStyle(Color.textSecondary)
                }
            }
            Text("아래 4단계로 회원님의 프로필과 목표에 맞춰 자동 계산한 값입니다. 프로필이나 목표를 바꾸면 다시 계산됩니다.")
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
            Text("매크로 분배")
                .font(.headingSmall)
                .foregroundStyle(Color.textPrimary)
            Text("총 칼로리를 단백질 → 지방 → 탄수화물 순으로 배분합니다.\n• 단백질: 체중 1kg당 \(breakdown.proteinPerKgText)g\n• 지방: 총 칼로리의 \(breakdown.fatRatioText)%\n• 탄수화물: 나머지 칼로리")
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 12) {
                macroChip("단백질", value: profile.proteinTargetG, color: .blue)
                macroChip("탄수화물", value: profile.carbTargetG, color: .orange)
                macroChip("지방", value: profile.fatTargetG, color: .pink)
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
                Text("프로필을 완성해 주세요")
                    .font(.headingSmall)
                    .foregroundStyle(Color.textPrimary)
            }
            Text("성별·생년월일·키·체중·활동 수준이 모두 입력되면 회원님 맞춤 권장 칼로리를 Mifflin-St Jeor 공식으로 계산해 드립니다. 현재는 기본값(2,000 kcal)을 사용합니다.")
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
            Text("계산식 출처")
                .font(.headingSmall)
                .foregroundStyle(Color.textPrimary)
            Text("• BMR: Mifflin-St Jeor 공식 (1990) — 미국영양학회(ADA)·대한비만학회 표준\n• 활동계수: Harris-Benedict 활동 수준 표\n• 매크로: ACSM·국제스포츠영양학회(ISSN) 가이드라인")
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
                Text("참고용 안내")
                    .font(.headingSmall).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
            }
            Text("계산값은 일반적인 추정치이며 개인의 건강 상태에 따라 달라질 수 있습니다. 식이 조절이 필요한 경우 의료 전문가와 상의하시기 바랍니다.")
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
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
            ? "10×체중 + 6.25×키 − 5×나이 − 161"
            : "10×체중 + 6.25×키 − 5×나이 + 5"
    }

    var goalAdjustmentFormula: String {
        let delta = Self.goalDelta(goalType)
        guard delta != 0 else {
            return "현재 목표(\(goalType?.displayName ?? "유지"))는 활동대사량을 그대로 유지합니다."
        }
        let sign = delta > 0 ? "+" : "−"
        return "\(goalType?.displayName ?? "목표") → \(tdeeText) \(sign) \(Int(abs(delta)))"
    }

    var goalNote: String {
        switch goalType {
        case .WEIGHT_LOSS:        "주 약 0.5kg 감량을 위해 500 kcal 적자를 둡니다."
        case .MUSCLE_GAIN:        "근육 증가(린벌크)를 위해 300 kcal를 더합니다."
        case .BODY_RECOMPOSITION: "체형 개선을 위해 가벼운 200 kcal 적자를 둡니다."
        case .ENDURANCE:          "글리코겐 회복을 위해 200 kcal를 더합니다."
        case .GENERAL_HEALTH, .none: "활성 목표가 없거나 전반적 건강이면 유지 칼로리를 사용합니다."
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
        case "SEDENTARY":         (1.2, "비활동적")
        case "LIGHTLY_ACTIVE":    (1.375, "가볍게 활동")
        case "MODERATELY_ACTIVE": (1.55, "보통 활동")
        case "VERY_ACTIVE":       (1.725, "활발히 활동")
        case "EXTRA_ACTIVE":      (1.9, "매우 활발")
        default:                  (1.2, "비활동적")
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
