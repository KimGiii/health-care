import SwiftUI

// MARK: - ViewModel (inline)

@MainActor
final class DietLogDetailViewModel: ObservableObject {
    @Published var detail: DietLogDetailResponse?
    @Published var userProfile: UserProfile?
    @Published var isLoading = false
    @Published var isDeleting = false
    @Published var errorMessage: String?

    // MARK: - 일일 권장량 (프로필 우선, 없거나 0이면 fallback)
    var dailyCalorieGoal: Double {
        if let t = userProfile?.calorieTarget, t > 0 { return Double(t) }
        return 2_000
    }
    var dailyProteinGoal: Double {
        if let g = userProfile?.proteinTargetG, g > 0 { return Double(g) }
        return 60
    }
    var dailyCarbsGoal: Double {
        if let g = userProfile?.carbTargetG, g > 0 { return Double(g) }
        return 250
    }
    var dailyFatGoal: Double {
        if let g = userProfile?.fatTargetG, g > 0 { return Double(g) }
        return 65
    }

    func load(id: Int, apiClient: APIClient) async {
        isLoading = true
        defer { isLoading = false }
        do {
            async let detailRequest: DietLogDetailResponse = apiClient.request(.getDietLog(id: id))
            async let profileRequest: UserProfile = apiClient.request(.getProfile)
            let loadedDetail = try await detailRequest
            detail = loadedDetail
            if let profile = try? await profileRequest {
                userProfile = profile
            }
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "diet.detail.error.load")
        }
    }

    func delete(id: Int, apiClient: APIClient) async -> Bool {
        guard !isDeleting else { return false }
        isDeleting = true
        errorMessage = nil
        defer { isDeleting = false }

        do {
            try await apiClient.requestVoid(.deleteDietLog(id: id))
            NotificationCenter.default.post(name: .dietRecordChanged, object: nil)
            return true
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "diet.error.delete")
        }
        return false
    }
}

// MARK: - DietLogDetailView

struct DietLogDetailView: View {
    let logId: Int
    let mealType: MealType
    let logDate: String

    @EnvironmentObject private var container: AppContainer
    @StateObject private var viewModel = DietLogDetailViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var showingEdit = false
    @State private var showingDeleteConfirm = false
    @State private var showingSources = false

