import GoogleMobileAds
import SwiftUI

// MARK: - Home Navigation Destinations
//
// 위젯 딥링크에서 homePath에 push할 수 있도록 value 기반 enum으로 노출.
// MainTabView.handlePushRoute가 캐시에서 goalId를 읽어 .goalDetail(id:)를 append.

enum HomeDestination: Hashable {
    case goalSetting           // 활성 목표 없을 때 — 목표 목록/추가 화면
    case goalDetail(id: Int)   // 활성 목표 상세 (GoalProgressView)
    case weeklyRetrospective   // 주간 회고 (구 Explore 탭 → 홈 흡수, #93)
    case changeAnalysis        // 변화 분석 (구 Explore 탭 → 홈 흡수, #93)
}

// MARK: - HomeView (대시보드)
//
// 기록 진입점 중심 → 활동 진행현황 대시보드 중심으로 재구성.
// 스크롤 없이 첫 화면에서 칼로리·매크로·운동·연속일·목표를 모두 파악 가능.

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    @EnvironmentObject private var container: AppContainer
    @State private var showCalorieExplanation = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    // 1. 대시보드 헤더
                    DashboardHeaderBar()
                        .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                        .padding(.top, Spacing.sm) // design-lint:ignore — micro/hero spacing

                    // 2. 메인 링 패널 (칼로리 + 운동 + 단백질)
                    ActivityRingPanel(
                        calorieProgress:   viewModel.calorieProgress,
                        activityProgress:  viewModel.activityProgress,
                        proteinProgress:   viewModel.proteinProgress,
                        todayCalories:     viewModel.todayCalories,
                        dailyCalorieGoal:  viewModel.dailyCalorieGoal,
                        todayDurationMinutes: viewModel.todayDurationMinutes,
                        todayBurnedCalories:  viewModel.todayBurnedCalories,
                        todayProteinG:    viewModel.todayProteinG,
                        dailyProteinGoal: viewModel.dailyProteinGoal,
                        onWhyTapped: viewModel.userProfile != nil
                            ? { showCalorieExplanation = true }
                            : nil
                    )

                    // 3. 매크로 + 연속일 — 2열 그리드
                    HStack(alignment: .top, spacing: 12) {
                        MacroBreakdownCard(
                            proteinG:    viewModel.todayProteinG,
                            carbsG:      viewModel.todayCarbsG,
                            fatG:        viewModel.todayFatG,
                            proteinGoal: viewModel.dailyProteinGoal,
                            carbsGoal:   viewModel.dailyCarbsGoal,
                            fatGoal:     viewModel.dailyFatGoal
                        )
                        .frame(maxWidth: .infinity)

                        StreakCard(
                            streakDays:     viewModel.streakDays,
                            weeklyActivity: viewModel.weeklyActivity
                        )
                        .frame(maxWidth: .infinity)
                    }
                    .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing

                    // 4. 주간 추세
                    WeeklyTrendCard(weeklyActivity: viewModel.weeklyActivity)

                    // 5. 목표 진행
                    GoalProgressCard(goal: viewModel.activeGoal)

                    // 6. 최근 식단 (기존 카드 재사용, 헤더 간소화)
                    MealsSectionCompact(logs: viewModel.todayDietLogs)

                    // 7. 최근 운동 (기존 카드 재사용, 높이 축소)
                    WorkoutSectionCompact(session: viewModel.recentSessions.first(where: { $0.sessionDate == viewModel.today }))

                    // 8. 이번 주 인사이트 (구 Explore 탭 흡수, #93) — 주간회고·변화분석 진입
                    HomeInsightsSection()

                    Spacer(minLength: 100)
                }
                .padding(.top, Spacing.xs) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.backgroundPage.ignoresSafeArea())
            .overlay(alignment: .center) {
                if viewModel.isLoading {
                    ProgressView()
                        .tint(Color.brandAccentGlow)
                        .scaleEffect(1.4)
                        .padding(Spacing.xxl) // design-lint:ignore — micro/hero spacing
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: Radius.lg))
                }
            }
            .alert(Text("오류"), isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("확인", role: .cancel) { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .safeAreaInset(edge: .bottom) {
                BannerAdView(adUnitID: AdsManager.shared.bannerAdUnitID)
                    .frame(height: 50)
            }
            .sheet(isPresented: $showCalorieExplanation) {
                if let profile = viewModel.userProfile {
                    CalorieExplanationView(
                        profile: profile,
                        goalType: viewModel.activeGoal?.goalType
                    )
                }
            }
            // .task는 view 생명주기에 묶여 있어 NavigationLink pop 시 재실행되지 않는다.
            // resetTab이 탭 전환마다 homeId를 새 UUID로 교체해 view를 재생성하므로
            // 탭 전환 시에는 항상 새로 로드된다. 이전의 .onAppear는 child push→pop 시에도
            // 재실행되어 두 번째 loadDashboard가 실패하면 데이터는 있는데 에러 alert이 뜨는
            // 버그를 일으켰다.
            .navigationDestination(for: HomeDestination.self) { dest in
                switch dest {
                case .goalSetting:
                    GoalSettingView()
                        .environmentObject(container)
                case .goalDetail(let id):
                    GoalProgressView(goalId: id)
                        .environmentObject(container)
                case .weeklyRetrospective:
                    WeeklyRetrospectiveView()
                        .environmentObject(container)
                case .changeAnalysis:
                    ChangeAnalysisView()
                        .environmentObject(container)
                }
            }
            .task { await viewModel.loadDashboard(apiClient: container.apiClient) }
            .refreshable {
                await viewModel.loadDashboard(apiClient: container.apiClient)
                // pull-to-refresh로 인한 실패는 alert으로 알리지 않음(기존 화면 데이터 유지).
                viewModel.errorMessage = nil
            }
            // 식단/운동/체중 기록이 변경되면 dashboard를 재로드해 위젯 스냅샷까지 갱신한다.
            // HomeView가 mount 상태일 때만 동작 — unmount이면 다음 .task에서 자연 처리.
            .onReceive(NotificationCenter.default.publisher(for: .dietRecordChanged)) { _ in
                Task { await viewModel.loadDashboard(apiClient: container.apiClient) }
            }
            .onReceive(NotificationCenter.default.publisher(for: .exerciseRecordChanged)) { _ in
                Task { await viewModel.loadDashboard(apiClient: container.apiClient) }
            }
            .onReceive(NotificationCenter.default.publisher(for: .bodyMeasurementDidChange)) { _ in
                Task { await viewModel.loadDashboard(apiClient: container.apiClient) }
            }

            // 8. 기록 FAB (기존 LogCTASection 대체)
            QuickLogFAB()
        }
    }
}

