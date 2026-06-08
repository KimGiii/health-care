import PhotosUI
import SwiftUI

// MARK: - AddDietLogView

struct AddDietLogView: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var authState: AuthState
    @StateObject private var viewModel: AddDietLogViewModel
    @State private var selectedPhotoItem: PhotosPickerItem?
    var onSaved: () -> Void

    private let remainingBeforeMeal: Double?

    init(initialDate: Date = Date(), remainingCalories: Double? = nil, onSaved: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: AddDietLogViewModel(initialDate: initialDate))
        self.remainingBeforeMeal = remainingCalories
        self.onSaved = onSaved
    }

    init(editing log: DietLogDetailResponse, onSaved: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: AddDietLogViewModel(editing: log))
        self.remainingBeforeMeal = nil
        self.onSaved = onSaved
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                Color.backgroundPage.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 20) {
                        mealTypeSection
                        photoAnalysisSection
                        nutritionPreviewCard
                        entriesSection
                        actionButtons
                        if !viewModel.notes.isEmpty || true {
                            notesSection
                        }
                        Spacer(minLength: 100)
                    }
                    .padding(.horizontal, Spacing.lg)
                    .padding(.top, Spacing.sm)
                }
                saveButton
            }
            .navigationTitle(viewModel.editingLogId != nil ? String(localized: "diet.title.edit") : String(localized: "diet.title.add"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(String(localized: "common.cancel")) { onSaved() }
                        .foregroundColor(Color.brandAccent)
                }
            }
            .numericKeyboardToolbar()
            .sheet(isPresented: $viewModel.showFoodSearch) {
                FoodSearchSheet(source: viewModel.foodEntrySource)
            }
            .sheet(isPresented: $viewModel.showPremiumPaywall) {
                PremiumPaywallSheet(isPresented: $viewModel.showPremiumPaywall)
            }
            .alert(Text("오류"), isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button(String(localized: "common.ok"), role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .onChange(of: selectedPhotoItem) { item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self) {
                        await viewModel.startPhotoAnalysis(imageData: data, apiClient: container.apiClient)
                    } else {
                        viewModel.errorMessage = String(localized: "diet.error.photoLoad")
                    }
                    selectedPhotoItem = nil
                }
            }
        }
    }

    // MARK: - 식사 유형 선택

    private var mealTypeSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("식사 유형")
                .font(.subheadline.bold())
                .foregroundColor(Color.textSecondary)
            HStack(spacing: 8) {
                ForEach(MealType.allCases, id: \.self) { type in
                    MealTypePill(type: type, isSelected: viewModel.selectedMealType == type) {
                        viewModel.selectedMealType = type
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - 사진 분석 상태

    private var photoAnalysisSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            if viewModel.isAnalyzingPhoto {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("AI가 식단 사진을 분석하고 있어요...")
                        .font(.subheadline)
                        .foregroundColor(Color.textSecondary)
                }
                .padding(Spacing.lg)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            }

            if case .photoAnalysis(_, _, let warnings, _) = viewModel.draft, !warnings.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Label(String(localized: "diet.button.aiHint"), systemImage: "sparkles")
                        .font(.subheadline.bold())
                        .foregroundColor(Color.brandAccent)
                    ForEach(warnings, id: \.self) { warning in
                        Text("• \(warning)")
                            .font(.caption)
                            .foregroundColor(Color.textSecondary)
                    }
                }
                .padding(Spacing.lg)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            }
        }
    }

    // MARK: - 영양 미리보기 카드

    private var nutritionPreviewCard: some View {
        VStack(spacing: 12) {
            HStack {
                Text("오늘 이 식사")
                    .font(.subheadline.bold())
                    .foregroundColor(Color.brandAccent)
                Spacer()
                Text(String(format: "%.0f kcal", viewModel.totalCalories))
                    .font(.title3.bold())
                    .foregroundColor(.brandAccent)
            }
            HStack(spacing: 0) {
                MacroCell(label: String(localized: "diet.nutrient.protein"), value: viewModel.totalProtein, color: .blue)
                Divider().frame(height: 30)
                MacroCell(label: String(localized: "diet.nutrient.carbs"), value: viewModel.totalCarbs, color: .orange)
                Divider().frame(height: 30)
                MacroCell(label: String(localized: "diet.nutrient.fat"), value: viewModel.totalFat, color: .pink)
            }
            if let remainingBeforeMeal {
                Divider()
                remainingCalorieHint(remainingBeforeMeal)
            }
        }
        .padding(Spacing.lg)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 4, y: 2)
    }

    private func remainingCalorieHint(_ remainingBeforeMeal: Double) -> some View {
        let remaining = remainingBeforeMeal - viewModel.totalCalories
        let isExceeded = remaining < 0
        return HStack(spacing: 6) {
            Image(systemName: isExceeded ? "exclamationmark.triangle.fill" : "flame.fill")
                .font(.caption)
                .foregroundColor(isExceeded ? Color.brandDanger : Color.textSecondary)
            Text(isExceeded ? String(localized: "diet.calories.exceeded") : String(localized: "diet.calories.remaining"))
                .font(.caption)
                .foregroundColor(Color.textSecondary)
            Spacer()
            Text("\(isExceeded ? "+" : "")\(abs(remaining).formatted(.number.precision(.fractionLength(0)))) kcal")
                .font(.subheadline.bold())
                .foregroundColor(isExceeded ? Color.brandDanger : Color.brandAccent)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            isExceeded
                ? String(format: String(localized: "diet.calories.exceeded.a11y"), abs(remaining))
                : String(format: String(localized: "diet.calories.remaining.a11y"), remaining)
        )
    }

    // MARK: - 추가된 식품 목록

    private var entriesSection: some View {
        Group {
            if !viewModel.draft.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("추가한 식품")
                        .font(.subheadline.bold())
                        .foregroundColor(Color.textSecondary)
                    ForEach(Array(viewModel.draft.entries.enumerated()), id: \.element.id) { idx, _ in
                        DraftEntryCard(entry: viewModel.entryBinding(at: idx)) {
                            viewModel.removeEntry(at: idx)
                        }
                    }
                }
            }
        }
    }

    private var actionButtons: some View {
        HStack(spacing: 12) {
            photoButton
            Button {
                viewModel.openFoodSearch()
            } label: {
                HStack {
                    Image(systemName: "plus.circle.fill")
                    Text("식품 추가")
                }
                .font(.subheadline.bold())
                .foregroundColor(Color.brandAccent)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.lg)
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            }
        }
    }

    @ViewBuilder
    private var photoButton: some View {
        if authState.isPremium {
            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                HStack {
                    Image(systemName: "camera.viewfinder")
                    Text("사진으로 시작")
                }
                .font(.subheadline.bold())
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.lg)
                .background(Color.brandPrimary)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            }
        } else {
            Button { viewModel.showPremiumPaywall = true } label: {
                HStack {
                    Image(systemName: "lock.fill")
                    Text("사진으로 시작")
                    Text("PRO")
                        .font(.caption2.bold())
                        .padding(.horizontal, Spacing.sm)
                        .padding(.vertical, Spacing.xs)
                        .background(Color.white.opacity(0.25))
                        .clipShape(Capsule())
                }
                .font(.subheadline.bold())
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.lg)
                .background(Color.brandPrimary.opacity(0.55))
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            }
        }
    }

    private var notesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("메모 (선택)")
                .font(.subheadline.bold())
                .foregroundColor(Color.textSecondary)
            TextField(String(localized: "diet.notes.placeholder"), text: $viewModel.notes, axis: .vertical)
                .font(.body)
                .lineLimit(3...6)
                .padding(Spacing.md)
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
        }
    }

    private var saveButton: some View {
        Button {
            Task {
                await viewModel.save(apiClient: container.apiClient) {
                    onSaved()
                    Task { @MainActor in
                        try? await Task.sleep(for: .seconds(0.4))
                        AdsManager.shared.showInterstitialIfReady()
                    }
                }
            }
        } label: {
            ZStack {
                if viewModel.isSaving {
                    ProgressView().tint(.white)
                } else {
                    Text("저장")
                        .font(.headline)
                        .foregroundColor(.white)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
            .background(viewModel.canSave ? Color.brandPrimary : Color.gray.opacity(0.4))
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .padding(.horizontal, Spacing.lg)
            .padding(.bottom, Spacing.lg)
        }
        .disabled(!viewModel.canSave || viewModel.isSaving)
    }
}

// MARK: - MealTypePill

private struct MealTypePill: View {
    let type: MealType
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: type.sfSymbol).font(.caption)
                Text(type.displayName).font(.caption.bold())
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .background(isSelected ? Color.brandPrimary : Color.surfaceCard)
            .foregroundColor(isSelected ? .white : .primary)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(isSelected ? Color.clear : Color.hairline, lineWidth: 1))
        }
    }
}

