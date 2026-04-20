import SwiftUI

struct DietRecordView: View {
    @EnvironmentObject private var container: AppContainer
    @StateObject private var viewModel = DietRecordViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Color.surfaceGrouped.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 0) {
                    DietHeroSection(viewModel: viewModel, onDismiss: { dismiss() })
                    VStack(spacing: 20) {
                        todayNutritionBar
                        if viewModel.isLoading {
                            ProgressView().padding(.top, 40)
                        } else if viewModel.todayLogs.isEmpty {
                            emptyState
                        } else {
                            logListSection
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 20)
                    .padding(.bottom, 80)
                }
            }
            .ignoresSafeArea(edges: .top)
            .refreshable { await viewModel.loadLogs(apiClient: container.apiClient) }

            if !viewModel.todayLogs.isEmpty {
                fabButton
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $viewModel.showAddLog) {
            AddDietLogView {
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
        .task { await viewModel.loadLogs(apiClient: container.apiClient) }
    }

    // MARK: - 오늘 영양소 바

    private var todayNutritionBar: some View {
        VStack(spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("오늘 섭취")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text(String(format: "%.0f kcal", viewModel.todayCalories))
                        .font(.title2.bold())
                        .foregroundColor(.brandPrimary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text("목표")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text(String(format: "%.0f kcal", DietRecordViewModel.dailyCalorieGoal))
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }
            // 칼로리 프로그레스 바
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color(.systemGray5))
                        .frame(height: 8)
                    RoundedRectangle(cornerRadius: 4)
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
                    label: "단백질",
                    current: viewModel.todayProteinG,
                    goal: DietRecordViewModel.dailyProteinGoal,
                    progress: viewModel.proteinProgress,
                    color: .blue
                )
                Divider().frame(height: 36)
                MacroProgressCell(
                    label: "탄수화물",
                    current: viewModel.todayCarbsG,
                    goal: DietRecordViewModel.dailyCarbsGoal,
                    progress: viewModel.carbsProgress,
                    color: .orange
                )
                Divider().frame(height: 36)
                MacroProgressCell(
                    label: "지방",
                    current: viewModel.todayFatG,
                    goal: DietRecordViewModel.dailyFatGoal,
                    progress: viewModel.fatProgress,
                    color: .pink
                )
            }
        }
        .padding(16)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.06), radius: 6, y: 3)
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
                        Label("삭제", systemImage: "trash")
                    }
                }
            }
        }
    }

    // MARK: - 빈 상태

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "fork.knife.circle")
                .font(.system(size: 56))
                .foregroundColor(.brandAccent.opacity(0.6))
                .padding(.top, 40)
            Text("아직 식단 기록이 없어요")
                .font(.headline)
                .foregroundColor(.primary)
            Text("오늘 먹은 음식을 기록해보세요.\n영양 목표 달성을 도와드립니다.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            Button {
                viewModel.showAddLog = true
            } label: {
                Label("첫 식사 기록하기", systemImage: "plus")
                    .font(.subheadline.bold())
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.brandPrimary)
                    .clipShape(Capsule())
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.bottom, 40)
    }

    // MARK: - FAB

    private var fabButton: some View {
        Button {
            viewModel.showAddLog = true
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 56, height: 56)
                .background(Color.brandPrimary)
                .clipShape(Circle())
                .shadow(color: Color.brandPrimary.opacity(0.4), radius: 8, y: 4)
        }
        .padding(.trailing, 20)
        .padding(.bottom, 24)
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
                .fill(Color.surfaceGrouped)
                .frame(height: 50)
                .offset(y: 1)

            VStack(spacing: 16) {
                // 헤더
                HStack {
                    Button(action: onDismiss) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(8)
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
                .padding(.horizontal, 16)
                .padding(.top, 56)

                // 오늘 요약 텍스트
                VStack(spacing: 4) {
                    Text(todayDisplayString())
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                    Text(String(format: "%.0f kcal", viewModel.todayCalories))
                        .font(.system(size: 40, weight: .bold, design: .rounded))
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
                .padding(.bottom, 50)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func todayDisplayString() -> String {
        let f = DateFormatter()
        f.dateFormat = "M월 d일 EEEE"
        f.locale = Locale(identifier: "ko_KR")
        return f.string(from: Date())
    }
}

private struct MealStatusChip: View {
    let type: MealType
    let count: Int

    var body: some View {
        HStack(spacing: 4) {
            Text(type.emoji).font(.caption)
            Text(count > 0 ? "✓" : type.displayName)
                .font(.caption2.bold())
                .foregroundColor(count > 0 ? .brandAccent : .white.opacity(0.7))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
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
                .foregroundColor(.secondary)
            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color(.systemGray5)).frame(height: 4)
                    Capsule().fill(color)
                        .frame(width: geo.size.width * progress, height: 4)
                        .animation(.spring(response: 0.5), value: progress)
                }
            }
            .frame(height: 4)
            .padding(.horizontal, 8)
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
                Text(log.mealType.emoji)
                    .font(.title2)
                Text(log.mealType.displayName)
                    .font(.caption2.bold())
                    .foregroundColor(.brandPrimary)
            }
            .frame(width: 52)
            .padding(.vertical, 10)
            .background(Color.brandSurface)
            .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 4) {
                Text(log.caloriesText)
                    .font(.subheadline.bold())
                    .foregroundColor(.brandPrimary)
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
                .foregroundColor(.secondary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
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
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.1))
            .clipShape(Capsule())
    }
}
