import SwiftUI

struct DietRecordView: View {
    @EnvironmentObject private var container: AppContainer
    @StateObject private var viewModel = DietRecordViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Color.backgroundPage.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 0) {
                    DietHeroSection(viewModel: viewModel, onDismiss: { dismiss() })
                    VStack(spacing: 20) {
                        todayNutritionBar
                        recommendationEntryCard
                        if viewModel.isLoading {
                            ProgressView().padding(.top, Spacing.xxxl)
                        } else if viewModel.todayLogs.isEmpty {
                            emptyState
                        } else {
                            logListSection
                        }
                    }
                    .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                    .padding(.top, Spacing.xl) // design-lint:ignore — micro/hero spacing
                    .padding(.bottom, 80) // design-lint:ignore — micro/hero spacing
                }
            }
            .ignoresSafeArea(edges: .top)
            .refreshable {
                await viewModel.loadLogs(apiClient: container.apiClient)
                viewModel.errorMessage = nil
            }

            if !viewModel.todayLogs.isEmpty {
                fabButton
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $viewModel.showAddLog) {
            AddDietLogView(remainingCalories: viewModel.remainingCalories) {
                viewModel.showAddLog = false
            }
            .environmentObject(container)
        }
        .onChange(of: viewModel.showAddLog) { isPresented in
            if !isPresented {
                Task {
                    await viewModel.loadLogs(apiClient: container.apiClient)
                }
            }
        }
        .onAppear {
            Task { await viewModel.loadLogs(apiClient: container.apiClient) }
        }
        .onReceive(NotificationCenter.default.publisher(for: .dietRecordChanged)) { _ in
            Task { await viewModel.loadLogs(apiClient: container.apiClient) }
        }
        .alert(Text("오류"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button(String(localized: "common.ok"), role: .cancel) { viewModel.errorMessage = nil }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }

    // MARK: - 오늘 영양소 바

    private var todayNutritionBar: some View {
        VStack(spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("오늘 섭취")
                        .font(.caption)
                        .foregroundColor(Color.textSecondary)
                    Text(String(format: "%.0f kcal", viewModel.todayCalories))
                        .font(.title2.bold())
                        .foregroundColor(Color.brandAccent)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(viewModel.remainingCalories >= 0 ? String(localized: "diet.summary.remaining") : String(localized: "diet.summary.exceeded"))
                        .font(.caption)
                        .foregroundColor(Color.textSecondary)
                    Text(String(format: "%.0f kcal", abs(viewModel.remainingCalories)))
                        .font(.subheadline.bold())
                        .foregroundColor(viewModel.remainingCalories >= 0
                            ? Color.textHeadline
                            : Color.brandDanger)
                }
            }
            // 칼로리 프로그레스 바
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: Radius.sm)
                        .fill(Color.hairline)
                        .frame(height: 8)
                    RoundedRectangle(cornerRadius: Radius.sm)
                        .fill(
                            LinearGradient(
                                colors: [.brandAccent, .brandPrimary],
                                startPoint: .leading, endPoint: .trailing
                            )
                        )
                        .frame(width: geo.size.width * viewModel.calorieProgress, height: 8)
                        .animation(.spring(response: 0.5), value: viewModel.calorieProgress)
                }
            }
            .frame(height: 8)

            // 3대 영양소 행
            HStack(spacing: 0) {
                MacroProgressCell(
                    label: String(localized: "diet.nutrient.protein"),
                    current: viewModel.todayProteinG,
                    goal: viewModel.dailyProteinGoal,
                    progress: viewModel.proteinProgress,
                    color: .blue
                )
                Divider().frame(height: 36)
                MacroProgressCell(
                    label: String(localized: "diet.nutrient.carbs"),
                    current: viewModel.todayCarbsG,
                    goal: viewModel.dailyCarbsGoal,
                    progress: viewModel.carbsProgress,
                    color: .orange
                )
                Divider().frame(height: 36)
                MacroProgressCell(
                    label: String(localized: "diet.nutrient.fat"),
                    current: viewModel.todayFatG,
                    goal: viewModel.dailyFatGoal,
                    progress: viewModel.fatProgress,
                    color: .pink
                )
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.06), radius: 6, y: 3)
    }

    private var recommendationEntryCard: some View {
        NavigationLink {
            DietRecommendationView()
                .environmentObject(container)
        } label: {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(Color.brandPrimary.opacity(0.12))
                        .frame(width: 44, height: 44)
                    Image(systemName: "sparkles")
                        .font(.headingMedium)
                        .foregroundColor(Color.brandPrimary)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(String(localized: "diet.recommendationCard.title"))
                        .font(.subheadline.bold())
                        .foregroundColor(Color.textPrimary)
                    Text(recommendationEntrySubtitle)
                        .font(.caption)
                        .foregroundColor(Color.textSecondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.bold())
                    .foregroundColor(Color.textTertiary)
            }
            .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .shadow(color: .black.opacity(0.04), radius: 4, y: 2)
        }
        .buttonStyle(.plain)
    }

    private var recommendationEntrySubtitle: String {
        if viewModel.todayLogs.isEmpty {
            return String(localized: "diet.recommendationCard.emptySubtitle")
        }
        return String(
            format: String(localized: "diet.recommendationCard.remainingSubtitle"),
            max(viewModel.remainingCalories, 0)
        )
    }

    // MARK: - 오늘 식단 기록 리스트

    private var logListSection: some View {
        VStack(spacing: 8) {
            ForEach(viewModel.todaySortedLogs) { log in
                NavigationLink(destination:
                    DietLogDetailView(logId: log.dietLogId, mealType: log.mealType, logDate: log.logDate)
                ) {
                    DietLogCard(log: log)
                }
                .buttonStyle(.plain)
                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                    Button(role: .destructive) {
                        Task { await viewModel.deleteLog(id: log.dietLogId, apiClient: container.apiClient) }
                    } label: {
                        Label(String(localized: "common.delete.button"), systemImage: "trash")
                    }
                }
            }
        }
    }

    // MARK: - 빈 상태

    private var emptyState: some View {
        EmptyState(
            icon: "fork.knife.circle",
            title: String(localized: "diet.empty.title"),
            message: String(localized: "diet.empty.message"),
            action: .init(label: String(localized: "diet.empty.action")) {
                viewModel.showAddLog = true
            }
        )
    }

    // MARK: - FAB

    private var fabButton: some View {
        Button {
            viewModel.showAddLog = true
        } label: {
            Image(systemName: "plus")
                .font(.headingLarge)
                .foregroundColor(.white)
                .frame(width: 56, height: 56)
                .background(Color.brandPrimary)
                .clipShape(Circle())
                .shadow(color: Color.brandPrimary.opacity(0.4), radius: 8, y: 4)
        }
        .padding(.trailing, Spacing.xl) // design-lint:ignore — micro/hero spacing
        .padding(.bottom, Spacing.xxl) // design-lint:ignore — micro/hero spacing
    }
}