// MARK: - DraftEntryCard

private struct DraftEntryCard: View {
    @Binding var entry: DraftFoodEntry
    let onDelete: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.displayName).font(.subheadline.bold())
                    if let cat = entry.food.category {
                        Text(cat.displayName)
                            .font(.caption)
                            .foregroundColor(Color.textSecondary)
                    }
                    if entry.analysisItemId != nil {
                        HStack(spacing: 6) {
                            Text("AI 추정")
                                .font(.caption2.bold())
                                .padding(.horizontal, Spacing.sm)
                                .padding(.vertical, Spacing.xs)
                                .background(Color.brandPrimary.opacity(0.12))
                                .foregroundColor(Color.brandAccent)
                                .clipShape(Capsule())
                            if let confidence = entry.aiConfidence {
                                Text("신뢰도 \(Int(confidence * 100))%")
                                    .font(.caption2)
                                    .foregroundColor(Color.textSecondary)
                            }
                        }
                    }
                }
                Spacer()
                Button(action: onDelete) {
                    Image(systemName: "xmark.circle.fill").foregroundColor(Color.textSecondary)
                }
            }

            HStack(spacing: 8) {
                Text("섭취량").font(.caption).foregroundColor(Color.textSecondary)
                TextField("g", text: $entry.servingGText)
                    .keyboardType(.decimalPad)
                    .font(.subheadline.bold())
                    .frame(width: 70)
                    .multilineTextAlignment(.trailing)
                    .padding(.horizontal, Spacing.sm)
                    .padding(.vertical, Spacing.xs)
                    .background(Color.surfaceCard)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                Text("g").font(.caption).foregroundColor(Color.textSecondary)
                Spacer()
                Text(String(format: "%.0f kcal", entry.calories))
                    .font(.subheadline.bold())
                    .foregroundColor(.brandAccent)
            }

            if entry.analysisItemId != nil {
                HStack(spacing: 8) {
                    portionButton(title: "0.5x", multiplier: 0.5)
                    portionButton(title: "1x", multiplier: 1.0)
                    portionButton(title: "2x", multiplier: 2.0)
                    Spacer()
                }
                if entry.needsReview || entry.unknownOrUncertain != nil {
                    Text(entry.unknownOrUncertain ?? String(localized: "diet.aiHint.review"))
                        .font(.caption)
                        .foregroundColor(.orange)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .padding(Spacing.lg)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
        .shadow(color: .black.opacity(0.04), radius: 3, y: 1)
    }

    private func portionButton(title: String, multiplier: Double) -> some View {
        Button(title) {
            let updated = max(entry.servingG * multiplier, 1)
            entry.servingGText = String(format: "%.0f", updated)
        }
        .font(.caption.bold())
        .foregroundColor(Color.brandAccent)
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.sm)
        .background(Color.surfaceCard)
        .clipShape(Capsule())
    }
}

