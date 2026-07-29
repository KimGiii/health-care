import SwiftUI
import UIKit

struct DietRecommendationView: View {
    @StateObject private var viewModel = DietRecommendationViewModel()
    @EnvironmentObject private var container: AppContainer
    @State private var showDisclaimer = false
    @State private var safetyBadgeVisible = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                configCard
                if viewModel.isLoading {
                    loadingView
                } else if let result = viewModel.result {
                    resultSection(result)
                } else {
                    emptyState
                }
            }
            .padding(Spacing.xl)
        }
        .background(Color.backgroundPage)
        .navigationTitle(Text("recommend.title"))
        .navigationBarTitleDisplayMode(.large)
        .alert(Text("common.error.title"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("common.ok") {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .sheet(isPresented: $viewModel.showFeedbackSheet) {
            RecommendationFeedbackSheet { reason in
                Task { await viewModel.requestRefresh(reason: reason, apiClient: container.apiClient) }
            }
            .presentationDetents([.medium])
        }
    }

    // MARK: - Config Card

    private var configCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            datePicker
            Divider()
            mealTypePicker
            Divider()
            strictModeToggle
            recommendButton
        }
        .padding(Spacing.lg)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .elevation(.low)
    }

    private var datePicker: some View {
        HStack {
            Label {
                Text("recommend.date")
                    .font(.subheadline).fontWeight(.medium)
                    .foregroundStyle(Color.textPrimary)
            } icon: {
                Image(systemName: "calendar")
                    .foregroundStyle(Color.brandPrimary)
            }
            Spacer()
            DatePicker("", selection: $viewModel.selectedDate, displayedComponents: .date)
                .labelsHidden()
        }
    }

    private var mealTypePicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("recommend.meals")
                .font(.subheadline).fontWeight(.medium)
                .foregroundStyle(Color.textPrimary)
            HStack(spacing: Spacing.sm) {
                ForEach(viewModel.orderedMeals, id: \.self) { meal in
                    let selected = viewModel.selectedMeals.contains(meal)
                    Button {
                        viewModel.toggleMeal(meal)
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: meal.sfSymbol)
                                .font(.system(size: 16)) // design-lint:ignore
                            Text(meal.displayName)
                                .font(.caption2).fontWeight(.medium)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.sm)
                        .background(selected ? Color.brandPrimary : Color.surfaceSecondary)
                        .foregroundStyle(selected ? Color.white : Color.textSecondary)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                    }
                }
            }
        }
    }

    private var strictModeToggle: some View {
        Toggle(isOn: $viewModel.strictAllergyMode) {
            VStack(alignment: .leading, spacing: 2) {
                Text("recommend.strictMode")
                    .font(.subheadline).fontWeight(.medium)
                    .foregroundStyle(Color.textPrimary)
                Text("recommend.strictMode.desc")
                    .font(.caption)
                    .foregroundStyle(Color.textSecondary)
            }
        }
        .tint(Color.brandDanger)
    }

    private var recommendButton: some View {
        Button {
            Task { await viewModel.recommend(apiClient: container.apiClient) }
        } label: {
            Text("recommend.cta")
                .font(.subheadline).fontWeight(.semibold)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.md)
                .background(viewModel.canRecommend ? Color.brandPrimary : Color.surfaceSecondary)
                .foregroundStyle(viewModel.canRecommend ? Color.white : Color.textSecondary)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
        }
        .disabled(!viewModel.canRecommend || viewModel.isLoading)
    }

    // MARK: - Loading

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView()
                .scaleEffect(1.2)
            Text("recommend.loading")
                .font(.subheadline)
                .foregroundStyle(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.xxxl)
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "fork.knife.circle")
                .font(.system(size: 44)) // design-lint:ignore
                .foregroundStyle(Color.brandAccent)
            Text("recommend.empty")
                .font(.subheadline)
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.xxxl)
    }

    // MARK: - Result

    @ViewBuilder
    private func resultSection(_ result: DailyDietRecommendationResponse) -> some View {
        targetsCard(result.targets)
        if result.failureReason != nil {
            failureCard(result)
        }
        if !result.meals.isEmpty {
            ForEach(result.meals) { meal in
                mealCard(meal)
            }
            totalsCard(result)
            if let rationale = result.rationale {
                rationaleCard(rationale)
            }
            if !result.appliedRestrictions.isEmpty {
                safetyBadge(result.appliedRestrictions)
            }
        }
        if !result.alternatives.isEmpty {
            alternativesSection(result.alternatives, primaryMeals: result.meals)
        }
        if result.snapshotId != nil {
            refreshButton
        }
        disclaimerCard(result.disclaimer)
    }

    private func targetsCard(_ t: NutritionTargets) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("recommend.targets")
                .font(.subheadline).fontWeight(.semibold)
                .foregroundStyle(Color.textPrimary)
            HStack(spacing: 0) {
                nutrientPill(label: String(localized: "nutrition.kcal"), value: String(format: "%.0f", t.targetKcal), unit: "kcal", color: .brandPrimary)
                Spacer()
                nutrientPill(label: String(localized: "nutrition.protein"), value: String(format: "%.0f", t.proteinG), unit: "g", color: .brandAccent)
                Spacer()
                nutrientPill(label: String(localized: "nutrition.carbs"), value: String(format: "%.0f", t.carbsG), unit: "g", color: .brandSunrise)
                Spacer()
                nutrientPill(label: String(localized: "nutrition.fat"), value: String(format: "%.0f", t.fatG), unit: "g", color: .brandEmber)
            }
        }
        .padding(Spacing.lg)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .elevation(.low)
    }

    private func nutrientPill(label: String, value: String, unit: String, color: Color) -> some View {
        VStack(spacing: 4) {
            Text(label)
                .font(.caption2).fontWeight(.medium)
                .foregroundStyle(Color.textSecondary)
            Text(value)
                .font(.headline).fontWeight(.bold)
                .foregroundStyle(color)
            Text(unit)
                .font(.caption2)
                .foregroundStyle(Color.textSecondary)
        }
        .frame(minWidth: 60)
    }

    private func mealCard(_ meal: RecommendedMeal) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label {
                    Text(meal.mealType.displayName)
                        .font(.subheadline).fontWeight(.semibold)
                        .foregroundStyle(Color.textPrimary)
                } icon: {
                    Image(systemName: meal.mealType.sfSymbol)
                        .foregroundStyle(Color.brandPrimary)
                }
                Spacer()
                Text(String(format: "%.0f kcal", meal.totalCalories))
                    .font(.caption).fontWeight(.medium)
                    .foregroundStyle(Color.textSecondary)
                saveMealButton(meal)
            }
            ForEach(meal.items) { item in
                foodRow(item)
            }
        }
        .padding(Spacing.lg)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .elevation(.low)
    }

    private func saveMealButton(_ meal: RecommendedMeal) -> some View {
        let isSaving = viewModel.isSaving(meal)
        let isSaved = viewModel.isSaved(meal)
        let isDisabled = isSaving || isSaved || meal.items.isEmpty
        let usesDisabledStyle = isSaved || meal.items.isEmpty

        return Button {
            Task { await viewModel.saveMeal(meal, apiClient: container.apiClient) }
        } label: {
            HStack(spacing: 4) {
                if isSaving {
                    ProgressView()
                        .controlSize(.small)
                        .tint(Color.white)
                } else {
                    Image(systemName: isSaved ? "checkmark.circle.fill" : "plus.circle.fill")
                        .font(.captionBold)
                }
                Text(saveMealButtonTitle(isSaving: isSaving, isSaved: isSaved))
                    .lineLimit(1)
            }
            .font(.caption2).fontWeight(.semibold)
            .padding(.horizontal, 10) // design-lint:ignore
            .padding(.vertical, Spacing.sm)
            .background(usesDisabledStyle ? Color.surfaceSecondary : Color.brandPrimary)
            .foregroundStyle(usesDisabledStyle ? Color.textSecondary : Color.white)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
    }

    private func saveMealButtonTitle(isSaving: Bool, isSaved: Bool) -> String {
        if isSaving { return String(localized: "recommend.saving") }
        if isSaved { return String(localized: "recommend.saved") }
        return String(localized: "recommend.save")
    }

    private func foodRow(_ item: RecommendedFoodEntry) -> some View {
        HStack(spacing: 10) {
            if item.needsCaution {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(Color.brandWarning)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(item.displayName)
                    .font(.subheadline)
                    .foregroundStyle(Color.textPrimary)
                Text("\(item.displayServing) · " + String(format: "%.0f kcal", item.calories))
                    .font(.caption)
                    .foregroundStyle(Color.textSecondary)
                if let caution = item.caution, !caution.isEmpty {
                    Text(caution)
                        .font(.caption2)
                        .foregroundStyle(Color.brandWarning)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(String(format: "P %.0fg", item.proteinG))
                    .font(.caption2)
                    .foregroundStyle(Color.brandAccent)
                Text(String(format: "C %.0fg", item.carbsG))
                    .font(.caption2)
                    .foregroundStyle(Color.brandSunrise)
            }
        }
        .padding(10) // design-lint:ignore
        .background(Color.backgroundPage)
        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
    }

    private func totalsCard(_ result: DailyDietRecommendationResponse) -> some View {
        HStack {
            Text("recommend.total")
                .font(.subheadline).fontWeight(.semibold)
                .foregroundStyle(Color.textPrimary)
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(String(format: "%.0f kcal", result.totalNutrients.totalCalories))
                    .font(.subheadline).fontWeight(.bold)
                    .foregroundStyle(Color.brandPrimary)
                Text(String(format: "P%.0f · C%.0f · F%.0f (g)",
                     result.totalNutrients.totalProteinG,
                     result.totalNutrients.totalCarbsG,
                     result.totalNutrients.totalFatG))
                    .font(.caption)
                    .foregroundStyle(Color.textSecondary)
            }
        }
        .padding(Spacing.lg)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .elevation(.low)
    }

    // MARK: - 실패 카드 (#95)
    // failureCode별 원인 메시지 + 회복 액션 1개를 짝으로 제공한다. "실패"라 쓰지 않고,
    // 색 이외 신호(텍스트 라벨)를 병기한다. 안전·목표 절대제약 완화는 제안하지 않는다.

    /// 회복 액션 — VM이 실제로 지원하는 레버만 사용(재시도 / 간식 포함 / 안전 모드 끄기).
    private enum RecoveryAction {
        case retry
        case includeSnack
        case disableStrict

        var label: String {
            switch self {
            case .retry:        return String(localized: "recommend.failure.action.retry", defaultValue: "다시 시도하기")
            case .includeSnack: return String(localized: "recommend.failure.action.snack", defaultValue: "간식을 포함해 다시 찾기")
            case .disableStrict:return String(localized: "recommend.failure.action.unstrict", defaultValue: "안전 모드를 끄고 다시 찾기")
            }
        }
    }

    private func failureCard(_ result: DailyDietRecommendationResponse) -> some View {
        let action = recoveryAction(for: result.failureCode)
        return VStack(alignment: .leading, spacing: Spacing.md) {
            // 색 이외 신호 — 아이콘 + 짧은 텍스트 라벨
            HStack(spacing: Spacing.sm) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Color.brandWarning)
                    .font(.subheadline)
                Text(failureLabel(result.failureCode))
                    .font(.footnote).fontWeight(.semibold)
                    .foregroundStyle(Color.textSecondary)
                    .textCase(.uppercase)
                    .tracking(0.5)
            }

            Text(failureMessage(result))
                .font(.subheadline)
                .foregroundStyle(Color.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            if let action {
                Button {
                    perform(action)
                } label: {
                    HStack(spacing: 4) {
                        Text(action.label)
                            .font(.subheadline).fontWeight(.semibold)
                        Image(systemName: "arrow.clockwise")
                            .font(.caption).fontWeight(.semibold)
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
                    .background(Color.brandPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.md))
                }
                .disabled(viewModel.isLoading)
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.brandWarning.opacity(0.1))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.md)
                .stroke(Color.brandWarning.opacity(0.4), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
    }

    private func failureLabel(_ code: String?) -> String {
        switch code {
        case "CALORIE_PROTEIN_CONFLICT":          return String(localized: "recommend.failure.label.conflict", defaultValue: "조건 충돌")
        case "ALLERGEN_VERIFIED_POOL_INSUFFICIENT": return String(localized: "recommend.failure.label.pool", defaultValue: "후보 부족")
        case "SERVING_OPTIONS_INFEASIBLE":        return String(localized: "recommend.failure.label.serving", defaultValue: "조합 없음")
        case "REMAINING_MEALS_INFEASIBLE":        return String(localized: "recommend.failure.label.meals", defaultValue: "끼니 부족")
        case "SEARCH_LIMIT_REACHED":              return String(localized: "recommend.failure.label.search", defaultValue: "탐색 한도")
        case "NUTRIENT_DATA_INCOMPLETE":          return String(localized: "recommend.failure.label.data", defaultValue: "데이터 부족")
        case "TARGET_PROFILE_MISSING":            return String(localized: "recommend.failure.label.profile", defaultValue: "프로필 필요")
        default:                                   return String(localized: "recommend.failure.label.generic", defaultValue: "추천 실패")
        }
    }

    private func failureMessage(_ result: DailyDietRecommendationResponse) -> String {
        switch result.failureCode {
        case "CALORIE_PROTEIN_CONFLICT":
            return String(localized: "recommend.failure.msg.conflict", defaultValue: "지금 목표로는 칼로리와 단백질을 같이 맞추기 어려워요.")
        case "ALLERGEN_VERIFIED_POOL_INSUFFICIENT":
            return String(localized: "recommend.failure.msg.pool", defaultValue: "제외한 재료가 많아 추천할 후보가 부족해요.")
        case "SERVING_OPTIONS_INFEASIBLE":
            return String(localized: "recommend.failure.msg.serving", defaultValue: "제공량 단위가 딱 맞는 조합을 찾지 못했어요.")
        case "REMAINING_MEALS_INFEASIBLE":
            return String(localized: "recommend.failure.msg.meals", defaultValue: "남은 끼니만으로 목표를 채우기 어려워요.")
        case "SEARCH_LIMIT_REACHED":
            return String(localized: "recommend.failure.msg.search", defaultValue: "조합을 더 찾지 못했어요.")
        case "NUTRIENT_DATA_INCOMPLETE":
            return String(localized: "recommend.failure.msg.data", defaultValue: "영양 정보가 부족한 항목이 있어 조합을 만들지 못했어요.")
        case "TARGET_PROFILE_MISSING":
            return String(localized: "recommend.failure.msg.profile", defaultValue: "추천에 필요한 프로필 정보가 부족해요. 마이페이지에서 키와 몸무게를 확인해 주세요.")
        default:
            // 알 수 없는 코드 — 서버 사유 문구로 폴백
            return result.failureReason ?? String(localized: "recommend.failure.msg.generic", defaultValue: "지금은 조건에 맞는 식단을 찾지 못했어요.")
        }
    }

    /// failureCode + 현재 설정 기준으로 실제 의미 있는 회복 액션 1개를 고른다.
    /// 프로필 부족(TARGET_PROFILE_MISSING)은 재시도로 해결되지 않으므로 CTA를 주지 않는다.
    private func recoveryAction(for code: String?) -> RecoveryAction? {
        let hasSnack = viewModel.selectedMeals.contains(.SNACK)
        switch code {
        case "CALORIE_PROTEIN_CONFLICT",
             "SERVING_OPTIONS_INFEASIBLE",
             "REMAINING_MEALS_INFEASIBLE":
            return hasSnack ? .retry : .includeSnack
        case "ALLERGEN_VERIFIED_POOL_INSUFFICIENT":
            return viewModel.strictAllergyMode ? .disableStrict : .retry
        case "SEARCH_LIMIT_REACHED", "NUTRIENT_DATA_INCOMPLETE":
            return .retry
        case "TARGET_PROFILE_MISSING":
            return nil
        default:
            return .retry
        }
    }

    private func perform(_ action: RecoveryAction) {
        switch action {
        case .retry:
            break
        case .includeSnack:
            viewModel.selectedMeals.insert(.SNACK)
        case .disableStrict:
            viewModel.strictAllergyMode = false
        }
        Task { await viewModel.recommend(apiClient: container.apiClient) }
    }

    private func rationaleCard(_ rationale: RecommendationRationale) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "checkmark.seal.fill")
                    .foregroundStyle(Color.brandAccent)
                    .font(.subheadline)
                Text("recommend.rationale.title")
                    .font(.subheadline).fontWeight(.semibold)
                    .foregroundStyle(Color.textPrimary)
            }
            Text(rationale.note)
                .font(.footnote)
                .foregroundStyle(Color.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: Spacing.md) {
                if let headroom = rationale.calorieHeadroom {
                    rationaleStat(
                        label: String(localized: "recommend.rationale.calorieHeadroom"),
                        value: String(format: "%.0f kcal", headroom))
                }
                if let gap = rationale.proteinGap {
                    rationaleStat(
                        label: String(localized: "recommend.rationale.proteinGap"),
                        value: String(format: "%+.0f g", gap))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14) // design-lint:ignore
        .background(Color.brandAccent.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
    }

    private func rationaleStat(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundStyle(Color.textSecondary)
            Text(value)
                .font(.caption).fontWeight(.semibold)
                .foregroundStyle(Color.textPrimary)
        }
    }

    // MARK: - 대안 조합 (#96)
    // DisclosureGroup(펼쳐야 보임) 폐기 → primary 대비 차이(단백질·칼로리)를 앞세운
    // 평면 카드로 한눈 비교. 데이터에서 도출 가능한 델타만 라벨로(조리 난이도 등 미보유값 금지).

    private func alternativesSection(_ alts: [RecommendationSolution], primaryMeals: [RecommendedMeal]) -> some View {
        let primaryCal = mealsCalories(primaryMeals)
        let primaryProtein = mealsProtein(primaryMeals)
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .foregroundStyle(Color.brandAccent)
                Text("recommend.alternatives.title")
                    .font(.subheadline).fontWeight(.semibold)
                    .foregroundStyle(Color.textPrimary)
            }
            ForEach(Array(alts.enumerated()), id: \.offset) { index, solution in
                alternativeCard(
                    solution: solution,
                    altIndex: index,
                    primaryCal: primaryCal,
                    primaryProtein: primaryProtein
                )
            }
        }
    }

    private func alternativeCard(
        solution: RecommendationSolution,
        altIndex: Int,
        primaryCal: Double,
        primaryProtein: Double
    ) -> some View {
        let proteinDelta = mealsProtein(solution.meals) - primaryProtein
        let calorieDelta = mealsCalories(solution.meals) - primaryCal
        return VStack(alignment: .leading, spacing: Spacing.sm) {
            // 차이 라벨 — 색 이외에 부호(+/-) 텍스트로도 방향을 전달
            HStack(spacing: 6) {
                if abs(proteinDelta) >= 1 {
                    diffChip(signedGram(proteinDelta, unit: "단백질"), tone: proteinDelta > 0 ? .up : .down)
                }
                if abs(calorieDelta) >= 5 {
                    diffChip(signedKcal(calorieDelta), tone: .neutral)
                }
                if abs(proteinDelta) < 1 && abs(calorieDelta) < 5 {
                    diffChip(String(localized: "recommend.alternative.diffNone", defaultValue: "구성이 달라요"), tone: .neutral)
                }
                Spacer()
            }

            // 이 조합에 담긴 식품 미리보기(펼치지 않고 바로 보임)
            Text(foodSummary(solution.meals))
                .font(.caption)
                .foregroundStyle(Color.textSecondary)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)

            applyAlternativeButton(altIndex: altIndex)
        }
        .padding(14) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .elevation(.low)
    }

    private enum DiffTone { case up, down, neutral }

    private func diffChip(_ text: String, tone: DiffTone) -> some View {
        let color: Color = {
            switch tone {
            case .up:      return Color.brandAccent
            case .down:    return Color.brandWarning
            case .neutral: return Color.textSecondary
            }
        }()
        return Text(text)
            .font(.caption2).fontWeight(.semibold)
            .foregroundStyle(color)
            .padding(.horizontal, Spacing.sm)
            .padding(.vertical, 4) // design-lint:ignore — micro/hero spacing
            .background(color.opacity(0.12))
            .clipShape(Capsule())
    }

    private func applyAlternativeButton(altIndex: Int) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                viewModel.applyAlternative(at: altIndex, apiClient: container.apiClient)
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.labelSmall)
                Text("recommend.alternative.apply")
                    .font(.caption).fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.sm)
            .background(Color.brandAccent.opacity(0.12))
            .foregroundStyle(Color.brandAccent)
            .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isLoading)
    }

    // MARK: - 대안 비교 헬퍼

    private func mealsCalories(_ meals: [RecommendedMeal]) -> Double {
        meals.flatMap(\.items).reduce(0) { $0 + $1.calories }
    }

    private func mealsProtein(_ meals: [RecommendedMeal]) -> Double {
        meals.flatMap(\.items).reduce(0) { $0 + $1.proteinG }
    }

    private func foodSummary(_ meals: [RecommendedMeal]) -> String {
        meals.flatMap(\.items).prefix(5).map(\.displayName).joined(separator: " · ")
    }

    private func signedGram(_ delta: Double, unit: String) -> String {
        let n = Int(delta.rounded())
        return "\(unit) \(n > 0 ? "+" : "")\(n)g"
    }

    private func signedKcal(_ delta: Double) -> String {
        let n = Int(delta.rounded())
        return "\(n > 0 ? "+" : "")\(n) kcal"
    }

    private var refreshButton: some View {
        Button {
            viewModel.showFeedbackSheet = true
        } label: {
            HStack(spacing: 6) {
                if viewModel.isSendingFeedback {
                    ProgressView().controlSize(.small).tint(Color.brandAccent)
                } else {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .font(.labelSmall)
                }
                Text("recommend.refresh.cta")
                    .font(.subheadline).fontWeight(.medium)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(Color.surfaceCard)
            .foregroundStyle(Color.brandAccent)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.md)
                    .stroke(Color.brandAccent.opacity(0.4), lineWidth: 1)
            )
            .elevation(.low)
        }
        .disabled(viewModel.isSendingFeedback)
    }

    private func disclaimerCard(_ text: String) -> some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: "info.circle")
                .foregroundStyle(Color.textTertiary)
                .font(.caption)
                .padding(.top, 1) // design-lint:ignore
            Text(text)
                .font(.caption)
                .foregroundStyle(Color.textTertiary)
        }
        .padding(Spacing.md)
        .background(Color.surfaceSecondary)
        .clipShape(RoundedRectangle(cornerRadius: 10)) // design-lint:ignore
    }

    // MARK: - 안전 배지 (#97)
    // 알러지 안전 게이트가 실제로 작동했음을 조용히 알린다. 축하하지 않고(회색조),
    // "정직한 안전" 브랜드를 시각화한다. ADR-0005대로 라벨 확인 안내를 병기한다.

    private func safetyBadge(_ restrictions: [DietRestriction]) -> some View {
        let labels = restrictions.map(\.displayLabel).filter { !$0.isEmpty }
        return VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "checkmark.shield.fill")
                    .foregroundStyle(Color.textSecondary)
                    .font(.caption)
                Text(safetyBadgeText(labels, total: restrictions.count))
                    .font(.caption).fontWeight(.medium)
                    .foregroundStyle(Color.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer()
            }
            Text(String(localized: "recommend.safety.checkLabel", defaultValue: "알러지 라벨은 직접 한 번 더 확인해 주세요"))
                .font(.caption2)
                .foregroundStyle(Color.textTertiary)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceSecondary)
        .overlay(
            RoundedRectangle(cornerRadius: Radius.sm)
                .stroke(Color.cardStroke, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
        .opacity(safetyBadgeVisible ? 1 : 0)
        .scaleEffect(safetyBadgeVisible ? 1 : 0.97)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(safetyBadgeA11y(labels, total: restrictions.count))
        .onAppear {
            guard !safetyBadgeVisible else { return }
            if reduceMotion {
                safetyBadgeVisible = true
            } else {
                withAnimation(.easeOut(duration: 0.35).delay(0.4)) {
                    safetyBadgeVisible = true
                }
            }
        }
    }

    /// "땅콩·새우 제외했어요" — 3건 초과면 "외 N건"으로 축약.
    private func safetyBadgeText(_ labels: [String], total: Int) -> String {
        let shown = labels.prefix(3).joined(separator: "·")
        if total > 3 {
            return String(
                localized: "recommend.safety.excludedMore",
                defaultValue: "\(shown) 외 \(total - 3)건 제외했어요"
            )
        }
        return String(
            localized: "recommend.safety.excluded",
            defaultValue: "\(shown) 제외했어요"
        )
    }

    private func safetyBadgeA11y(_ labels: [String], total: Int) -> String {
        String(
            localized: "recommend.safety.a11y",
            defaultValue: "제한 재료 \(total)건이 자동으로 제외되었어요. 알러지 라벨을 꼭 확인해 주세요."
        )
    }
}

// MARK: - Feedback Sheet

struct RecommendationFeedbackSheet: View {
    let onSelect: (RecommendationFeedbackReason) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 4) {
                Text("recommend.feedback.title")
                    .font(.title3).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
                Text("recommend.feedback.subtitle")
                    .font(.subheadline)
                    .foregroundStyle(Color.textSecondary)
            }
            .padding(.top, Spacing.sm)

            VStack(spacing: 10) {
                ForEach(RecommendationFeedbackReason.allCases) { reason in
                    Button {
                        dismiss()
                        onSelect(reason)
                    } label: {
                        HStack {
                            Text(reason.displayName)
                                .font(.subheadline)
                                .foregroundStyle(Color.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundStyle(Color.textTertiary)
                        }
                        .padding(.horizontal, Spacing.lg)
                        .padding(.vertical, 14) // design-lint:ignore
                        .background(Color.surfaceCard)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
                    }
                    .buttonStyle(.plain)
                }
            }
            Spacer()
        }
        .padding(.horizontal, Spacing.xl)
        .background(Color.backgroundPage)
    }
}
