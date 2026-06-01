import PhotosUI
import SwiftUI

// MARK: - AddDietLogView

struct AddDietLogView: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var authState: AuthState
    @StateObject private var viewModel: AddDietLogViewModel
    @State private var selectedPhotoItem: PhotosPickerItem?
    var onSaved: () -> Void

    /// 이 식사 입력 전 기준, 오늘 남은 칼로리(일일 권장 − 이미 기록된 섭취). nil이면 hint 미표시.
    private let remainingBeforeMeal: Double?

    init(initialDate: Date = Date(), remainingCalories: Double? = nil, onSaved: @escaping () -> Void) {
        _viewModel = StateObject(
            wrappedValue: AddDietLogViewModel(initialDate: initialDate)
        )
        self.remainingBeforeMeal = remainingCalories
        self.onSaved = onSaved
    }

    init(editing log: DietLogDetailResponse, onSaved: @escaping () -> Void) {
        _viewModel = StateObject(
            wrappedValue: AddDietLogViewModel(editing: log)
        )
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
                    .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                    .padding(.top, Spacing.sm) // design-lint:ignore — micro/hero spacing
                }
                saveButton
            }
            .navigationTitle(viewModel.editingLogId != nil ? "식단 수정" : "식단 기록")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { onSaved() }
                        .foregroundColor(Color.brandAccent)
                }
            }
            .sheet(isPresented: $viewModel.showFoodSearch) {
                FoodSearchSheet(viewModel: viewModel)
            }
            .sheet(isPresented: $viewModel.showCustomFoodForm) {
                AddCustomFoodView(viewModel: viewModel)
            }
            .sheet(isPresented: $viewModel.showPremiumPaywall) {
                PremiumPaywallSheet(isPresented: $viewModel.showPremiumPaywall)
            }
            .alert("오류", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("확인", role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .onChange(of: selectedPhotoItem) { item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self) {
                        await viewModel.startPhotoAnalysis(
                            imageData: data,
                            suggestedFileName: "meal-photo.jpg",
                            apiClient: container.apiClient
                        )
                    } else {
                        viewModel.errorMessage = "사진을 불러오지 못했습니다."
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

    // MARK: - 영양 미리보기 카드

    private var photoAnalysisSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            if viewModel.isAnalyzingPhoto {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("AI가 식단 사진을 분석하고 있어요...")
                        .font(.subheadline)
                        .foregroundColor(Color.textSecondary)
                }
                .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            }

            if !viewModel.analysisWarnings.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Label("AI 추정치 안내", systemImage: "sparkles")
                        .font(.subheadline.bold())
                        .foregroundColor(Color.brandAccent)
                    ForEach(viewModel.analysisWarnings, id: \.self) { warning in
                        Text("• \(warning)")
                            .font(.caption)
                            .foregroundColor(Color.textSecondary)
                    }
                }
                .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            }
        }
    }

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
                MacroCell(label: "단백질", value: viewModel.totalProtein, color: .blue)
                Divider().frame(height: 30)
                MacroCell(label: "탄수화물", value: viewModel.totalCarbs, color: .orange)
                Divider().frame(height: 30)
                MacroCell(label: "지방", value: viewModel.totalFat, color: .pink)
            }
            if let remainingBeforeMeal {
                Divider()
                remainingCalorieHint(remainingBeforeMeal)
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 4, y: 2)
    }

    /// 이 식사를 더한 뒤 오늘 남는 칼로리. 음수면 초과로 danger 톤 표시.
    private func remainingCalorieHint(_ remainingBeforeMeal: Double) -> some View {
        let remaining = remainingBeforeMeal - viewModel.totalCalories
        let isExceeded = remaining < 0
        return HStack(spacing: 6) {
            Image(systemName: isExceeded ? "exclamationmark.triangle.fill" : "flame.fill")
                .font(.caption)
                .foregroundColor(isExceeded ? Color.brandDanger : Color.textSecondary)
            Text(isExceeded ? "오늘 권장 초과" : "오늘 남은 칼로리")
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
                ? "오늘 권장 칼로리를 \(String(format: "%.0f", abs(remaining))) 킬로칼로리 초과합니다."
                : "오늘 남은 칼로리 \(String(format: "%.0f", remaining)) 킬로칼로리."
        )
    }

    // MARK: - 추가된 식품 목록

    private var entriesSection: some View {
        Group {
            if !viewModel.draftEntries.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("추가한 식품")
                        .font(.subheadline.bold())
                        .foregroundColor(Color.textSecondary)
                    ForEach(Array(viewModel.draftEntries.enumerated()), id: \.element.id) { idx, entry in
                        DraftEntryCard(entry: $viewModel.draftEntries[idx]) {
                            viewModel.draftEntries.remove(at: idx)
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
                viewModel.searchQuery = ""
                viewModel.catalogResults = []
                viewModel.externalResults = []
                viewModel.showFoodSearch = true
            } label: {
                HStack {
                    Image(systemName: "plus.circle.fill")
                    Text("식품 추가")
                }
                .font(.subheadline.bold())
                .foregroundColor(Color.brandAccent)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            }
        }
    }

    // 프리미엄 사용자만 PhotosPicker 노출. 비프리미엄은 잠금 표시 + paywall 시트 트리거.
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
                .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .background(Color.brandPrimary)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            }
        } else {
            Button {
                viewModel.showPremiumPaywall = true
            } label: {
                HStack {
                    Image(systemName: "lock.fill")
                    Text("사진으로 시작")
                    Text("PRO")
                        .font(.caption2.bold())
                        .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
                        .padding(.vertical, 2) // design-lint:ignore — micro/hero spacing
                        .background(Color.white.opacity(0.25))
                        .clipShape(Capsule())
                }
                .font(.subheadline.bold())
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
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
            TextField("식사 메모를 입력하세요", text: $viewModel.notes, axis: .vertical)
                .font(.body)
                .lineLimit(3...6)
                .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
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
            .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .background(viewModel.canSave ? Color.brandPrimary : Color.gray.opacity(0.4))
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.bottom, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
        .disabled(!viewModel.canSave || viewModel.isSaving)
    }
}