// MARK: - MacroCell

struct MacroCell: View {
    let label: String
    let value: Double
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(String(format: "%.1fg", value)).font(.subheadline.bold()).foregroundColor(color)
            Text(label).font(.caption2).foregroundColor(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - FoodSearchSheet

struct FoodSearchSheet: View {
    @EnvironmentObject private var container: AppContainer
    @ObservedObject var source: FoodEntrySource
    @Environment(\.dismiss) private var dismiss
    @FocusState private var searchFocused: Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundColor(Color.textSecondary)
                    TextField(String(localized: "diet.search.placeholder"), text: $source.searchQuery)
                        .focused($searchFocused)
                        .submitLabel(.search)
                        .onSubmit { triggerSearch() }
                    if !source.searchQuery.isEmpty {
                        Button { source.clearSearch() } label: {
                            Image(systemName: "xmark.circle.fill").foregroundColor(Color.textSecondary)
                        }
                    }
                }
                .padding(Spacing.md)
                .background(Color.backgroundPage)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.md)
                .onChange(of: source.searchQuery) { _ in
                    source.scheduleSearch(apiClient: container.apiClient)
                }

                if source.isSearching {
                    Spacer()
                    ProgressView(String(localized: "diet.search.loading"))
                    Spacer()
                } else {
                    combinedList
                }
            }
            .navigationTitle(Text("diet.search.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(String(localized: "common.close")) { dismiss() }
                        .foregroundColor(Color.brandAccent)
                }
            }
            .sheet(isPresented: $source.showCustomFoodForm) {
                AddCustomFoodView(source: source)
            }
        }
    }

    private var combinedList: some View {
        let hasQuery = !source.searchQuery.isEmpty
        let hasAny = !source.catalogResults.isEmpty || !source.externalResults.isEmpty

        return Group {
            if hasQuery && !hasAny {
                ScrollView {
                    VStack(spacing: 12) {
                        emptyHeader(message: String(localized: "diet.search.empty"))
                        aiActionSection
                    }
                    .padding(Spacing.xxl)
                }
            } else {
                VStack(spacing: 0) {
                    List {
                        if !source.catalogResults.isEmpty {
                            Section(header: Text("내 카탈로그")) {
                                ForEach(source.catalogResults) { item in
                                    CatalogFoodRow(item: item) { source.select(food: item) }
                                }
                            }
                        }
                        if !source.externalResults.isEmpty {
                            Section(header: Text("외부 검색")) {
                                ForEach(source.externalResults) { item in
                                    ExternalFoodRow(item: item) {
                                        Task { await source.importAndAdd(external: item, apiClient: container.apiClient) }
                                    }
                                }
                            }
                        }
                    }
                    .listStyle(.plain)

                    if hasQuery {
                        Divider()
                        aiActionSection
                            .padding(Spacing.lg)
                            .background(Color.backgroundPage)
                    }
                }
            }
        }
    }

    private func emptyHeader(message: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: "fork.knife.circle")
                .font(.displayLarge)
                .foregroundColor(.secondary.opacity(0.5))
            Text(message)
                .font(.subheadline)
                .foregroundColor(Color.textSecondary)
                .multilineTextAlignment(.center)
        }
    }

    @ViewBuilder
    private var aiActionSection: some View {
        if let estimate = source.aiEstimateResult,
           estimate.isFood,
           let item = estimate.firstItem {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Label(String(localized: "diet.ai.estimateNutrition"), systemImage: "sparkles")
                        .font(.subheadline.bold())
                        .foregroundColor(Color.brandAccent)
                    Spacer()
                    Text(item.confidenceLabel).font(.caption).foregroundColor(Color.textSecondary)
                }
                Text(item.displayName).font(.headline)
                HStack(spacing: 6) {
                    if let category = item.category {
                        Text(category.displayName).font(.caption).foregroundColor(Color.textSecondary)
                        Text("·").foregroundColor(Color.textSecondary).font(.caption)
                    }
                    Text(item.servingBasis.displayName).font(.caption).foregroundColor(Color.textSecondary)
                    if item.estimatedWeightG > 0 {
                        Text("· \(Int(item.estimatedWeightG))g").font(.caption).foregroundColor(Color.textSecondary)
                    }
                }
                HStack(spacing: 10) {
                    aiMacro(String(localized: "diet.macro.label.kcal"), value: item.nutrition.caloriesKcal, unit: "kcal")
                    aiMacro(String(localized: "diet.macro.label.protein"), value: item.nutrition.proteinG, unit: "g")
                    aiMacro(String(localized: "diet.macro.label.carbs"), value: item.nutrition.carbohydrateG, unit: "g")
                    aiMacro(String(localized: "diet.macro.label.fat"), value: item.nutrition.fatG, unit: "g")
                }
                HStack(spacing: 10) {
                    aiMacro(String(localized: "diet.macro.label.sugars"), value: item.nutrition.sugarsG, unit: "g")
                    aiMacro(String(localized: "diet.macro.label.fiber"), value: item.nutrition.dietaryFiberG, unit: "g")
                    aiMacro(String(localized: "diet.macro.label.sodium"), value: item.nutrition.sodiumMg, unit: "mg")
                    aiMacro(String(localized: "diet.macro.label.cholesterol"), value: item.nutrition.cholesterolMg, unit: "mg")
                }
                if estimate.isMultiItem {
                    Text(String(format: String(localized: "diet.ai.multiItem.warning"), estimate.items.count))
                        .font(.caption).foregroundColor(Color.textSecondary)
                }
                if !item.estimationNote.isEmpty {
                    Text(item.estimationNote).font(.caption).foregroundColor(Color.textSecondary)
                }
                Text(estimate.disclaimer).font(.caption).foregroundColor(.orange)
                Button {
                    Task { await source.addAiEstimatedFood(apiClient: container.apiClient) }
                } label: {
                    Label(String(localized: "diet.ai.addEstimate"), systemImage: "plus.circle.fill")
                        .font(.subheadline.bold())
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.brandPrimary)
            }
            .padding(Spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
        } else {
            VStack(spacing: 10) {
                Button {
                    Task { await source.estimateWithAI(apiClient: container.apiClient) }
                } label: {
                    if source.isAiEstimating {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Label(String(localized: "diet.ai.estimate"), systemImage: "sparkles")
                            .font(.subheadline.bold())
                            .frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(.brandPrimary)
                .disabled(source.isAiEstimating)

                Button {
                    source.showCustomFoodForm = true
                } label: {
                    Label(String(localized: "diet.ai.directAdd"), systemImage: "plus.circle")
                        .font(.subheadline.bold())
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(.brandSecondary)
            }
        }
    }

    private func aiMacro(_ title: String, value: Double, unit: String) -> some View {
        VStack(spacing: 2) {
            Text(String(format: "%.0f%@", value, unit)).font(.caption.bold()).foregroundColor(Color.textHeadline)
            Text(title).font(.caption2).foregroundColor(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.sm)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
    }

    private func triggerSearch() {
        searchFocused = false
        source.triggerImmediateSearch(apiClient: container.apiClient)
    }
}

// MARK: - CatalogFoodRow

private struct CatalogFoodRow: View {
    let item: FoodCatalogItem
    let onAdd: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: item.category?.sfSymbol ?? "fork.knife")
                .font(.title2)
                .frame(width: 40, height: 40)
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(item.displayName).font(.subheadline.bold())
                    if item.custom {
                        Text("사용자 등록")
                            .font(.caption2.bold())
                            .padding(.horizontal, Spacing.sm)
                            .padding(.vertical, Spacing.xs)
                            .background(Color.brandAccent.opacity(0.2))
                            .foregroundColor(.brandAccent)
                            .clipShape(Capsule())
                    }
                }
                if let kcal = item.caloriesPer100g {
                    Text(String(format: "%.0f kcal / 100g", kcal))
                        .font(.caption)
                        .foregroundColor(Color.textSecondary)
                }
            }
            Spacer()
            Button(action: onAdd) {
                Image(systemName: "plus.circle.fill").font(.title2).foregroundColor(Color.brandAccent)
            }
        }
        .padding(.vertical, Spacing.xs)
    }
}

