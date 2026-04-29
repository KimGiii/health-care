import PhotosUI
import SwiftUI

// MARK: - AddDietLogView

struct AddDietLogView: View {
    @EnvironmentObject private var container: AppContainer
    @StateObject private var viewModel = AddDietLogViewModel()
    @State private var selectedPhotoItem: PhotosPickerItem?
    var onSaved: () -> Void

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                Color(.systemGroupedBackground).ignoresSafeArea()
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
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }
                saveButton
            }
            .navigationTitle("식단 기록")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { onSaved() }
                        .foregroundColor(.brandPrimary)
                }
            }
            .sheet(isPresented: $viewModel.showFoodSearch) {
                FoodSearchSheet(viewModel: viewModel)
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
                .foregroundColor(.secondary)
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
                        .foregroundColor(.secondary)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            if !viewModel.analysisWarnings.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Label("AI 추정치 안내", systemImage: "sparkles")
                        .font(.subheadline.bold())
                        .foregroundColor(.brandPrimary)
                    ForEach(viewModel.analysisWarnings, id: \.self) { warning in
                        Text("• \(warning)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private var nutritionPreviewCard: some View {
        VStack(spacing: 12) {
            HStack {
                Text("오늘 이 식사")
                    .font(.subheadline.bold())
                    .foregroundColor(.brandPrimary)
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
        }
        .padding(16)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.05), radius: 4, y: 2)
    }

    // MARK: - 추가된 식품 목록

    private var entriesSection: some View {
        Group {
            if !viewModel.draftEntries.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("추가한 식품")
                        .font(.subheadline.bold())
                        .foregroundColor(.secondary)
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
            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                HStack {
                    Image(systemName: "camera.viewfinder")
                    Text("사진으로 시작")
                }
                .font(.subheadline.bold())
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color.brandPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

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
                .foregroundColor(.brandPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color.brandSurface)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private var notesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("메모 (선택)")
                .font(.subheadline.bold())
                .foregroundColor(.secondary)
            TextField("식사 메모를 입력하세요", text: $viewModel.notes, axis: .vertical)
                .font(.body)
                .lineLimit(3...6)
                .padding(12)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }

    private var saveButton: some View {
        Button {
            Task {
                await viewModel.save(apiClient: container.apiClient) {
                    onSaved()
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
            .padding(.vertical, 16)
            .background(viewModel.canSave ? Color.brandPrimary : Color.gray.opacity(0.4))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
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
                Text(type.emoji)
                    .font(.caption)
                Text(type.displayName)
                    .font(.caption.bold())
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(isSelected ? Color.brandPrimary : Color(.systemBackground))
            .foregroundColor(isSelected ? .white : .primary)
            .clipShape(Capsule())
            .overlay(
                Capsule().stroke(isSelected ? Color.clear : Color(.systemGray4), lineWidth: 1)
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
                            .foregroundColor(.secondary)
                    }
                    if entry.analysisItemId != nil {
                        HStack(spacing: 6) {
                            Text("AI 추정")
                                .font(.caption2.bold())
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(Color.brandPrimary.opacity(0.12))
                                .foregroundColor(.brandPrimary)
                                .clipShape(Capsule())
                            if let confidence = entry.aiConfidence {
                                Text("신뢰도 \(Int(confidence * 100))%")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
                Spacer()
                Button(action: onDelete) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
            }

            HStack(spacing: 8) {
                Text("섭취량")
                    .font(.caption)
                    .foregroundColor(.secondary)
                TextField("g", text: $entry.servingGText)
                    .keyboardType(.decimalPad)
                    .font(.subheadline.bold())
                    .frame(width: 70)
                    .multilineTextAlignment(.trailing)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.brandSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                Text("g")
                    .font(.caption)
                    .foregroundColor(.secondary)
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
        .padding(14)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.04), radius: 3, y: 1)
    }

    private func portionButton(title: String, multiplier: Double) -> some View {
        Button(title) {
            let updated = max(entry.servingG * multiplier, 1)
            entry.servingGText = String(format: "%.0f", updated)
        }
        .font(.caption.bold())
        .foregroundColor(.brandPrimary)
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(Color.brandSurface)
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
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - FoodSearchSheet

struct FoodSearchSheet: View {
    @EnvironmentObject private var container: AppContainer
    @ObservedObject var viewModel: AddDietLogViewModel

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // 검색바
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.secondary)
                    TextField("식품명 검색", text: $viewModel.searchQuery)
                        .submitLabel(.search)
                        .onSubmit { triggerSearch() }
                    if !viewModel.searchQuery.isEmpty {
                        Button {
                            viewModel.clearSearch()
                        } label: {
                            Image(systemName: "xmark.circle.fill").foregroundColor(.secondary)
                        }
                    }
                }
                .padding(10)
                .background(Color(.systemGray6))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
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
                        .foregroundColor(.brandPrimary)
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
                .font(.system(size: 48))
                .foregroundColor(.secondary.opacity(0.5))
            Text(message)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            // Codex 작업: 검색 결과가 없을 때 AI 영양 추정 플로우를 화면에 연결합니다.
            if let estimate = viewModel.aiEstimateResult {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Label("AI 영양 추정", systemImage: "sparkles")
                            .font(.subheadline.bold())
                            .foregroundColor(.brandPrimary)
                        Spacer()
                        Text("신뢰도 \(Int(estimate.confidence * 100))%")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    Text(estimate.foodName)
                        .font(.headline)

                    if let category = estimate.category {
                        Text(category.displayName)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    HStack(spacing: 10) {
                        aiMacro("열량", value: estimate.caloriesPer100g, unit: "kcal")
                        aiMacro("단백질", value: estimate.proteinPer100g, unit: "g")
                        aiMacro("탄수", value: estimate.carbsPer100g, unit: "g")
                        aiMacro("지방", value: estimate.fatPer100g, unit: "g")
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
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
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
            }

            Spacer()
        }
        .padding(32)
    }

    private func aiMacro(_ title: String, value: Double, unit: String) -> some View {
        VStack(spacing: 2) {
            Text(String(format: "%.0f%@", value, unit))
                .font(.caption.bold())
                .foregroundColor(.primary)
            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(Color.brandSurface)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func triggerSearch() {
        viewModel.triggerImmediateSearch(apiClient: container.apiClient)
    }
}

// MARK: - CatalogFoodRow

private struct CatalogFoodRow: View {
    let item: FoodCatalogItem
    let onAdd: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text(item.category?.emoji ?? "🍽")
                .font(.title2)
                .frame(width: 40, height: 40)
                .background(Color.brandSurface)
                .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(item.displayName)
                        .font(.subheadline.bold())
                    if item.custom {
                        Text("MY")
                            .font(.caption2.bold())
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.brandAccent.opacity(0.2))
                            .foregroundColor(.brandAccent)
                            .clipShape(Capsule())
                    }
                }
                if let kcal = item.caloriesPer100g {
                    Text(String(format: "%.0f kcal / 100g", kcal))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            Spacer()
            Button(action: onAdd) {
                Image(systemName: "plus.circle.fill")
                    .font(.title2)
                    .foregroundColor(.brandPrimary)
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - ExternalFoodRow

private struct ExternalFoodRow: View {
    let item: ExternalFoodResult
    let onAdd: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text(item.category?.emoji ?? "🔍")
                .font(.title2)
                .frame(width: 40, height: 40)
                .background(Color(.systemGray6))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 3) {
                Text(item.displayName)
                    .font(.subheadline.bold())
                    .lineLimit(1)
                HStack(spacing: 4) {
                    Text(item.source.displayName)
                        .font(.caption2.bold())
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color(.systemGray5))
                        .clipShape(Capsule())
                    Text(item.nutritionSummary)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer()
            Button(action: onAdd) {
                Image(systemName: "plus.circle.fill")
                    .font(.title2)
                    .foregroundColor(.brandPrimary)
            }
        }
        .padding(.vertical, 4)
    }
}