// MARK: - Dashboard Header Bar

private struct DashboardHeaderBar: View {
    @EnvironmentObject private var container: AppContainer
    @Environment(\.scenePhase) private var scenePhase
    @State private var showNotifications = false
    @State private var unreadCount: Int = 0

    private var greetingText: String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<12:  return String(localized: "home.greeting.morning")
        case 12..<17: return String(localized: "home.greeting.afternoon")
        case 17..<21: return String(localized: "home.greeting.evening")
        default:      return String(localized: "home.greeting.night")
        }
    }

    private var dateText: String {
        let f = DateFormatter()
        f.locale = LocaleManager.shared.effectiveLocale
        f.dateFormat = String(localized: "home.date.format")
        return f.string(from: Date())
    }

    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 3) {
                Text(dateText)
                    .font(.captionXSmall).fontWeight(.semibold)
                    .tracking(0.5)
                    .foregroundStyle(Color.textTertiary)
                Text(greetingText)
                    .font(.numeralMedium).fontWeight(.bold)
                    .foregroundStyle(Color.textHeadline)
            }

            Spacer()

            NotificationBellButton(unreadCount: unreadCount) {
                showNotifications = true
            }
        }
        .sheet(isPresented: $showNotifications, onDismiss: {
            Task { await refreshUnreadCount() }
        }) {
            NavigationStack {
                NotificationsView().environmentObject(container)
            }
        }
        .task { await refreshUnreadCount() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { Task { await refreshUnreadCount() } }
        }
    }

    private func refreshUnreadCount() async {
        struct Response: Decodable { let count: Int }
        let response: Response? = try? await container.apiClient.request(.getNotificationsUnreadCount)
        if let response { unreadCount = response.count }
    }
}

// MARK: - Notification Bell Button

