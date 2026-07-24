import SwiftUI

/// 목표 탭 내에서 특정 목표 진행 상세로 딥링크 push할 때 쓰는 값(#100, WIDGET_GOAL).
struct GoalDetailRoute: Hashable {
    let id: Int
}

struct MainTabView: View {
    @State private var selectedTab: Tab = .home
    @ObservedObject private var pushRouter = PushRouter.shared

    // NavigationPath — push 스택 리셋용
    @State private var homePath    = NavigationPath()
    @State private var diaryPath   = NavigationPath()
    @State private var recordPath  = NavigationPath()
    @State private var goalsPath   = NavigationPath()
    @State private var myPagePath  = NavigationPath()

    // 각 탭 root view의 id — 변경 시 SwiftUI가 view를 새로 만들어 ViewModel/스크롤 위치까지 초기화
    @State private var homeId    = UUID()
    @State private var diaryId   = UUID()
    @State private var recordId  = UUID()
    @State private var goalsId   = UUID()
    @State private var myPageId  = UUID()

    enum Tab: Int, CaseIterable {
        // Explore 탭은 제거됨(#93). 목표 탭은 승격됨(#100).
        case home, diary, record, goals, myPage

        var titleKey: LocalizedStringKey {
            switch self {
            case .home:    return "tab.home"
            case .diary:   return "tab.diary"
            case .record:  return "tab.record"
            case .goals:   return "tab.goals"
            case .myPage:  return "tab.myPage"
            }
        }

        var systemImage: String {
            switch self {
            case .home:    return "square.grid.2x2.fill"
            case .diary:   return "calendar"
            case .record:  return "plus.circle.fill"
            case .goals:   return "target"
            case .myPage:  return "person.crop.circle"
            }
        }
    }

    var body: some View {
        TabView(selection: tabBinding) {
            NavigationStack(path: $homePath) {
                HomeView().id(homeId)
            }
            .tabItem { Label(Tab.home.titleKey, systemImage: Tab.home.systemImage) }
            .tag(Tab.home)

            NavigationStack(path: $diaryPath) {
                DiaryView().id(diaryId)
            }
            .tabItem { Label(Tab.diary.titleKey, systemImage: Tab.diary.systemImage) }
            .tag(Tab.diary)

            NavigationStack(path: $recordPath) {
                RecordHubView(showsDismissButton: false).id(recordId)
            }
            .tabItem { Label(Tab.record.titleKey, systemImage: Tab.record.systemImage) }
            .tag(Tab.record)

            NavigationStack(path: $goalsPath) {
                GoalSettingView()
                    .id(goalsId)
                    // WIDGET_GOAL 딥링크가 특정 목표 진행 상세로 직행할 수 있게 값 기반 destination 등록.
                    // GoalSettingView 내부의 NavigationLink(destination:)와 같은 스택에서 공존한다.
                    .navigationDestination(for: GoalDetailRoute.self) { route in
                        GoalProgressView(goalId: route.id)
                    }
            }
            .tabItem { Label(Tab.goals.titleKey, systemImage: Tab.goals.systemImage) }
            .tag(Tab.goals)

            NavigationStack(path: $myPagePath) {
                MyPageView().id(myPageId)
            }
            .tabItem { Label(Tab.myPage.titleKey, systemImage: Tab.myPage.systemImage) }
            .tag(Tab.myPage)
        }
        .tint(Color.brandPrimary)
        // 푸시 라우팅: PushRouter에 쌓인 pending route를 onAppear / onChange에서 소비.
        // .onReceive(NotificationCenter)는 cold-start race condition이 있어 제거됨.
        .onAppear {
            print("[MainTabView] onAppear — pending=\(pushRouter.pendingRoute ?? "nil")")
            processPendingPushRoute()
        }
        .onChange(of: pushRouter.pendingRoute) { newValue in
            print("[MainTabView] pendingRoute changed → \(newValue ?? "nil")")
            processPendingPushRoute()
        }
    }

    private func processPendingPushRoute() {
        guard let type = pushRouter.consume() else { return }
        handlePushRoute(type: type)
    }

    /// 같은 탭을 다시 눌렀을 때만 path/view id를 리셋한다(root로 돌아가기).
    /// 다른 탭에서 전환해 들어오는 경우엔 기존 view를 유지해 캐시된 데이터가 즉시 보이도록 하고,
    /// 매번 4개 병렬 API 호출이 발생해 cold-start 부담이 누적되는 문제를 막는다.
    private var tabBinding: Binding<Tab> {
        Binding(
            get: { selectedTab },
            set: { newTab in
                if newTab == selectedTab {
                    resetTab(newTab)
                }
                selectedTab = newTab
            }
        )
    }

    private func resetTab(_ tab: Tab) {
        switch tab {
        case .home:
            homePath = NavigationPath()
            homeId = UUID()
        case .diary:
            diaryPath = NavigationPath()
            diaryId = UUID()
        case .record:
            recordPath = NavigationPath()
            recordId = UUID()
        case .goals:
            goalsPath = NavigationPath()
            goalsId = UUID()
        case .myPage:
            myPagePath = NavigationPath()
            myPageId = UUID()
        }
    }

    private func handlePushRoute(type: String) {
        print("[MainTabView] handlePushRoute type=\(type)")
        switch type {
        case "WIDGET_CALORIE":
            // 칼로리 위젯 탭 → 기록 탭의 식단 기록 화면으로 직진.
            // 식사 리마인더 푸시와 동일한 패턴 (AddDietLogView가 시간대 기반 mealType 자동 선택).
            recordPath = NavigationPath([RecordDestination.diet])
            selectedTab = .record
        case "WIDGET_GOAL":
            // 목표 위젯 → 목표 탭(#100). 위젯 스냅샷 캐시에서 goalId를 읽어 진행 상세로 직행.
            // 활성 목표가 없으면 목표 탭 root(GoalSettingView 목록)로.
            let snapshot = WidgetDataStore()?.loadGoal()
            if let goalId = snapshot?.goal?.goalId {
                goalsPath = NavigationPath([GoalDetailRoute(id: goalId)])
            } else {
                goalsPath = NavigationPath()
            }
            selectedTab = .goals
        case "WIDGET_STREAK":
            // 스트릭 위젯 → 홈 탭으로.
            homePath = NavigationPath()
            selectedTab = .home
        case "WEEKLY_SUMMARY":
            // 주간회고는 홈에서 진입(#93, Explore 탭 제거). homePath에 destination을 push.
            homePath = NavigationPath([HomeDestination.weeklyRetrospective])
            selectedTab = .home
        case "MEAL_BREAKFAST_REMINDER",
             "MEAL_LUNCH_REMINDER",
             "MEAL_DINNER_REMINDER":
            // 식사 시간대 미기록 리마인더 → 기록 탭의 식단 기록 화면으로 직진.
            // AddDietLogView가 sheet로 열릴 때 시간대 기반으로 mealType이 자동 선택된다.
            recordPath = NavigationPath([RecordDestination.diet])
            selectedTab = .record
        case "DAILY_LOG_REMINDER":
            // 하루 전체 미기록 리마인더 → 기록 허브로 이동(사용자가 무엇을 기록할지 선택).
            recordPath = NavigationPath()
            selectedTab = .record
        default:
            print("[MainTabView] handlePushRoute — 매핑되지 않은 type: \(type)")
            break
        }
    }
}
