import SwiftUI

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                // ── Hero Header (dark green wave) ──────────────────────
                HeroHeaderView(viewModel: viewModel)

                // ── Body content ──────────────────────────────────────
                VStack(alignment: .leading, spacing: 28) {
                    HomeSectionView(title: "나의 플랜") {
                        PlanCardView(goal: viewModel.activeGoal)
                    }
                    HomeSectionView(title: "최근 식단") {
                        RecentMealsRowView(logs: viewModel.todayDietLogs)
                    }
                    HomeSectionView(title: "최근 운동") {
                        WorkoutRoutineCardView(session: viewModel.recentSessions.first)
                    }
                }
                .padding(.vertical, 24)
                .background(Color.surfacePrimary)
            }
        }
        .ignoresSafeArea(edges: .top)
        .background(Color.surfacePrimary)
        .task { await viewModel.loadDashboard(apiClient: container.apiClient) }
        .refreshable { await viewModel.loadDashboard(apiClient: container.apiClient) }
    }
}

// MARK: - Hero Header

private struct HeroHeaderView: View {
    @ObservedObject var viewModel: HomeViewModel

    var body: some View {
        ZStack(alignment: .top) {
            WaveBackground()
                .frame(height: 310)

            VStack(spacing: 10) {
                // App title
                Text("VITALITY")
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .padding(.top, 44)

                // 오늘의 요약 — 풀너비
                SnapshotCard(
                    calorieProgress: viewModel.calorieProgress,
                    activityProgress: viewModel.activityProgress
                )
                .padding(.horizontal, 20)

                // 기록하기 CTA — 풀너비
                LogButton()
                    .padding(.horizontal, 20)
                    .padding(.top, 10)
            }
        }
    }
}

// MARK: - Wave Background Shape

private struct WaveBackground: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.brandPrimary
                Ellipse()
                    .fill(Color.brandSecondary.opacity(0.55))
                    .frame(width: geo.size.width * 0.75, height: geo.size.height * 0.65)
                    .offset(x: geo.size.width * 0.25, y: -geo.size.height * 0.12)
                    .rotationEffect(.degrees(-18))

                WaveCurve()
                    .fill(Color.surfacePrimary)
                    .frame(height: 80)
                    .frame(maxWidth: .infinity)
                    .offset(y: geo.size.height - 40)
            }
        }
    }
}

private struct WaveCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.height))
        path.addCurve(
            to: CGPoint(x: rect.width, y: rect.height),
            control1: CGPoint(x: rect.width * 0.25, y: 0),
            control2: CGPoint(x: rect.width * 0.75, y: rect.height * 0.6)
        )
        path.addLine(to: CGPoint(x: rect.width, y: rect.height))
        path.addLine(to: CGPoint(x: 0, y: rect.height))
        path.closeSubpath()
        return path
    }
}

// MARK: - Today's Snapshot Card

private struct SnapshotCard: View {
    let calorieProgress: Double
    let activityProgress: Double

    var body: some View {
        HStack(spacing: 0) {
            // 칼로리 링
            SnapshotRingItem(
                label: "칼로리",
                progress: calorieProgress,
                color: Color.brandAccent
            )

            Divider().frame(height: 52)

            // 활동 링
            SnapshotRingItem(
                label: "활동",
                progress: activityProgress,
                color: Color.brandPrimary
            )

            Spacer()

            // 오늘 날짜
            VStack(alignment: .trailing, spacing: 3) {
                Text("오늘의 요약")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.textSecondary)
                Text(todayShort())
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textSecondary.opacity(0.7))
            }
            .padding(.trailing, 4)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.surfacePrimary)
                .shadow(color: .black.opacity(0.10), radius: 12, x: 0, y: 4)
        )
    }

    private func todayShort() -> String {
        let f = DateFormatter()
        f.dateFormat = "M월 d일 E"
        f.locale = Locale(identifier: "ko_KR")
        return f.string(from: Date())
    }
}

private struct SnapshotRingItem: View {
    let label: String
    let progress: Double
    let color: Color

