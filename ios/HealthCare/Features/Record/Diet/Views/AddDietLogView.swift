import SwiftUI

// MARK: - AddDietLogView

struct AddDietLogView: View {
    @EnvironmentObject private var container: AppContainer
    @StateObject private var viewModel = AddDietLogViewModel()
    var onSaved: () -> Void

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                Color(.systemGroupedBackground).ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 20) {
                        mealTypeSection
                        nutritionPreviewCard
                        entriesSection
                        addFoodButton
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

    private var addFoodButton: some View {
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
                    Text(entry.food.displayName)
                        .font(.subheadline.bold())
                    if let cat = entry.food.category {
                        Text(cat.displayName)
                            .font(.caption)
                            .foregroundColor(.secondary)
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
        }
        .padding(14)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.04), radius: 3, y: 1)
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
    @State private var selectedTab = 0

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
                            viewModel.searchQuery = ""
                            viewModel.catalogResults = []
                            viewModel.externalResults = []
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
                    triggerSearch()
                }

                // 탭 선택
                Picker("소스", selection: $selectedTab) {
                    Text("내 카탈로그").tag(0)
                    Text("외부 검색").tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.bottom, 8)

                if viewModel.isSearching {
                    Spacer()
                    ProgressView("검색 중...")
                    Spacer()
                } else if selectedTab == 0 {
                    catalogList
                } else {
                    externalList
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

    private var catalogList: some View {
        Group {
            if viewModel.catalogResults.isEmpty && !viewModel.searchQuery.isEmpty {
                emptyState(message: "카탈로그에 일치하는 식품이 없습니다.\n외부 검색을 이용해보세요.")
            } else {
                List(viewModel.catalogResults) { item in
                    CatalogFoodRow(item: item) {
                        viewModel.addEntry(food: item)
                    }
                }
                .listStyle(.plain)
            }
        }
    }

    private var externalList: some View {
        Group {
            if viewModel.externalResults.isEmpty && !viewModel.searchQuery.isEmpty {
                emptyState(message: "외부 검색 결과가 없습니다.")
            } else {
                List(viewModel.externalResults) { item in
                    ExternalFoodRow(item: item) {
                        Task {
                            await viewModel.importAndAdd(external: item, apiClient: container.apiClient)
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
            Spacer()
        }
        .padding(32)
    }

    private func triggerSearch() {
        Task {
            if selectedTab == 0 {
                await viewModel.searchCatalog(apiClient: container.apiClient)
            } else {
                await viewModel.searchExternal(apiClient: container.apiClient)
            }
        }
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