private struct NotificationBellButton: View {
    let unreadCount: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "bell")
                .font(.bodyMedium).fontWeight(.semibold)
                .foregroundStyle(Color.textHeadline.opacity(0.75))
                .frame(width: 38, height: 38)
                .background(
                    Circle()
                        .fill(Color.surfaceCard)
                        .overlay(Circle().stroke(Color.cardStroke, lineWidth: 1))
                )
                .overlay(alignment: .topTrailing) {
                    if unreadCount > 0 {
                        Text(unreadCount > 99 ? "99+" : "\(unreadCount)")
                            .font(.captionXSmall).fontWeight(.bold)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 5) // design-lint:ignore — badge inner padding
                            .frame(minWidth: 18, minHeight: 18)
                            .background(Color.brandDanger)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(Color.surfaceCard, lineWidth: 1.5))
                            .offset(x: 6, y: -4)
                    }
                }
                .elevation(.low)
        }
        .accessibilityLabel(
            unreadCount > 0
                ? String(format: String(localized: "home.notification.bell.unread"), unreadCount)
                : String(localized: "home.notification.bell.empty")
        )
    }
}

// MARK: - Meals Section (compact)

private struct MealsSectionCompact: View {
    let logs: [DietLogSummary]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(title: String(localized: "home.section.meals.title"), eyebrow: "MEALS")
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 12) {
                    if logs.isEmpty {
                        EmptyMealCard()
                    } else {
                        ForEach(logs) { log in
                            NavigationLink(destination: DietLogDetailView(
                                logId: log.dietLogId,
                                mealType: log.mealType,
                                logDate: log.logDate
                            )) {
                                MealCard(log: log)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    NavigationLink(destination: DietRecordView()) {
                        AddMealCard()
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
            }
        }
    }
}

private struct MealCard: View {
    let log: DietLogSummary

    private var accessibilityDescription: String {
        var parts = ["\(log.mealType.displayName): \(String(format: "%.0f", log.totalCalories ?? 0)) kcal"]
        if let p = log.totalProteinG, let c = log.totalCarbsG {
            parts.append("단백질 \(String(format: "%.0f", p))g, 탄수화물 \(String(format: "%.0f", c))g")
        }
        return parts.joined(separator: ", ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                .fill(Color.surfaceCard)
                .frame(width: 140, height: 114)
                .overlay {
                    VStack(spacing: 8) {
                        Image(systemName: log.mealType.sfSymbol)
                            .font(.system(size: 36, weight: .medium)) // design-lint:ignore — SF Symbol icon sizing
                            .foregroundStyle(Color.brandAccent)
                            .accessibilityHidden(true)
                        Text(log.mealType.displayName)
                            .font(.system(size: 11, weight: .semibold)) // design-lint:ignore — SF Symbol icon label
                            .foregroundStyle(Color.textSecondary)
                    }
                }

            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .lastTextBaseline, spacing: 2) {
                    Text(String(format: "%.0f", log.totalCalories ?? 0))
                        .font(.numeralMedium).fontWeight(.heavy)
                        .foregroundStyle(Color.textHeadline)
                    Text("kcal")
                        .font(.captionXSmall)
                        .foregroundStyle(Color.textSecondary)
                }
                if let p = log.totalProteinG, let c = log.totalCarbsG {
                    Text(String(format: "P %.0f · C %.0f", p, c))
                        .font(.dataSmall).foregroundStyle(Color.textTertiary)
                }
            }
            .frame(width: 140, alignment: .leading)
            .padding(.top, 9) // design-lint:ignore — micro/hero spacing
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityDescription)
    }
}

private struct EmptyMealCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                    .fill(Color.backgroundPage)
                    .overlay(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                        .stroke(Color.textHeadline.opacity(0.08), style: StrokeStyle(lineWidth: 1, dash: [4])))
                VStack(spacing: 8) {
                    Image(systemName: "fork.knife")
                        .font(.system(size: 28, weight: .light)) // design-lint:ignore — SF Symbol size
                        .foregroundStyle(Color.textTertiary.opacity(0.6))
                    Text("기록이 아직 없어요").font(.caption).foregroundStyle(Color.textTertiary)
                }
            }
            .frame(width: 140, height: 114)
            Text("식단을 기록해 보세요")
                .font(.captionBold).foregroundStyle(Color.textSecondary)
                .frame(width: 140, alignment: .leading).padding(.top, 9) // design-lint:ignore — micro/hero spacing
        }
    }
}