    var body: some View {
        ZStack(alignment: .top) {
            Color.backgroundPage.ignoresSafeArea()
            if viewModel.isLoading {
                VStack { Spacer(); ProgressView(); Spacer() }
            } else if let detail = viewModel.detail {
                ScrollView {
                    VStack(spacing: 0) {
                        DietDetailHeader(detail: detail)
                        VStack(spacing: 16) {
                            nutritionCard(detail: detail)
                            recommendedProgressCard(detail: detail)
                            entriesSection(detail: detail)
                            if let notes = detail.notes, !notes.isEmpty {
                                notesCard(notes: notes)
                            }
                        }
                        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                        .padding(.top, Spacing.xl) // design-lint:ignore — micro/hero spacing
                        .padding(.bottom, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                    }
                }
                .ignoresSafeArea(edges: .top)
            }
        }
        .navigationBarHidden(true)
        .overlay(alignment: .topLeading) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.cta)
                    .foregroundColor(.white)
                    .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
                    .background(Color.black.opacity(0.25))
                    .clipShape(Circle())
            }
            .padding(.leading, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.top, 56) // design-lint:ignore — micro/hero spacing
        }
        .overlay(alignment: .topTrailing) {
            if viewModel.detail != nil {
                HStack(spacing: 10) {
                    Button {
                        showingDeleteConfirm = true
                    } label: {
                        Image(systemName: viewModel.isDeleting ? "hourglass" : "trash")
                            .font(.cta)
                            .foregroundColor(.white)
                            .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
                            .background(Color.black.opacity(0.25))
                            .clipShape(Circle())
                    }
                    .disabled(viewModel.isDeleting)

                    Button {
                        showingEdit = true
                    } label: {
                        Image(systemName: "pencil")
                            .font(.cta)
                            .foregroundColor(.white)
                            .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
                            .background(Color.black.opacity(0.25))
                            .clipShape(Circle())
                    }
                    .disabled(viewModel.isDeleting)
                }
                .padding(.trailing, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .padding(.top, 56) // design-lint:ignore — micro/hero spacing
            }
        }
        .confirmationDialog(Text("식단 기록 삭제"), isPresented: $showingDeleteConfirm, titleVisibility: .visible) {
            Button(String(localized: "common.delete.button"), role: .destructive) {
                Task {
                    if await viewModel.delete(id: logId, apiClient: container.apiClient) {
                        dismiss()
                    }
                }
            }
            Button(String(localized: "common.cancel"), role: .cancel) {}
        } message: {
            Text("이 식단 기록을 삭제하시겠습니까? 되돌릴 수 없습니다.")
        }
        .sheet(isPresented: $showingEdit) {
            if let detail = viewModel.detail {
                AddDietLogView(editing: detail) {
                    showingEdit = false
                    Task { await viewModel.load(id: logId, apiClient: container.apiClient) }
                }
                .environmentObject(container)
            }
        }
        .task { await viewModel.load(id: logId, apiClient: container.apiClient) }
        .sheet(isPresented: $showingSources) { MedicalSourcesView() }
        .alert(Text("오류"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button(String(localized: "common.ok"), role: .cancel) { viewModel.errorMessage = nil }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }

    private func nutritionCard(detail: DietLogDetailResponse) -> some View {
        VStack(spacing: 14) {
            HStack {
                Text("영양 정보")
                    .font(.subheadline.bold())
                    .foregroundColor(Color.brandAccent)
                Spacer()
                Button {
                    showingSources = true
                } label: {
                    Image(systemName: "info.circle")
                        .font(.bodyMedium)
                        .foregroundStyle(Color.textSecondary)
                }
                .accessibilityLabel(String(localized: "diet.detail.viewSources.a11y"))
            }
            HStack(spacing: 0) {
                NutritionStatCell(
                    label: String(localized: "diet.nutrient.calories"),
                    value: String(format: "%.0f", detail.totalCalories ?? 0),
                    unit: "kcal",
                    color: .brandAccent
                )
                Divider().frame(height: 40)
                NutritionStatCell(
                    label: String(localized: "diet.nutrient.protein"),
                    value: String(format: "%.1f", detail.totalProteinG ?? 0),
                    unit: "g",
                    color: .blue
                )
                Divider().frame(height: 40)
                NutritionStatCell(
                    label: String(localized: "diet.nutrient.carbs"),
                    value: String(format: "%.1f", detail.totalCarbsG ?? 0),
                    unit: "g",
                    color: .orange
                )
                Divider().frame(height: 40)
                NutritionStatCell(
                    label: String(localized: "diet.nutrient.fat"),
                    value: String(format: "%.1f", detail.totalFatG ?? 0),
                    unit: "g",
                    color: .pink
                )
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 4, y: 2)
    }

    private func recommendedProgressCard(detail: DietLogDetailResponse) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("일일 권장량 대비")
                .font(.subheadline.bold())
                .foregroundColor(Color.brandAccent)
            VStack(spacing: 12) {
                MacroProgressRow(
                    label: String(localized: "diet.nutrient.calories"),
                    current: detail.totalCalories ?? 0,
                    goal: viewModel.dailyCalorieGoal,
                    unit: "kcal",
                    color: .brandAccent
                )
                MacroProgressRow(
                    label: String(localized: "diet.nutrient.protein"),
                    current: detail.totalProteinG ?? 0,
                    goal: viewModel.dailyProteinGoal,
                    unit: "g",
                    color: .blue
                )
                MacroProgressRow(
                    label: String(localized: "diet.nutrient.carbs"),
                    current: detail.totalCarbsG ?? 0,
                    goal: viewModel.dailyCarbsGoal,
                    unit: "g",
                    color: .orange
                )
                MacroProgressRow(
                    label: String(localized: "diet.nutrient.fat"),
                    current: detail.totalFatG ?? 0,
                    goal: viewModel.dailyFatGoal,
                    unit: "g",
                    color: .pink
                )
            }
            Text("이 식사가 오늘 권장량에서 차지하는 비율입니다.")
                .font(.caption)
                .foregroundColor(Color.textTertiary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 4, y: 2)
    }

    private func entriesSection(detail: DietLogDetailResponse) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("식품 목록")
                .font(.subheadline.bold())
                .foregroundColor(Color.textSecondary)
            VStack(spacing: 1) {
                ForEach(Array(detail.entries.enumerated()), id: \.element.id) { idx, entry in
                    FoodEntryRow(entry: entry)
                    if idx < detail.entries.count - 1 {
                        Divider().padding(.leading, 56) // design-lint:ignore — micro/hero spacing
                    }
                }
            }
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .shadow(color: .black.opacity(0.05), radius: 4, y: 2)
        }
    }

    private func notesCard(notes: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("메모", systemImage: "note.text")
                .font(.subheadline.bold())
                .foregroundColor(Color.textSecondary)
            Text(notes)
                .font(.body)
                .foregroundColor(Color.textHeadline)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 4, y: 2)
    }
}

// MARK: - MacroProgressRow

private struct MacroProgressRow: View {
    let label: String
    let current: Double
    let goal: Double
    let unit: String
    let color: Color