// MARK: - ExternalFoodRow

private struct ExternalFoodRow: View {
    let item: ExternalFoodResult
    let onAdd: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: item.category?.sfSymbol ?? "magnifyingglass")
                .font(.title2)
                .frame(width: 40, height: 40)
                .background(Color.backgroundPage)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
            VStack(alignment: .leading, spacing: 3) {
                Text(item.displayName).font(.subheadline.bold()).lineLimit(1)
                HStack(spacing: 4) {
                    Text(item.source.displayName)
                        .font(.caption2.bold())
                        .padding(.horizontal, Spacing.sm)
                        .padding(.vertical, Spacing.xs)
                        .background(Color.hairline)
                        .clipShape(Capsule())
                    Text(item.nutritionSummary)
                        .font(.caption)
                        .foregroundColor(Color.textSecondary)
                        .lineLimit(1)
                }
            }
            Spacer()
            Button(action: onAdd) {
                Image(systemName: "plus.circle.fill").font(.title2).foregroundColor(Color.brandAccent)
            }
        }
        .padding(.vertical, Spacing.xs)
    }
}

// MARK: - AddCustomFoodView

private struct AddCustomFoodView: View {
    @EnvironmentObject private var container: AppContainer
    @ObservedObject var source: FoodEntrySource
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var category: FoodCategory = .OTHER
    @State private var calories = ""
    @State private var protein = ""
    @State private var carbs = ""
    @State private var fat = ""

