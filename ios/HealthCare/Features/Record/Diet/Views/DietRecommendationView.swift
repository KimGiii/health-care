import SwiftUI

struct DietRecommendationView: View {
    @StateObject private var viewModel = DietRecommendationViewModel()
    @EnvironmentObject private var container: AppContainer
    @State private var showDisclaimer = false

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
        if let reason = result.failureReason {
            failureReasonBanner(reason)
        }
        if !result.meals.isEmpty {
            ForEach(result.meals) { meal in
                mealCard(meal)
            }
            totalsCard(result)
            if let rationale = result.rationale {
                rationaleCard(rationale)
            }
        }
        if !result.alternatives.isEmpty {
            alternativesSection(result.alternatives)
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
                Text(String(format: "%.0fg · %.0f kcal", item.servingG, item.calories))
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

    private func failureReasonBanner(_ reason: String) -> some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(Color.brandWarning)
                .font(.subheadline)
                .padding(.top, 1) // design-lint:ignore
            Text(reason)
                .font(.subheadline)
                .foregroundStyle(Color.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14) // design-lint:ignore
        .background(Color.brandWarning.opacity(0.1))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.md)
                .stroke(Color.brandWarning.opacity(0.4), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
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

    private func alternativesSection(_ alts: [RecommendationSolution]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .foregroundStyle(Color.brandAccent)
                Text("recommend.alternatives.title")
                    .font(.subheadline).fontWeight(.semibold)
                    .foregroundStyle(Color.textPrimary)
            }
            ForEach(Array(alts.enumerated()), id: \.offset) { index, solution in
                alternativeCard(index: index + 1, meals: solution.meals, altIndex: index)
            }
        }
    }

    private func alternativeCard(index: Int, meals: [RecommendedMeal], altIndex: Int) -> some View {
        DisclosureGroup {
            VStack(spacing: 10) {
                ForEach(meals) { meal in
                    alternativeMealRow(meal)
                }
                applyAlternativeButton(altIndex: altIndex)
            }
            .padding(.top, Spacing.sm)
        } label: {
            HStack {
                Text(String(format: String(localized: "recommend.alternative.n"), index))
                    .font(.subheadline).fontWeight(.medium)
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                Text(String(format: "%.0f kcal",
                            meals.flatMap(\.items).reduce(0) { $0 + $1.calories }))
                    .font(.caption).fontWeight(.medium)
                    .foregroundStyle(Color.textSecondary)
            }
        }
        .padding(14) // design-lint:ignore
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: 14)) // design-lint:ignore
        .elevation(.low)
    }

    private func applyAlternativeButton(altIndex: Int) -> some View {
        Button {
            withAnimation { viewModel.applyAlternative(at: altIndex) }
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
        .padding(.top, Spacing.xs)
    }

    private func alternativeMealRow(_ meal: RecommendedMeal) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Label {
                    Text(meal.mealType.displayName)
                        .font(.caption).fontWeight(.semibold)
                        .foregroundStyle(Color.textPrimary)
                } icon: {
                    Image(systemName: meal.mealType.sfSymbol)
                        .font(.caption2)
                        .foregroundStyle(Color.brandPrimary)
                }
                Spacer()
                Text(String(format: "%.0f kcal", meal.totalCalories))
                    .font(.caption2)
                    .foregroundStyle(Color.textSecondary)
            }
            ForEach(meal.items) { item in
                HStack(spacing: 6) {
                    if item.needsCaution {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 10)) // design-lint:ignore
                            .foregroundStyle(Color.brandWarning)
                    }
                    Text(item.displayName)
                        .font(.caption)
                        .foregroundStyle(Color.textPrimary)
                    Spacer()
                    Text(String(format: "%.0fg · %.0f kcal", item.servingG, item.calories))
                        .font(.caption2)
                        .foregroundStyle(Color.textSecondary)
                }
                .padding(.horizontal, Spacing.xs)
            }
        }
        .padding(10) // design-lint:ignore
        .background(Color.backgroundPage)
        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
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