    var body: some View {
        HStack(spacing: 10) {
            ZStack {
                Circle()
                    .stroke(color.opacity(0.18), lineWidth: 7)
                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(color, style: StrokeStyle(lineWidth: 7, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .animation(.easeInOut(duration: 0.8), value: progress)
            }
            .frame(width: 52, height: 52)

            VStack(alignment: .leading, spacing: 2) {
                Text(String(format: "%.0f%%", progress * 100))
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.textPrimary)
                Text(label)
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textSecondary)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Log CTA Button

private struct LogButton: View {
    var body: some View {
        NavigationLink(destination: RecordHubView()) {
            HStack(spacing: 14) {
                // 아이콘 뱃지
                ZStack {
                    Circle()
                        .fill(.white.opacity(0.18))
                        .frame(width: 46, height: 46)
                    Image(systemName: "plus")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.white)
                }

                // 텍스트
                VStack(alignment: .leading, spacing: 3) {
                    Text("오늘의 기록 추가하기")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                    Text("운동 · 식단 · 신체 변화")
                        .font(.system(size: 12))
                        .foregroundStyle(.white.opacity(0.7))
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.6))
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(.white.opacity(0.14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(.white.opacity(0.22), lineWidth: 1)
                    )
            )
        }
    }
}

// MARK: - Section wrapper

private struct HomeSectionView<Content: View>: View {
    let title: String
    let content: () -> Content

    init(title: String, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Color.textPrimary)
                .padding(.horizontal, 20)

            content()
        }
    }
}

// MARK: - Plan Card

private struct PlanCardView: View {
    let goal: GoalSummary?

    var body: some View {
        NavigationLink(destination: GoalSettingView()) {
            ZStack(alignment: .bottomTrailing) {
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.brandSurface)
                    .frame(height: 140)

                Circle()
                    .fill(Color.brandAccent.opacity(0.3))
                    .frame(width: 120, height: 120)
                    .offset(x: 20, y: 30)

                if let goal {
                    // 활성 목표 있음
                    HStack(alignment: .center) {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 6) {
                                Text(goal.goalType.emoji)
                                    .font(.system(size: 16))
                                Text(goal.goalType.displayName)
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundStyle(Color.brandPrimary)
                            }
                            // 진행률 바
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Capsule()
                                        .fill(Color.brandAccent.opacity(0.2))
                                        .frame(height: 6)
                                    Capsule()
                                        .fill(Color.brandAccent)
                                        .frame(width: geo.size.width * goal.progressRatio, height: 6)
                                        .animation(.spring(response: 0.6), value: goal.progressRatio)
                                }
                            }
                            .frame(width: 140, height: 6)

                            if let days = goal.daysRemaining {
                                Text("D-\(days) · \(String(format: "%.0f", goal.progressRatio * 100))% 달성")
                                    .font(.system(size: 12))
                                    .foregroundStyle(Color.textSecondary)
                            }
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.brandPrimary.opacity(0.5))
                    }
                    .padding(20)
                } else {
                    // 목표 없음 — 설정 유도
                    HStack {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("목표 관리")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(Color.brandPrimary)
                            Text("목표를 설정하고 달성률을 확인하세요")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.textSecondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.brandPrimary.opacity(0.5))
                    }
                    .padding(20)
                }
            }
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 20)
        .clipped()
    }
}

// MARK: - Recent Meals Row

private struct RecentMealsRowView: View {
    let logs: [DietLogSummary]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 14) {
                if logs.isEmpty {
                    EmptyMealPlaceholder()
                } else {
                    ForEach(logs) { log in
                        NavigationLink(
                            destination: DietLogDetailView(
                                logId: log.dietLogId,
                                mealType: log.mealType,
                                logDate: log.logDate
                            )
                        ) {
                            MealLogCard(log: log)
                        }
                        .buttonStyle(.plain)
                    }
                }
                NavigationLink(destination: DietRecordView()) {
                    AddMealCard()
                }
            }
            .padding(.horizontal, 20)
        }
    }
}