// MARK: - DietHeroSection (Wave 헤더 + 링)

private struct DietHeroSection: View {
    @ObservedObject var viewModel: DietRecordViewModel
    let onDismiss: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            // 배경
            Color.brandPrimary
            DietWaveBackground()
                .fill(
                    LinearGradient(
                        colors: [Color.brandPrimary, Color(hex: "#2D6A4F")],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    )
                )
            // Wave 전환
            DietWaveCurve()
                .fill(Color.backgroundPage)
                .frame(height: 50)
                .offset(y: 1)

            VStack(spacing: 16) {
                // 헤더
                HStack {
                    Button(action: onDismiss) {
                        Image(systemName: "chevron.left")
                            .font(.cta)
                            .foregroundColor(.white)
                            .padding(Spacing.sm) // design-lint:ignore — micro/hero spacing
                            .background(Color.white.opacity(0.2))
                            .clipShape(Circle())
                    }
                    Spacer()
                    Text("식단 기록")
                        .font(.headline)
                        .foregroundColor(.white)
                    Spacer()
                    // 균형을 위한 빈 공간
                    Color.clear.frame(width: 36, height: 36)
                }
                .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .padding(.top, 56) // design-lint:ignore — micro/hero spacing

                // 오늘 요약 텍스트
                VStack(spacing: 4) {
                    Text(todayDisplayString())
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                    Text(String(format: "%.0f kcal", viewModel.todayCalories))
                        .font(.system(size: 40, weight: .bold, design: .rounded)) // design-lint:ignore — SF Symbol/hero
                        .foregroundColor(.white)
                    Text("오늘 섭취 칼로리")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                }

                // 식사별 현황 칩
                HStack(spacing: 8) {
                    ForEach(MealType.allCases, id: \.self) { mealType in
                        let count = viewModel.todayLogs.filter { $0.mealType == mealType }.count
                        MealStatusChip(type: mealType, count: count)
                    }
                }
                .padding(.bottom, 50) // design-lint:ignore — micro/hero spacing
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func todayDisplayString() -> String {
        let f = DateFormatter()
        f.locale = LocaleManager.resolvedLocale
        f.dateFormat = String(localized: "home.date.format")
        return f.string(from: Date())
    }
}

private struct MealStatusChip: View {
    let type: MealType
    let count: Int

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: type.sfSymbol).font(.caption)
            Text(count > 0 ? "✓" : type.displayName)
                .font(.caption2.bold())
                .foregroundColor(count > 0 ? .brandAccent : .white.opacity(0.7))
        }
        .padding(.horizontal, Spacing.md) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, 5) // design-lint:ignore — micro/hero spacing
        .background(count > 0 ? Color.white.opacity(0.95) : Color.white.opacity(0.15))
        .clipShape(Capsule())
    }
}