    private var progress: Double { min(current / max(goal, 1), 1.0) }
    private var percent: Int { Int((current / max(goal, 1) * 100).rounded()) }
    private var isExceeded: Bool { current > goal && goal > 0 }

    var body: some View {
        VStack(spacing: 5) {
            HStack(alignment: .lastTextBaseline) {
                Text(label)
                    .font(.captionBold)
                    .foregroundStyle(Color.textSecondary)
                Spacer()
                HStack(alignment: .lastTextBaseline, spacing: 4) {
                    Text("\(percent)%")
                        .font(.subheadline.bold())
                        .foregroundStyle(isExceeded ? Color.brandDanger : Color.textPrimary)
                    Text("\(Int(current.rounded())) / \(Int(goal))\(unit)")
                        .font(.captionXSmall)
                        .foregroundStyle(Color.textTertiary)
                }
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill((isExceeded ? Color.brandDanger : color).opacity(0.12))
                        .frame(height: 6)
                    Capsule()
                        .fill(isExceeded ? Color.brandDanger : color)
                        .frame(width: geo.size.width * progress, height: 6)
                        .animation(.spring(response: 0.8, dampingFraction: 0.82), value: progress)
                }
            }
            .frame(height: 6)
            .accessibilityHidden(true)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            "\(label): 권장량의 \(percent) 퍼센트, \(Int(current.rounded()))\(unit) / \(Int(goal))\(unit)"
            + (isExceeded ? ", " + String(localized: "diet.detail.macro.exceeded.a11y") : "")
        )
    }
}

// MARK: - DietDetailHeader (Wave 헤더)

private struct DietDetailHeader: View {
    let detail: DietLogDetailResponse

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.brandPrimary
            DietDetailWaveCurve()
                .fill(Color.backgroundPage)
                .frame(height: 40)
                .offset(y: 1)

            VStack(spacing: 6) {
                Image(systemName: detail.mealType.sfSymbol)
                    .font(.system(size: 40)) // design-lint:ignore — SF Symbol hero icon sizing
                Text(detail.mealType.displayName)
                    .font(.title2.bold())
                    .foregroundColor(.white)
                Text(formattedDate(detail.logDate))
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
            }
            .padding(.top, 80) // design-lint:ignore — micro/hero spacing
            .padding(.bottom, 48) // design-lint:ignore — micro/hero spacing
        }
        .frame(maxWidth: .infinity)
    }

    private func formattedDate(_ s: String) -> String {
        let parts = s.split(separator: "-")
        guard parts.count == 3 else { return s }
        return {
            let parser = DateFormatter()
            parser.dateFormat = "yyyy-MM-dd"
            parser.locale = Locale(identifier: "en_US_POSIX")
            guard let date = parser.date(from: "\(parts[0])-\(parts[1])-\(parts[2])") else {
                return "\(parts[0])-\(parts[1])-\(parts[2])"
            }
            let display = DateFormatter()
            display.locale = LocaleManager.shared.effectiveLocale
            display.dateFormat = String(localized: "diet.date.format.longKR")
            return display.string(from: date)
        }()
    }
}

private struct DietDetailWaveCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.maxY))
        path.addCurve(
            to: CGPoint(x: rect.maxX, y: rect.maxY),
            control1: CGPoint(x: rect.width * 0.3, y: rect.minY),
            control2: CGPoint(x: rect.width * 0.7, y: rect.minY)
        )
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

// MARK: - NutritionStatCell

private struct NutritionStatCell: View {
    let label: String
    let value: String
    let unit: String
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.subheadline.bold())
                .foregroundColor(color)
            Text(unit)
                .font(.caption2)
                .foregroundColor(Color.textSecondary)
            Text(label)
                .font(.caption2)
                .foregroundColor(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - FoodEntryRow

private struct FoodEntryRow: View {
    let entry: FoodEntryResponse

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: entry.category?.sfSymbol ?? "fork.knife")
                .font(.title3)
                .frame(width: 40, height: 40)
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))

            VStack(alignment: .leading, spacing: 3) {
                Text(entry.displayName)
                    .font(.subheadline.bold())
                Text(String(format: "%.0fg", entry.servingG))
                    .font(.caption)
                    .foregroundColor(Color.textSecondary)
            }
            Spacer()
            Text(entry.calories.map { String(format: "%.0f kcal", $0) } ?? "-")
                .font(.subheadline.bold())
                .foregroundColor(.brandAccent)
        }
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
    }
}

private extension Optional where Wrapped == Double {
    func map(_ transform: (Double) -> String) -> String? {
        guard let self = self else { return nil }
        return transform(self)
    }
}
