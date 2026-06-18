import SwiftUI

struct DietRestrictionView: View {
    @StateObject private var viewModel = DietRestrictionViewModel()
    @EnvironmentObject private var container: AppContainer
    @State private var selectedTab: RestrictionTab = .allergen
    @State private var keywordInput: String = ""
    @State private var showKeywordInput = false
    @State private var addingType: RestrictionType = .ALLERGY

    enum RestrictionTab: String, CaseIterable {
        case allergen, category, keyword

        var title: String {
            switch self {
            case .allergen:  return String(localized: "restriction.tab.allergen")
            case .category:  return String(localized: "restriction.tab.category")
            case .keyword:   return String(localized: "restriction.tab.keyword")
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            tabPicker
            tabContent
        }
        .background(Color.backgroundPage)
        .navigationTitle(Text("restriction.title"))
        .navigationBarTitleDisplayMode(.large)
        .alert(Text("common.error.title"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("common.ok") {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .task { await viewModel.load(apiClient: container.apiClient) }
    }

    // MARK: - Tab Picker

    private var tabPicker: some View {
        Picker("", selection: $selectedTab) {
            ForEach(RestrictionTab.allCases, id: \.self) { tab in
                Text(tab.title).tag(tab)
            }
        }
        .pickerStyle(.segmented)
        .padding(.horizontal, Spacing.xl)
        .padding(.vertical, Spacing.md)
        .background(Color.surfaceCard)
    }

    // MARK: - Tab Content

    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case .allergen:  allergenTab
        case .category:  categoryTab
        case .keyword:   keywordTab
        }
    }

    // MARK: - Allergen Tab

    private var allergenTab: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                typePicker
                allergenGrid
                activeList(for: viewModel.allergenRestrictions(), emptyKey: "restriction.empty.allergen")
            }
            .padding(Spacing.xl)
        }
    }

    private var typePicker: some View {
        HStack(spacing: 8) {
            ForEach(RestrictionType.allCases, id: \.self) { type in
                Button {
                    addingType = type
                } label: {
                    Text(type.displayName)
                        .font(.subheadline).fontWeight(.medium)
                        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
                        .background(addingType == type ? Color.brandPrimary : Color.surfaceSecondary)
                        .foregroundStyle(addingType == type ? Color.white : Color.textSecondary)
                        .clipShape(Capsule())
                }
            }
            Spacer()
        }
    }

    private var allergenGrid: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 90), spacing: 10)], spacing: 10) {
            ForEach(AllergenTag.allCases) { tag in
                let isActive = viewModel.hasAllergenTag(tag)
                Button {
                    if isActive {
                        if let r = viewModel.restrictions.first(where: {
                            $0.targetType == .ALLERGEN_TAG && $0.allergenTag == tag
                        }) {
                            Task { await viewModel.delete(restriction: r, apiClient: container.apiClient) }
                        }
                    } else {
                        Task { await viewModel.addAllergenTag(tag, type: addingType, apiClient: container.apiClient) }
                    }
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: tag.sfSymbol)
                            .font(.system(size: 18)) // design-lint:ignore
                        Text(tag.displayName)
                            .font(.caption).fontWeight(.medium)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10) // design-lint:ignore
                    .background(isActive ? Color.brandPrimary.opacity(0.15) : Color.surfaceSecondary)
                    .foregroundStyle(isActive ? Color.brandPrimary : Color.textSecondary)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.md))
                    .overlay(
                        RoundedRectangle(cornerRadius: Radius.md)
                            .strokeBorder(isActive ? Color.brandPrimary : Color.clear, lineWidth: 1.5)
                    )
                }
            }
        }
    }

    // MARK: - Category Tab

    private var categoryTab: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 90), spacing: 10)], spacing: 10) {
                    ForEach(FoodCategory.allCases, id: \.self) { category in
                        let isActive = viewModel.hasCategory(category)
                        Button {
                            if isActive {
                                if let r = viewModel.restrictions.first(where: {
                                    $0.targetType == .CATEGORY && $0.category == category
                                }) {
                                    Task { await viewModel.delete(restriction: r, apiClient: container.apiClient) }
                                }
                            } else {
                                Task { await viewModel.addCategory(category, type: .AVOID, apiClient: container.apiClient) }
                            }
                        } label: {
                            VStack(spacing: 4) {
                                Image(systemName: category.sfSymbol)
                                    .font(.system(size: 18)) // design-lint:ignore
                                Text(category.displayName)
                                    .font(.caption).fontWeight(.medium)
                                    .multilineTextAlignment(.center)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10) // design-lint:ignore
                            .background(isActive ? Color.brandWarning.opacity(0.15) : Color.surfaceSecondary)
                            .foregroundStyle(isActive ? Color.brandWarning : Color.textSecondary)
                            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
                            .overlay(
                                RoundedRectangle(cornerRadius: Radius.md)
                                    .strokeBorder(isActive ? Color.brandWarning : Color.clear, lineWidth: 1.5)
                            )
                        }
                    }
                }
                activeList(for: viewModel.categoryRestrictions(), emptyKey: "restriction.empty.category")
            }
            .padding(Spacing.xl)
        }
    }

    // MARK: - Keyword Tab

    private var keywordTab: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                keywordInputRow
                activeList(for: viewModel.keywordRestrictions(), emptyKey: "restriction.empty.keyword")
            }
            .padding(Spacing.xl)
        }
    }

    private var keywordInputRow: some View {
        HStack(spacing: 10) {
            TextField(String(localized: "restriction.keyword.placeholder"), text: $keywordInput)
                .padding(Spacing.md)
                .background(Color.surfaceSecondary)
                .clipShape(RoundedRectangle(cornerRadius: 10)) // design-lint:ignore
                .submitLabel(.done)
                .onSubmit { submitKeyword() }
            Button(action: submitKeyword) {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 28)) // design-lint:ignore
                    .foregroundStyle(keywordInput.trimmingCharacters(in: .whitespaces).isEmpty
                        ? Color.textSecondary : Color.brandPrimary)
            }
            .disabled(keywordInput.trimmingCharacters(in: .whitespaces).isEmpty)
        }
    }

    private func submitKeyword() {
        let kw = keywordInput
        keywordInput = ""
        Task { await viewModel.addKeyword(kw, apiClient: container.apiClient) }
    }

    // MARK: - Active List

    private func activeList(for restrictions: [DietRestriction], emptyKey: String.LocalizationValue) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if !restrictions.isEmpty {
                Text("restriction.active.title")
                    .font(.subheadline).fontWeight(.semibold)
                    .foregroundStyle(Color.textPrimary)
            }
            if restrictions.isEmpty {
                Text(String(localized: emptyKey))
                    .font(.subheadline)
                    .foregroundStyle(Color.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.top, Spacing.sm)
            } else {
                ForEach(restrictions) { r in
                    activeRestrictionRow(r)
                }
            }
        }
    }

    private func activeRestrictionRow(_ r: DietRestriction) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(r.displayLabel)
                    .font(.subheadline).fontWeight(.medium)
                    .foregroundStyle(Color.textPrimary)
                Text(r.typeLabel)
                    .font(.caption)
                    .foregroundStyle(r.restrictionType == .ALLERGY ? Color.brandDanger : Color.textSecondary)
            }
            Spacer()
            Button {
                Task { await viewModel.delete(restriction: r, apiClient: container.apiClient) }
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .foregroundStyle(Color.textSecondary)
            }
        }
        .padding(Spacing.md)
        .background(Color.surfaceSecondary)
        .clipShape(RoundedRectangle(cornerRadius: 10)) // design-lint:ignore
    }
}