// MARK: - Wave Shapes

private struct DietWaveBackground: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: .zero)
        path.addLine(to: CGPoint(x: rect.maxX, y: 0))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: .init(x: 0, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

private struct DietWaveCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.maxY))
        path.addCurve(
            to: CGPoint(x: rect.maxX, y: rect.maxY),
            control1: CGPoint(x: rect.width * 0.25, y: rect.minY - 10),
            control2: CGPoint(x: rect.width * 0.75, y: rect.minY + 10)
        )
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

// MARK: - MacroProgressCell

private struct MacroProgressCell: View {
    let label: String
    let current: Double
    let goal: Double
    let progress: Double
    let color: Color

    var body: some View {
        VStack(spacing: 6) {
            Text(String(format: "%.0fg", current))
                .font(.subheadline.bold())
                .foregroundColor(color)
            Text("/ \(Int(goal))g")
                .font(.caption2)
                .foregroundColor(Color.textSecondary)
            Text(label)
                .font(.caption2)
                .foregroundColor(Color.textSecondary)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.hairline).frame(height: 4)
                    Capsule().fill(color)
                        .frame(width: geo.size.width * progress, height: 4)
                        .animation(.spring(response: 0.5), value: progress)
                }
            }
            .frame(height: 4)
            .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - DietLogCard

private struct DietLogCard: View {
    let log: DietLogSummary

    var body: some View {
        HStack(spacing: 14) {
            // 식사 유형 배지
            VStack(spacing: 4) {
                Image(systemName: log.mealType.sfSymbol)
                    .font(.title2)
                Text(log.mealType.displayName)
                    .font(.caption2.bold())
                    .foregroundColor(Color.brandAccent)
            }
            .frame(width: 52)
            .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.sm))

            VStack(alignment: .leading, spacing: 4) {
                Text(log.caloriesText)
                    .font(.subheadline.bold())
                    .foregroundColor(Color.brandAccent)
                // 3대 영양소 요약
                HStack(spacing: 8) {
                    if let p = log.totalProteinG {
                        MacroTag(label: "P", value: p, color: .blue)
                    }
                    if let c = log.totalCarbsG {
                        MacroTag(label: "C", value: c, color: .orange)
                    }
                    if let f = log.totalFatG {
                        MacroTag(label: "F", value: f, color: .pink)
                    }
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundColor(Color.textSecondary)
        }
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 4, y: 2)
    }
}

private struct MacroTag: View {
    let label: String
    let value: Double
    let color: Color

    var body: some View {
        Text("\(label) \(String(format: "%.0f", value))g")
            .font(.caption2.bold())
            .foregroundColor(color)
            .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, 2) // design-lint:ignore — micro/hero spacing
            .background(color.opacity(0.1))
            .clipShape(Capsule())
    }
}