// MARK: - 식사 유형 Pill

private struct MealTypePill: View {
    let type: MealType
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: type.sfSymbol)
                    .font(.caption)
                Text(type.displayName)
                    .font(.caption.bold())
            }
            .padding(.horizontal, Spacing.md) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, 7) // design-lint:ignore — micro/hero spacing
            .background(isSelected ? Color.brandPrimary : Color.surfaceCard)
            .foregroundColor(isSelected ? .white : .primary)
            .clipShape(Capsule())
            .overlay(
                Capsule().stroke(isSelected ? Color.clear : Color.hairline, lineWidth: 1)
            )
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
                    Text(entry.displayName)
                        .font(.subheadline.bold())
                    if let cat = entry.food.category {
                        Text(cat.displayName)
                            .font(.caption)
                            .foregroundColor(Color.textSecondary)
                    }
                    if entry.analysisItemId != nil {
                        HStack(spacing: 6) {
                            Text("AI 추정")
                                .font(.caption2.bold())
                                .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
                                .padding(.vertical, 3) // design-lint:ignore — micro/hero spacing
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
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(Color.textSecondary)
                }
            }

            HStack(spacing: 8) {
                Text("섭취량")
                    .font(.caption)
                    .foregroundColor(Color.textSecondary)
                TextField("g", text: $entry.servingGText)
                    .keyboardType(.decimalPad)
                    .font(.subheadline.bold())
                    .frame(width: 70)
                    .multilineTextAlignment(.trailing)
                    .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
                    .padding(.vertical, Spacing.xs) // design-lint:ignore — micro/hero spacing
                    .background(Color.surfaceCard)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                    .numericKeyboardToolbar()
                Text("g")
                    .font(.caption)
                    .foregroundColor(Color.textSecondary)
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
                    Text(entry.unknownOrUncertain ?? "AI 추정 항목이라 저장 전 검토를 권장합니다.")
                        .font(.caption)
                        .foregroundColor(.orange)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
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
        .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, Spacing.sm) // design-lint:ignore — micro/hero spacing
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
            Text(String(format: "%.1fg", value))
                .font(.subheadline.bold())
                .foregroundColor(color)
            Text(label)
                .font(.caption2)
                .foregroundColor(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - FoodSearchSheet

struct FoodSearchSheet: View {
    @EnvironmentObject private var container: AppContainer
    @ObservedObject var viewModel: AddDietLogViewModel
    @FocusState private var searchFocused: Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // 검색바
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(Color.textSecondary)
                    TextField("식품명 검색", text: $viewModel.searchQuery)
                        .focused($searchFocused)
                        .submitLabel(.search)
                        .onSubmit { triggerSearch() }
                    if !viewModel.searchQuery.isEmpty {
                        Button {
                            viewModel.clearSearch()
                        } label: {
                            Image(systemName: "xmark.circle.fill").foregroundColor(Color.textSecondary)
                        }
                    }
                }
                .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
                .background(Color.backgroundPage)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
                .onChange(of: viewModel.searchQuery) { _ in
                    viewModel.scheduleSearch(apiClient: container.apiClient)
                }

                if viewModel.isSearching {
                    Spacer()
                    ProgressView("검색 중...")
                    Spacer()
                } else {
                    combinedList
                }
            }
            .navigationTitle("식품 검색")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("닫기") { viewModel.showFoodSearch = false }
                        .foregroundColor(Color.brandAccent)
                }
            }
        }
    }

    private var combinedList: some View {
        let hasQuery = !viewModel.searchQuery.isEmpty
        let hasAny = !viewModel.catalogResults.isEmpty || !viewModel.externalResults.isEmpty

        return Group {
            if hasQuery && !hasAny {
                emptyState(message: "검색 결과가 없습니다.")
            } else {
                List {
                    if !viewModel.catalogResults.isEmpty {
                        Section(header: Text("내 카탈로그")) {
                            ForEach(viewModel.catalogResults) { item in
                                CatalogFoodRow(item: item) {
                                    viewModel.addEntry(food: item)
                                }
                            }
                        }
                    }
                    if !viewModel.externalResults.isEmpty {
                        Section(header: Text("외부 검색")) {
                            ForEach(viewModel.externalResults) { item in
                                ExternalFoodRow(item: item) {
                                    Task {
                                        await viewModel.importAndAdd(external: item, apiClient: container.apiClient)
                                    }
                                }
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
    }

    private func emptyState(message: String) -> some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "fork.knife.circle")
                .font(.system(size: 48)) // design-lint:ignore — SF Symbol/hero
                .foregroundColor(.secondary.opacity(0.5))
            Text(message)
                .font(.subheadline)
                .foregroundColor(Color.textSecondary)
                .multilineTextAlignment(.center)

            // 검색 결과가 없을 때 AI 영양 추정 플로우를 화면에 연결.
            if let estimate = viewModel.aiEstimateResult,
               estimate.isFood,
               let item = estimate.firstItem {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Label("AI 영양 추정", systemImage: "sparkles")
                            .font(.subheadline.bold())
                            .foregroundColor(Color.brandAccent)
                        Spacer()
                        Text(item.confidenceLabel)
                            .font(.caption)
                            .foregroundColor(Color.textSecondary)
                    }

                    Text(item.displayName)
                        .font(.headline)

                    HStack(spacing: 6) {
                        if let category = item.category {
                            Text(category.displayName)
                                .font(.caption)
                                .foregroundColor(Color.textSecondary)
                            Text("·").foregroundColor(Color.textSecondary).font(.caption)
                        }
                        Text(item.servingBasis.displayName)
                            .font(.caption)
                            .foregroundColor(Color.textSecondary)
                        if item.estimatedWeightG > 0 {
                            Text("· \(Int(item.estimatedWeightG))g")
                                .font(.caption)
                                .foregroundColor(Color.textSecondary)
                        }
                    }

                    HStack(spacing: 10) {
                        aiMacro("열량", value: item.nutrition.caloriesKcal, unit: "kcal")
                        aiMacro("단백질", value: item.nutrition.proteinG, unit: "g")
                        aiMacro("탄수", value: item.nutrition.carbohydrateG, unit: "g")
                        aiMacro("지방", value: item.nutrition.fatG, unit: "g")
                    }
                    HStack(spacing: 10) {
                        aiMacro("당류", value: item.nutrition.sugarsG, unit: "g")
                        aiMacro("식이섬유", value: item.nutrition.dietaryFiberG, unit: "g")
                        aiMacro("나트륨", value: item.nutrition.sodiumMg, unit: "mg")
                        aiMacro("콜레스테롤", value: item.nutrition.cholesterolMg, unit: "mg")
                    }

                    if estimate.isMultiItem {
                        Text("여러 음식이 인식되었습니다 (\(estimate.items.count)개). 현재는 첫 번째 항목만 추가됩니다.")
                            .font(.caption)
                            .foregroundColor(Color.textSecondary)
                    }

                    if !item.estimationNote.isEmpty {
                        Text(item.estimationNote)
                            .font(.caption)
                            .foregroundColor(Color.textSecondary)
                    }

                    Text(estimate.disclaimer)
                        .font(.caption)
                        .foregroundColor(.orange)

                    Button {
                        Task {
                            await viewModel.addAiEstimatedFood(apiClient: container.apiClient)
                        }
                    } label: {
                        Label("추정값으로 추가", systemImage: "plus.circle.fill")
                            .font(.subheadline.bold())
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.brandPrimary)
                }
                .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            } else {
                VStack(spacing: 10) {
                    Button {
                        Task {
                            await viewModel.estimateWithAI(apiClient: container.apiClient)
                        }
                    } label: {
                        if viewModel.isAiEstimating {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            Label("AI로 영양 추정", systemImage: "sparkles")
                                .font(.subheadline.bold())
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.brandPrimary)
                    .disabled(viewModel.isAiEstimating)

                    Button {
                        viewModel.showFoodSearch = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                            viewModel.showCustomFoodForm = true
                        }
                    } label: {
                        Label("직접 등록하기", systemImage: "plus.circle")
                            .font(.subheadline.bold())
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(.brandSecondary)
                }
            }

            Spacer()
        }
        .padding(Spacing.xxl) // design-lint:ignore — micro/hero spacing
    }

    private func aiMacro(_ title: String, value: Double, unit: String) -> some View {
        VStack(spacing: 2) {
            Text(String(format: "%.0f%@", value, unit))
                .font(.caption.bold())
                .foregroundColor(Color.textHeadline)
            Text(title)
                .font(.caption2)
                .foregroundColor(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.sm) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
    }

    private func triggerSearch() {
        searchFocused = false
        viewModel.triggerImmediateSearch(apiClient: container.apiClient)
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
                    Text(item.displayName)
                        .font(.subheadline.bold())
                    if item.custom {
                        Text("사용자 등록")
                            .font(.caption2.bold())
                            .padding(.horizontal, 5) // design-lint:ignore — micro/hero spacing
                            .padding(.vertical, 2) // design-lint:ignore — micro/hero spacing
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
                Image(systemName: "plus.circle.fill")
                    .font(.title2)
                    .foregroundColor(Color.brandAccent)
            }
        }
        .padding(.vertical, Spacing.xs) // design-lint:ignore — micro/hero spacing
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
                Text(item.displayName)
                    .font(.subheadline.bold())
                    .lineLimit(1)
                HStack(spacing: 4) {
                    Text(item.source.displayName)
                        .font(.caption2.bold())
                        .padding(.horizontal, 5) // design-lint:ignore — micro/hero spacing
                        .padding(.vertical, 2) // design-lint:ignore — micro/hero spacing
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
                Image(systemName: "plus.circle.fill")
                    .font(.title2)
                    .foregroundColor(Color.brandAccent)
            }
        }
        .padding(.vertical, Spacing.xs) // design-lint:ignore — micro/hero spacing
    }
}