private struct AddMealCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: Radius.lg, style: .continuous).fill(Color.brandDusk)
                VStack(spacing: 8) {
                    Image(systemName: "plus")
                        .font(.headingMedium).fontWeight(.heavy).foregroundStyle(Color.brandDusk)
                        .frame(width: 38, height: 38).background(Circle().fill(Color.brandAccentGlow))
                    Text("기록 추가").font(.captionBold).fontWeight(.bold).foregroundStyle(.white)
                }
            }
            .frame(width: 140, height: 114)
            Text("+ 새 식단")
                .font(.captionXSmall).fontWeight(.semibold).foregroundStyle(Color.textHeadline)
                .frame(width: 140, alignment: .leading).padding(.top, 9) // design-lint:ignore — micro/hero spacing
        }
    }
}

// MARK: - Workout Section (compact)

private struct WorkoutSectionCompact: View {
    let session: SessionSummary?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(title: String(localized: "home.section.exercise.title"), eyebrow: "EXERCISE")
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing

            NavigationLink(destination: ExerciseRecordView()) {
                WorkoutCompactCard(session: session)
            }
            .buttonStyle(.plain)
            .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
        }
    }
}

private struct WorkoutCompactCard: View {
    let session: SessionSummary?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                .fill(LinearGradient.forestHero)
            RadialGradient(
                colors: [Color.brandAccent.opacity(0.30), .clear],
                center: UnitPoint(x: 0.9, y: 0.15), startRadius: 10, endRadius: 200
            )
            .clipShape(RoundedRectangle(cornerRadius: Radius.xl, style: .continuous))

            HStack(alignment: .center) {
                if let session {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("COMPLETED")
                            .eyebrowStyle(Color.brandAccentGlow)
                        HStack(spacing: 10) {
                            if let dur = session.durationMinutes {
                                WorkoutChip(
                                    icon: "clock.fill",
                                    value: "\(dur)",
                                    unit: String(localized: "home.workout.duration.unit")
                                )
                            }
                            if let cal = session.caloriesBurned {
                                WorkoutChip(icon: "flame.fill", value: String(format: "%.0f", cal), unit: "kcal")
                            }
                            if let vol = session.totalVolumeKg {
                                WorkoutChip(icon: "dumbbell.fill", value: String(format: "%.0f", vol), unit: "kg")
                            }
                        }
                    }
                } else {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("READY")
                            .eyebrowStyle(Color.brandAccentGlow)
                        Text("오늘 운동을 시작해보세요")
                            .font(.headingSmall).fontWeight(.bold)
                            .foregroundStyle(.white)
                    }
                }

                Spacer()

                ZStack {
                    Circle().fill(Color.brandAccentGlow).frame(width: 46, height: 46)
                    Image(systemName: "arrow.up.right")
                        .font(.bodyLarge).fontWeight(.heavy)
                        .foregroundStyle(Color.brandDusk)
                }
                .accessibilityHidden(true)
            }
            .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
        .frame(height: 90)
        .elevation(.forest)
    }
}

private struct WorkoutChip: View {
    let icon: String; let value: String; let unit: String
    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: icon)
                .font(.system(size: 9, weight: .bold)) // design-lint:ignore — SF Symbol or hero numeric
                .foregroundStyle(Color.brandAccentGlow)
                .accessibilityHidden(true)
            HStack(alignment: .lastTextBaseline, spacing: 3) {
                Text(value).font(.system(size: 13, weight: .heavy, design: .rounded)).foregroundStyle(.white) // design-lint:ignore — 데이터 카운터
                Text(unit).font(.system(size: 9, weight: .medium)).foregroundStyle(.white.opacity(0.6)) // design-lint:ignore — 단위 라벨
            }
        }
        .padding(.horizontal, 9).padding(.vertical, 5) // design-lint:ignore — micro/hero spacing
        .background(Capsule().fill(Color.white.opacity(0.08)).overlay(Capsule().stroke(Color.white.opacity(0.13), lineWidth: 0.7)))
    }
}

// MARK: - Section Label

private struct SectionLabel: View {
    let title: String
    let eyebrow: String

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Rectangle()
                .fill(LinearGradient.sunrise)
                .frame(width: 18, height: 2)
                .offset(y: -3)
            VStack(alignment: .leading, spacing: 2) {
                Text(eyebrow).eyebrowStyle(Color.textTertiary)
                Text(title)
                    .font(.headingMedium).fontWeight(.bold)
                    .foregroundStyle(Color.textHeadline)
            }
            Spacer()
        }
    }
}
