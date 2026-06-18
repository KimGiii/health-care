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
            .padding(20)
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
        .padding(16)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
            HStack(spacing: 8) {
                ForEach(viewModel.orderedMeals, id: \.self) { meal in
                    let selected = viewModel.selectedMeals.contains(meal)
                    Button {
                        viewModel.toggleMeal(meal)
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: meal.sfSymbol)
                                .font(.system(size: 16))
                            Text(meal.displayName)
                                .font(.caption2).fontWeight(.medium)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(selected ? Color.brandPrimary : Color.surfaceSecondary)
                        .foregroundStyle(selected ? Color.white : Color.textSecondary)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
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
                .padding(.vertical, 12)
                .background(viewModel.canRecommend ? Color.brandPrimary : Color.surfaceSecondary)
                .foregroundStyle(viewModel.canRecommend ? Color.white : Color.textSecondary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
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
        .padding(.vertical, 40)
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "fork.knife.circle")
                .font(.system(size: 44))
                .foregroundStyle(Color.brandAccent)
            Text("recommend.empty")
                .font(.subheadline)
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
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
        }
        if !result.alternatives.isEmpty {
            alternativesSection(result.alternatives)
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
        .padding(16)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
        .padding(16)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
                        .font(.system(size: 12, weight: .semibold))
                }
                Text(saveMealButtonTitle(isSaving: isSaving, isSaved: isSaved))
                    .lineLimit(1)
            }
            .font(.caption2).fontWeight(.semibold)
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
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
                    .font(.system(size: 12))
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
        .padding(10)
        .background(Color.backgroundPage)
        .clipShape(RoundedRectangle(cornerRadius: 8))
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
        .padding(16)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .elevation(.low)
    }

    private func failureReasonBanner(_ reason: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(Color.brandWarning)
                .font(.subheadline)
                .padding(.top, 1)
            Text(reason)
                .font(.subheadline)
                .foregroundStyle(Color.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .background(Color.brandWarning.opacity(0.1))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.brandWarning.opacity(0.4), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func alternativesSection(_ alts: [[RecommendedMeal]]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .foregroundStyle(Color.brandAccent)
                Text("recommend.alternatives.title")
                    .font(.subheadline).fontWeight(.semibold)
                    .foregroundStyle(Color.textPrimary)
            }
            ForEach(Array(alts.enumerated()), id: \.offset) { index, altMeals in
                alternativeCard(index: index + 1, meals: altMeals)
            }
        }
    }

    private func alternativeCard(index: Int, meals: [RecommendedMeal]) -> some View {
        DisclosureGroup {
            VStack(spacing: 10) {
                ForEach(meals) { meal in
                    alternativeMealRow(meal)
                }
            }
            .padding(.top, 8)
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
        .padding(14)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .elevation(.low)
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
                            .font(.system(size: 10))
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
                .padding(.horizontal, 4)
            }
        }
        .padding(10)
        .background(Color.backgroundPage)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func disclaimerCard(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "info.circle")
                .foregroundStyle(Color.textTertiary)
                .font(.caption)
                .padding(.top, 1)
            Text(text)
                .font(.caption)
                .foregroundStyle(Color.textTertiary)
        }
        .padding(12)
        .background(Color.surfaceSecondary)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