// MARK: - AddCustomFoodView

private struct AddCustomFoodView: View {
    @EnvironmentObject private var container: AppContainer
    @ObservedObject var viewModel: AddDietLogViewModel

    @State private var name = ""
    @State private var category: FoodCategory = .OTHER
    @State private var calories = ""
    @State private var protein = ""
    @State private var carbs = ""
    @State private var fat = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("기본 정보") {
                    TextField("식품명", text: $name)
                        .textInputAutocapitalization(.never)
                    Picker("카테고리", selection: $category) {
                        ForEach(FoodCategory.allCases, id: \.self) { category in
                            Text(category.displayName).tag(category)
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
                    Button("취소") { viewModel.showCustomFoodForm = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await submit() }
                    } label: {
                        if viewModel.isSubmittingCustomFood {
                            ProgressView()
                        } else {
                            Text("등록")
                        }
                    }
                    .disabled(!canSubmit || viewModel.isSubmittingCustomFood)
                }
            }
            .onAppear {
                if name.isEmpty {
                    name = viewModel.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }
        }
    }

    private var canSubmit: Bool {
        !normalizedName.isEmpty && caloriesValue != nil
    }

    private var normalizedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var caloriesValue: Double? {
        parseRequiredNumber(calories)
    }

    private func nutritionField(
        _ title: String,
        text: Binding<String>,
        unit: String,
        required: Bool
    ) -> some View {
        HStack {
            Text(title)
            if required {
                Text("필수")
                    .font(.caption2.bold())
                    .foregroundColor(Color.brandAccent)
            }
            Spacer()
            TextField(unit, text: text)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 90)
                .numericKeyboardToolbar()
            Text(unit)
                .font(.caption)
                .foregroundColor(Color.textSecondary)
        }
    }

    private func submit() async {
        guard let caloriesValue else { return }
        await viewModel.submitCustomFood(
            name: normalizedName,
            category: category,
            caloriesPer100g: caloriesValue,
            proteinPer100g: parseOptionalNumber(protein),
            carbsPer100g: parseOptionalNumber(carbs),
            fatPer100g: parseOptionalNumber(fat),
            apiClient: container.apiClient
        )
    }

    private func parseRequiredNumber(_ text: String) -> Double? {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let number = Double(value), (0...9999).contains(number) else { return nil }
        return number
    }

    private func parseOptionalNumber(_ text: String) -> Double? {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return nil }
        guard let number = Double(value), (0...9999).contains(number) else { return nil }
        return number
    }
}