    var body: some View {
        NavigationStack {
            Form {
                Section(String(localized: "diet.foodEdit.section.basic")) {
                    TextField(String(localized: "diet.foodEdit.field.name"), text: $name)
                        .textInputAutocapitalization(.never)
                    Picker(String(localized: "diet.foodEdit.field.category"), selection: $category) {
                        ForEach(FoodCategory.allCases, id: \.self) { cat in
                            Text(cat.displayName).tag(cat)
                        }
                    }
                }
                Section("100g 기준 영양 정보") {
                    nutritionField("칼로리", text: $calories, unit: "kcal", required: true)
                    nutritionField("단백질", text: $protein, unit: "g", required: false)
                    nutritionField("탄수화물", text: $carbs, unit: "g", required: false)
                    nutritionField("지방", text: $fat, unit: "g", required: false)
                }
            }
            .navigationTitle("직접 등록")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await submit() }
                    } label: {
                        if source.isSubmittingCustomFood { ProgressView() } else { Text("등록") }
                    }
                    .disabled(!canSubmit || source.isSubmittingCustomFood)
                }
            }
            .numericKeyboardToolbar()
            .onAppear {
                if name.isEmpty {
                    name = source.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }
        }
    }

    private var canSubmit: Bool { !normalizedName.isEmpty && caloriesValue != nil }
    private var normalizedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var caloriesValue: Double? { parseNumber(calories, required: true) }

    private func nutritionField(_ title: String, text: Binding<String>, unit: String, required: Bool) -> some View {
        HStack {
            Text(title)
            if required {
                Text("필수").font(.caption2.bold()).foregroundColor(Color.brandAccent)
            }
            Spacer()
            TextField(unit, text: text)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 90)
            Text(unit).font(.caption).foregroundColor(Color.textSecondary)
        }
    }

    private func submit() async {
        guard let caloriesValue else { return }
        await source.submitCustomFood(
            name: normalizedName,
            category: category,
            caloriesPer100g: caloriesValue,
            proteinPer100g: parseNumber(protein, required: false),
            carbsPer100g: parseNumber(carbs, required: false),
            fatPer100g: parseNumber(fat, required: false),
            apiClient: container.apiClient
        )
    }

    private func parseNumber(_ text: String, required: Bool) -> Double? {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return nil }
        guard let number = Double(value), (0...9999).contains(number) else { return nil }
        return number
    }
}