private struct MealLogCard: View {
    let log: DietLogSummary

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color.brandSurface)
                    .frame(width: 130, height: 110)

                VStack(spacing: 6) {
                    Text(log.mealType.emoji)
                        .font(.system(size: 38))
                    Text(String(format: "%.0f kcal", log.totalCalories ?? 0))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.brandPrimary)
                }
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(log.mealType.displayName)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                if let p = log.totalProteinG, let c = log.totalCarbsG {
                    Text(String(format: "P %.0fg  C %.0fg", p, c))
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textSecondary)
                }
            }
            .frame(width: 130, height: 34, alignment: .topLeading)
            .padding(.top, 8)
        }
    }
}

private struct EmptyMealPlaceholder: View {
    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color.brandSurface.opacity(0.5))
                    .frame(width: 130, height: 110)

                VStack(spacing: 6) {
                    Text("🍽")
                        .font(.system(size: 38))
                        .opacity(0.4)
                    Text("아직 없어요")
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textSecondary)
                }
            }

            Text("오늘 첫 식사")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.textSecondary)
                .frame(width: 130, height: 34, alignment: .topLeading)
                .padding(.top, 8)
        }
    }
}

private struct AddMealCard: View {
    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.brandAccent.opacity(0.5),
                            style: StrokeStyle(lineWidth: 1.5, dash: [5]))
                    .frame(width: 130, height: 110)

                VStack(spacing: 6) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 28))
                        .foregroundStyle(Color.brandAccent)
                    Text("추가")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.brandAccent)
                }
            }

            Text("식단 추가")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.brandAccent)
                .frame(width: 130, height: 34, alignment: .topLeading)
                .padding(.top, 8)
        }
    }
}

// MARK: - Workout Routine Card

private struct WorkoutRoutineCardView: View {
    let session: SessionSummary?

    var body: some View {
        NavigationLink(destination: ExerciseRecordView()) {
            ZStack(alignment: .bottomTrailing) {
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.brandPrimary)
                    .frame(height: 140)

                Ellipse()
                    .fill(Color.brandSecondary.opacity(0.5))
                    .frame(width: 150, height: 100)
                    .rotationEffect(.degrees(-25))
                    .offset(x: 40, y: 20)

                HStack {
                    if let session {
                        // 실제 세션 데이터
                        VStack(alignment: .leading, spacing: 6) {
                            Text("최근 운동")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(.white.opacity(0.75))
                            Text(session.formattedDate)
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(.white)
                            HStack(spacing: 12) {
                                if let vol = session.totalVolumeKg {
                                    statChip(
                                        icon: "figure.strengthtraining.traditional",
                                        value: String(format: "%.0fkg", vol)
                                    )
                                }
                                if let cal = session.caloriesBurned {
                                    statChip(
                                        icon: "flame.fill",
                                        value: String(format: "%.0fkcal", cal)
                                    )
                                }
                                if let dur = session.durationMinutes {
                                    statChip(icon: "clock", value: "\(dur)분")
                                }
                            }
                        }
                    } else {
                        // 세션 없음 — 기록 유도
                        VStack(alignment: .leading, spacing: 6) {
                            Text("오늘의 운동")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(.white.opacity(0.75))
                            Text("운동을 기록해보세요")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(.white)
                            Text("기록 시작하기 →")
                                .font(.system(size: 12))
                                .foregroundStyle(.white.opacity(0.65))
                        }
                    }
                    Spacer()
                    Image(systemName: "arrow.right.circle.fill")
                        .font(.system(size: 36))
                        .foregroundStyle(.white.opacity(0.75))
                }
                .padding(20)
            }
            .padding(.horizontal, 20)
            .clipped()
        }
        .buttonStyle(.plain)
    }

    private func statChip(icon: String, value: String) -> some View {
        HStack(spacing: 3) {
            Image(systemName: icon)
                .font(.system(size: 10))
                .foregroundStyle(.white.opacity(0.7))
            Text(value)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(.white.opacity(0.85))
        }
    }
}
